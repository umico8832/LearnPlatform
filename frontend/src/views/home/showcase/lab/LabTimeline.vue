<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useSpring } from '@/composables/useSpring'
import { springs } from '@/motion'

/**
 * The lab's big scrubber: one tick per simulated step, a little gap between loop
 * rounds, and a playhead that springs from step to step. Dragging anywhere scrubs;
 * every step can be reached in either direction because each one is a full frame.
 */
const props = defineProps<{
  count: number
  index: number
  /** Which loop round or phase each step belongs to; a change opens a gap. */
  groups: string[]
  /** Steps to light up, e.g. where a pointed-at line of code ran. */
  lit?: number[]
  /** A stable key per step, so ticks that mean the same moment slide instead of popping. */
  keys?: number[]
}>()

const emit = defineEmits<{ seek: [index: number]; scrub: [active: boolean] }>()

/** A gap between rounds, in step widths. */
const GAP = 0.9

const track = ref<HTMLElement | null>(null)
const thumb = ref<HTMLElement | null>(null)
const width = ref(0)
const head = useSpring(0, springs.snappy)
const scrubbing = ref(false)
let observer: ResizeObserver | null = null

/** Each step's position along the track, in units before scaling to the width. */
const units = computed(() => {
  const out: number[] = []
  let at = 0
  for (let i = 0; i < props.count; i++) {
    if (i > 0) at += 1 + (props.groups[i] !== props.groups[i - 1] ? GAP : 0)
    out.push(at)
  }
  return out
})

function xOf(i: number) {
  const all = units.value
  const span = all[all.length - 1] || 1
  return ((all[Math.max(0, Math.min(i, all.length - 1))] ?? 0) / span) * width.value
}

function indexAt(x: number) {
  let best = 0
  let distance = Infinity
  for (let i = 0; i < props.count; i++) {
    const d = Math.abs(xOf(i) - x)
    if (d < distance) {
      best = i
      distance = d
    }
  }
  return best
}

const ticks = computed(() => {
  const lit = new Set(props.lit)
  return Array.from({ length: props.count }, (_, i) => ({
    key: props.keys?.[i] ?? i,
    x: xOf(i),
    played: i <= props.index,
    lit: lit.has(i),
  }))
})

watch(
  () => [props.index, width.value, props.count] as const,
  () => {
    if (!scrubbing.value) head.set(xOf(props.index))
  },
)

function local(event: PointerEvent) {
  const box = track.value!.getBoundingClientRect()
  return Math.max(0, Math.min(box.width, event.clientX - box.left))
}

function onDown(event: PointerEvent) {
  if (event.button !== 0 || !track.value) return
  track.value.setPointerCapture(event.pointerId)
  scrubbing.value = true
  emit('scrub', true)
  onMove(event)
}

function onMove(event: PointerEvent) {
  if (!scrubbing.value) return
  const x = local(event)
  head.jump(x)
  const i = indexAt(x)
  if (i !== props.index) emit('seek', i)
}

function onUp() {
  if (!scrubbing.value) return
  scrubbing.value = false
  emit('scrub', false)
  head.set(xOf(props.index))
}

/** For the demo pointer, which drags the playhead by a pixel offset. */
function scrubTo(x: number) {
  const clamped = Math.max(0, Math.min(width.value, x))
  head.jump(clamped)
  const i = indexAt(clamped)
  if (i !== props.index) emit('seek', i)
}

function settle() {
  head.set(xOf(props.index))
}

defineExpose({ thumb, xOf, scrubTo, settle })

onMounted(() => {
  observer = new ResizeObserver(([entry]) => {
    width.value = entry.contentRect.width
    head.jump(xOf(props.index))
  })
  if (track.value) observer.observe(track.value)
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <div
    class="timeline"
    role="slider"
    aria-label="Step"
    :aria-valuemin="1"
    :aria-valuemax="count"
    :aria-valuenow="index + 1"
    tabindex="0"
    :data-scrubbing="scrubbing || undefined"
    @keydown.right.prevent="emit('seek', Math.min(count - 1, index + 1))"
    @keydown.left.prevent="emit('seek', Math.max(0, index - 1))"
  >
    <div ref="track" class="track" @pointerdown="onDown" @pointermove="onMove" @pointerup="onUp" @pointercancel="onUp">
      <span
        v-for="tick in ticks"
        :key="tick.key"
        class="tick"
        :data-played="tick.played || undefined"
        :data-lit="tick.lit || undefined"
        :style="{ left: `${tick.x}px` }"
      />
      <span class="fill" :style="{ width: `${Math.max(0, head.value.value)}px` }" />
      <span ref="thumb" class="thumb" :style="{ transform: `translateX(${head.value.value}px)` }">
        <span class="knob" />
      </span>
    </div>
  </div>
</template>

<style scoped>
.timeline {
  padding: 0 10px;
  border-radius: var(--lt-radius-md);
  outline: none;
}

.timeline:focus-visible {
  box-shadow: 0 0 0 3px var(--lt-accent-soft);
}

.track {
  position: relative;
  height: 44px;
  cursor: grab;
  touch-action: none;
}

.timeline[data-scrubbing] .track {
  cursor: grabbing;
}

/* A ruler of steps: faint ahead of the playhead, brighter behind it. */
.tick {
  position: absolute;
  top: 50%;
  width: 2px;
  height: 12px;
  margin-left: -1px;
  border-radius: 1px;
  background: var(--lt-label-3);
  opacity: 0.55;
  translate: 0 -50%;
  transition:
    left var(--lt-dur-layout) var(--lt-ease-layout),
    height var(--lt-dur-snappy) var(--lt-ease-snappy),
    box-shadow var(--lt-dur-fade) var(--lt-ease-fade),
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    opacity var(--lt-dur-fade) var(--lt-ease-fade);
}

.tick[data-played] {
  background: var(--lt-label-2);
  opacity: 1;
}

@starting-style {
  .tick {
    height: 0;
    opacity: 0;
  }
}

/* Where the pointed-at line ran: taller, in the accent, glowing. */
.tick[data-lit] {
  height: 22px;
  background: var(--lt-accent);
  box-shadow: 0 0 10px var(--lt-accent);
  opacity: 1;
}

.fill {
  position: absolute;
  top: 50%;
  left: 0;
  height: 2px;
  border-radius: 1px;
  background: color-mix(in oklab, var(--lt-accent) 45%, transparent);
  translate: 0 -50%;
  pointer-events: none;
}

.thumb {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 0;
  pointer-events: none;
}

.knob {
  position: absolute;
  top: 50%;
  left: -8px;
  width: 16px;
  height: 34px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-accent-fill);
  box-shadow:
    0 0 0 3px var(--lt-surface),
    0 0 18px color-mix(in oklab, var(--lt-accent) 60%, transparent);
  translate: 0 -50%;
  transition: scale var(--lt-dur-snappy) var(--lt-ease-snappy);
}

.timeline[data-scrubbing] .knob {
  scale: 1.12;
}
</style>
