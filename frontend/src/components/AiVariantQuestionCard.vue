<template>
  <article class="variant-card" :class="resultClass">
    <header class="variant-card__header">
      <div>
        <h3>变式检验</h3>
      </div>
      <div class="variant-card__meta">
        <el-tag effect="plain">单选题</el-tag>
        <el-tag type="warning" effect="plain">难度 {{ question.difficulty }}/5</el-tag>
      </div>
    </header>

    <div class="variant-card__question">
      <MarkdownRenderer :content="question.questionContent" />
    </div>

    <el-radio-group
      v-model="selectedAnswer"
      class="variant-options"
      :disabled="Boolean(training.answered) || submitting"
    >
      <el-radio v-for="option in question.options" :key="option.label" :value="option.label" class="variant-option">
        <span class="variant-option__label">{{ option.label }}</span>
        <span class="variant-option__content">{{ option.content }}</span>
      </el-radio>
    </el-radio-group>

    <div v-if="!training.answered" class="variant-card__actions">
      <el-button type="primary" :loading="submitting" :disabled="!selectedAnswer" @click="submitAnswer">
        提交答案
      </el-button>
    </div>

    <div v-if="submitError" class="variant-error" role="alert">
      <span>{{ submitError }}</span
      ><el-button size="small" @click="submitAnswer">重试</el-button>
    </div>
    <section
      v-else-if="training.answered"
      class="variant-result"
      :class="training.correct === true ? 'is-correct' : training.correct === false ? 'is-wrong' : 'is-review'"
    >
      <AnswerRewardFeedback v-if="lastReward" :reward="lastReward" />
      <div class="variant-result__headline">
        <span aria-hidden="true">{{ training.correct === true ? '✓' : training.correct === false ? '!' : '—' }}</span>
        <div>
          <strong>{{
            training.correct === null ? '已保存，供你自评' : training.correct ? '回答正确' : '这次未答对'
          }}</strong>
          <p>
            你的答案：{{ training.userAnswer || '-'
            }}<template v-if="training.correct !== true && training.correctAnswer">
              · 参考答案：{{ training.correctAnswer }}</template
            >
          </p>
        </div>
      </div>
      <div v-if="training.analysis" class="variant-result__analysis">
        <span>解析</span>
        <MarkdownRenderer :content="training.analysis" />
      </div>
    </section>
  </article>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { useGamificationStore } from '@/stores/gamification'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import AnswerRewardFeedback from '@/components/gamification/AnswerRewardFeedback.vue'
import type { RewardFeedback } from '@/api/gamification'
import { submitVariantAnswer, type AiVariantQuestion, type AiVariantTrainingStatus } from '@/api/ai'

const props = defineProps<{
  questionId: number
  question: AiVariantQuestion
  training: {
    answered?: boolean
    correct?: boolean | null
    userAnswer?: string | null
    correctAnswer?: string | null
    analysis?: string | null
  }
}>()

const emit = defineEmits<{
  answered: [training: AiVariantTrainingStatus]
}>()

const selectedAnswer = ref(props.training.userAnswer || '')
const submitting = ref(false)
const lastReward = ref<RewardFeedback>()
const submitError = ref('')
let generation = 0
let alive = true
const resultClass = computed(() =>
  props.training.answered
    ? props.training.correct === true
      ? 'has-correct-result'
      : props.training.correct === false
        ? 'has-wrong-result'
        : 'has-review-result'
    : '',
)

watch(
  () => props.training.userAnswer,
  (value) => {
    selectedAnswer.value = value || ''
  },
)

watch(
  () => props.questionId,
  () => {
    generation++
    lastReward.value = undefined
    selectedAnswer.value = props.training.userAnswer || ''
    submitting.value = false
    submitError.value = ''
  },
)
const unsubscribeAuth = onAuthSessionChange(() => {
  generation++
  submitting.value = false
  selectedAnswer.value = ''
  lastReward.value = undefined
  submitError.value = ''
})
onBeforeUnmount(() => {
  alive = false
  generation++
  unsubscribeAuth()
})

function isCurrent(requestGeneration: number, authSession: number, questionId: number) {
  return (
    alive &&
    requestGeneration === generation &&
    authSession === getAuthSessionVersion() &&
    questionId === props.questionId
  )
}

