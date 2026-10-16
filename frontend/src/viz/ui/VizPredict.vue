<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ArrowRight, Check, Sparkles } from '@lucide/vue'
import { LtButton } from '@/components/living'
import { blurIn, springAnimate, springs } from '@/motion'
import type { GhostLayer } from '../engine/types'
import type { VizSession } from '../useVizSession'

/**
 * Predict-before-reveal checkpoint. Wrong options get misconception-specific
 * feedback and keep their ghost on the stage; looking at the answer is always allowed.
 */
const props = defineProps<{
  session: VizSession
  /** Step whose question is open. */
  index: number
  /** Where the panel grows out of, e.g. the checkpoint diamond on the timeline. */
  origin?: () => DOMRect | null
}>()

const KEYS = ['A', 'B', 'C', 'D']

const root = ref<HTMLElement | null>(null)
const feedbackEl = ref<HTMLElement | null>(null)
const continueButton = ref<InstanceType<typeof LtButton> | null>(null)
const optionEls: HTMLButtonElement[] = []
const solvedWith = ref<string | null>(null)
const wrong = ref(new Set<string>())
const feedback = ref<{ text: string; tone: 'right' | 'wrong' } | null>(null)
let pinned: GhostLayer | null = null

const predict = computed(() => props.session.trace.value?.steps[props.index]?.predict ?? null)
const ordinal = computed(() => props.session.checkpoints.value.findIndex((item) => item.index === props.index) + 1)

function order(text: string): number {
  let h = 2166136261
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

/** Stable shuffled order per question, so the right answer is not always first. */
const options = computed(() => {
  const current = predict.value
  if (!current) return []
  return [...current.options].sort((a, b) => order(current.id + a.id) - order(current.id + b.id))
})
const hasGhost = computed(() => options.value.some((option) => option.ghost))

function stateOf(id: string) {
  if (solvedWith.value === id) return 'right'
  if (wrong.value.has(id)) return 'wrong'
  return solvedWith.value ? 'dim' : 'idle'
}

function setGhost(layer: GhostLayer | null) {
  props.session.showGhost(layer)
}

function preview(i: number) {
  if (solvedWith.value) return
  setGhost(options.value[i]?.ghost ?? pinned)
}

function showFeedback(text: string, tone: 'right' | 'wrong') {
  feedback.value = { text, tone }
  void nextTick(() => feedbackEl.value && blurIn(feedbackEl.value, 3))
}

function solve(i: number) {
  const option = options.value[i]
  if (!option) return
  solvedWith.value = option.id
  pinned = null
  setGhost(null)
  void nextTick(() => {
    const button = optionEls[i]
    if (button) springAnimate(button, [{ transform: 'scale(0.96)' }, { transform: 'scale(1)' }], springs.pointer)
    const el = continueButton.value?.$el as HTMLElement | undefined
    el?.focus({ preventScroll: true })
  })
}

function choose(i: number) {
  const option = options.value[i]
  if (solvedWith.value || !option) return
  const { correct } = props.session.player.answer(option.id)
  showFeedback(option.feedback, correct ? 'right' : 'wrong')
  if (correct) {
    solve(i)
    return
  }
  wrong.value = new Set(wrong.value).add(option.id)
  pinned = option.ghost ?? null
  setGhost(pinned)
  const button = optionEls[i]
  if (button) {
    springAnimate(
      button,
      [
        { transform: 'translateX(0)' },
        { transform: 'translateX(-6px)' },
        { transform: 'translateX(5px)' },
        { transform: 'translateX(0)' },
      ],
      springs.fade,
      { duration: 360 },
    )
  }
}

function reveal() {
  const i = options.value.findIndex((option) => option.correct)
  props.session.player.reveal()
  showFeedback(options.value[i]?.feedback ?? '', 'right')
  solve(i)
}

function proceed() {
  props.session.player.resolve(props.session.player.playing)
}

/** A–D or 1–4 choose, Enter continues once solved. Returns true when handled. */
function handleKey(event: KeyboardEvent): boolean {
  const key = event.key.toUpperCase()
  const pick = KEYS.includes(key) ? KEYS.indexOf(key) : Number(key) - 1
  if (Number.isInteger(pick) && pick >= 0 && pick < options.value.length) {
    choose(pick)
    optionEls[pick]?.focus()
    return true
  }
  if (event.key === 'Enter' && solvedWith.value) {
    proceed()
    return true
  }
  return false
}

function zoomIn() {
  const el = root.value
  if (!el) return
  const origin = props.origin?.()
  const rect = el.getBoundingClientRect()
  el.style.transformOrigin =
    origin && rect.width
      ? `${origin.left + origin.width / 2 - rect.left}px ${origin.top + origin.height / 2 - rect.top}px`
      : ''
  springAnimate(
    el,
    [
      { transform: 'scale(0.2)', opacity: 0, filter: 'blur(8px)' },
      { transform: 'scale(1)', opacity: 1, filter: 'blur(0)' },
    ],
    springs.layout,
  )
}

watch(
  () => props.index,
  () => {
    solvedWith.value = null
    wrong.value = new Set()
    feedback.value = null
    pinned = null
    setGhost(null)
    void nextTick(zoomIn)
  },
)

onMounted(zoomIn)

defineExpose({ handleKey })
</script>

<template>
  <div v-if="predict" ref="root" class="viz-predict" role="group" aria-label="预测检查点">
    <div class="viz-predict-head">
      <span class="viz-predict-badge"><Sparkles :size="14" aria-hidden="true" />先预测一下</span>
      <span class="viz-predict-count">检查点 {{ ordinal }} / {{ session.checkpoints.value.length }}</span>
    </div>
    <h3 class="viz-predict-prompt">{{ predict.prompt }}</h3>
    <div class="viz-predict-options" :data-count="options.length">
      <button
        v-for="(option, i) in options"
        :key="option.id"
        :ref="(el) => (optionEls[i] = el as HTMLButtonElement)"
        type="button"
        class="viz-option"
        :data-state="stateOf(option.id)"
        :disabled="Boolean(solvedWith) && solvedWith !== option.id"
        @click="choose(i)"
        @pointerenter="preview(i)"
        @pointerleave="preview(-1)"
        @focus="preview(i)"
        @blur="preview(-1)"
      >
        <kbd class="viz-option-key">{{ KEYS[i] }}</kbd>
        <span class="viz-option-text">{{ option.text }}</span>
        <Check v-if="solvedWith === option.id" class="viz-option-mark" :size="16" aria-hidden="true" />
      </button>
    </div>
    <div class="viz-predict-foot">
      <p v-if="feedback" ref="feedbackEl" class="viz-predict-feedback" :data-tone="feedback.tone" aria-live="polite">
        {{ feedback.text }}
      </p>
      <div v-if="solvedWith" class="viz-predict-actions">
        <p class="viz-predict-takeaway"><span>要点</span>{{ predict.takeaway }}</p>
        <LtButton ref="continueButton" variant="primary" @click="proceed">
          继续
          <template #trailing><ArrowRight aria-hidden="true" /></template>
        </LtButton>
      </div>
      <div v-else class="viz-predict-actions">
        <span class="viz-predict-hint">
          {{ hasGhost ? '把指针移到选项上，图里会用虚线画出它的后果' : '想好再选，答错也会告诉你错在哪' }}
        </span>
        <LtButton variant="quiet" @click="reveal">直接看答案</LtButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.viz-predict-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--lt-text-caption);
}

