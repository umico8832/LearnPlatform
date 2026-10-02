import { onMounted, onScopeDispose, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { addFavorite, getFavoriteIds, removeFavorite } from '@/api/favorite'
import { getQuestionById, submitQuestionCorrectionReport, type QuestionVO } from '@/api/question'
import { useUserStore } from '@/stores/user'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import { savePracticeSession } from '@/utils/practiceSession'

export function useQuestionCatalogActions() {
  const router = useRouter()
  const user = useUserStore()
  const favoriteSet = ref(new Set<number>())
  const favoritePending = ref(new Set<number>())
  const favoritesLoading = ref(false)
  const favoritesReady = ref(false)
  const favoritesError = ref('')
  const actionError = ref('')
  const notice = ref('')
  const practiceStartingId = ref<number | null>(null)
  const correctionDialogVisible = ref(false)
  const correctionSubmitting = ref(false)
  const correctionQuestion = ref<QuestionVO | null>(null)
  const correctionForm = reactive({ reportType: 'CONTENT', description: '' })
  const correctionError = ref('')
  let alive = true
  let generation = 0
  let favoriteRequest = 0

  function capture() {
    const version = generation
    const session = getAuthSessionVersion()
    return () => alive && version === generation && session === getAuthSessionVersion() && isAuthenticated()
  }

  async function loadFavoriteIds() {
    const current = capture()
    const request = ++favoriteRequest
    if (!current()) return
    favoritesLoading.value = true
    favoritesReady.value = false
    favoritesError.value = ''
    try {
      const response = await getFavoriteIds({ errorDisplay: 'inline' })
      if (!current() || request !== favoriteRequest) return
      favoriteSet.value = new Set(response.data)
      favoritesReady.value = true
    } catch {
      if (current() && request === favoriteRequest) favoritesError.value = '收藏状态暂时无法读取。'
    } finally {
      if (current() && request === favoriteRequest) favoritesLoading.value = false
    }
  }

  async function toggleFavorite(questionId: number) {
    if (!favoritesReady.value || favoritesLoading.value || favoritePending.value.has(questionId)) return
    const current = capture()
    if (!current()) return
    const wasFavorite = favoriteSet.value.has(questionId)
    favoritePending.value = new Set(favoritePending.value).add(questionId)
    actionError.value = ''
    notice.value = ''
    try {
      if (wasFavorite) await removeFavorite(questionId, { errorDisplay: 'inline' })
      else await addFavorite(questionId, { errorDisplay: 'inline' })
      if (!current()) return
      const next = new Set(favoriteSet.value)
      if (wasFavorite) next.delete(questionId)
      else next.add(questionId)
      favoriteSet.value = next
      notice.value = wasFavorite ? '已取消收藏。' : '已加入收藏，可在收藏页继续练习。'
    } catch {
      if (current()) actionError.value = '未能更新收藏，请重试。'
    } finally {
      if (current()) {
        const next = new Set(favoritePending.value)
        next.delete(questionId)
        favoritePending.value = next
      }
    }
  }

  function openCorrectionDialog(question: QuestionVO) {
    if (correctionSubmitting.value) return
    if (correctionQuestion.value?.id !== question.id) {
      correctionForm.reportType = 'CONTENT'
      correctionForm.description = ''
      correctionError.value = ''
    }
    correctionQuestion.value = question
    correctionDialogVisible.value = true
  }

  async function submitCorrection() {
    if (!correctionQuestion.value || correctionSubmitting.value) return
    const description = correctionForm.description.trim()
    if (!description) {
      correctionError.value = '请描述需要核对的问题。'
      return
    }
    const current = capture()
    if (!current()) return
    correctionSubmitting.value = true
    correctionError.value = ''
    try {
      await submitQuestionCorrectionReport(
        correctionQuestion.value.id,
        { reportType: correctionForm.reportType, description },
        { errorDisplay: 'inline' },
      )
      if (!current()) return
      correctionDialogVisible.value = false
      correctionForm.description = ''
      notice.value = '纠错反馈已提交，感谢你帮助完善题目。'
    } catch {
      if (current()) correctionError.value = '暂时无法确认提交结果，内容已保留，请稍后重试。'
    } finally {
      if (current()) correctionSubmitting.value = false
    }
  }

  async function startQuestionPractice(questionId: number) {
    if (practiceStartingId.value !== null) return
    const current = capture()
    const userId = user.userInfo?.id
    if (!current() || !userId) return
    practiceStartingId.value = questionId
    actionError.value = ''
    try {
      const response = await getQuestionById(questionId, { errorDisplay: 'inline' })
      if (!current() || userId !== user.userInfo?.id) return
      const q = response.data
      if (!q || q.id !== questionId) throw new Error('Missing question')
      const question = {
        id: q.id,
        content: q.content,
        questionType: q.questionType,
        courseId: q.courseId,
        courseName: q.courseName,
        difficulty: q.difficulty,
        score: q.score,
        tags: q.tags,
        knowledgePointIds: q.knowledgePointIds,
        knowledgePointNames: q.knowledgePointNames,
        options: (q.options ?? []).map(({ id, content, optionLabel, sortOrder }) => ({
          id,
          content,
          optionLabel,
          sortOrder,
        })),
      }
      if (savePracticeSession(userId, [question])) await router.push({ name: 'PracticeSession' })
    } catch {
      if (current()) actionError.value = '这道题暂时无法开始练习，请重试。'
    } finally {
      if (current()) practiceStartingId.value = null
    }
  }

  onMounted(loadFavoriteIds)
  onScopeDispose(
    onAuthSessionChange(() => {
      generation++
      favoriteRequest++
      favoriteSet.value = new Set()
      favoritePending.value = new Set()
      favoritesReady.value = false
      favoritesLoading.value = false
      favoritesError.value = ''
      actionError.value = ''
      notice.value = ''
      practiceStartingId.value = null
      correctionDialogVisible.value = false
      correctionSubmitting.value = false
      correctionQuestion.value = null
      correctionForm.reportType = 'CONTENT'
      correctionForm.description = ''
      correctionError.value = ''
      if (isAuthenticated()) void loadFavoriteIds()
    }),
  )
  onScopeDispose(() => {
    alive = false
    generation++
  })

  return {
    favoriteSet,
    favoritePending,
    favoritesLoading,
    favoritesReady,
    favoritesError,
    loadFavoriteIds,
    actionError,
    notice,
    toggleFavorite,
    practiceStartingId,
    startQuestionPractice,
    correctionDialogVisible,
    correctionSubmitting,
    correctionQuestion,
    correctionForm,
    correctionError,
    openCorrectionDialog,
    submitCorrection,
  }
}
