<template>
  <section class="agent-practice" :aria-labelledby="opened ? headingId : undefined">
    <el-button v-if="!opened" data-testid="practice-open" :loading="loading" :disabled="loading" @click="load">
      {{ failure ? '重试加载练习' : '开始变式练习' }}
    </el-button>
    <template v-else-if="practice">
      <h3 :id="headingId">变式练习</h3>
      <p class="practice-content">{{ practice.question.content }}</p>
      <el-radio-group
        v-model="answer"
        class="practice-options"
        aria-label="选择变式练习答案"
        :disabled="!!practice.result || submitting || loading || syncRequired"
      >
        <el-radio v-for="option in practice.question.options" :key="option.label" :value="option.label">
          {{ option.label }}. {{ option.content }}
        </el-radio>
      </el-radio-group>
      <el-button
        data-testid="practice-submit"
        type="primary"
        :loading="submitting"
        :disabled="!answer || !!practice.result || submitting || loading || syncRequired"
        @click="submit"
        >提交作答</el-button
      >
      <div v-if="practice.result" class="practice-result" role="status">
        <strong>{{ practice.result.correct ? '回答正确' : '回答不正确' }}</strong>
        <p>你的选择：{{ practice.result.userAnswer }} · 参考答案：{{ practice.result.correctAnswer }}</p>
        <p v-if="practice.result.analysis" class="practice-content">{{ practice.result.analysis }}</p>
        <el-button
          v-if="allowFollowUp"
          data-testid="practice-follow-up"
          :disabled="busy || loading || submitting"
          @click="continuePractice"
          >请 Tutor 根据这次练习继续指导</el-button
        >
      </div>
    </template>
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" show-icon />
    <el-button v-if="syncRequired" data-testid="practice-sync" :loading="loading" @click="load">
      重新同步作答
    </el-button>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
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
  const target = context()
  loading.value = true
  failure.value = ''
  try {
    const response = await getTutorAgentPractice(...target)
    if (current !== generation) return
    apply(response.data)
  } catch (error) {
    if (current === generation) failure.value = errorMessage(error, '暂时无法加载练习，请重试')
  } finally {
    if (current === generation) loading.value = false
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
  const chosen = answer.value
  submitting.value = true
  failure.value = ''
  try {
    const response = await submitTutorAgentPractice(...target, chosen)
    if (current !== generation) return
    if (!response.data.result) throw new Error('暂时无法确认作答结果')
    apply(response.data)
  } catch (error) {
    if (current !== generation) return
    try {
      const restored = await getTutorAgentPractice(...target)
      if (current !== generation) return
      apply(restored.data)
      if (!restored.data.result) failure.value = errorMessage(error, '暂时无法提交作答，请重试')
    } catch {
      if (current !== generation) return
      syncRequired.value = true
      failure.value = '暂时无法确认作答是否保存，请先同步结果。'
    }
  } finally {
    if (current === generation) submitting.value = false
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
    opened.value = loading.value = submitting.value = syncRequired.value = false
    practice.value = undefined
    answer.value = failure.value = ''
  },
)
onBeforeUnmount(() => generation++)
</script>

<style scoped>
.agent-practice {
  display: grid;
  gap: var(--lp-space-3);
  justify-items: start;
  margin-top: var(--lp-space-3);
}
.agent-practice:has(h3) {
  padding: var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface);
}
h3,
p {
  margin: 0;
}
h3 {
  font-size: var(--lp-text-base);
}
.practice-options,
.practice-result {
  display: grid;
  gap: var(--lp-space-2);
  width: 100%;
}
.practice-options :deep(.el-radio) {
  height: auto;
  min-height: 36px;
  margin-right: 0;
  white-space: normal;
}
.practice-options :deep(.el-radio__label),
.practice-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.practice-result {
  padding-top: var(--lp-space-3);
  border-top: var(--lp-border-hairline);
}
</style>
