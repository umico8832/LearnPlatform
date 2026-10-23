<template>
  <LtCard as="section" radius="xl" class="lt agent-panel" data-appearance="dark" aria-labelledby="tutor-agent-heading">
    <header class="agent-heading">
      <div>
        <p class="agent-eyebrow">按需追问</p>
        <h2 id="tutor-agent-heading">继续问 Tutor</h2>
      </div>
      <span v-if="statusLabel" class="agent-status" role="status" aria-atomic="true">{{ statusLabel }}</span>
    </header>
    <p class="agent-intro">教学解释只依据本节已审查内容；理解检查会按实际作答记录。</p>

    <div v-if="run?.messages.length" class="agent-messages">
      <article
        v-for="item in run.messages"
        :key="item.sequence"
        class="agent-message"
        :class="item.role === 'USER' ? 'is-user' : 'is-tutor'"
      >
        <span>{{ item.role === 'USER' ? '你' : 'Tutor' }}</span>
        <QuestionProse v-if="item.role === 'ASSISTANT'" class="agent-message-content" :content="item.content" />
        <p v-else>{{ item.content }}</p>
        <span
          v-for="action in (item.role === 'ASSISTANT' ? (item.actions ?? []) : []).filter(
            (candidate) => candidate.type === 'HINT',
          )"
          :key="action.type + action.level"
          class="agent-action-marker"
        >
          第 {{ action.level }} 级提示
        </span>
        <LtButton
          v-if="item.role === 'ASSISTANT' && (item.actions ?? []).some((action) => action.type === 'CHECK')"
          data-testid="agent-request-check"
          class="agent-check-action"
          @click="emit('request-check')"
        >
          {{ checkResult ? '查看检查结果' : '开始理解检查' }}
        </LtButton>
        <TutorAgentPractice
          v-for="action in (item.actions ?? []).filter(
            (candidate) => item.role === 'ASSISTANT' && candidate.type === 'PRACTICE',
          )"
          :key="`practice-${item.sequence}-${action.type}`"
          :course-id="courseId"
          :session-key="sessionKey"
          :run-key="run!.runKey"
          :sequence="item.sequence"
          :busy="!canContinueFromCheck"
          :allow-follow-up="item.sequence === latestPracticeSequence"
          @continue-practice="continueFromPractice"
        />
        <TutorAgentPlan
          v-for="action in (item.actions ?? []).filter(
            (candidate) => item.role === 'ASSISTANT' && candidate.type === 'PLAN',
          )"
          :key="`plan-${item.sequence}-${action.type}`"
          :course-id="courseId"
          :session-key="sessionKey"
          :run-key="run!.runKey"
          :sequence="item.sequence"
          :busy="!canContinueFromCheck"
        />
      </article>
    </div>

    <p v-if="!restoring && !failure && !run?.messages.length" class="agent-empty">
      还没有追问记录，可以从一个问题开始。
    </p>
    <p v-if="submitting" class="agent-thinking" role="status">Tutor 正在生成完整回复…</p>
    <p v-if="failure" class="agent-error" role="alert">{{ failure }}</p>

    <LtButton
      v-if="run?.status === 'RUNNING' || restoreFailed"
      data-testid="agent-refresh"
      :disabled="restoring || submitting"
      @click="restore()"
    >
      {{ restoreFailed ? '重试恢复对话' : '刷新状态' }}
    </LtButton>

    <p v-if="run?.status === 'FAILED'" class="agent-thinking">上次回答未完成，可重新发送问题；历史对话已保留。</p>

    <div class="agent-quick-actions">
      <LtButton data-testid="agent-request-plan" :disabled="!canContinueFromCheck" @click="requestPlan"
        >建议学习安排</LtButton
      >
      <LtButton data-testid="agent-request-practice" :disabled="!canContinueFromCheck" @click="requestPractice"
        >练一道变式题</LtButton
      >

      <LtButton v-if="!checkResult" data-testid="agent-request-hint" :disabled="!canRequestHint" @click="requestHint">
        {{ hintLevel ? '再提示一步' : '给我一点提示' }}
      </LtButton>

      <LtButton
        v-if="checkResult"
        data-testid="agent-follow-up-check"
        :disabled="!canContinueFromCheck"
        @click="continueFromCheck"
      >
        请 Tutor 根据作答继续指导
      </LtButton>
    </div>

    <div class="agent-composer">
      <label for="tutor-agent-question">你的问题</label>
      <textarea
        id="tutor-agent-question"
        v-model="question"
        data-testid="agent-input"
        rows="3"
        maxlength="2000"
        :disabled="submitting"
        placeholder="例如：为什么移动方向不能反过来？"
        @keydown.meta.enter="send"
        @keydown.ctrl.enter="send"
      ></textarea>
      <div class="composer-actions">
        <small>按 Ctrl / ⌘ + Enter 发送 · {{ question.length }} / 2000</small>
        <LtButton data-testid="agent-submit" variant="primary" :disabled="!canSend" @click="send">
          {{ run ? '继续提问' : '开始提问' }}
        </LtButton>
      </div>
    </div>

    <details class="agent-records" data-testid="agent-records">
      <summary>学习偏好与本次复盘</summary>
      <TutorMemory :course-id="courseId" :busy="!canContinueFromCheck" />
      <TutorSessionNotes
        :course-id="courseId"
        :session-key="sessionKey"
        :check-result="checkResult"
        :busy="!canContinueFromCheck"
      />
    </details>
  </LtCard>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  getTutorAgentRun,
  getLatestTutorAgentRun,
  resumeTutorAgentRun,
  startTutorAgentRun,
  type TutorAgentRunVO,
} from '@/api/tutor'
import { errorMessage } from '@/utils/errors'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import { LtButton, LtCard } from '@/components/living'
import QuestionProse from '@/views/question/QuestionProse.vue'
import TutorAgentPractice from './TutorAgentPractice.vue'
import TutorAgentPlan from './TutorAgentPlan.vue'
import TutorMemory from './TutorMemory.vue'
import TutorSessionNotes from './TutorSessionNotes.vue'

