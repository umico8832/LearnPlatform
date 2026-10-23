<template>
  <section v-if="plan" class="agent-plan" :aria-labelledby="headingId">
    <header class="plan-heading">
      <div>
        <h3 :id="headingId">本课程建议安排</h3>
        <p>确认安排不会记录作答或学习完成。</p>
      </div>
      <span v-if="plan.confirmed" class="plan-status" role="status">已确认</span>
    </header>

    <ol class="plan-steps">
      <li v-for="step in plan.steps.slice(0, 3)" :key="stepKey(step)" class="plan-step">
        <div>
          <strong>{{ step.title }}</strong>
          <p>{{ step.reason }}</p>
        </div>
        <LtButton v-if="plan.confirmed" data-testid="plan-open-step" :disabled="!canOpenStep" @click="openStep(step)">
          开始这一步
        </LtButton>
      </li>
    </ol>

    <p v-if="plan.confirmedAt" class="plan-confirmed-at">确认时间：{{ plan.confirmedAt }}</p>
    <p v-if="!plan.available" class="plan-unavailable" role="status">目标已有变化，可请求新建议。</p>
    <p v-if="failure" class="plan-error" role="alert">{{ failure }}</p>
    <div class="plan-actions">
      <LtButton data-testid="plan-confirm" variant="primary" :disabled="!canConfirm" @click="confirmPlan">
        {{ plan.confirmed ? '已确认这份安排' : '确认这份安排' }}
      </LtButton>
      <LtButton v-if="syncRequired" data-testid="plan-sync" :disabled="loading" @click="load"> 重新同步安排 </LtButton>
    </div>
  </section>
  <p v-else-if="loading" class="plan-loading" role="status">正在恢复建议安排…</p>
  <div v-else-if="failure" class="plan-retry">
    <p class="plan-error" role="alert">{{ failure }}</p>
    <LtButton data-testid="plan-retry" :disabled="loading" @click="load"> 重试恢复安排 </LtButton>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { LtButton } from '@/components/living'
import { useRouter } from 'vue-router'
import {
  confirmTutorAgentPlan,
  getTutorAgentPlan,
  type TutorAgentPlanStepVO,
  type TutorAgentPlanVO,
} from '@/api/tutorPlan'
import { openLearningTarget } from '@/utils/learningTarget'
import { errorMessage } from '@/utils/errors'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'

const props = withDefaults(
  defineProps<{
    courseId: number
    sessionKey: string
    runKey: string
    sequence: number
    busy?: boolean
  }>(),
  { busy: false },
)

const router = useRouter()
const plan = ref<TutorAgentPlanVO>()
const loading = ref(false)
const confirming = ref(false)
const syncRequired = ref(false)
const failure = ref('')
const headingId = computed(() => `tutor-plan-${props.runKey}-${props.sequence}`)
const canConfirm = computed(
  () =>
    !!plan.value &&
    !plan.value.confirmed &&
    plan.value.available &&
    !props.busy &&
    !loading.value &&
    !confirming.value &&
    !syncRequired.value,
)
const canOpenStep = computed(
  () =>
    !!plan.value?.confirmed &&
    plan.value.available &&
    !props.busy &&
    !loading.value &&
    !confirming.value &&
    !syncRequired.value,
)
let generation = 0
let alive = true

function isCurrent(current: number, authSession: number) {
  return alive && current === generation && authSession === getAuthSessionVersion()
}

function context() {
  return [props.courseId, props.sessionKey, props.runKey, props.sequence] as const
}

function apply(value: TutorAgentPlanVO) {
  plan.value = { ...value, steps: value.steps.slice(0, 3) }
  syncRequired.value = false
}

async function load() {
  if (loading.value || confirming.value) return
  const current = generation
  const target = context()
  const authSession = getAuthSessionVersion()
  loading.value = true
  failure.value = ''
  try {
    const response = await getTutorAgentPlan(...target, { errorDisplay: 'inline' })
    if (!isCurrent(current, authSession)) return
    apply(response.data)
  } catch (error) {
    if (isCurrent(current, authSession)) failure.value = errorMessage(error, '暂时无法恢复建议安排，请重试')
  } finally {
    if (isCurrent(current, authSession)) loading.value = false
  }
}

async function confirmPlan() {
  if (!canConfirm.value) return
  const current = generation
  const target = context()
  const authSession = getAuthSessionVersion()
  confirming.value = true
  failure.value = ''
  try {
    const response = await confirmTutorAgentPlan(...target, { errorDisplay: 'inline' })
    if (!isCurrent(current, authSession)) return
    apply(response.data)
  } catch (error) {
    if (!isCurrent(current, authSession)) return
    try {
      const restored = await getTutorAgentPlan(...target, { errorDisplay: 'inline' })
      if (!isCurrent(current, authSession)) return
      apply(restored.data)
      if (!restored.data.confirmed) failure.value = errorMessage(error, '暂时无法确认安排，请重试')
    } catch {
      if (!isCurrent(current, authSession)) return
      syncRequired.value = true
      failure.value = '暂时无法确认安排是否已保存，请先同步安排。'
    }
  } finally {
    if (isCurrent(current, authSession)) confirming.value = false
  }
}

function stepKey(step: TutorAgentPlanStepVO) {
  return `${step.type}-${step.knowledgePointId ?? ''}-${step.questionId ?? ''}-${step.title}`
}

function openStep(step: TutorAgentPlanStepVO) {
  if (!canOpenStep.value) return
  openLearningTarget(router, props.courseId, {
    type: step.type,
    title: step.title,
    reason: step.reason,
    knowledgePointId: step.knowledgePointId ?? null,
    questionId: step.questionId ?? null,
  })
}

watch(
  () => [props.courseId, props.sessionKey, props.runKey, props.sequence],
  () => {
    generation++
    plan.value = undefined
    loading.value = confirming.value = syncRequired.value = false
    failure.value = ''
    void load()
  },
)
const unsubscribeAuth = onAuthSessionChange(() => {
  generation++
  plan.value = undefined
  loading.value = confirming.value = syncRequired.value = false
  failure.value = ''
})

onMounted(() => void load())
onBeforeUnmount(() => {
  alive = false
  generation++
  unsubscribeAuth()
})
</script>

<style scoped>
.agent-plan {
  display: grid;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-3);
  padding: var(--lt-space-4);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-md);
  background: var(--lt-surface-raised);
}
.plan-heading,
.plan-step,
.plan-actions {
  display: flex;
  gap: var(--lt-space-3);
  align-items: center;
  justify-content: space-between;
}
h3,
p,
ol {
  margin: 0;
}
h3 {
  font-size: var(--lt-text-body);
}
.plan-heading p,
.plan-confirmed-at {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.plan-status {
  color: var(--lt-green-text);
  font-size: var(--lt-text-footnote);
}
.plan-steps {
  display: grid;
  gap: var(--lt-space-3);
  padding-left: var(--lt-space-5);
}
.plan-step p,
.plan-unavailable {
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
  overflow-wrap: anywhere;
}
.plan-step > div {
  display: grid;
  gap: var(--lt-space-1);
}
.plan-loading {
  color: var(--lt-label-2);
  margin-top: var(--lt-space-3);
}
.plan-error {
  margin: 0;
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-sm);
  background: var(--lt-red-soft);
  color: var(--lt-red-text);
}
.plan-retry {
  display: grid;
  gap: var(--lt-space-3);
  justify-items: start;
  margin-top: var(--lt-space-3);
}
</style>
