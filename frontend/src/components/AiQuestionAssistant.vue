<template>
  <section class="ai-assistant" aria-label="AI 学习助手">
    <div class="ai-toolbar">
      <h3 class="ai-title">AI 学习助手</h3>
      <div class="ai-actions">
        <el-button
          :aria-pressed="activeType === 'explanation'"
          :disabled="loading || disabled"
          @click="generate('explanation')"
          ><el-icon><Reading /></el-icon>补充解析</el-button
        >
        <el-button :aria-pressed="activeType === 'variant'" :disabled="loading || disabled" @click="generate('variant')"
          ><el-icon><EditPen /></el-icon>变式练习</el-button
        >
      </div>
    </div>
    <p v-if="disabled && disabledReason" class="assistant-note">{{ disabledReason }}</p>
    <div v-if="activeType" class="ai-result" :aria-busy="loading">
      <div class="result-heading">
        <strong>{{ activeType === 'variant' ? '变式练习' : '补充解析' }}</strong>
        <span v-if="activeSource" class="assistant-note">{{
          activeSource === 'fallback' ? '本地提示' : 'AI 生成'
        }}</span>
        <el-button v-if="loading" text @click="stop">停止生成</el-button>
      </div>
      <p v-if="loading" class="stream-status" role="status">
        {{ activeResult ? '正在生成…' : '正在连接 AI 服务…' }}
      </p>
      <p v-else-if="stopped" class="stream-status" role="status">已停止，当前内容可能不完整。</p>
      <p v-if="error" class="assistant-error" role="alert">{{ error }}</p>
      <MarkdownRenderer v-if="activeResult" :content="activeResult" />
      <el-button
        v-if="(error || stopped) && !loading"
        :disabled="disabled"
        class="retry-action"
        @click="generate(activeType, true)"
      >
        重新生成
      </el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onScopeDispose, ref, watch } from 'vue'
import { EditPen, Reading } from '@element-plus/icons-vue'
import { streamExamLearningAi, streamQuestionAi } from '@/api/ai'
import { errorMessage, isAbortError } from '@/utils/errors'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

type AssistantType = 'explanation' | 'variant'
const props = defineProps<{
  questionId: number
  learningSessionId?: number
  disabled?: boolean
  disabledReason?: string
}>()
const activeType = ref<AssistantType | null>(null)
const loading = ref(false)
const error = ref('')
const stopped = ref(false)
const results = ref<Record<AssistantType, string>>({ explanation: '', variant: '' })
const sources = ref<Record<AssistantType, string>>({ explanation: '', variant: '' })
const completed = new Set<AssistantType>()
let controller: AbortController | null = null
let generation = 0
let alive = true
const activeResult = computed(() => (activeType.value ? results.value[activeType.value] : ''))
const activeSource = computed(() => (activeType.value ? sources.value[activeType.value] : ''))

function stop() {
  generation++
  controller?.abort()
  controller = null
  loading.value = false
  stopped.value = true
}
function reset() {
  stop()
  activeType.value = null
  stopped.value = false
  error.value = ''
  completed.clear()
  results.value = { explanation: '', variant: '' }
  sources.value = { explanation: '', variant: '' }
}

async function generate(type: AssistantType, retry = false) {
  if (props.disabled || loading.value) return
  activeType.value = type
  error.value = ''
  stopped.value = false
  if (!retry && completed.has(type)) return
  const version = ++generation,
    session = getAuthSessionVersion()
  const request = new AbortController()
  const current = () =>
    alive && version === generation && session === getAuthSessionVersion() && !request.signal.aborted
  controller = request
  completed.delete(type)
  results.value[type] = ''
  sources.value[type] = ''
  loading.value = true
  try {
    const handlers = {
      onContent: (content: string) => {
        if (current()) results.value[type] += content
      },
      onDone: (source: string) => {
        if (!current()) return
        sources.value[type] = source
        completed.add(type)
      },
    }
    if (props.learningSessionId)
      await streamExamLearningAi(type, props.learningSessionId, props.questionId, handlers, request.signal)
    else await streamQuestionAi(type, props.questionId, handlers, request.signal)
  } catch (cause) {
    if (current() && !isAbortError(cause)) error.value = errorMessage(cause, 'AI 暂时无法完成回答，请重试。')
  } finally {
    if (current()) {
      controller = null
      loading.value = false
    }
  }
}
watch(() => [props.questionId, props.learningSessionId], reset, { flush: 'sync' })
watch(
  () => props.disabled,
  (value) => {
    if (value && loading.value) stop()
  },
)
onScopeDispose(onAuthSessionChange(reset))
onScopeDispose(() => {
  alive = false
  reset()
})
</script>

<style scoped>
.ai-assistant {
  margin-top: var(--lp-space-5);
  padding: var(--lp-space-5);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface-subtle);
  text-align: left;
}
.ai-toolbar,
.ai-actions,
.result-heading {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  flex-wrap: wrap;
}
.ai-toolbar {
  justify-content: space-between;
}
.ai-title {
  margin: 0;
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-semibold);
}
.ai-actions :deep(.el-button) {
  margin-left: 0;
}
.ai-actions :deep([aria-pressed='true']) {
  color: var(--lp-primary);
  border-color: var(--lp-primary);
  background: var(--lp-primary-soft);
}
.assistant-note,
.stream-status {
  margin: var(--lp-space-3) 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.ai-result {
  margin-top: var(--lp-space-4);
  padding-top: var(--lp-space-4);
  border-top: var(--lp-border-hairline);
}
.result-heading {
  margin-bottom: var(--lp-space-3);
  font-size: var(--lp-text-sm);
}
.result-heading .assistant-note {
  margin: 0;
}
.result-heading :deep(.el-button) {
  margin-left: auto;
}
.assistant-error {
  color: var(--lp-danger);
  font-size: var(--lp-text-sm);
}
.retry-action {
  margin-top: var(--lp-space-4);
}
@media (max-width: 720px) {
  .ai-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
  .ai-actions {
    width: 100%;
  }
  .ai-actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