const props = defineProps<{
  courseId: number
  sessionKey: string
  checkResult?: { correct: boolean } | null
}>()
const emit = defineEmits<{ 'request-check': [] }>()
const question = ref('')
const submitting = ref(false)
const failure = ref('')
const run = ref<TutorAgentRunVO>()
const restoring = ref(false)
const restoreFailed = ref(false)
const storedRunKey = ref<string | null>(null)
const canSend = computed(
  () =>
    !submitting.value &&
    !restoring.value &&
    !restoreFailed.value &&
    run.value?.status !== 'RUNNING' &&
    question.value.trim().length > 0,
)
const canContinueFromCheck = computed(
  () => !submitting.value && !restoring.value && !restoreFailed.value && run.value?.status !== 'RUNNING',
)
const hintLevel = computed(() => {
  let highest = 0
  for (const message of run.value?.messages ?? []) {
    if (message.role !== 'ASSISTANT') continue
    for (const action of message.actions ?? []) {
      if (action.type === 'HINT') highest = Math.max(highest, action.level)
    }
  }
  return highest
})
const canRequestHint = computed(() => !props.checkResult && canContinueFromCheck.value && hintLevel.value < 3)
const latestPracticeSequence = computed(() =>
  Math.max(
    0,
    ...(run.value?.messages ?? [])
      .filter(
        (message) => message.role === 'ASSISTANT' && message.actions?.some((action) => action.type === 'PRACTICE'),
      )
      .map((message) => message.sequence),
  ),
)
const storageKey = computed(() => `lp:tutor-agent-run:${props.courseId}:${props.sessionKey}`)
const statusLabel = computed(() => {
  if (restoring.value) return '正在恢复对话'
  if (submitting.value || run.value?.status === 'RUNNING') return '正在处理'
  if (restoreFailed.value) return '对话恢复失败'
  if (run.value?.status === 'FAILED') return '回答未完成，可重试'
  return run.value ? '等待你的问题' : ''
})
let generation = 0
let alive = true

function clearStoredRunKeys() {
  try {
    for (let index = sessionStorage.length - 1; index >= 0; index--) {
      const key = sessionStorage.key(index)
      if (key?.startsWith('lp:tutor-agent-run:')) sessionStorage.removeItem(key)
    }
  } catch {
    // Storage is optional; the authenticated server session remains the source of truth.
  }
}

function isCurrent(current: number, authSession: number) {
  return alive && current === generation && authSession === getAuthSessionVersion()
}

function clearForNewAccount() {
  generation++
  clearStoredRunKeys()
  run.value = undefined
  question.value = ''
  failure.value = ''
  submitting.value = false
  restoring.value = false
  restoreFailed.value = false
  storedRunKey.value = null
}
const unsubscribeAuth = onAuthSessionChange(clearForNewAccount)

async function send() {
  if (!canSend.value) return
  await sendMessage(question.value.trim(), true)
}

async function continueFromCheck() {
  if (!canContinueFromCheck.value) return
  await sendMessage('请根据我本节理解检查的实际作答，继续指导我。', false)
}

async function requestHint() {
  if (!canRequestHint.value) return
  await sendMessage('请给我本节理解检查的下一步提示，不要直接告诉我答案。', false)
}
async function requestPractice() {
  if (!canContinueFromCheck.value) return
  await sendMessage('请推荐一道本节已审查的变式题，让我自己作答。', false)
}
async function requestPlan() {
  if (!canContinueFromCheck.value) return
  await sendMessage('请建议本课程接下来的学习安排，由我确认是否采用。', false)
}
async function continueFromPractice() {
  if (!canContinueFromCheck.value) return
  await sendMessage('请根据我刚才变式练习的服务端结果，继续指导我。', false)
}

async function sendMessage(message: string, clearQuestion: boolean) {
  const current = generation
  const authSession = getAuthSessionVersion()
  submitting.value = true
  failure.value = ''
  try {
    const response = run.value
      ? await resumeTutorAgentRun(props.courseId, props.sessionKey, run.value.runKey, message, {
          errorDisplay: 'inline',
        })
      : await startTutorAgentRun(props.courseId, props.sessionKey, message, { errorDisplay: 'inline' })
    if (!isCurrent(current, authSession)) return
    rememberRun(response.data)
    if (clearQuestion) question.value = ''
  } catch (error) {
    if (!isCurrent(current, authSession)) return
    failure.value = errorMessage(error, 'Tutor 暂时无法回答，请重试')
    await restore(true)
  } finally {
    if (isCurrent(current, authSession)) submitting.value = false
  }
}

