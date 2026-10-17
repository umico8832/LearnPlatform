<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useSpring } from '@/composables/useSpring'
import { Spring, springs } from '@/motion'
import type { Ghost } from './ghost'
import { useHeroFrames } from './activity'

/**
 * Spaced review, drawn as the forgetting curve it works against. Memory decays after every
 * review and jumps back to full at the next one; each kept review pushes the next one further
 * out, so the teeth of the curve grow longer. Below sits the stack of upcoming reviews: swipe
 * the top card right and a new, longer tooth draws itself in; swipe it left and the last
 * stretch sinks below the line and the next review comes back to tomorrow.
 */
const LADDER = [1, 3, 7, 16, 35]
const KEEP = 0.7
const LOST = 0.42
const FLOOR = 0.36
const WIDTH = 448
const HEIGHT = 116
const TOP = 14
const BOTTOM = HEIGHT - 10
const PAD = 10
const SHOWN = 4
const TAIL = 0.2
const COMMIT = 64
const REACH = 100
const FLY = 560
/** Where the curve starts inside the scene, to hang the date label under its point. */
const SCENE_X = 24
const SCENE_Y = 22

interface Review {
  at: number
  /** How far memory had sunk by this review: just to the line if kept, well below if not. */
  depth: Spring
  kept: boolean
}

const reviews: Review[] = [
  { at: 0, depth: new Spring(KEEP), kept: true },
  { at: 1, depth: new Spring(KEEP), kept: true },
  { at: 4, depth: new Spring(KEEP), kept: true },
]
const step = ref(2)
const next = ref(4 + LADDER[2])
const from = new Spring(0, springs.camera)
const reach = new Spring(next.value * (1 + TAIL), springs.camera)
const draw = new Spring(1, springs.camera)
const drawing = shallowRef({ line: '', area: '', tail: '', dots: [] as { x: number; y: number; kept: boolean }[] })
const marker = ref({ x: 0, y: 0, on: 1 })
const session = ref(3)
const offset = useSpring(0, springs.pointer)
const top = ref<HTMLElement | null>(null)
let grab: { pointer: number; start: number } | null = null
let leaving = false
let last = 0
let timer = 0

const lean = computed(() => Math.max(-1, Math.min(1, offset.value.value / COMMIT)))
const days = computed(() => next.value - reviews[reviews.length - 1].at)

/** Time runs on a gently compressed axis, so later, longer gaps still fit but read longer. */
function xOf(t: number) {
  const u = (t - from.value) / (reach.value - from.value)
  return PAD + Math.sign(u) * Math.abs(u) ** 0.62 * (WIDTH - PAD * 2)
}

const yOf = (memory: number) => TOP + ((1 - memory) / (1 - FLOOR)) * (BOTTOM - TOP)

/** Memory between two reviews: full right after the first, sinking to `depth` by the second. */
function stretch(start: number, end: number, depth: number, until = end, since = start) {
  const points: string[] = []
  for (let k = 0; k <= 28; k += 1) {
    const t = since + ((until - since) * k) / 28
    points.push(`${xOf(t).toFixed(1)},${yOf(depth ** ((t - start) / (end - start))).toFixed(1)}`)
  }
  return points
}

function paint() {
  const points: string[] = []
  for (let i = 0; i < reviews.length - 1; i += 1)
    points.push(...stretch(reviews[i].at, reviews[i + 1].at, reviews[i + 1].depth.value))
  const lastAt = reviews[reviews.length - 1].at
  const drawn = Math.max(0.001, Math.min(1, draw.value))
  points.push(...stretch(lastAt, next.value, KEEP, lastAt + (next.value - lastAt) * drawn))
  const line = `M${points.join(' L')}`
  const first = points[0].split(',')[0]
  const end = points[points.length - 1].split(',')[0]
  const after = next.value + (next.value - lastAt) * 0.6
  const settled = Math.max(0, (draw.value - 0.85) / 0.15)
  drawing.value = {
    line,
    area: `${line} L${end},${BOTTOM} L${first},${BOTTOM} Z`,
    tail: settled > 0 ? `M${stretch(lastAt, next.value, KEEP, after, next.value).join(' L')}` : '',
    dots: reviews.map((review) => ({ x: xOf(review.at), y: yOf(1), kept: review.kept })),
  }
  marker.value = { x: xOf(next.value), y: yOf(KEEP), on: Math.min(1, settled) }
}

function tick(now: number) {
  const dt = Math.min((now - last) / 1000, 1 / 30)
  last = now
  for (const spring of [from, reach, draw, ...reviews.map((review) => review.depth)]) spring.step(dt)
  paint()
}

