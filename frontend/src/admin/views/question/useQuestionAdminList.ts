import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type TableInstance } from 'element-plus'
import { clearAssetCache } from '@/api/ai'
import { getAllCourses, type CourseVO } from '@/api/course'
import { deleteQuestion, getAdminQuestionPage, type QuestionVO } from '@/api/question'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import { errorMessage } from '@/utils/errors'

export function useQuestionAdminList() {
  const questions = ref<QuestionVO[]>([])
  const questionTableRef = ref<TableInstance>()
  const selectedQuestions = ref<QuestionVO[]>([])
  const loading = ref(true)
  const listError = ref('')
  const hasLoaded = ref(false)
  const actionPending = ref(false)
  const actionError = ref('')
  const pageNum = ref(1)
  const pageSize = ref(10)
  const total = ref(0)
  const filters = reactive({
    keyword: '',
    questionType: '',
    courseId: null as number | null,
    difficulty: null as number | null,
    sourceType: '',
  })
  const courseList = ref<CourseVO[]>([])
  let alive = true
  let listVersion = 0
  let actionVersion = 0
  const listCurrent = (version: number, session: number) =>
    alive && version === listVersion && session === getAuthSessionVersion()
  const actionCurrent = (version: number, session: number) =>
    alive && version === actionVersion && session === getAuthSessionVersion()

  const fetchQuestions = async () => {
    const version = ++listVersion
    const session = getAuthSessionVersion()
    loading.value = true
    listError.value = ''
    try {
      const response = await getAdminQuestionPage(
        {
          pageNum: pageNum.value,
          pageSize: pageSize.value,
          keyword: filters.keyword || undefined,
          questionType: filters.questionType || undefined,
          courseId: filters.courseId || undefined,
          difficulty: filters.difficulty || undefined,
          sourceType: filters.sourceType || undefined,
        },
        { errorDisplay: 'inline' },
      )
      if (!listCurrent(version, session)) return
      questions.value = response.data.records
      total.value = response.data.total
      selectedQuestions.value = []
      hasLoaded.value = true
    } catch (cause) {
      if (listCurrent(version, session)) listError.value = errorMessage(cause, '题目列表暂时无法读取，请重试。')
    } finally {
      if (listCurrent(version, session)) loading.value = false
    }
  }

  const handleQuestionSelectionChange = (selection: QuestionVO[]) => {
    selectedQuestions.value = selection
  }
  const clearQuestionSelection = () => questionTableRef.value?.clearSelection()
  async function runAction(label: string, work: () => Promise<number>) {
    if (actionPending.value) return
    const version = ++actionVersion
    const session = getAuthSessionVersion()
    actionPending.value = true
    actionError.value = ''
    try {
      const completed = await work()
      if (!actionCurrent(version, session)) return
      if (completed) ElMessage.success(label)
      await fetchQuestions()
    } catch (cause) {
      if (actionCurrent(version, session)) actionError.value = errorMessage(cause, `${label}失败，请重试。`)
    } finally {
      if (actionCurrent(version, session)) actionPending.value = false
    }
  }
  async function confirmAction(
    message: string,
    title: string,
    work: () => Promise<{ completed: number; failed: number }>,
  ) {
    if (actionPending.value) return
    const version = ++actionVersion
    const session = getAuthSessionVersion()
    actionPending.value = true
    actionError.value = ''
    try {
      await ElMessageBox.confirm(message, title, {
        type: 'warning',
        confirmButtonText: title.includes('删除') ? '删除' : '清除',
        cancelButtonText: '取消',
      })
      if (!actionCurrent(version, session)) return
      const result = await work()
      if (!actionCurrent(version, session)) return
      if (result.failed)
        actionError.value = `已${title.includes('删除') ? '删除' : '清除'} ${result.completed} 项，${result.failed} 项处理失败。`
      if (result.completed) ElMessage.success(title)
      if (title.includes('缓存')) clearQuestionSelection()
      await fetchQuestions()
    } catch (cause) {
      if (actionCurrent(version, session) && cause !== 'cancel' && cause !== 'close')
        actionError.value = errorMessage(cause, `${title}失败，请重试。`)
    } finally {
      if (actionCurrent(version, session)) actionPending.value = false
    }
  }
  const handleDelete = (id: number) =>
    confirmAction('确定删除该题目？此操作不可恢复。', '删除题目', async () => {
      await deleteQuestion(id, { errorDisplay: 'inline' })
      return { completed: 1, failed: 0 }
    })
  const handleBulkDelete = () => {
    const targets = [...selectedQuestions.value]
    if (!targets.length) return Promise.resolve()
    return confirmAction(`确定删除选中的 ${targets.length} 道题目？此操作不可恢复。`, '批量删除题目', async () => {
      const results = await Promise.allSettled(
        targets.map((question) => deleteQuestion(question.id, { errorDisplay: 'inline' })),
      )
      const failed = results.filter((result) => result.status === 'rejected').length
      return { completed: targets.length - failed, failed }
    })
  }
  const handleClearAiCache = (questionId: number) =>
    runAction('AI 学习资产缓存已清除', async () => {
      await clearAssetCache(questionId, { errorDisplay: 'inline' })
      return 1
    })
  const handleBulkClearAiCache = () => {
    const targets = [...selectedQuestions.value]
    if (!targets.length) return Promise.resolve()
    return confirmAction(`确定清除选中 ${targets.length} 道题目的 AI 学习资产缓存？`, '批量清除缓存', async () => {
      const results = await Promise.allSettled(
        targets.map((question) => clearAssetCache(question.id, { errorDisplay: 'inline' })),
      )
      const failed = results.filter((result) => result.status === 'rejected').length
      return { completed: targets.length - failed, failed }
    })
  }

  const stopSession = onAuthSessionChange(() => {
    listVersion++
    actionVersion++
    questions.value = []
    total.value = 0
    selectedQuestions.value = []
    loading.value = false
    listError.value = ''
    actionPending.value = false
    actionError.value = ''
    hasLoaded.value = false
  })
  onMounted(() => {
    void fetchQuestions()
    getAllCourses({ errorDisplay: 'inline' })
      .then((response) => {
        if (alive) courseList.value = response.data
      })
      .catch(() => undefined)
  })
  onBeforeUnmount(() => {
    alive = false
    listVersion++
    actionVersion++
    stopSession()
  })
  return {
    questions,
    questionTableRef,
    selectedQuestions,
    loading,
    listError,
    hasLoaded,
    actionPending,
    actionError,
    pageNum,
    pageSize,
    total,
    filters,
    courseList,
    fetchQuestions,
    handleQuestionSelectionChange,
    clearQuestionSelection,
    handleDelete,
    handleBulkDelete,
    handleClearAiCache,
    handleBulkClearAiCache,
  }
}