async function submitAnswer() {
  if (!selectedAnswer.value || submitting.value || props.training.answered) return
  const requestGeneration = generation
  const authSession = getAuthSessionVersion()
  const questionId = props.questionId
  submitting.value = true
  submitError.value = ''
  try {
    const response = await submitVariantAnswer(questionId, selectedAnswer.value, { errorDisplay: 'inline' })
    if (!isCurrent(requestGeneration, authSession, questionId)) return
    if (response.data.reward) {
      lastReward.value = response.data.reward
      useGamificationStore().acceptReward(response.data.reward, authSession)
    }
    emit('answered', response.data)
  } catch {
    if (isCurrent(requestGeneration, authSession, questionId)) submitError.value = '提交答案失败，请重试。'
  } finally {
    if (isCurrent(requestGeneration, authSession, questionId)) submitting.value = false
  }
}
</script>

<style scoped>
.variant-card {
  --variant-accent: var(--lp-primary);
  position: relative;
  padding: var(--lp-space-5);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
  box-shadow: var(--lp-shadow-xs);
}
.variant-card::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--variant-accent);
  content: '';
}
.variant-card.has-correct-result {
  --variant-accent: var(--lp-success);
}
.variant-card.has-wrong-result {
  --variant-accent: var(--lp-danger);
}
.variant-card__header,
.variant-card__actions,
.variant-result__headline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-3);
}
.variant-card h3 {
  margin: 0;
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
}
.variant-card__meta {
  display: flex;
  gap: var(--lp-space-2);
}
.variant-card__question {
  margin: var(--lp-space-5) 0 var(--lp-space-3);
  color: var(--lp-text);
  line-height: var(--lp-leading-relaxed);
}
.variant-options {
  display: grid;
  gap: var(--lp-space-2);
  width: 100%;
}
.variant-option {
  box-sizing: border-box;
  width: 100%;
  min-height: var(--lp-control-height-large);
  margin: 0;
  padding: var(--lp-space-3) var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-control);
  background: var(--lp-surface);
}
.variant-option:hover {
  border-color: var(--lp-primary);
  background: var(--lp-surface-hover);
}
.variant-option :deep(.el-radio__label) {
  display: inline-flex;
  align-items: center;
  gap: var(--lp-space-2);
  color: var(--lp-text);
  white-space: normal;
}
.variant-option__label {
  display: inline-grid;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  place-items: center;
  border-radius: var(--lp-radius-md);
  background: var(--lp-primary-soft);
  color: var(--variant-accent);
  font-weight: var(--lp-weight-bold);
}
.variant-card__actions {
  justify-content: flex-end;
  margin-top: var(--lp-space-4);
  padding-top: var(--lp-space-4);
  border-top: var(--lp-border-hairline);
}
.variant-result {
  margin-top: var(--lp-space-4);
  padding: var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-inline-start: 3px solid var(--variant-accent);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface-subtle);
}
.variant-result.is-review {
  border-inline-start-color: var(--lp-info);
}
.variant-error {
  display: flex;
  justify-content: space-between;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-4);
  padding: var(--lp-space-3);
  color: var(--lp-text);
  background: var(--lp-danger-soft);
  border-inline-start: 3px solid var(--lp-danger);
}
.variant-result__headline {
  justify-content: flex-start;
}
.variant-result__headline > span {
  display: grid;
  width: var(--lp-control-height);
  height: var(--lp-control-height);
  flex: 0 0 var(--lp-control-height);
  place-items: center;
  border-radius: var(--lp-radius-full);
  background: var(--variant-accent);
  color: var(--lp-on-primary);
  font-weight: var(--lp-weight-bold);
}
.variant-result__headline strong {
  color: var(--lp-text);
}
.variant-result__headline p {
  margin: var(--lp-space-1) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.variant-result__analysis {
  margin-top: var(--lp-space-3);
  padding-top: var(--lp-space-3);
  border-top: var(--lp-border-hairline);
}
.variant-result__analysis > span {
  color: var(--variant-accent);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-semibold);
}
@media (max-width: 720px) {
  .variant-card {
    padding: var(--lp-space-4);
  }
  .variant-card__header,
  .variant-card__actions {
    align-items: stretch;
    flex-direction: column;
  }
  .variant-card__actions :deep(.el-button) {
    width: 100%;
  }
}
</style>
