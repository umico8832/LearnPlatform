import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import { getQuestionPage, type QuestionVO } from '@/api/question'
import { getAllCourses, type CourseVO } from '@/api/course'
import { useQuestionCatalogActions } from './useQuestionCatalogActions'

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
  const actions = useQuestionCatalogActions()
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
    { value: 5, label: '综合' },
  ]
  const courseList = ref<CourseVO[]>([])
  const coursesLoading = ref(false)
  const coursesError = ref('')
  const expandedComments = ref<Set<number>>(new Set())
  const selectedQuestionId = ref<number | null>(null)
  const selectedKnowledgePointId = ref<number | null>(null)
  const searchQueryWarning = ref('')
  let requestGeneration = 0
  let coursesRequest = 0
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
      if (alive && generation === requestGeneration && session === getAuthSessionVersion()) loading.value = false
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
  const questionTypeLabel = (type: string) => questionTypes.find((item) => item.value === type)?.label || type
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
  const loadCourses = async () => {
    const request = ++coursesRequest
    const session = getAuthSessionVersion()
    const current = () => alive && request === coursesRequest && session === getAuthSessionVersion()
    coursesLoading.value = true
    coursesError.value = ''
    try {
      const response = await getAllCourses({ errorDisplay: 'inline' })
      if (current()) courseList.value = response.data
    } catch {
      if (current()) coursesError.value = '课程筛选暂时无法加载。'
    } finally {
      if (current()) coursesLoading.value = false
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
    coursesRequest++
    coursesLoading.value = false
    coursesError.value = ''
    courseList.value = []
    expandedComments.value = new Set()
    if (!isAuthenticated()) {
      requestGeneration++
      loading.value = false
      loadError.value = ''
      questions.value = []
      total.value = 0
      expandedComments.value = new Set()
      return
    }
    syncRouteFilters()
    void loadCourses()
  })

  onMounted(() => {
    void loadCourses()
  })

  onBeforeUnmount(() => {
    alive = false
    requestGeneration++
    unsubscribeSession()
  })

  return {
    ...actions,
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
    coursesLoading,
    coursesError,
    loadCourses,
    expandedComments,
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
  }
}
