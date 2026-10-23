<template>
  <section class="agent-practice" :aria-labelledby="opened ? headingId : undefined">
    <LtButton v-if="!opened" data-testid="practice-open" :disabled="loading" @click="load">
      {{ failure ? '重试加载练习' : '开始变式练习' }}
    </LtButton>
    <template v-else-if="practice">
      <h3 :id="headingId">变式练习</h3>
      <p class="practice-content">{{ practice.question.content }}</p>
      <fieldset class="practice-options" :disabled="!!practice.result || submitting || loading || syncRequired">
        <legend class="lt-sr-only">选择变式练习答案</legend>
        <label
          v-for="option in practice.question.options"
          :key="option.label"
          class="practice-option course-choice"
          :data-state="optionState(option.label)"
        >
          <input
            class="course-choice-input"
            v-model="answer"
            type="radio"
            name="practice-answer"
            :value="option.label"
          />
          <span class="course-choice-key" aria-hidden="true">{{ option.label }}</span>
          <span class="course-choice-text">{{ option.content }}</span>
          <span v-if="practice.result && option.label === practice.result.userAnswer" class="course-choice-tag">
            你的选择，{{ practice.result.correct ? '正确' : '错误' }}
          </span>
          <span v-else-if="optionState(option.label) === 'right'" class="course-choice-tag">正确答案</span>
        </label>
      </fieldset>
      <LtButton
        data-testid="practice-submit"
        variant="primary"
        :disabled="!answer || !!practice.result || submitting || loading || syncRequired"
        @click="submit"
        >提交作答</LtButton
      >
      <div v-if="practice.result" class="practice-result" role="status">
        <AnswerRewardFeedback v-if="practice.result.reward" :reward="practice.result.reward" />
        <strong>{{ practice.result.correct ? '回答正确' : '回答不正确' }}</strong>
        <p>你的选择：{{ practice.result.userAnswer }} · 参考答案：{{ practice.result.correctAnswer }}</p>
        <p v-if="practice.result.analysis" class="practice-content">{{ practice.result.analysis }}</p>
        <LtButton
          v-if="allowFollowUp"
          data-testid="practice-follow-up"
          :disabled="busy || loading || submitting"
          @click="continuePractice"
          >请 Tutor 根据这次练习继续指导</LtButton
        >
      </div>
    </template>
    <p v-if="failure" class="practice-error" role="alert">{{ failure }}</p>
    <LtButton v-if="syncRequired" data-testid="practice-sync" @click="load"> 重新同步作答 </LtButton>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import './course-choice.css'
import { LtButton } from '@/components/living'
import { useGamificationStore } from '@/stores/gamification'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import AnswerRewardFeedback from '@/views/practice/PracticeRewardFeedback.vue'
import { getTutorAgentPractice, submitTutorAgentPractice, type TutorAgentPracticeVO } from '@/api/tutor'
import { errorMessage } from '@/utils/errors'

const props = withDefaults(
  defineProps<{
    courseId: number
    sessionKey: string
    runKey: string
    sequence: number
    busy?: boolean
    allowFollowUp?: boolean
  }>(),
  { busy: false, allowFollowUp: true },
)
const emit = defineEmits<{ 'continue-practice': [] }>()
const opened = ref(false)
const loading = ref(false)
const submitting = ref(false)
const syncRequired = ref(false)
const answer = ref('')
const practice = ref<TutorAgentPracticeVO>()
const failure = ref('')
const headingId = computed(() => `tutor-practice-${props.runKey}-${props.sequence}`)
let generation = 0

function optionState(label: string) {
  const result = practice.value?.result
  if (!result) return answer.value === label ? 'selected' : 'idle'
  if (result.correctAnswer === label) return 'right'
  if (result.userAnswer === label) return 'wrong'
  return 'dim'
}

function resetForContext() {
  opened.value = false
  loading.value = false
  submitting.value = false
  syncRequired.value = false
  practice.value = undefined
  answer.value = ''
  failure.value = ''
}

function isCurrent(current: number, authSession: number) {
  return current === generation && authSession === getAuthSessionVersion()
}

const unsubscribeAuth = onAuthSessionChange(() => {
  generation++
  resetForContext()
})

function context() {
  return [props.courseId, props.sessionKey, props.runKey, props.sequence] as const
}

function apply(value: TutorAgentPracticeVO) {
  practice.value = value
  answer.value = value.result?.userAnswer ?? answer.value
  opened.value = true
  syncRequired.value = false
}

async function load() {
  if (loading.value || submitting.value) return
  const current = generation
  const authSession = getAuthSessionVersion()
  const target = context()
  loading.value = true
  failure.value = ''
  try {
    const response = await getTutorAgentPractice(...target, { errorDisplay: 'inline' })
    if (!isCurrent(current, authSession)) return
    apply(response.data)
  } catch (error) {
    if (isCurrent(current, authSession)) failure.value = errorMessage(error, '暂时无法加载练习，请重试')
  } finally {
    if (isCurrent(current, authSession)) loading.value = false
  }
}

async function submit() {
  if (
    !practice.value ||
    !answer.value ||
    practice.value.result ||
    submitting.value ||
    loading.value ||
    syncRequired.value
  ) {
    return
  }
  const current = generation
  const target = context()
  const authSession = getAuthSessionVersion()
  const chosen = answer.value
  submitting.value = true
  failure.value = ''
  try {
    const response = await submitTutorAgentPractice(...target, chosen, undefined, { errorDisplay: 'inline' })
    if (!isCurrent(current, authSession)) return
    if (response.data.result?.reward) useGamificationStore().acceptReward(response.data.result.reward, authSession)
    if (!response.data.result) throw new Error('暂时无法确认作答结果')
    apply(response.data)
  } catch (error) {
    if (!isCurrent(current, authSession)) return
    try {
      const restored = await getTutorAgentPractice(...target, { errorDisplay: 'inline' })
      if (!isCurrent(current, authSession)) return
      apply(restored.data)
      if (!restored.data.result) failure.value = errorMessage(error, '暂时无法提交作答，请重试')
    } catch {
      if (!isCurrent(current, authSession)) return
      syncRequired.value = true
      failure.value = '暂时无法确认作答是否保存，请先同步结果。'
    }
  } finally {
    if (isCurrent(current, authSession)) submitting.value = false
  }
}

function continuePractice() {
  if (practice.value?.result && props.allowFollowUp && !props.busy && !loading.value && !submitting.value) {
    emit('continue-practice')
  }
}

watch(
  () => [props.courseId, props.sessionKey, props.runKey, props.sequence],
  () => {
    generation++
    resetForContext()
  },
)
onBeforeUnmount(() => {
  generation++
  unsubscribeAuth()
})
</script>

<style scoped>
.agent-practice {
  display: grid;
  gap: var(--lt-space-3);
  justify-items: start;
  margin-top: var(--lt-space-3);
}
.agent-practice:has(h3) {
  padding: var(--lt-space-4);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-md);
  background: var(--lt-surface-raised);
}
h3,
p {
  margin: 0;
}
h3 {
  font-size: var(--lt-text-body);
}
.practice-options,
.practice-result {
  display: grid;
  gap: var(--lt-space-2);
  width: 100%;
}
.practice-options {
  border: 0;
  padding: 0;
}
.practice-error {
  margin: 0;
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-sm);
  background: var(--lt-red-soft);
  color: var(--lt-red-text);
}

.practice-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.practice-result {
  padding-top: var(--lt-space-3);
  border-top: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
}
</style>