/** The review happens: the curve jumps back to full and the next review moves. */
function review(kept: boolean) {
  const at = next.value
  const depth = new Spring(KEEP, springs.layout)
  depth.set(kept ? KEEP : LOST)
  reviews.push({ at, depth, kept })
  step.value = kept ? Math.min(step.value + 1, LADDER.length - 1) : 0
  next.value = at + LADDER[step.value]
  draw.jump(0)
  draw.set(1)
  from.set(reviews[Math.max(0, reviews.length - SHOWN)].at)
  reach.set(next.value + (next.value - from.target) * TAIL)
}

/** Rubber band: the card follows the finger, but less the further it goes. */
function follow(dx: number) {
  if (!leaving) offset.jump(REACH * Math.tanh(dx / REACH))
}

/** Far enough and the card is thrown off the stack; otherwise it springs back. */
function release() {
  const dx = offset.value.value
  if (leaving || Math.abs(dx) < COMMIT * 0.8) return offset.set(0)
  leaving = true
  review(dx > 0)
  offset.set(Math.sign(dx) * FLY)
  timer = window.setTimeout(() => {
    session.value += 1
    offset.jump(0)
    leaving = false
  }, 300)
}

function onDown(event: PointerEvent) {
  if (leaving) return
  top.value?.setPointerCapture(event.pointerId)
  grab = { pointer: event.pointerId, start: event.clientX - offset.value.value }
}

function onMove(event: PointerEvent) {
  if (grab?.pointer === event.pointerId) follow(event.clientX - grab.start)
}

function onUp(event: PointerEvent) {
  if (grab?.pointer !== event.pointerId) return
  grab = null
  release()
}

async function demo(ghost: Ghost) {
  try {
    await ghost.rest(800)
    await ghost.drag(top.value, 130, follow)
    release()
    await ghost.rest(1900)
    await ghost.drag(top.value, -130, follow)
    release()
    await ghost.rest(1700)
  } finally {
    if (!grab && !leaving && offset.value.value !== 0) offset.set(0)
  }
}

useHeroFrames(tick)

defineExpose({ demo })

onMounted(() => {
  from.jump(reviews[Math.max(0, reviews.length - SHOWN)].at)
  reach.jump(next.value + (next.value - from.value) * TAIL)
  last = performance.now()
})

onBeforeUnmount(() => {
  window.clearTimeout(timer)
})
</script>

<template>
  <div class="hero-scene">
    <svg class="curve" :viewBox="`0 0 ${WIDTH} ${HEIGHT}`" aria-hidden="true">
      <defs>
        <linearGradient id="remember-area" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" class="area-top" />
          <stop offset="1" class="area-bottom" />
        </linearGradient>
      </defs>
      <line class="keep" :x1="0" :x2="WIDTH" :y1="yOf(KEEP)" :y2="yOf(KEEP)" />
      <path class="area" :d="drawing.area" fill="url(#remember-area)" />
      <path class="line" :d="drawing.line" />
      <path class="tail" :d="drawing.tail" />
      <circle
        v-for="(dot, i) in drawing.dots"
        :key="i"
        class="review"
        :data-kept="dot.kept || undefined"
        :cx="dot.x"
        :cy="dot.y"
        r="4.5"
      />
      <g class="next" :transform="`translate(${marker.x} ${marker.y})`" :opacity="marker.on">
        <circle class="halo" r="10" />
        <circle class="core" r="5" />
      </g>
    </svg>
    <div
      class="when"
      :style="{ left: `${SCENE_X + marker.x}px`, top: `${SCENE_Y + marker.y + 12}px`, opacity: marker.on }"
    >
      <Transition name="hero-swap">
        <span :key="days">{{ days === 1 ? 'Tomorrow' : `In ${days} days` }}</span>
      </Transition>
    </div>

    <div class="deck">
      <span class="side kept" :style="{ opacity: Math.max(0, lean) }">Got it</span>
      <span class="side forgot" :style="{ opacity: Math.max(0, -lean) }">Forgot</span>
      <div
        v-for="(n, depth) in [session, session + 1, session + 2]"
        :key="n"
        :ref="(el) => depth === 0 && (top = el as HTMLElement)"
        class="card"
        :data-depth="depth"
        :data-moving="(depth === 0 && (grab !== null || offset.value.value !== 0)) || undefined"
        :style="
          depth === 0
            ? { transform: `translateX(${offset.value.value}px) rotate(${offset.value.value * 0.035}deg)` }
            : undefined
        "
        @pointerdown="depth === 0 && onDown($event)"
        @pointermove="depth === 0 && onMove($event)"
        @pointerup="depth === 0 && onUp($event)"
        @pointercancel="depth === 0 && onUp($event)"
      >
        <template v-if="depth === 0">
          <span class="tint kept-tint" :style="{ opacity: Math.max(0, lean) }" />
          <span class="tint forgot-tint" :style="{ opacity: Math.max(0, -lean) }" />
        </template>
        <span class="topic">Binary trees</span>
        <span class="meta">Review {{ n }}</span>
        <span class="due">Due now</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.curve {
  display: block;
  width: 100%;
  height: auto;
  overflow: hidden;
  mask-image: linear-gradient(to right, transparent, black 4%, black 96%, transparent);
  animation: hero-appear var(--lt-dur-fade) var(--lt-ease-fade) backwards 120ms;
}

