import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getExamSession, getPaperDetail, submitExam, type ExamQuestionItem } from '@/api/exam'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import { errorMessage } from '@/utils/errors'
import { useExamAnswers } from './useExamAnswers'
import { useExamCountdown } from './useExamCountdown'
import { useExamLeaveGuard } from './useExamLeaveGuard'

export function useExamTakingSession(recordId: Readonly<Ref<number>>) {
  const router = useRouter()
  const loading = ref(false),
    loadError = ref(''),
    submitError = ref('')
  const questions = ref<ExamQuestionItem[]>([]),
    paperTitle = ref(''),
    currentIndex = ref(0)
  const submitted = ref(false),
    confirming = ref(false),
    finished = ref(false)
  const answerState = useExamAnswers(() => questions.value.length)
  const currentQuestion = computed(() => questions.value[currentIndex.value] || null)
  const locked = computed(() => submitted.value || confirming.value || finished.value)
  const { allowNavigation } = useExamLeaveGuard({
    hasQuestions: () => questions.value.length > 0,
    submitting: submitted,
    finished,
  })
  let generation = 0,
    alive = true
  const isCurrent = (version: number, auth: number) =>
    alive && version === generation && auth === getAuthSessionVersion()
  const leaveForRecords = async () => {
    allowNavigation()
    await router.replace({ name: 'ExamList', query: { tab: 'records' } })
  }
  async function expire(message = '考试时间已结束，已返回考试列表') {
    if (!alive || finished.value) return
    finished.value = true
    if (confirming.value) ElMessageBox.close?.()
    ElMessage.warning(message)
    await leaveForRecords()
  }
  const countdown = useExamCountdown({
    submitted,
    hasQuestions: () => questions.value.length > 0,
    onExpired: expire,
  })
  function clearSession() {
    countdown.stop()
    if (confirming.value) ElMessageBox.close?.()
    questions.value = []
    paperTitle.value = ''
    answerState.answers.value = {}
    currentIndex.value = 0
    submitted.value = false
    confirming.value = false
    finished.value = false
    submitError.value = ''
    loadError.value = ''
  }
  async function load() {
    const version = ++generation,
      auth = getAuthSessionVersion(),
      id = recordId.value
    clearSession()
    if (!Number.isInteger(id) || id <= 0) {
      loadError.value = '考试记录无效，请从考试记录重新进入。'
      loading.value = false
      return
    }
    loading.value = true
    try {
      const startedAt = Date.now()
      const response = await getExamSession(id, { errorDisplay: 'inline' })
      if (!isCurrent(version, auth)) return
      if (response.code !== 0 || !response.data) throw new Error(response.message || '暂时无法恢复考试，请重试。')
      const session = response.data
      if (session.status === 1 || session.status === 3) {
        finished.value = true
        allowNavigation()
        await router.replace({ name: 'ExamResult', params: { recordId: String(id) } })
        return
      }
      if (session.status === 2) {
        await expire('考试已超时，已返回考试列表')
        return
      }
      if (!countdown.configure(session.deadline || '', session.serverTime || '', startedAt))
        throw new Error('考试时间信息暂时无法确认，请重试恢复。')
      const paper = await getPaperDetail(session.examPaperId, { errorDisplay: 'inline' })
      if (!isCurrent(version, auth)) return
      if (paper.code !== 0 || !paper.data) throw new Error(paper.message || '试卷暂时无法读取，请重试。')
      questions.value = paper.data.questions || []
      paperTitle.value = paper.data.title || session.examTitle || '限时考试'
      countdown.start()
    } catch (cause) {
      if (isCurrent(version, auth)) loadError.value = errorMessage(cause, '暂时无法恢复考试，请检查网络后重试。')
    } finally {
      if (isCurrent(version, auth)) loading.value = false
    }
  }
  async function doSubmit() {
    if (submitted.value || finished.value || !questions.value.length) return
    if (countdown.remainSeconds.value === 0) return expire()
    const version = generation,
      auth = getAuthSessionVersion(),
      id = recordId.value
    submitted.value = true
    submitError.value = ''
    const answers = questions.value.map((question) => ({
      questionId: question.questionId,
      userAnswer: answerState.answers.value[question.questionId] || '',
    }))
    try {
      const response = await submitExam({ examRecordId: id, answers }, { errorDisplay: 'inline' })
      if (!isCurrent(version, auth)) return
      if (response.code !== 0 || !response.data) throw new Error(response.message || '交卷失败，请重试。')
      finished.value = true
      countdown.stop()
      allowNavigation()
      await router.replace({ name: 'ExamResult', params: { recordId: String(response.data.id) } })
    } catch (cause) {
      if (!isCurrent(version, auth)) return
      if (countdown.remainSeconds.value === 0) await expire()
      else submitError.value = errorMessage(cause, '交卷暂时失败，答案仍保留在本页，请重试。')
    } finally {
      if (isCurrent(version, auth)) submitted.value = false
    }
  }
  async function handleSubmit() {
    if (locked.value || !questions.value.length) return
    const version = generation,
      auth = getAuthSessionVersion()
    const unanswered = questions.value.length - answerState.answeredCount.value
    confirming.value = true
    try {
      await ElMessageBox.confirm(
        unanswered
          ? `还有 ${unanswered} 题未作答。确定提交试卷？提交后不可修改。`
          : '已完成全部作答。确定提交试卷？提交后不可修改。',
        '提交确认',
        { type: 'warning', confirmButtonText: '提交试卷', cancelButtonText: '继续作答', autofocus: false },
      )
      if (isCurrent(version, auth) && !finished.value) await doSubmit()
    } catch {
      // 取消确认保留当前选项，不发送交卷请求。
    } finally {
      if (isCurrent(version, auth)) confirming.value = false
    }
  }
  watch(recordId, () => void load(), { immediate: true })
  const unsubscribe = onAuthSessionChange(() => {
    generation++
    clearSession()
    loading.value = false
    void leaveForRecords()
  })
  onBeforeUnmount(() => {
    alive = false
    generation++
    countdown.stop()
    if (confirming.value) ElMessageBox.close?.()
    unsubscribe()
  })
  return {
    ...answerState,
    loading,
    loadError,
    submitError,
    paperTitle,
    questions,
    currentIndex,
    currentQuestion,
    submitted,
    confirming,
    locked,
    remainSeconds: countdown.remainSeconds,
    countdownText: countdown.countdownText,
    load,
    handleSubmit,
    doSubmit,
    leaveForRecords,
  }
}
