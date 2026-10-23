<script setup lang="ts">
import { computed, onScopeDispose, ref, watch } from 'vue'
import { Edit3, BookOpen } from '@lucide/vue'
import { LtButton } from '@/components/living'
import MarkdownRenderer from '@/views/question/QuestionProse.vue'
import { streamExamLearningAi, streamQuestionAi } from '@/api/ai'
import { errorMessage, isAbortError } from '@/utils/errors'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'

defineOptions({ name: 'PracticeAiAssistant' })
type AssistantType = 'explanation' | 'variant'
const props = defineProps<{
  questionId: number
  learningSessionId?: number
  disabled?: boolean
  disabledReason?: string
}>()
const activeType = ref<AssistantType | null>(null),
  loading = ref(false),
  error = ref(''),
  stopped = ref(false)
const results = ref<Record<AssistantType, string>>({ explanation: '', variant: '' }),
  sources = ref<Record<AssistantType, string>>({ explanation: '', variant: '' })
const completed = new Set<AssistantType>()
let controller: AbortController | null = null,
  generation = 0,
  alive = true
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
    session = getAuthSessionVersion(),
    request = new AbortController()
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

<template>
  <section class="assistant" aria-label="AI 学习助手">
    <div class="toolbar">
      <h3>AI 学习助手</h3>
      <div class="actions">
        <LtButton
          :aria-pressed="activeType === 'explanation'"
          :disabled="loading || disabled"
          @click="generate('explanation')"
          ><template #icon><BookOpen aria-hidden="true" /></template>补充解析</LtButton
        ><LtButton :aria-pressed="activeType === 'variant'" :disabled="loading || disabled" @click="generate('variant')"
          ><template #icon><Edit3 aria-hidden="true" /></template>变式练习</LtButton
        >
      </div>
    </div>
    <p v-if="disabled && disabledReason" class="note">{{ disabledReason }}</p>
    <div v-if="activeType" class="result" :aria-busy="loading">
      <div class="result-head">
        <strong>{{ activeType === 'variant' ? '变式练习' : '补充解析' }}</strong
        ><span v-if="activeSource" class="note">{{ activeSource === 'fallback' ? '本地提示' : 'AI 生成' }}</span
        ><LtButton v-if="loading" size="sm" variant="quiet" @click="stop">停止生成</LtButton>
      </div>
      <p v-if="loading" class="note" role="status">{{ activeResult ? '正在生成…' : '正在连接 AI 服务…' }}</p>
      <p v-else-if="stopped" class="note" role="status">已停止，当前内容可能不完整。</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <MarkdownRenderer class="markdown-body" v-if="activeResult" :content="activeResult" /><LtButton
        v-if="(error || stopped) && !loading"
        :disabled="disabled"
        @click="generate(activeType, true)"
        >重新生成</LtButton
      >
    </div>
  </section>
</template>

<style scoped>
.assistant {
  display: grid;
  gap: var(--lt-space-4);
  margin-top: var(--lt-space-5);
  padding: var(--lt-space-5);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
}
.toolbar,
.actions,
.result-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--lt-space-3);
}
.toolbar {
  justify-content: space-between;
}
.toolbar h3 {
  margin: 0;
  font-size: var(--lt-text-body);
}
.note {
  margin: 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.result {
  display: grid;
  gap: var(--lt-space-3);
  padding-top: var(--lt-space-4);
  border-top: solid var(--lt-hairline);
}
.result-head .lt-button {
  margin-left: auto;
}
.error {
  margin: 0;
  color: var(--lt-red-text);
  font-size: var(--lt-text-footnote);
}
</style>