.keep {
  stroke: var(--lt-separator);
  stroke-dasharray: 2 4;
  stroke-linecap: round;
}

.area-top {
  stop-color: var(--lt-accent);
  stop-opacity: 0.22;
}

.area-bottom {
  stop-color: var(--lt-accent);
  stop-opacity: 0;
}

.line,
.tail {
  fill: none;
  stroke: var(--lt-accent);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.tail {
  stroke-dasharray: 2 5;
  opacity: 0.5;
}

.review {
  fill: var(--lt-label-3);
  stroke: var(--lt-surface);
  stroke-width: 2;
}

.review[data-kept] {
  fill: var(--lt-green);
}

.review:not([data-kept]) {
  fill: var(--lt-orange);
}

.review:first-of-type {
  fill: var(--lt-label-3);
}

.next .halo {
  fill: var(--lt-accent-soft);
  animation: breathe 1.8s var(--lt-ease-fade) infinite;
  transform-box: fill-box;
  transform-origin: center;
}

.next .core {
  fill: var(--lt-accent);
  stroke: var(--lt-surface);
  stroke-width: 2;
}

@keyframes breathe {
  50% {
    scale: 1.35;
    opacity: 0.5;
  }
}

/* The next review's date hangs under its point on the curve, clear of the review dots on top. */
.when {
  position: absolute;
  display: grid;
  translate: -50% 0;
  color: var(--lt-accent);
  font-family: var(--lt-font-rounded);
  font-size: var(--lt-text-caption);
  font-weight: 600;
  white-space: nowrap;
  pointer-events: none;
}

.when > span {
  grid-area: 1 / 1;
  justify-self: center;
}

.deck {
  position: relative;
  display: grid;
  height: 100px;
  margin-top: 20px;
  animation: hero-rise var(--lt-dur-layout) var(--lt-ease-layout) backwards 260ms;
}

.side {
  position: absolute;
  z-index: 2;
  top: 36px;
  font-size: var(--lt-text-footnote);
  font-weight: 600;
}

.kept {
  left: 16px;
  color: var(--lt-green-text);
}

.forgot {
  right: 16px;
  color: var(--lt-orange-text);
}

/* Upcoming reviews wait behind, each a little smaller and further back. */
.card {
  position: relative;
  display: grid;
  grid-area: 1 / 1;
  grid-template-columns: 1fr auto;
  align-content: center;
  gap: 2px 12px;
  height: 76px;
  overflow: hidden;
  padding: 0 16px;
  border-radius: var(--lt-radius-md);
  background: var(--lt-surface);
  box-shadow:
    0 0 0 0.5px var(--lt-separator),
    0 6px 18px light-dark(rgba(0, 0, 0, 0.08), rgba(0, 0, 0, 0.35));
  cursor: grab;
  touch-action: none;
  user-select: none;
  transition:
    transform var(--lt-dur-layout) var(--lt-ease-layout),
    opacity var(--lt-dur-fade) var(--lt-ease-fade);
}

.card[data-depth='0'] {
  z-index: 3;
}

.card[data-depth='1'] {
  z-index: 1;
  transform: translateY(12px) scale(0.94);
  opacity: 0.75;
}

.card[data-depth='2'] {
  z-index: 0;
  transform: translateY(23px) scale(0.88);
  opacity: 0.45;
}

/* Only the card on top shows what it is; the ones behind are just the shape of a queue. */
.card > span {
  transition: opacity var(--lt-dur-fade) var(--lt-ease-fade);
}

.card:not([data-depth='0']) > span {
  opacity: 0;
}

.card[data-moving] {
  transition: none;
}

.card[data-depth='0']:active {
  cursor: grabbing;
}

.tint {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.kept-tint {
  background: var(--lt-green-soft);
}

.forgot-tint {
  background: var(--lt-orange-soft);
}

.topic {
  position: relative;
  color: var(--lt-label);
  font-size: var(--lt-text-subhead);
  font-weight: 600;
}

.meta {
  position: relative;
  grid-row: 2;
  color: var(--lt-label-3);
  font-size: var(--lt-text-footnote);
}

.due {
  position: relative;
  grid-row: 1 / 3;
  grid-column: 2;
  align-self: center;
  padding: 4px 10px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-accent-soft);
  color: var(--lt-accent);
  font-size: var(--lt-text-caption);
  font-weight: 600;
}
</style>