.viz-predict-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--lt-purple-text);
  font-weight: 600;
}

.viz-predict-count {
  color: var(--lt-label-3);
}

.viz-predict-prompt {
  margin: 6px 0 12px;
  font-size: var(--lt-text-headline);
  line-height: 1.4;
  text-wrap: pretty;
}

.viz-predict-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.viz-predict-options:is([data-count='2'], [data-count='4']) {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.viz-option {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  padding: 10px 12px;
  border: 0;
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 0.5px var(--lt-separator);
  color: var(--lt-label);
  font: 500 var(--lt-text-subhead) / 1.4 var(--lt-font);
  text-align: left;
  cursor: pointer;
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    box-shadow var(--lt-dur-fade) var(--lt-ease-fade),
    opacity var(--lt-dur-fade) var(--lt-ease-fade),
    transform var(--lt-dur-snappy) var(--lt-ease-snappy);
}

.viz-option:active:not(:disabled) {
  transform: scale(0.98);
}

.viz-option:disabled {
  cursor: default;
}

.viz-option-key {
  display: grid;
  flex: none;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 7px;
  background: var(--lt-surface);
  box-shadow: 0 0 0 0.5px var(--lt-separator);
  color: var(--lt-label-2);
  font: 600 var(--lt-text-caption) / 1 var(--lt-font-rounded);
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    color var(--lt-dur-fade) var(--lt-ease-fade);
}

.viz-option-text {
  flex: 1;
  min-width: 0;
}

.viz-option-mark {
  flex: none;
  color: var(--lt-green-text);
}

.viz-option[data-state='wrong'] {
  background: var(--lt-orange-soft);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--lt-orange) 45%, transparent);
}

.viz-option[data-state='wrong'] .viz-option-key {
  background: var(--lt-orange);
  color: var(--lt-on-tint);
}

.viz-option[data-state='right'] {
  background: var(--lt-green-soft);
  box-shadow: inset 0 0 0 1.5px var(--lt-green);
}

.viz-option[data-state='right'] .viz-option-key {
  background: var(--lt-green);
  color: var(--lt-on-tint);
}

.viz-option[data-state='dim'] {
  opacity: 0.45;
}

.viz-predict-foot {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}

.viz-predict-feedback {
  margin: 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-subhead);
  line-height: 1.55;
}

.viz-predict-feedback[data-tone='wrong'] {
  color: var(--lt-orange-text);
}

.viz-predict-feedback[data-tone='right'] {
  color: var(--lt-green-text);
}

.viz-predict-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-4);
}

.viz-predict-hint {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.viz-predict-takeaway {
  flex: 1;
  margin: 0;
  color: var(--lt-label);
  font-size: var(--lt-text-footnote);
  line-height: 1.55;
}

.viz-predict-takeaway span {
  margin-right: 8px;
  padding: 2px 7px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption2);
  font-weight: 600;
}

@media (hover: hover) and (pointer: fine) {
  .viz-option:not(:disabled):hover {
    background: var(--lt-fill-2);
  }
}
</style>