function rememberRun(value: TutorAgentRunVO | null) {
  run.value = value ?? undefined
  storedRunKey.value = value?.runKey ?? null
  if (!value) return
  try {
    sessionStorage.setItem(storageKey.value, value.runKey)
  } catch {
    // 存储不可用时仍可通过本人会话恢复服务端已保存的运行。
  }
}

async function restore(preserveFailure = false) {
  if (restoring.value) return
  const current = generation
  const authSession = getAuthSessionVersion()
  restoring.value = true
  restoreFailed.value = false
  if (!preserveFailure) failure.value = ''
  try {
    const response = storedRunKey.value
      ? await getTutorAgentRun(props.courseId, props.sessionKey, storedRunKey.value, { errorDisplay: 'inline' })
      : await getLatestTutorAgentRun(props.courseId, props.sessionKey, { errorDisplay: 'inline' })
    if (!isCurrent(current, authSession)) return
    rememberRun(response.data)
  } catch (error) {
    if (!isCurrent(current, authSession)) return
    restoreFailed.value = true
    failure.value = errorMessage(error, '暂时无法恢复对话，请重试')
  } finally {
    if (isCurrent(current, authSession)) restoring.value = false
  }
}

watch(
  () => [props.courseId, props.sessionKey],
  () => {
    generation++
    run.value = undefined
    question.value = ''
    failure.value = ''
    submitting.value = false
    restoring.value = false
    restoreFailed.value = false
    try {
      storedRunKey.value = sessionStorage.getItem(storageKey.value)
    } catch {
      storedRunKey.value = null
    }
    void restore()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  alive = false
  generation++
  unsubscribeAuth()
})
</script>
<style scoped>
.agent-panel {
  min-width: 0;
}
.agent-heading {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: var(--lt-space-4);
}
.agent-heading h2 {
  margin: var(--lt-space-1) 0 0;
  font-size: var(--lt-text-title3);
}
.agent-eyebrow {
  margin: 0;
  font-size: var(--lt-text-footnote);
  color: var(--lt-label-2);
}
.agent-status {
  padding: var(--lt-space-1) var(--lt-space-2);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.agent-intro,
.agent-thinking,
.agent-empty {
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
}
.agent-error {
  color: var(--lt-red-text);
  line-height: var(--lt-leading-reading);
}
.agent-messages {
  display: grid;
  gap: var(--lt-space-5);
  margin-top: var(--lt-space-5);
  padding-top: var(--lt-space-5);
  border-top: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
}
.agent-message {
  min-width: 0;
  width: min(100%, calc(var(--lt-space-10) * 10));
  animation: tutor-message-arrive var(--lt-dur-fade) var(--lt-ease-fade);
}
@keyframes tutor-message-arrive {
  from {
    opacity: 0;
    transform: translateY(var(--lt-space-1));
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.agent-message > span {
  display: block;
  margin-bottom: var(--lt-space-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.agent-message p,
.agent-message-content {
  margin: 0;
  padding: var(--lt-space-4);
  background: var(--lt-well);
  border-radius: var(--lt-radius-md);
  line-height: var(--lt-leading-reading);
  overflow-wrap: anywhere;
}
.agent-message.is-user {
  justify-self: end;
}
.agent-message.is-user > span {
  text-align: end;
}
.agent-message.is-user p {
  background: var(--lt-accent-soft);
  white-space: pre-wrap;
}
.agent-check-action,
.agent-action-marker {
  margin-top: var(--lt-space-3);
}
.agent-message-content :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
  margin-top: var(--lt-space-3);
}
.agent-message-content :deep(th),
.agent-message-content :deep(td) {
  padding: var(--lt-space-2) var(--lt-space-3);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  text-align: start;
}
.agent-message-content :deep(:is(h1, h2, h3, h4)) {
  font-size: var(--lt-text-headline);
  margin: var(--lt-space-4) 0 var(--lt-space-2);
}
.agent-quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-2);
  margin-top: var(--lt-space-4);
}
.agent-composer {
  display: grid;
  gap: var(--lt-space-2);
  margin-top: var(--lt-space-5);
}
.agent-composer label {
  font-size: var(--lt-text-body);
}
.agent-composer textarea {
  width: 100%;
  padding: var(--lt-space-4);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-md);
  background: var(--lt-well);
  color: var(--lt-label);
  resize: vertical;
  font: inherit;
  line-height: var(--lt-leading-reading);
}
.agent-composer textarea::placeholder {
  color: var(--lt-label-2);
  opacity: 1;
}
.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-4);
}
.composer-actions small {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.agent-records {
  margin-top: var(--lt-space-6);
  padding-top: var(--lt-space-4);
  border-top: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
}
.agent-records > summary {
  color: var(--lt-label-2);
  cursor: pointer;
}
</style>
