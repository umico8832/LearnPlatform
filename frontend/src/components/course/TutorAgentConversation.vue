<template>
  <section class="agent-panel" aria-labelledby="tutor-agent-heading">
    <header class="agent-heading">
      <div>
        <LpKicker>按需追问</LpKicker>
        <h2 id="tutor-agent-heading">继续问 Tutor</h2>
      </div>
      <span v-if="statusLabel" class="agent-status" role="status">{{ statusLabel }}</span>
    </header>
    <p class="agent-intro">回答只依据本节已审查内容；理解检查仍由服务端判分。</p>

    <div v-if="run?.messages.length" class="agent-messages" aria-live="polite">
      <article
        v-for="item in run.messages"
        :key="item.sequence"
        class="agent-message"
        :class="item.role === 'USER' ? 'is-user' : 'is-tutor'"
      >
        <span>{{ item.role === 'USER' ? '你' : 'Tutor' }}</span>
        <p>{{ item.content }}</p>
      </article>
    </div>

    <p v-if="submitting" class="agent-thinking" role="status">Tutor 正在核对本节内容…</p>
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" show-icon />

    <el-button
      v-if="run?.status === 'RUNNING' || restoreFailed"
      data-testid="agent-refresh"
      :loading="restoring"
      :disabled="restoring || submitting"
      @click="restore()"
    >
      {{ restoreFailed ? '重试恢复对话' : '刷新状态' }}
    </el-button>
    <p v-if="run?.status === 'FAILED'" class="agent-thinking">上次回答未完成，可重新发送问题；历史对话已保留。</p>

    <div class="agent-composer">
      <label for="tutor-agent-question">你的问题</label>
      <el-input
        id="tutor-agent-question"
        v-model="question"
        data-testid="agent-input"
        type="textarea"
        :rows="3"
        :maxlength="2000"
        show-word-limit
        :disabled="submitting"
        placeholder="例如：为什么移动方向不能反过来？"
        @keydown.meta.enter="send"
        @keydown.ctrl.enter="send"
      />
      <div class="composer-actions">
        <small>按 Ctrl / ⌘ + Enter 发送</small>
        <el-button data-testid="agent-submit" type="primary" :loading="submitting" :disabled="!canSend" @click="send">
          {{ run ? '继续提问' : '开始提问' }}
        </el-button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getTutorAgentRun, resumeTutorAgentRun, startTutorAgentRun, type TutorAgentRunVO } from '@/api/tutor'
import { errorMessage } from '@/utils/errors'

const props = defineProps<{ courseId: number; sessionKey: string }>()
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
const storageKey = computed(() => `lp:tutor-agent-run:${props.courseId}:${props.sessionKey}`)
const statusLabel = computed(() => {
  if (restoring.value) return '正在恢复对话'
  if (submitting.value || run.value?.status === 'RUNNING') return '正在处理'
  if (restoreFailed.value) return '对话恢复失败'
  if (run.value?.status === 'FAILED') return '回答未完成，可重试'
  return run.value ? '等待你的问题' : ''
})
let generation = 0

async function send() {
  if (!canSend.value) return
  const message = question.value.trim()
  const current = generation
  submitting.value = true
  failure.value = ''
  try {
    const response = run.value
      ? await resumeTutorAgentRun(props.courseId, props.sessionKey, run.value.runKey, message)
      : await startTutorAgentRun(props.courseId, props.sessionKey, message)
    if (current !== generation) return
    run.value = response.data
    storedRunKey.value = run.value.runKey
    try {
      sessionStorage.setItem(storageKey.value, run.value.runKey)
    } catch {
      // 存储不可用不改变已成功保存到服务端的回答。
    }
    question.value = ''
  } catch (error) {
    if (current !== generation) return
    failure.value = errorMessage(error, 'Tutor 暂时无法回答，请稍后重试')
    if (storedRunKey.value) await restore(true)
  } finally {
    if (current === generation) submitting.value = false
  }
}

async function restore(preserveFailure = false) {
  if (!storedRunKey.value || restoring.value) return
  const current = generation
  restoring.value = true
  restoreFailed.value = false
  if (!preserveFailure) failure.value = ''
  try {
    const response = await getTutorAgentRun(props.courseId, props.sessionKey, storedRunKey.value)
    if (current !== generation) return
    run.value = response.data
  } catch (error) {
    if (current !== generation) return
    restoreFailed.value = true
    failure.value = errorMessage(error, '暂时无法恢复对话，请重试')
  } finally {
    if (current === generation) restoring.value = false
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
  generation++
})
</script>

<style scoped>
.agent-panel {
  padding: var(--lp-space-6);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-left: 3px solid var(--lp-primary);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.agent-heading {
  display: flex;
  gap: var(--lp-space-4);
  align-items: flex-start;
  justify-content: space-between;
}

.agent-heading h2 {
  margin-top: var(--lp-space-1);
  color: var(--lp-text);
  font-size: var(--lp-text-xl);
}

.agent-status {
  flex: 0 0 auto;
  padding: var(--lp-space-1) var(--lp-space-2);
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
  border-radius: var(--lp-radius-full);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-semibold);
}

.agent-intro {
  margin: var(--lp-space-2) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}

.agent-messages {
  display: grid;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-5);
  padding-top: var(--lp-space-5);
  border-top: var(--lp-border-hairline);
}

.agent-message {
  max-width: min(88%, 680px);
}

.agent-message > span {
  display: block;
  margin-bottom: var(--lp-space-1);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-semibold);
}

.agent-message p {
  margin: 0;
  padding: var(--lp-space-3) var(--lp-space-4);
  color: var(--lp-text);
  background: var(--lp-surface-soft);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-relaxed);
  white-space: pre-wrap;
}

.agent-message.is-user {
  justify-self: end;
}

.agent-message.is-user > span {
  text-align: right;
}

.agent-message.is-user p {
  background: var(--lp-primary-soft);
  border-color: var(--lp-primary-softer);
}

.agent-thinking {
  margin: var(--lp-space-3) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}

.agent-composer {
  display: grid;
  gap: var(--lp-space-2);
  margin-top: var(--lp-space-5);
}

.agent-composer label {
  color: var(--lp-text);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
}

.composer-actions {
  display: flex;
  gap: var(--lp-space-4);
  align-items: center;
  justify-content: space-between;
}

.composer-actions small {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}

@media (max-width: 767px) {
  .agent-panel {
    padding: var(--lp-space-4);
  }

  .agent-message {
    max-width: 100%;
  }
}
</style>
