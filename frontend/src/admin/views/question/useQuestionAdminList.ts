import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type TableInstance } from 'element-plus'
import { clearAssetCache } from '@/api/ai'
import { getAllCourses, type CourseVO } from '@/api/course'
import { deleteQuestion, getAdminQuestionPage, type QuestionVO } from '@/api/question'

export function useQuestionAdminList() {
  const questions = ref<QuestionVO[]>([])
  const questionTableRef = ref<TableInstance>()
  const selectedQuestions = ref<QuestionVO[]>([])
  const loading = ref(false)
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

  const fetchQuestions = async () => {
    loading.value = true
    try {
      const response = await getAdminQuestionPage({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        keyword: filters.keyword || undefined,
        questionType: filters.questionType || undefined,
        courseId: filters.courseId || undefined,
        difficulty: filters.difficulty || undefined,
        sourceType: filters.sourceType || undefined,
      })
      questions.value = response.data.records
      total.value = response.data.total
      selectedQuestions.value = []
    } catch {
      return
    } finally {
      loading.value = false
    }
  }

  const handleQuestionSelectionChange = (selection: QuestionVO[]) => {
    selectedQuestions.value = selection
  }
  const clearQuestionSelection = () => questionTableRef.value?.clearSelection()

  const handleDelete = async (id: number) => {
    try {
      await deleteQuestion(id)
      ElMessage.success('删除成功')
      await fetchQuestions()
    } catch {
      return
    }
  }

  const handleBulkDelete = async () => {
    if (!selectedQuestions.value.length) return
    try {
      await ElMessageBox.confirm(
        `确定删除选中的 ${selectedQuestions.value.length} 道题目？此操作不可恢复。`,
        '批量删除题目',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
      )
      loading.value = true
      const targets = [...selectedQuestions.value]
      const results = await Promise.allSettled(targets.map((question) => deleteQuestion(question.id)))
      const failed = results.filter((result) => result.status === 'rejected').length
      if (failed) ElMessage.warning(`已删除 ${targets.length - failed} 道题，${failed} 道处理失败`)
      else ElMessage.success(`已删除 ${targets.length} 道题`)
      await fetchQuestions()
    } catch {
      return
    } finally {
      loading.value = false
    }
  }

  const handleClearAiCache = async (questionId: number) => {
    try {
      await clearAssetCache(questionId)
      ElMessage.success('AI 学习资产缓存已清除')
    } catch {
      ElMessage.error('清除失败')
    }
  }

  const handleBulkClearAiCache = async () => {
    if (!selectedQuestions.value.length) return
    try {
      await ElMessageBox.confirm(
        `确定清除选中 ${selectedQuestions.value.length} 道题目的 AI 学习资产缓存？`,
        '批量清除缓存',
        { type: 'warning', confirmButtonText: '清除', cancelButtonText: '取消' },
      )
      loading.value = true
      const targets = [...selectedQuestions.value]
      const results = await Promise.allSettled(targets.map((question) => clearAssetCache(question.id)))
      const failed = results.filter((result) => result.status === 'rejected').length
      if (failed) ElMessage.warning(`已清除 ${targets.length - failed} 道题缓存，${failed} 道处理失败`)
      else ElMessage.success(`已清除 ${targets.length} 道题缓存`)
      clearQuestionSelection()
    } catch {
      return
    } finally {
      loading.value = false
    }
  }

  onMounted(() => {
    void fetchQuestions()
    getAllCourses()
      .then((response) => (courseList.value = response.data))
      .catch(() => undefined)
  })

  return {
    questions,
    questionTableRef,
    selectedQuestions,
    loading,
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
