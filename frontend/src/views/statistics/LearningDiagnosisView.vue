<template>
  <div class="lt learning-diagnosis" data-appearance="dark">
    <div class="diagnosis-wrap">
      <LtPageHeader
        title="下一步从最需要处理的内容开始"
        title-id="diagnosis-heading"
        description="诊断只汇总已判分的学习事实；待判分记录会在完成批阅后更新。"
      />
      <div v-if="state === 'loading'" class="diagnosis-state" aria-busy="true">
        <span class="skeleton" /><span class="skeleton" /><span class="sr-only" role="status">正在整理学习记录</span>
      </div>
      <div v-else-if="state === 'empty'" class="diagnosis-state">
        <h2>还没有可诊断的学习记录</h2>
        <p>完成一次练习或复习后，这里会基于真实记录整理下一步。</p>
      </div>
      <div v-else-if="state === 'error'" class="diagnosis-state" role="alert">
        <h2>暂时无法读取学习诊断</h2>
        <p>{{ loadError }}</p>
        <button class="retry" type="button" @click="loadDiagnosis"><RotateCcw aria-hidden="true" />重试读取</button>
      </div>
      <section v-if="state === 'ready' && data" class="diagnosis-content" aria-labelledby="diagnosis-heading">
        <LearningDiagnosisSummary
          :data="data"
          :ai-advice-loading="aiAdviceLoading"
          :ai-advice-streaming="aiAdviceStreaming"
          :ai-advice-content="aiAdviceContent"
          :ai-advice-error="aiAdviceError"
          @generate-ai-advice="generateAiAdvice"
          @stop-ai-advice="cancelAiAdvice"
        />
        <LearningDiagnosisErrorPatterns
          :patterns="data.errorPatterns"
          @similar-question="loadSimilarQuestions"
          @question-error-analysis="loadQuestionErrorAnalysis"
        />
        <LearningDiagnosisRecommendations
          :course-masteries="data.courseMasteries"
          :recommendations="data.dailyRecommendations"
          :starting-practice="startingPractice"
          :practice-error="practiceError"
          @start-recommend-practice="startRecommendPractice"
          @similar-question="loadSimilarQuestions"
        />
      </section>
    </div>

    <QuestionErrorAnalysisDialog
      v-model="errorAnalysisDialogVisible"
      :loading="errorAnalysisLoading"
      :data="errorAnalysisData"
      :error="errorAnalysisError"
      @retry="retryErrorAnalysis"
    />
    <SimilarQuestionDialog
      v-model="similarDialogVisible"
      :loading="similarLoading"
      :data="similarData"
      :source-content="similarSourceContent"
      :error="similarError"
      :starting="startingPractice"
      :practice-error="practiceError"
      @start-practice="startSimilarPractice"
      @retry="retrySimilarQuestions"
    />
  </div>
</template>

<script setup lang="ts">
import LtPageHeader from '@/components/living/LtPageHeader.vue'
import { onBeforeUnmount, ref } from 'vue'
import { RotateCcw } from '@lucide/vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { savePracticeSession } from '@/utils/practiceSession'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import { errorMessage, isAbortError } from '@/utils/errors'
import { getQuestionById } from '@/api/question'
import {
  getAiAdviceStream,
  getLearningDiagnosis,
  getQuestionErrorAnalysis,
  getSimilarQuestions,
  type LearningDiagnosis,
  type QuestionErrorAnalysis,
  type SimilarQuestions,
} from '@/api/statistics'
import LearningDiagnosisErrorPatterns from '@/components/statistics/LearningDiagnosisErrorPatterns.vue'
import LearningDiagnosisRecommendations from '@/components/statistics/LearningDiagnosisRecommendations.vue'
import LearningDiagnosisSummary from '@/components/statistics/LearningDiagnosisSummary.vue'
import QuestionErrorAnalysisDialog from '@/components/statistics/QuestionErrorAnalysisDialog.vue'
import SimilarQuestionDialog from '@/components/statistics/SimilarQuestionDialog.vue'

