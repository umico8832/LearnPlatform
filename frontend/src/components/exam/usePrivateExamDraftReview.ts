import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { generatePrivateExamDraftAnswer, reviewPrivateExamDraftQuestion } from '@/api/exam'
import type { PrivateExamDraft } from '@/api/exam'
import { errorMessage } from '@/utils/errors'

type Operation = 'generate' | 'review'
type QuestionState = {
  answerEdited: boolean
  analysisEdited: boolean
  operation: Operation | null
  phase: 'waiting' | 'running' | null
  error: string
}

export function usePrivateExamDraftReview(
  getDraft: () => PrivateExamDraft,
  onUpdated: (draft: PrivateExamDraft) => void,
) {
  const draftAnswers = ref<Record<number, string[]>>({})
  const draftAnalyses = ref<Record<number, string>>({})
  const questionStates = ref<Record<number, QuestionState>>({})
  let latestDraft = getDraft()
  let session = { active: true, tail: Promise.resolve() }

  function mergeDraft(draft: PrivateExamDraft) {
    const ids = new Set(draft.questions.map((question) => question.id))
    for (const id of Object.keys(questionStates.value).map(Number)) {
      if (!ids.has(id)) {
        delete questionStates.value[id]
        delete draftAnswers.value[id]
        delete draftAnalyses.value[id]
      }
    }
    for (const question of draft.questions) {
      const state = (questionStates.value[question.id] ??= {
        answerEdited: false,
        analysisEdited: false,
        operation: null,
        phase: null,
        error: '',
      })
      if (question.reviewStatus === 'REVIEWED') {
        draftAnswers.value[question.id] = [...question.finalAnswerLabels]
        draftAnalyses.value[question.id] = question.finalAnalysis ?? ''
        state.answerEdited = false
        state.analysisEdited = false
        state.error = ''
      } else {
        if (!state.answerEdited) {
          draftAnswers.value[question.id] = [
            ...(question.generationStatus === 'GENERATED' ? question.aiAnswerLabels : question.originalAnswerLabels),
          ]
        }
        if (!state.analysisEdited) {
          draftAnalyses.value[question.id] =
            (question.generationStatus === 'GENERATED' ? question.aiAnalysis : question.originalAnalysis) ?? ''
        }
      }
    }
    latestDraft = draft
  }

  watch(
    getDraft,
    (draft) => {
      if (latestDraft.id !== draft.id) {
        session.active = false
        session = { active: true, tail: Promise.resolve() }
        draftAnswers.value = {}
        draftAnalyses.value = {}
        questionStates.value = {}
      }
      mergeDraft(draft)
    },
    { immediate: true, flush: 'sync' },
  )

  onBeforeUnmount(() => {
    session.active = false
  })

  function canAct(questionId: number) {
    const question = latestDraft.questions.find((item) => item.id === questionId)
    return session.active &&
      latestDraft.status !== 'CONFIRMED' &&
      question &&
      question.reviewStatus !== 'REVIEWED' &&
      !questionStates.value[questionId]?.operation
      ? question
      : undefined
  }

  function enqueue(
    questionId: number,
    operation: Operation,
    request: (draftId: number) => ReturnType<typeof generatePrivateExamDraftAnswer>,
  ) {
    const currentSession = session
    const draftId = latestDraft.id
    const state = questionStates.value[questionId]!
    state.operation = operation
    state.phase = 'waiting'
    state.error = ''
    const isCurrent = () =>
      currentSession.active &&
      session === currentSession &&
      latestDraft.id === draftId &&
      questionStates.value[questionId] === state
    // API 返回整份草稿且没有版本号；同一会话顺序写入，避免并行响应回退其他题的状态。
    const pending = currentSession.tail.then(async () => {
      if (!isCurrent()) return
      try {
        const question = latestDraft.questions.find((item) => item.id === questionId)
        if (!question || question.reviewStatus === 'REVIEWED' || latestDraft.status === 'CONFIRMED') return
        if (operation === 'generate' && question.generationStatus !== 'PENDING') return
        state.phase = 'running'
        const response = await request(draftId)
        if (!isCurrent()) return
        if (response.code !== 0 || !response.data) {
          state.error = response.message || (operation === 'generate' ? 'AI 生成失败，请重试' : '复核失败，请重试')
          return
        }
        if (response.data.id !== draftId || !response.data.questions.some((item) => item.id === questionId)) {
          state.error = '草稿响应不匹配，请重新打开草稿后重试'
          return
        }
        mergeDraft(response.data)
        onUpdated(response.data)
        ElMessage.success(operation === 'generate' ? 'AI 建议已生成，请人工核对' : '本题已人工复核')
      } catch (error) {
        if (isCurrent()) {
          state.error = `${operation === 'generate' ? 'AI 生成失败' : '复核失败'}：${errorMessage(error, '请稍后重试')}`
        }
      } finally {
        if (isCurrent()) {
          state.operation = null
          state.phase = null
        }
      }
    })
    currentSession.tail = pending
    return pending
  }

  async function generateDraftAnswer(questionId: number) {
    const question = canAct(questionId)
    if (!question || question.generationStatus !== 'PENDING') return
    await enqueue(questionId, 'generate', (draftId) => generatePrivateExamDraftAnswer(draftId, questionId))
  }

  async function reviewDraftQuestion(questionId: number) {
    const question = canAct(questionId)
    if (!question) return
    const state = questionStates.value[questionId]!
    const answers = [...(draftAnswers.value[questionId] || [])]
    const analysis = draftAnalyses.value[questionId]?.trim() || ''
    if (question.generationStatus === 'PENDING') {
      state.error = '请先生成 AI 建议，再确认本题'
      return
    }
    if (!answers.length || !analysis) {
      state.error = '请选择答案并填写人工确认解析'
      return
    }
    if (question.questionType !== 'MULTIPLE_CHOICE' && answers.length !== 1) {
      state.error = '单选或判断题只能确认一个答案'
      return
    }
    if (question.questionType === 'MULTIPLE_CHOICE' && answers.length < 2) {
      state.error = '多选题至少确认两个答案'
      return
    }
    state.answerEdited = true
    state.analysisEdited = true
    await enqueue(questionId, 'review', (draftId) =>
      reviewPrivateExamDraftQuestion(draftId, questionId, {
        answerLabels: answers,
        analysis,
      }),
    )
  }

  return { draftAnswers, draftAnalyses, questionStates, generateDraftAnswer, reviewDraftQuestion }
}
