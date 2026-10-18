<script setup lang="ts">
import { computed, nextTick, ref, useId } from 'vue'
import { ArrowUp, Lightbulb, Play, Square } from '@lucide/vue'
import { LtButton } from '@/components/living'
import type { AskSuggestion } from '@/api/questionAsk'
import QuestionProse from './QuestionProse.vue'
import { ASK_MAX_LENGTH, type useQuestionAsk } from './useQuestionAsk'

/**
 * “还是不懂？问一句”：学习者用自己的话问这道题。答对之前回答不给答案，建议只引导到
 * 提示或动画，由学习者自己点。
 */
const props = defineProps<{ ask: ReturnType<typeof useQuestionAsk>; solved: boolean }>()
const emit = defineEmits<{ hint: []; demo: [] }>()

const id = useId()
const draft = ref('')
const input = ref<HTMLTextAreaElement | null>(null)
const records = computed(() => props.ask.records.value)
const running = computed(() => props.ask.running.value)
const info = computed(() => props.ask.info.value)
const busy = computed(() => running.value !== null)
const tooLong = computed(() => draft.value.length > ASK_MAX_LENGTH)
const canSend = computed(() => !busy.value && !props.ask.exhausted.value && !!draft.value.trim() && !tooLong.value)

const remainingText = computed(() => {
  const value = info.value
  if (!value) return ''
  if (value.questionRemaining <= 0) return '这道题的追问次数用完了'
  const daily = value.dailyRemaining !== null && value.dailyRemaining < value.questionRemaining
  return daily ? `今天还能问 ${value.dailyRemaining} 次` : `这道题还能问 ${value.questionRemaining} 次`
})

async function submit() {
  if (!canSend.value) return
  const message = draft.value
  draft.value = ''
  const ok = await props.ask.send(message)
  if (!ok && !draft.value) draft.value = message
  await nextTick()
  input.value?.focus({ preventScroll: true })
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
    event.preventDefault()
    void submit()
  }
}

function suggestionLabel(suggestion: AskSuggestion) {
  return suggestion.action.type === 'HINT' ? `看第 ${suggestion.action.level} 级提示` : '去看演示'
}

function follow(suggestion: AskSuggestion) {
  if (suggestion.action.type === 'HINT') emit('hint')
  else emit('demo')
}

function failedText(code: number | null) {
  if (code === 1014) return '已停止'
  if (code === 1013) return '回答超时'
  return '没有得到回答'
}
</script>

<template>
  <div class="ask">
    <h2 class="title" tabindex="-1" data-focus>还是不懂？问一句</h2>
    <p class="lead">
      用你自己的话问这道题。{{ solved ? '现在可以讲完整的思路了。' : '答对之前，它不会直接给出答案。' }}
    </p>

    <ol v-if="records.length || running" class="thread">
      <li v-for="record in records" :key="record.requestId" class="turn">
        <p class="question"><span class="who">你问</span>{{ record.message }}</p>
        <template v-if="record.status === 'SUCCEEDED' && record.content">
          <QuestionProse class="answer" :content="record.content" />
          <div v-if="record.suggestion" class="suggestion">
            <span class="suggestion-reason">{{ record.suggestion.reason }}</span>
            <LtButton size="sm" @click="follow(record.suggestion)">
              <template #icon>
                <Lightbulb v-if="record.suggestion.action.type === 'HINT'" aria-hidden="true" />
                <Play v-else aria-hidden="true" />
              </template>
              {{ suggestionLabel(record.suggestion) }}
            </LtButton>
          </div>
        </template>
        <p v-else class="failed">{{ failedText(record.errorCode) }}</p>
      </li>
      <li v-if="running" class="turn" aria-live="polite">
        <p class="question"><span class="who">你问</span>{{ running.message }}</p>
        <QuestionProse v-if="running.content" class="answer" :content="running.content" />
        <p v-else class="thinking">正在想，回答核对完才会显示…</p>
      </li>
    </ol>

    <p v-if="ask.error.value" class="error" role="alert">{{ ask.error.value }}</p>

    <form class="composer" :data-disabled="ask.exhausted.value || undefined" @submit.prevent="submit">
      <label class="sr-only" :for="`${id}-input`">你的问题</label>
      <textarea
        :id="`${id}-input`"
        ref="input"
        v-model="draft"
        class="input"
        rows="2"
        :placeholder="ask.exhausted.value ? '次数用完了' : '比如：我选的这一项，错在哪一步？'"
        :disabled="ask.exhausted.value"
        :aria-describedby="`${id}-meta`"
        @keydown="onKeydown"
      />
      <div class="composer-foot">
        <span :id="`${id}-meta`" class="meta" :data-over="tooLong || undefined">
          <template v-if="tooLong">超过 {{ ASK_MAX_LENGTH }} 字了</template>
          <template v-else>{{ remainingText }} · ⌘/Ctrl + Enter 发送</template>
        </span>
        <LtButton v-if="busy" size="sm" @click="ask.stop()">
          <template #icon><Square aria-hidden="true" /></template>
          停止
        </LtButton>
        <LtButton v-else type="submit" variant="primary" size="sm" :disabled="!canSend">
          <template #icon><ArrowUp aria-hidden="true" /></template>
          发送
        </LtButton>
      </div>
    </form>
  </div>
</template>

<style scoped>
.title {
  margin: 0;
  font-size: var(--lt-text-title3);
}

.title:focus-visible {
  outline: none;
}

.lead {
  max-width: 40em;
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}

.thread {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-6);
  margin: var(--lt-space-6) 0 0;
  padding: 0;
  list-style: none;
}

.question {
  display: flex;
  gap: var(--lt-space-3);
  margin: 0;
  color: var(--lt-label);
  font-size: var(--lt-text-body);
  font-weight: 500;
  line-height: 1.55;
}

.who {
  flex: none;
  margin-top: 2px;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
  font-weight: 400;
}

.answer {
  max-width: 40em;
  margin-top: var(--lt-space-2);
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}

.thinking,
.failed {
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.suggestion {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-3);
}

.suggestion-reason {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}

.error {
  margin: var(--lt-space-4) 0 0;
  color: var(--lt-red-text);
  font-size: var(--lt-text-footnote);
}

.composer {
  max-width: 40em;
  margin-top: var(--lt-space-5);
  padding: var(--lt-space-3) var(--lt-space-3) var(--lt-space-2) var(--lt-space-4);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-surface);
  box-shadow:
    var(--lt-shadow-card),
    inset 0 0 0 0.5px var(--lt-separator);
  transition: box-shadow var(--lt-dur-fade) var(--lt-ease-fade);
}

.composer:focus-within {
  box-shadow:
    inset 0 0 0 1.5px var(--lt-accent),
    0 0 0 4px var(--lt-accent-soft);
}

.composer[data-disabled] {
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 0.5px var(--lt-separator);
}

.input {
  display: block;
  width: 100%;
  min-height: 48px;
  padding: 0;
  border: 0;
  outline: none;
  resize: vertical;
  background: transparent;
  color: var(--lt-label);
  font: var(--lt-text-body) / 1.55 var(--lt-font);
}

.input:focus-visible {
  outline: none;
}

.input::placeholder {
  color: var(--lt-label-3);
}

.composer-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-2);
}

.meta {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.meta[data-over] {
  color: var(--lt-red-text);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
