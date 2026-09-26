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
        <el-button v-if="plan.confirmed" data-testid="plan-open-step" :disabled="!canOpenStep" @click="openStep(step)">
          开始这一步
        </el-button>
      </li>
    </ol>

    <p v-if="plan.confirmedAt" class="plan-confirmed-at">确认时间：{{ plan.confirmedAt }}</p>
    <p v-if="!plan.available" class="plan-unavailable" role="status">目标已有变化，可请求新建议。</p>
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" show-icon />
    <div class="plan-actions">
      <el-button
        data-testid="plan-confirm"
        type="primary"
        :loading="confirming"
        :disabled="!canConfirm"
        @click="confirmPlan"
      >
        {{ plan.confirmed ? '已确认这份安排' : '确认这份安排' }}
      </el-button>
      <el-button v-if="syncRequired" data-testid="plan-sync" :loading="loading" :disabled="loading" @click="load">
        重新同步安排
      </el-button>
    </div>
  </section>
  <p v-else-if="loading" class="plan-loading" role="status">正在恢复建议安排…</p>
  <div v-else-if="failure" class="plan-retry">
    <el-alert :title="failure" type="error" :closable="false" show-icon />
    <el-button data-testid="plan-retry" :loading="loading" :disabled="loading" @click="load"> 重试恢复安排 </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  confirmTutorAgentPlan,
  getTutorAgentPlan,
  type TutorAgentPlanStepVO,
  type TutorAgentPlanVO,
} from '@/api/tutorPlan'
import { openLearningTarget } from '@/utils/learningTarget'
import { errorMessage } from '@/utils/errors'

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
  loading.value = true
  failure.value = ''
  try {
    const response = await getTutorAgentPlan(...target)
    if (current !== generation) return
    apply(response.data)
  } catch (error) {
    if (current === generation) failure.value = errorMessage(error, '暂时无法恢复建议安排，请重试')
  } finally {
    if (current === generation) loading.value = false
  }
}

async function confirmPlan() {
  if (!canConfirm.value) return
  const current = generation
  const target = context()
  confirming.value = true
  failure.value = ''
  try {
    const response = await confirmTutorAgentPlan(...target)
    if (current !== generation) return
    apply(response.data)
  } catch (error) {
    if (current !== generation) return
    try {
      const restored = await getTutorAgentPlan(...target)
      if (current !== generation) return
      apply(restored.data)
      if (!restored.data.confirmed) failure.value = errorMessage(error, '暂时无法确认安排，请重试')
    } catch {
      if (current !== generation) return
      syncRequired.value = true
      failure.value = '暂时无法确认安排是否已保存，请先同步安排。'
    }
  } finally {
    if (current === generation) confirming.value = false
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
onMounted(() => void load())
onBeforeUnmount(() => generation++)
</script>

<style scoped>
.agent-plan {
  display: grid;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-3);
  padding: var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface);
}
.plan-heading,
.plan-step,
.plan-actions {
  display: flex;
  gap: var(--lp-space-3);
  align-items: center;
  justify-content: space-between;
}
h3,
p,
ol {
  margin: 0;
}
h3 {
  font-size: var(--lp-text-base);
}
.plan-heading p,
.plan-confirmed-at {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.plan-status {
  color: var(--lp-success);
  font-size: var(--lp-text-sm);
}
.plan-steps {
  display: grid;
  gap: var(--lp-space-3);
  padding-left: var(--lp-space-5);
}
.plan-step p,
.plan-unavailable {
  color: var(--lp-text-secondary);
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.plan-step > div {
  display: grid;
  gap: var(--lp-space-1);
}
.plan-loading {
  color: var(--lp-text-secondary);
  margin-top: var(--lp-space-3);
}
.plan-retry {
  display: grid;
  gap: var(--lp-space-3);
  justify-items: start;
  margin-top: var(--lp-space-3);
}
</style>
