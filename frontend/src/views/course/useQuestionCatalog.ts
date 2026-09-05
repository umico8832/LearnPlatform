import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { errorMessage } from '@/utils/errors'
import { getQuestionPage, submitQuestionCorrectionReport, type QuestionVO } from '@/api/question'
import { getAllCourses, type CourseVO } from '@/api/course'
import { addFavorite, getFavoriteIds, removeFavorite } from '@/api/favorite'

export function useQuestionCatalog() {
  const route = useRoute()
  const questions = ref<QuestionVO[]>([])
  const loading = ref(false)
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

  const activeFilterCount = computed(
    () => [filters.questionType, filters.courseId, filters.difficulty].filter(Boolean).length,
  )
  const resultSummary = computed(() => {
    if (loading.value) return '正在加载题目...'
    if (total.value === 0) return '当前筛选下没有题目，换个条件再试试。'
    const start = (pageNum.value - 1) * pageSize.value + 1
    const end = Math.min(pageNum.value * pageSize.value, total.value)
    return `显示第 ${start}-${end} 题，共 ${total.value} 题。`
  })

  const fetchQuestions = async () => {
    loading.value = true
    try {
      const response = await getQuestionPage({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        questionType: filters.questionType || undefined,
        courseId: filters.courseId || undefined,
        difficulty: filters.difficulty || undefined,
      })
      questions.value = response.data.records
      total.value = response.data.total
    } catch {
      return
    } finally {
      loading.value = false
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
    try {
      if (favoriteSet.value.has(questionId)) {
        await removeFavorite(questionId)
        favoriteSet.value.delete(questionId)
        ElMessage.success('已取消收藏')
      } else {
        await addFavorite(questionId)
        favoriteSet.value.add(questionId)
        ElMessage.success('已收藏')
      }
      favoriteSet.value = new Set(favoriteSet.value)
    } catch (error) {
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
    try {
      const response = await getFavoriteIds()
      if (response.code === 0 && response.data) favoriteSet.value = new Set(response.data)
    } catch {
      return
    }
  }

  onMounted(() => {
    const courseId = Number(route.query.courseId)
    filters.courseId = Number.isFinite(courseId) && courseId > 0 ? courseId : null
    void fetchQuestions()
    void loadCourses()
    void loadFavoriteIds()
  })

  return {
    questions,
    loading,
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
    toggleComment,
    questionTypeLabel,
    questionTypeTag,
    difficultyLabel,
    handleFilterChange,
    handleSizeChange,
    selectDifficulty,
    resetFilters,
    fetchQuestions,
    toggleFavorite,
    openCorrectionDialog,
    submitCorrection,
  }
}
