<template>
  <section class="agent-panel" aria-labelledby="tutor-agent-heading">
    <header class="agent-heading">
      <div>
        <LpKicker>按需追问</LpKicker>
        <h2 id="tutor-agent-heading">继续问 Tutor</h2>
      </div>
      <span v-if="run" class="agent-status">等待你的问题</span>
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
import { computed, onMounted, ref } from 'vue'
import { getTutorAgentRun, resumeTutorAgentRun, startTutorAgentRun, type TutorAgentRunVO } from '@/api/tutor'
import { errorMessage } from '@/utils/errors'

const props = defineProps<{ courseId: number; sessionKey: string }>()
const question = ref('')
const submitting = ref(false)
const failure = ref('')
const run = ref<TutorAgentRunVO>()
const canSend = computed(() => !submitting.value && question.value.trim().length > 0)
const storageKey = `lp:tutor-agent-run:${props.courseId}:${props.sessionKey}`

async function send() {
  const message = question.value.trim()
  if (!message || submitting.value) return
  submitting.value = true
  failure.value = ''
  try {
    const response = run.value
      ? await resumeTutorAgentRun(props.courseId, props.sessionKey, run.value.runKey, message)
      : await startTutorAgentRun(props.courseId, props.sessionKey, message)
    run.value = response.data
    sessionStorage.setItem(storageKey, run.value.runKey)
    question.value = ''
  } catch (error) {
    failure.value = errorMessage(error, 'Tutor 暂时无法回答，请稍后重试')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  const storedRunKey = sessionStorage.getItem(storageKey)
  if (!storedRunKey) return
  try {
    run.value = (await getTutorAgentRun(props.courseId, props.sessionKey, storedRunKey)).data
  } catch {
    sessionStorage.removeItem(storageKey)
  }
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
