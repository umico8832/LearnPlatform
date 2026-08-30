<template>
  <div class="learning-diagnosis">
    <el-page-header @back="$router.back()">
      <template #content><span class="page-title">🧠 学习诊断</span></template>
    </el-page-header>

    <div v-if="loading" v-loading="true" class="page-loading"></div>
    <template v-else-if="data">
      <LearningDiagnosisSummary
        :data="data"
        :ai-advice-loading="aiAdviceLoading"
        :ai-advice-streaming="aiAdviceStreaming"
        :ai-advice-content="aiAdviceContent"
        @generate-ai-advice="generateAiAdvice"
      />
      <LearningDiagnosisErrorPatterns
        :patterns="data.errorPatterns"
        @similar-question="loadSimilarQuestions"
        @question-error-analysis="loadQuestionErrorAnalysis"
      />
      <LearningDiagnosisRecommendations
        :course-masteries="data.courseMasteries"
        :recommendations="data.dailyRecommendations"
        @start-recommend-practice="startRecommendPractice"
        @similar-question="loadSimilarQuestions"
      />
    </template>

    <QuestionErrorAnalysisDialog
      v-model="errorAnalysisDialogVisible"
      :loading="errorAnalysisLoading"
      :data="errorAnalysisData"
    />
    <SimilarQuestionDialog
      v-model="similarDialogVisible"
      :loading="similarLoading"
      :data="similarData"
      :source-content="similarSourceContent"
      @start-practice="startSimilarPractice"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getAiAdviceStream,
  getLearningDiagnosis,
  getQuestionErrorAnalysis,
  getSimilarQuestions,
  type LearningDiagnosis,
  type QuestionErrorAnalysis,
  type SimilarQuestions,
} from '@/api/statistics'
import { getQuestionById } from '@/api/question'
import LearningDiagnosisErrorPatterns from '@/components/statistics/LearningDiagnosisErrorPatterns.vue'
import LearningDiagnosisRecommendations from '@/components/statistics/LearningDiagnosisRecommendations.vue'
import LearningDiagnosisSummary from '@/components/statistics/LearningDiagnosisSummary.vue'
import QuestionErrorAnalysisDialog from '@/components/statistics/QuestionErrorAnalysisDialog.vue'
import SimilarQuestionDialog from '@/components/statistics/SimilarQuestionDialog.vue'
import { errorMessage, isAbortError } from '@/utils/errors'

const router = useRouter()
const loading = ref(true)
const data = ref<LearningDiagnosis | null>(null)

const errorAnalysisDialogVisible = ref(false)
const errorAnalysisLoading = ref(false)
const errorAnalysisData = ref<QuestionErrorAnalysis | null>(null)

async function loadQuestionErrorAnalysis(questionId: number) {
  errorAnalysisDialogVisible.value = true
  errorAnalysisLoading.value = true
  errorAnalysisData.value = null
  try {
    const response = await getQuestionErrorAnalysis(questionId)
    errorAnalysisData.value = response.data
  } catch (error) {
    ElMessage.error('加载错因分析失败: ' + errorMessage(error, '未知错误'))
  } finally {
    errorAnalysisLoading.value = false
  }
}

const similarDialogVisible = ref(false)
const similarLoading = ref(false)
const similarData = ref<SimilarQuestions | null>(null)
const similarSourceContent = ref('')

async function loadSimilarQuestions(questionId: number, questionContent?: string) {
  similarDialogVisible.value = true
  similarLoading.value = true
  similarData.value = null
  similarSourceContent.value = questionContent || ''
  try {
    const response = await getSimilarQuestions(questionId, 8)
    similarData.value = response.data
  } catch (error) {
    ElMessage.error('加载相似题失败: ' + errorMessage(error, '未知错误'))
  } finally {
    similarLoading.value = false
  }
}

function startSimilarPractice() {
  if (!similarData.value?.similarQuestions?.length) return
  const similarQuestions = similarData.value.similarQuestions
  similarDialogVisible.value = false
  Promise.all(similarQuestions.map((item) => getQuestionById(item.questionId).then((response) => response.data)))
    .then((questions) => {
      sessionStorage.setItem('practice_questions', JSON.stringify(questions))
      sessionStorage.setItem('practice_mode', 'similar')
      router.push({ path: '/practice/session' })
    })
    .catch(() => ElMessage.error('加载相似题失败，请重试'))
}

const aiAdviceLoading = ref(false)
const aiAdviceStreaming = ref(false)
const aiAdviceContent = ref('')

async function generateAiAdvice() {
  aiAdviceLoading.value = true
  aiAdviceStreaming.value = true
  aiAdviceContent.value = ''
  try {
    const response = await getAiAdviceStream()
    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`)
    }
    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('无法读取响应流')
    }
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (!line.startsWith('data:')) continue
        const json = line.slice(5).trim()
        if (!json) continue
        try {
          const parsed = JSON.parse(json)
          if (parsed.content !== undefined) {
            aiAdviceContent.value += parsed.content
          }
        } catch {
          // 忽略非 JSON SSE 数据行。
        }
      }
    }
  } catch (error) {
    if (!isAbortError(error)) {
      ElMessage.error('AI 建议生成失败: ' + errorMessage(error, '未知错误'))
    }
  } finally {
    aiAdviceLoading.value = false
    aiAdviceStreaming.value = false
  }
}

function startRecommendPractice() {
  if (!data.value?.dailyRecommendations?.length) return
  Promise.all(
    data.value.dailyRecommendations.map((item) => getQuestionById(item.questionId).then((response) => response.data)),
  )
    .then((questions) => {
      sessionStorage.setItem('practice_questions', JSON.stringify(questions))
      sessionStorage.setItem('practice_mode', 'recommended')
      router.push({ path: '/practice/session' })
    })
    .catch(() => ElMessage.error('加载推荐练习失败，请重试'))
}

onMounted(async () => {
  try {
    const response = await getLearningDiagnosis()
    data.value = response.data
  } catch (error) {
    ElMessage.error('加载学习诊断失败: ' + errorMessage(error, '未知错误'))
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.learning-diagnosis {
  padding: 0 0 var(--lp-space-6);
}

.page-title {
  font-size: var(--lp-text-2xl);
  font-weight: var(--lp-weight-semibold);
}

.page-loading {
  height: 400px;
}
</style>
