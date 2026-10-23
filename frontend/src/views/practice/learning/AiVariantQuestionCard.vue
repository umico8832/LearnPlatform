<template>
  <article class="variant-card" :class="resultClass">
    <header class="variant-card__header">
      <div>
        <h3>变式检验</h3>
      </div>
      <div class="variant-card__meta">
        <span class="asset-tag">单选题</span>
        <span class="asset-tag">难度 {{ question.difficulty }}/5</span>
      </div>
    </header>

    <div class="variant-card__question">
      <MarkdownRenderer :content="question.questionContent" />
    </div>

    <fieldset class="variant-options" :disabled="Boolean(training.answered) || submitting">
      <legend class="lt-sr-only">选择变式答案</legend>
      <label
        v-for="option in question.options"
        :key="option.label"
        class="variant-option"
        :class="{ selected: selectedAnswer === option.label }"
        ><input v-model="selectedAnswer" type="radio" :name="`variant-${questionId}`" :value="option.label" /><span
          class="variant-option__label"
          >{{ option.label }}</span
        ><span class="variant-option__content">{{ option.content }}</span></label
      >
    </fieldset>

    <div v-if="!training.answered" class="variant-card__actions">
      <LtButton
        variant="primary"
        :aria-busy="submitting"
        :disabled="!selectedAnswer || submitting"
        @click="submitAnswer"
      >
        提交答案
      </LtButton>
    </div>

    <div v-if="submitError" class="variant-error" role="alert">
      <span>{{ submitError }}</span
      ><LtButton size="sm" @click="submitAnswer">重试</LtButton>
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
import { LtButton } from '@/components/living'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import MarkdownRenderer from '@/views/question/QuestionProse.vue'
import { useGamificationStore } from '@/stores/gamification'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import AnswerRewardFeedback from '@/views/practice/PracticeRewardFeedback.vue'
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
  --variant-accent: var(--lt-accent-fill);
  position: relative;
  padding: var(--lt-space-5);
  border: thin solid var(--lt-separator);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-card);
}
.variant-card::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: calc(var(--lt-space-1) * 0.75);
  background: var(--variant-accent);
  content: '';
}
.variant-card.has-correct-result {
  --variant-accent: var(--lt-green-text);
}
.variant-card.has-wrong-result {
  --variant-accent: var(--lt-orange-text);
}
.variant-card__header,
.variant-card__actions,
.variant-result__headline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-3);
}
.variant-card h3 {
  margin: 0;
  color: var(--lt-label);
  font-size: var(--lt-text-title3);
}
.variant-card__meta {
  display: flex;
  gap: var(--lt-space-2);
}
.variant-card__question {
  margin: var(--lt-space-5) 0 var(--lt-space-3);
  color: var(--lt-label);
  line-height: var(--lt-leading-reading);
}
.variant-options {
  border: 0;
  padding: 0;
  margin: 0;
  display: grid;
  gap: var(--lt-space-2);
  width: 100%;
}
.variant-option {
  box-sizing: border-box;
  width: 100%;
  min-height: var(--lt-control-lg);
  margin: 0;
  padding: var(--lt-space-3) var(--lt-space-4);
  border: thin solid var(--lt-separator);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-surface);
}
.variant-option.selected {
  border-color: var(--lt-accent);
  background: var(--lt-accent-soft);
}
.variant-option:hover {
  border-color: var(--lt-accent);
  background: var(--lt-fill-hover);
}
.variant-option {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-2);
  color: var(--lt-label);
  white-space: normal;
}
.variant-option__label {
  display: inline-grid;
  width: calc(var(--lt-space-1) * 7);
  height: calc(var(--lt-space-1) * 7);
  flex: 0 0 calc(var(--lt-space-1) * 7);
  place-items: center;
  border-radius: var(--lt-radius-md);
  background: var(--lt-accent-soft);
  color: var(--variant-accent);
  font-weight: 600;
}
.variant-card__actions {
  justify-content: flex-end;
  margin-top: var(--lt-space-4);
  padding-top: var(--lt-space-4);
  border-top: thin solid var(--lt-separator);
}
.variant-result {
  margin-top: var(--lt-space-4);
  padding: var(--lt-space-4);
  border: thin solid var(--lt-separator);
  border-inline-start: calc(var(--lt-space-1) * 0.75) solid var(--variant-accent);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-well);
}
.variant-result.is-review {
  border-inline-start-color: var(--lt-label-2);
}
.variant-error {
  display: flex;
  justify-content: space-between;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-4);
  padding: var(--lt-space-3);
  color: var(--lt-label);
  background: var(--lt-orange-soft);
  border-inline-start: calc(var(--lt-space-1) * 0.75) solid var(--lt-orange-text);
}
.variant-result__headline {
  justify-content: flex-start;
}
.variant-result__headline > span {
  display: grid;
  width: var(--lt-control-md);
  height: var(--lt-control-md);
  flex: 0 0 var(--lt-control-md);
  place-items: center;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font-weight: 600;
}
.variant-result__headline strong {
  color: var(--lt-label);
}
.variant-result__headline p {
  margin: var(--lt-space-1) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.variant-result__analysis {
  margin-top: var(--lt-space-3);
  padding-top: var(--lt-space-3);
  border-top: thin solid var(--lt-separator);
}
.variant-result__analysis > span {
  color: var(--variant-accent);
  font-size: var(--lt-text-caption);
  font-weight: 600;
}
@media (max-width: 720px) {
  .variant-card {
    padding: var(--lt-space-4);
  }
  .variant-card__header,
  .variant-card__actions {
    align-items: stretch;
    flex-direction: column;
  }
  .variant-card__actions :deep(.lt-button) {
    width: 100%;
  }
}
</style>