const router = useRouter()
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const data = ref<LearningDiagnosis | null>(null)
const loadError = ref('')
const errorAnalysisDialogVisible = ref(false)
const errorAnalysisLoading = ref(false)
const errorAnalysisData = ref<QuestionErrorAnalysis | null>(null)
const errorAnalysisError = ref('')
const errorAnalysisQuestionId = ref<number | null>(null)
const similarDialogVisible = ref(false)
const similarLoading = ref(false)
const similarData = ref<SimilarQuestions | null>(null)
const similarSourceContent = ref('')
const similarError = ref('')
const similarQuestionId = ref<number | null>(null)
const startingPractice = ref(false)
const practiceError = ref('')
const aiAdviceLoading = ref(false)
const aiAdviceStreaming = ref(false)
const aiAdviceContent = ref('')
const aiAdviceError = ref('')
let alive = true
let version = 0
let errorAnalysisGeneration = 0
let similarGeneration = 0
let aiController: AbortController | undefined

function current(request: number, session: number) {
  return alive && request === version && session === getAuthSessionVersion()
}

async function loadDiagnosis() {
  const request = ++version
  const session = getAuthSessionVersion()
  state.value = 'loading'
  data.value = null
  loadError.value = ''
  try {
    const response = await getLearningDiagnosis({ errorDisplay: 'inline' })
    if (!current(request, session)) return
    if (!response.data || response.data.totalPractice <= 0) state.value = 'empty'
    else {
      data.value = response.data
      state.value = 'ready'
    }
  } catch (cause) {
    if (current(request, session)) {
      loadError.value = errorMessage(cause, '请检查网络后重试。')
      state.value = 'error'
    }
  }
}

async function loadQuestionErrorAnalysis(questionId: number) {
  const request = version
  const detailRequest = ++errorAnalysisGeneration
  const session = getAuthSessionVersion()
  errorAnalysisDialogVisible.value = true
  errorAnalysisLoading.value = true
  errorAnalysisData.value = null
  errorAnalysisError.value = ''
  errorAnalysisQuestionId.value = questionId
  try {
    const response = await getQuestionErrorAnalysis(questionId, { errorDisplay: 'inline' })
    if (current(request, session) && detailRequest === errorAnalysisGeneration) errorAnalysisData.value = response.data
  } catch (cause) {
    if (current(request, session) && detailRequest === errorAnalysisGeneration)
      errorAnalysisError.value = errorMessage(cause, '错因记录暂时无法读取，请重试。')
  } finally {
    if (current(request, session) && detailRequest === errorAnalysisGeneration) errorAnalysisLoading.value = false
  }
}
function retryErrorAnalysis() {
  if (errorAnalysisQuestionId.value) void loadQuestionErrorAnalysis(errorAnalysisQuestionId.value)
}

async function loadSimilarQuestions(questionId: number, questionContent = '') {
  const request = version
  const detailRequest = ++similarGeneration
  const session = getAuthSessionVersion()
  similarDialogVisible.value = true
  similarLoading.value = true
  similarData.value = null
  similarSourceContent.value = questionContent
  similarError.value = ''
  similarQuestionId.value = questionId
  try {
    const response = await getSimilarQuestions(questionId, 8, { errorDisplay: 'inline' })
    if (current(request, session) && detailRequest === similarGeneration) similarData.value = response.data
  } catch (cause) {
    if (current(request, session) && detailRequest === similarGeneration)
      similarError.value = errorMessage(cause, '相似题暂时无法读取，请重试。')
  } finally {
    if (current(request, session) && detailRequest === similarGeneration) similarLoading.value = false
  }
}
function retrySimilarQuestions() {
  if (similarQuestionId.value) void loadSimilarQuestions(similarQuestionId.value, similarSourceContent.value)
}

async function loadPractice(items: { questionId: number }[], mode: 'similar' | 'recommended') {
  if (startingPractice.value || !items.length) return
  const request = version
  const session = getAuthSessionVersion()
  const userId = useUserStore().userInfo?.id
  startingPractice.value = true
  practiceError.value = ''
  try {
    const questions = await Promise.all(
      items.map((item) =>
        getQuestionById(item.questionId, { errorDisplay: 'inline' }).then((response) => response.data),
      ),
    )
    if (
      !current(request, session) ||
      userId !== useUserStore().userInfo?.id ||
      !savePracticeSession(userId, questions, mode)
    )
      return
    await router.push({ path: '/practice/session' })
  } catch (cause) {
    if (current(request, session)) practiceError.value = errorMessage(cause, '题面暂时无法读取，请重试。')
  } finally {
    if (current(request, session)) startingPractice.value = false
  }
}
function startSimilarPractice() {
  if (similarData.value) void loadPractice(similarData.value.similarQuestions, 'similar')
}
function startRecommendPractice() {
  if (data.value) void loadPractice(data.value.dailyRecommendations, 'recommended')
}

