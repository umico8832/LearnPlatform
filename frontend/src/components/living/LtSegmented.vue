<script setup lang="ts" generic="T extends string">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useSpring } from '@/composables/useSpring'
import { springs } from '@/motion'
import type { SegmentOption } from './types'

const props = withDefaults(
  defineProps<{
    options: SegmentOption<T>[]
    /** Accessible name of the radio group. */
    label: string
    size?: 'sm' | 'md'
  }>(),
  { size: 'md' },
)

const model = defineModel<T>({ required: true })

const root = ref<HTMLElement | null>(null)
const buttons = new Map<string, HTMLButtonElement>()
const measured = ref(false)
const x = useSpring(0, springs.snappy)
const w = useSpring(0, springs.snappy)

function bindButton(value: string, el: unknown) {
  if (el instanceof HTMLButtonElement) buttons.set(value, el)
  else buttons.delete(value)
}

/** Move the thumb under the selected segment; the first placement jumps. */
function place() {
  const button = buttons.get(model.value)
  if (!button || !button.offsetWidth) return
  if (!measured.value) {
    x.jump(button.offsetLeft)
    w.jump(button.offsetWidth)
    measured.value = true
  } else {
    x.set(button.offsetLeft)
    w.set(button.offsetWidth)
  }
}

/** The thumb stretches with its speed and settles back: a liquid selection. */
const thumbStyle = computed(() => {
  const velocity = x.velocity.value
  const speed = Math.abs(velocity)
  const stretch = Math.min(speed * 0.045, 26)
  const squash = 1 - Math.min(speed / 9000, 0.14)
  const left = x.value.value - (velocity > 0 ? stretch * 0.35 : stretch * 0.65)
  return {
    width: `${w.value.value + stretch}px`,
    transform: `translateX(${left}px) scaleY(${squash})`,
    opacity: measured.value ? 1 : 0,
  }
})

function select(option: SegmentOption<T>) {
  if (option.disabled) return
  model.value = option.value
}

function onKeydown(event: KeyboardEvent) {
  const forward = event.key === 'ArrowRight' || event.key === 'ArrowDown'
  const backward = event.key === 'ArrowLeft' || event.key === 'ArrowUp'
  if (!forward && !backward) return
  event.preventDefault()
  const enabled = props.options.filter((option) => !option.disabled)
  if (!enabled.length) return
  const index = enabled.findIndex((option) => option.value === model.value)
  const next = enabled[(index + (forward ? 1 : -1) + enabled.length) % enabled.length]
  model.value = next.value
  void nextTick(() => buttons.get(next.value)?.focus())
}

let observer: ResizeObserver | null = null

onMounted(() => {
  place()
  if (typeof ResizeObserver !== 'undefined' && root.value) {
    observer = new ResizeObserver(() => place())
    observer.observe(root.value)
  }
})

onBeforeUnmount(() => observer?.disconnect())

watch(model, () => void nextTick(place))
watch(
  () => props.options.map((option) => option.label).join('\u0000'),
  () => void nextTick(place),
)
</script>

<template>
  <div ref="root" class="lt-segmented" role="radiogroup" :aria-label="label" :data-size="size">
    <span class="lt-segmented-thumb" :style="thumbStyle" aria-hidden="true" />
    <button
      v-for="option in options"
      :key="option.value"
      :ref="(el) => bindButton(option.value, el)"
      type="button"
      role="radio"
      class="lt-segmented-item"
      :aria-checked="option.value === model"
      :tabindex="option.value === model ? 0 : -1"
      :disabled="option.disabled"
      @click="select(option)"
      @keydown="onKeydown"
    >
      <span>{{ option.label }}</span>
      <span v-if="option.hint" class="lt-segmented-hint">{{ option.hint }}</span>
    </button>
  </div>
</template>

<style scoped>
.lt-segmented {
  position: relative;
  isolation: isolate;
  display: inline-flex;
  flex: none;
  padding: 3px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
}

.lt-segmented-thumb {
  position: absolute;
  top: 3px;
  bottom: 3px;
  left: 0;
  z-index: -1;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-thumb);
  box-shadow: var(--lt-shadow-thumb);
}

.lt-segmented-item {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 16px;
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: none;
  color: var(--lt-label-2);
  font: 500 var(--lt-text-footnote) / 1 var(--lt-font);
  white-space: nowrap;
  cursor: pointer;
  transition: color var(--lt-dur-fade) var(--lt-ease-fade);
}

.lt-segmented-item[aria-checked='true'] {
  color: var(--lt-label);
}

.lt-segmented-item:disabled {
  color: var(--lt-label-3);
  cursor: not-allowed;
}

.lt-segmented-hint {
  padding: 2px 6px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption2);
}

.lt-segmented[data-size='sm'] .lt-segmented-item {
  height: 24px;
  padding: 0 10px;
  font-size: var(--lt-text-caption);
}

@media (hover: hover) and (pointer: fine) {
  .lt-segmented-item:not([aria-checked='true'], :disabled):hover {
    color: var(--lt-label);
  }
}
</style>
