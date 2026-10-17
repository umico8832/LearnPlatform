<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { Spring, Spring2, springAnimate, springs, type SpringConfig } from '@/motion'
import type { Ghost } from './ghost'

/**
 * What understanding feels like, with nothing to read. A tangle of nodes and crossing threads
 * drifts about until it is pressed; then every node springs home, centre first, and the
 * threads come apart into one clean structure in the variable colours. Any node can be pulled:
 * the structure stretches with it, less the further away it is, and wobbles back on release.
 * Pressing again lets it go.
 */
const WIDTH = 448
const HEIGHT = 236
const CX = WIDTH / 2
const CY = HEIGHT / 2
const RINGS = [0, 36, 70, 104]
const SIZES = [8, 6, 5, 4]
const BRANCHES = ['blue', 'purple', 'teal', 'orange'] as const
const PULL = 0.55
const SPIN = 0.05
const REACH = 16

interface Seat {
  ring: number
  parent: number
  angle: number
  color: string
}

/** A radial tree: four branches, each splitting twice, every node centred over its children. */
const seats: Seat[] = []
function grow(parent: number, ring: number, start: number, span: number, color: string) {
  const index = seats.length
  seats.push({ ring, parent, angle: start + span / 2, color })
  if (ring === RINGS.length - 1) return
  const kids = ring === 0 ? BRANCHES.length : 2
  for (let k = 0; k < kids; k += 1)
    grow(index, ring + 1, start + (span / kids) * k, span / kids, ring === 0 ? BRANCHES[k] : color)
}
grow(-1, 0, -Math.PI / 2, Math.PI * 2, 'blue')
const edges = seats.flatMap((seat, i) => (seat.parent < 0 ? [] : [[seat.parent, i] as const]))
const near = seats.map((_, i) => edges.flatMap(([a, b]) => (a === i ? [b] : b === i ? [a] : [])))