function cancelAiAdvice() {
  aiController?.abort()
  aiController = undefined
  aiAdviceLoading.value = false
  aiAdviceStreaming.value = false
}

function parseSseEvent(block: string) {
  let name = 'message'
  const dataLines: string[] = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) name = line.slice(6).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
  }
  if (!dataLines.length) return null
  try {
    return { name, data: JSON.parse(dataLines.join('\n')) as Record<string, unknown> }
  } catch {
    return null
  }
}

async function generateAiAdvice() {
  if (aiAdviceLoading.value) return
  const request = version
  const session = getAuthSessionVersion()
  const controller = new AbortController()
  aiController?.abort()
  aiController = controller
  aiAdviceLoading.value = true
  aiAdviceStreaming.value = true
  aiAdviceContent.value = ''
  aiAdviceError.value = ''
  try {
    const response = await getAiAdviceStream(controller.signal)
    if (!response.ok || !response.body) throw new Error(`AI 建议请求失败 (${response.status})`)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    const handleEvent = (block: string) => {
      const event = parseSseEvent(block)
      if (!event || !current(request, session) || aiController !== controller || controller.signal.aborted) return
      if (event.name === 'error') {
        throw new Error(typeof event.data.message === 'string' ? event.data.message : 'AI 服务调用失败')
      }
      if (event.name === 'content' && typeof event.data.content === 'string')
        aiAdviceContent.value += event.data.content
    }
    try {
      while (!controller.signal.aborted) {
        const { done, value } = await reader.read()
        buffer += decoder.decode(value, { stream: !done })
        const events = buffer.split(/\r?\n\r?\n/)
        buffer = events.pop() || ''
        for (const event of events) handleEvent(event)
        if (done) break
      }
      if (!controller.signal.aborted && buffer.trim()) handleEvent(buffer)
    } finally {
      await reader.cancel().catch(() => undefined)
      reader.releaseLock()
    }
  } catch (cause) {
    if (!isAbortError(cause) && current(request, session) && aiController === controller && !controller.signal.aborted)
      aiAdviceError.value = errorMessage(cause, 'AI 建议暂时无法生成，请重试。')
  } finally {
    if (current(request, session) && aiController === controller) {
      aiAdviceLoading.value = false
      aiAdviceStreaming.value = false
      aiController = undefined
    }
  }
}

const unsubscribe = onAuthSessionChange(() => {
  version++
  errorAnalysisGeneration++
  similarGeneration++
  cancelAiAdvice()
  data.value = null
  aiAdviceContent.value = ''
  aiAdviceError.value = ''
  errorAnalysisData.value = null
  errorAnalysisError.value = ''
  errorAnalysisLoading.value = false
  similarData.value = null
  similarSourceContent.value = ''
  similarError.value = ''
  similarLoading.value = false
  errorAnalysisQuestionId.value = null
  similarQuestionId.value = null
  startingPractice.value = false
  practiceError.value = ''
  errorAnalysisDialogVisible.value = false
  similarDialogVisible.value = false
  if (isAuthenticated()) void loadDiagnosis()
  else state.value = 'empty'
})
void loadDiagnosis()
onBeforeUnmount(() => {
  alive = false
  version++
  cancelAiAdvice()
  unsubscribe()
})
</script>

<style scoped>
.diagnosis-content {
  display: grid;
  gap: var(--lt-space-6);
}
.diagnosis-state {
  display: grid;
  justify-items: start;
  gap: var(--lt-space-3);
  min-height: calc(var(--lt-space-10) * 2 + var(--lt-space-5) + var(--lt-space-4));
  padding: var(--lt-space-5);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface-raised);
  box-shadow: var(--lt-shadow-card);
}
.diagnosis-state h2,
.diagnosis-state p {
  margin: 0;
}
.diagnosis-state p {
  color: var(--lt-label-2);
}
.skeleton {
  width: 100%;
  height: calc(var(--lt-space-7) - var(--lt-space-2) / 4);
  border-radius: var(--lt-radius-sm);
  background: var(--lt-fill-2);
}
.retry {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-2);
  min-height: var(--lt-control-md);
  padding: 0 var(--lt-space-4);
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-accent-fill);
  color: var(--lt-on-accent);
  font: 500 var(--lt-text-footnote)/1 var(--lt-font);
  cursor: pointer;
}
.retry:focus-visible {
  outline: 2px solid var(--lt-accent);
  outline-offset: 2px;
}
</style>
