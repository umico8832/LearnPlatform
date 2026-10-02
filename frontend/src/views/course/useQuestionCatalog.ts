import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { errorMessage } from '@/utils/errors'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import { getQuestionPage, submitQuestionCorrectionReport, type QuestionVO } from '@/api/question'
import { getAllCourses, type CourseVO } from '@/api/course'
import { addFavorite, getFavoriteIds, removeFavorite } from '@/api/favorite'

function positiveQueryId(value: unknown) {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return null
  const id = Number(value)
  return Number.isSafeInteger(id) ? id : null
}

function hasInvalidPositiveQueryId(value: unknown) {
  return value !== undefined && positiveQueryId(value) === null
}

const questionTypeValues = new Set(['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'FILL_BLANK', 'SHORT_ANSWER'])

function questionTypeQuery(value: unknown) {
  return typeof value === 'string' && questionTypeValues.has(value) ? value : ''
}

function difficultyQuery(value: unknown) {
  const difficulty = positiveQueryId(value)
  return difficulty && difficulty <= 5 ? difficulty : null
}

export function useQuestionCatalog() {
  const route = useRoute()
  const router = useRouter()
  const questions = ref<QuestionVO[]>([])
  const loading = ref(false)
  const loadError = ref('')
  const pageNum = ref(1)
  const pageSize = ref(10)
  const total = ref(0)
  const filters = reactive({
    questionType: '',
    courseId: null as number | null,
    difficulty: null as number | null,
  })
  const questionTypes = [
    { label: '全部题型', shortLabel: '全部', value: '' },
    { label: '单选题', shortLabel: '单选', value: 'SINGLE_CHOICE' },
    { label: '多选题', shortLabel: '多选', value: 'MULTIPLE_CHOICE' },
    { label: '判断题', shortLabel: '判断', value: 'TRUE_FALSE' },
    { label: '填空题', shortLabel: '填空', value: 'FILL_BLANK' },
    { label: '简答题', shortLabel: '简答', value: 'SHORT_ANSWER' },
  ]
  const difficultyOptions = [
    { value: 1, label: '入门' },
    { value: 2, label: '基础' },
    { value: 3, label: '进阶' },
    { value: 4, label: '挑战' },
    { value: 5, label: '压轴' },
  ]
  const courseList = ref<CourseVO[]>([])
  const favoriteSet = ref<Set<number>>(new Set())
  const expandedComments = ref<Set<number>>(new Set())
  const correctionDialogVisible = ref(false)
  const correctionSubmitting = ref(false)
  const correctionQuestion = ref<QuestionVO | null>(null)
  const correctionForm = reactive({ reportType: 'CONTENT', description: '' })
  const selectedQuestionId = ref<number | null>(null)
  const selectedKnowledgePointId = ref<number | null>(null)
  const searchQueryWarning = ref('')
  let requestGeneration = 0
  let alive = true

  const activeFilterCount = computed(
    () => [filters.questionType, filters.courseId, filters.difficulty].filter(Boolean).length,
  )
  const resultSummary = computed(() => {
    if (loading.value) return '正在加载题目...'
    if (loadError.value) return '题目暂时无法加载。'
    if (total.value === 0) return '当前筛选下没有题目，换个条件再试试。'
    const start = (pageNum.value - 1) * pageSize.value + 1
    const end = Math.min(pageNum.value * pageSize.value, total.value)
    return `显示第 ${start}-${end} 题，共 ${total.value} 题。`
  })
  const searchContext = computed(() => {
    if (selectedQuestionId.value) return { label: '搜索选中的题目', kind: 'question' as const }
    if (selectedKnowledgePointId.value) return { label: '知识点相关题目', kind: 'knowledgePoint' as const }
    return null
  })

  const fetchQuestions = async () => {
    const generation = ++requestGeneration
    const session = getAuthSessionVersion()
    if (!isAuthenticated()) {
      questions.value = []
      total.value = 0
      loadError.value = ''
      loading.value = false
      return
    }
    loading.value = true
    loadError.value = ''
    questions.value = []
    total.value = 0
    try {
      const response = await getQuestionPage({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        questionType: filters.questionType || undefined,
        courseId: filters.courseId || undefined,
        difficulty: filters.difficulty || undefined,
        questionId: selectedQuestionId.value || undefined,
        knowledgePointId: selectedKnowledgePointId.value || undefined,
      })
      if (!alive || generation !== requestGeneration || session !== getAuthSessionVersion()) return
      questions.value = response.data.records
      total.value = response.data.total
    } catch {
      if (alive && generation === requestGeneration && session === getAuthSessionVersion()) {
        loadError.value = '题目暂时无法加载，请重试'
      }
    } finally {
      if (generation === requestGeneration && session === getAuthSessionVersion()) loading.value = false
    }
  }

  const handleFilterChange = () => {
    pageNum.value = 1
    void fetchQuestions()
  }
  const handleSizeChange = handleFilterChange
  const selectDifficulty = (value: number) => {
    filters.difficulty = filters.difficulty === value ? null : value
    handleFilterChange()
  }
  const resetFilters = () => {
    filters.questionType = ''
    filters.courseId = null
    filters.difficulty = null
    handleFilterChange()
  }
  const retryFetch = () => void fetchQuestions()
  const clearSearchContext = async () => {
    const query = { ...route.query }
    delete query.questionId
    delete query.knowledgePointId
    if (filters.questionType) query.questionType = filters.questionType
    else delete query.questionType
    if (filters.courseId) query.courseId = String(filters.courseId)
    else delete query.courseId
    if (filters.difficulty) query.difficulty = String(filters.difficulty)
    else delete query.difficulty
    await router.replace({ query })
  }
  const toggleComment = (questionId: number) => {
    if (expandedComments.value.has(questionId)) expandedComments.value.delete(questionId)
    else expandedComments.value.add(questionId)
    expandedComments.value = new Set(expandedComments.value)
  }
  const questionTypeLabel = (type: string) => questionTypes.find((item) => item.value === type)?.shortLabel || type
  const questionTypeTag = (type: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' => {
    const tags: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
      SINGLE_CHOICE: 'primary',
      MULTIPLE_CHOICE: 'success',
      TRUE_FALSE: 'warning',
      FILL_BLANK: 'info',
      SHORT_ANSWER: 'danger',
    }
    return tags[type] || 'primary'
  }
  const difficultyLabel = (difficulty: number) => {
    const option = difficultyOptions.find((item) => item.value === difficulty)
    return option ? `${option.label}难度` : `${difficulty} 星难度`
  }
  const toggleFavorite = async (questionId: number) => {
    const session = getAuthSessionVersion()
    if (!isAuthenticated()) return
    try {
      if (favoriteSet.value.has(questionId)) {
        await removeFavorite(questionId)
        if (!alive || session !== getAuthSessionVersion() || !isAuthenticated()) return
        favoriteSet.value.delete(questionId)
        ElMessage.success('已取消收藏')
      } else {
        await addFavorite(questionId)
        if (!alive || session !== getAuthSessionVersion() || !isAuthenticated()) return
        favoriteSet.value.add(questionId)
        ElMessage.success('已收藏')
      }
      favoriteSet.value = new Set(favoriteSet.value)
    } catch (error) {
      if (alive && session === getAuthSessionVersion() && isAuthenticated())
        ElMessage.error(errorMessage(error, '操作失败'))
    }
  }
  const openCorrectionDialog = (question: QuestionVO) => {
    correctionQuestion.value = question
    correctionForm.reportType = 'CONTENT'
    correctionForm.description = ''
    correctionDialogVisible.value = true
  }
  const submitCorrection = async () => {
    if (!correctionQuestion.value) return
    if (!correctionForm.description.trim()) {
      ElMessage.warning('请填写问题描述')
      return
    }
    correctionSubmitting.value = true
    try {
      await submitQuestionCorrectionReport(correctionQuestion.value.id, {
        reportType: correctionForm.reportType,
        description: correctionForm.description.trim(),
      })
      ElMessage.success('纠错反馈已提交')
      correctionDialogVisible.value = false
    } catch {
      return
    } finally {
      correctionSubmitting.value = false
    }
  }

  const loadCourses = async () => {
    try {
      courseList.value = (await getAllCourses()).data
    } catch {
      return
    }
  }
  const loadFavoriteIds = async () => {
    const session = getAuthSessionVersion()
    if (!isAuthenticated()) {
      favoriteSet.value = new Set()
      return
    }
    try {
      const response = await getFavoriteIds()
      if (alive && session === getAuthSessionVersion() && isAuthenticated() && response.code === 0 && response.data) {
        favoriteSet.value = new Set(response.data)
      }
    } catch {
      return
    }
  }

  const syncRouteFilters = () => {
    const questionId = positiveQueryId(route.query.questionId)
    const knowledgePointId = positiveQueryId(route.query.knowledgePointId)
    const courseId = positiveQueryId(route.query.courseId)
    const questionType = questionTypeQuery(route.query.questionType)
    const difficulty = difficultyQuery(route.query.difficulty)
    const hasInvalidSearchQuery =
      hasInvalidPositiveQueryId(route.query.questionId) || hasInvalidPositiveQueryId(route.query.knowledgePointId)

    selectedQuestionId.value = questionId
    selectedKnowledgePointId.value = knowledgePointId
    filters.questionType = questionType
    filters.courseId = courseId
    filters.difficulty = difficulty
    searchQueryWarning.value = hasInvalidSearchQuery ? '搜索链接中的筛选条件无效，已显示普通题目列表。' : ''
    pageNum.value = 1
    void fetchQuestions()
  }

  watch(
    () => [
      route.query.courseId,
      route.query.questionId,
      route.query.knowledgePointId,
      route.query.questionType,
      route.query.difficulty,
    ],
    syncRouteFilters,
    { immediate: true },
  )

  const unsubscribeSession = onAuthSessionChange(() => {
    if (!isAuthenticated()) {
      requestGeneration++
      loading.value = false
      loadError.value = ''
      questions.value = []
      total.value = 0
      favoriteSet.value = new Set()
      return
    }
    syncRouteFilters()
    void loadFavoriteIds()
  })

  onMounted(() => {
    void loadCourses()
    void loadFavoriteIds()
  })

  onBeforeUnmount(() => {
    alive = false
    requestGeneration++
    unsubscribeSession()
  })

  return {
    questions,
    loading,
    loadError,
    pageNum,
    pageSize,
    total,
    filters,
    questionTypes,
    difficultyOptions,
    courseList,
    favoriteSet,
    expandedComments,
    correctionDialogVisible,
    correctionSubmitting,
    correctionQuestion,
    correctionForm,
    activeFilterCount,
    resultSummary,
    searchContext,
    searchQueryWarning,
    toggleComment,
    questionTypeLabel,
    questionTypeTag,
    difficultyLabel,
    handleFilterChange,
    handleSizeChange,
    selectDifficulty,
    resetFilters,
    retryFetch,
    clearSearchContext,
    fetchQuestions,
    toggleFavorite,
    openCorrectionDialog,
    submitCorrection,
  }
}