function random(seed: number) {
  return () => {
    seed = (seed + 0x6d2b79f5) | 0
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/** Where each node floats while tangled: an anchor anywhere, and a slow Lissajous wander. */
function scatter(next: () => number) {
  return seats.map(() => ({
    x: 30 + next() * (WIDTH - 60),
    y: 22 + next() * (HEIGHT - 44),
    size: 12 + next() * 14,
    wx: 0.25 + next() * 0.35,
    wy: 0.25 + next() * 0.35,
    px: next() * Math.PI * 2,
    py: next() * Math.PI * 2,
  }))
}

let anchors = scatter(random(7))
const bodies = seats.map((_, i) => new Spring2(anchors[i].x, anchors[i].y, springs.layout))
const tints = seats.map(() => new Spring(0, springs.fade))
const stage = ref<SVGSVGElement | null>(null)
const dots = ref<SVGCircleElement[]>([])
const pulse = ref<HTMLElement | null>(null)
const view = shallowRef(seats.map((_, i) => ({ x: anchors[i].x, y: anchors[i].y, k: 0 })))
const calm = ref(0)

let clear = false
let changedAt = -Infinity
let delays = seats.map(() => 0)
let hover: { x: number; y: number } | null = null
let grab: { index: number; x: number; y: number; hops: number[] } | null = null
let press: { x: number; y: number; moved: boolean } | null = null
let frame = 0
let last = 0

function homeOf(i: number, t: number) {
  const seat = seats[i]
  const r = RINGS[seat.ring] * (1 + 0.012 * Math.sin(t * 1.3))
  const angle = seat.angle + t * SPIN
  return { x: CX + r * Math.cos(angle), y: CY + r * Math.sin(angle) }
}

function driftOf(i: number, t: number) {
  const a = anchors[i]
  return { x: a.x + a.size * Math.sin(a.wx * t + a.px), y: a.y + a.size * 0.8 * Math.sin(a.wy * t + a.py) }
}

/** Graph distance from one node to every other, for how much a pull reaches them. */
function hopsFrom(start: number) {
  const hops = seats.map(() => Infinity)
  hops[start] = 0
  const queue = [start]
  while (queue.length) {
    const at = queue.shift()!
    for (const next of near[at])
      if (hops[next] === Infinity) {
        hops[next] = hops[at] + 1
        queue.push(next)
      }
  }
  return hops
}

const clamp = (v: number) => Math.max(0, Math.min(1, v))

function tick(now: number) {
  const dt = Math.min((now - last) / 1000, 1 / 30)
  last = now
  const t = now / 1000
  const bases = seats.map((_, i) => {
    // Nodes switch over one by one: centre first on the way in, leaves first on the way out.
    const homeward = clear === now - changedAt >= delays[i]
    return { homeward, at: homeward ? homeOf(i, t) : driftOf(i, t) }
  })
  const pulled = grab && { dx: grab.x - bases[grab.index].at.x, dy: grab.y - bases[grab.index].at.y }
  const next = bases.map(({ homeward, at }, i) => {
    const target = { ...at }
    if (grab && pulled) {
      const share = PULL ** grab.hops[i]
      target.x += pulled.dx * share
      target.y += pulled.dy * share
    } else if (hover) {
      const gap = Math.hypot(hover.x - target.x, hover.y - target.y)
      const lean = 0.2 * Math.exp(-(gap * gap) / (2 * 48 * 48))
      target.x += (hover.x - target.x) * lean
      target.y += (hover.y - target.y) * lean
    }
    const body = bodies[i]
    const config: SpringConfig = grab?.index === i ? springs.snappy : homeward ? springs.pointer : springs.layout
    body.x.config = body.y.config = config
    body.set(target.x, target.y)
    body.step(dt)
    const x = body.x.value
    const y = body.y.value
    // A node takes its colour as it arrives, keeps it while pulled, and greys as it lets go.
    const tint = tints[i]
    tint.set(homeward ? clamp(1 - (Math.hypot(x - target.x, y - target.y) - 6) / 54) : 0)
    tint.step(dt)
    return { x, y, k: clamp(tint.value) }
  })
  view.value = next
  calm.value = next.reduce((sum, node) => sum + node.k, 0) / next.length
  frame = requestAnimationFrame(tick)
}

function settle(next: boolean) {
  if (clear === next) return
  clear = next
  changedAt = performance.now()
  delays = seats.map((seat) => (next ? seat.ring * 90 : (RINGS.length - 1 - seat.ring) * 50) + Math.random() * 70)
  if (next && pulse.value)
    springAnimate(
      pulse.value,
      [
        { opacity: 0.55, transform: 'translate(-50%, -50%) scale(0.15)' },
        { opacity: 0, transform: 'translate(-50%, -50%) scale(1)' },
      ],
      springs.camera,
    )
  else anchors = scatter(Math.random)
}

function hold(index: number) {
  grab = { index, x: bodies[index].x.value, y: bodies[index].y.value, hops: hopsFrom(index) }
}

function local(event: PointerEvent) {
  const box = stage.value!.getBoundingClientRect()
  return { x: ((event.clientX - box.left) / box.width) * WIDTH, y: ((event.clientY - box.top) / box.height) * HEIGHT }
}

function onDown(event: PointerEvent) {
  const p = local(event)
  stage.value?.setPointerCapture(event.pointerId)
  press = { ...p, moved: false }
  let best = -1
  let gap = REACH
  view.value.forEach((node, i) => {
    const d = Math.hypot(node.x - p.x, node.y - p.y)
    if (d < gap) [best, gap] = [i, d]
  })
  if (best >= 0) hold(best)
}

function onMove(event: PointerEvent) {
  const p = local(event)
  hover = p
  if (!press) return
  if (Math.hypot(p.x - press.x, p.y - press.y) > 4) press.moved = true
  if (grab) [grab.x, grab.y] = [p.x, p.y]
}

/** A press that never moved is a tap: it settles the tangle, or lets the structure go. */
function onUp() {
  if (press && !press.moved) settle(!clear)
  press = null
  grab = null
}

function onLeave() {
  hover = null
}

async function demo(ghost: Ghost) {
  await ghost.rest(1100)
  const core = dots.value[0]?.getBoundingClientRect()
  await ghost.click(dots.value[0])
  settle(true)
  // Step aside to the empty lower right so the structure forms in full view.
  const box = stage.value?.getBoundingClientRect()
  if (core && box)
    await ghost.moveBy(box.right - 34 - (core.x + core.width / 2), box.bottom - 18 - (core.y + core.height / 2))
  await ghost.rest(1800)
  // Pull the rightmost leaf out and let it spring back.
  const leaves = seats.flatMap((seat, i) => (seat.ring === RINGS.length - 1 ? [i] : []))
  const leaf = leaves.reduce((a, b) => (view.value[b].x > view.value[a].x ? b : a))
  const scale = (stage.value?.getBoundingClientRect().width ?? WIDTH) / WIDTH
  try {
    await ghost.drag(dots.value[leaf], 72, (offset) => {
      if (grab?.index !== leaf) hold(leaf)
      const from = homeOf(leaf, performance.now() / 1000)
      grab!.x = from.x + offset / scale
      grab!.y = from.y + (Math.abs(offset) * 0.06) / scale
    })
  } finally {
    grab = null
  }
  await ghost.rest(1500)
}

defineExpose({ demo })

onMounted(() => {
  last = performance.now()
  frame = requestAnimationFrame(tick)
})

onBeforeUnmount(() => cancelAnimationFrame(frame))
</script>

<template>
  <div class="hero-scene">
    <div class="stage">
      <div class="glow" :style="{ opacity: calm }" />
      <span ref="pulse" class="pulse" />
      <svg
        ref="stage"
        class="tangle"
        :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
        aria-hidden="true"
        @pointerdown="onDown"
        @pointermove="onMove"
        @pointerup="onUp"
        @pointercancel="onUp"
        @pointerleave="onLeave"
      >
        <circle v-for="r in RINGS.slice(1)" :key="r" class="orbit" :cx="CX" :cy="CY" :r="r" :opacity="calm * calm" />
        <line
          v-for="([a, b], i) in edges"
          :key="i"
          class="thread"
          :data-color="seats[b].color"
          :x1="view[a].x"
          :y1="view[a].y"
          :x2="view[b].x"
          :y2="view[b].y"
          :style="{ '--k': Math.min(view[a].k, view[b].k) }"
        />
        <circle
          v-for="(seat, i) in seats"
          :key="i"
          :ref="(el) => (dots[i] = el as SVGCircleElement)"
          class="node"
          :data-color="seat.color"
          :data-core="seat.ring === 0 || undefined"
          :cx="view[i].x"
          :cy="view[i].y"
          :r="SIZES[seat.ring]"
          :style="{ '--k': view[i].k }"
        />
      </svg>
    </div>
  </div>
</template>

<style scoped>
.stage {
  position: relative;
  animation: hero-appear var(--lt-dur-fade) var(--lt-ease-fade) backwards 120ms;
}

.tangle {
  position: relative;
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
  cursor: grab;
  touch-action: none;
}

.tangle:active {
  cursor: grabbing;
}

.glow {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: radial-gradient(
    closest-side,
    color-mix(in srgb, var(--lt-var-blue) 22%, transparent),
    color-mix(in srgb, var(--lt-var-purple) 8%, transparent) 60%,
    transparent
  );
  pointer-events: none;
  transform: translate(-50%, -50%);
}

.pulse {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 280px;
  height: 280px;
  border: 1.5px solid var(--lt-var-blue);
  border-radius: 50%;
  opacity: 0;
  pointer-events: none;
}

.orbit {
  fill: none;
  stroke: var(--lt-fill);
  stroke-dasharray: 1 5;
  stroke-linecap: round;
}

.thread {
  stroke: color-mix(in oklab, var(--var-color) calc(var(--k) * 100%), var(--lt-label-3));
  stroke-linecap: round;
  stroke-opacity: calc(0.4 + var(--k) * 0.45);
  stroke-width: calc(1px + var(--k) * 0.9px);
}

.node {
  fill: color-mix(in oklab, var(--var-color) calc(var(--k) * 100%), var(--lt-label-3));
  stroke: var(--lt-surface);
  stroke-width: 1.5;
}

.node[data-core] {
  fill: color-mix(in oklab, var(--lt-label) calc(var(--k) * 100%), var(--lt-label-2));
  filter: drop-shadow(0 0 calc(var(--k) * 8px) var(--lt-var-blue));
}
</style>
