<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Spring, springs } from '@/motion'
import { buildCity, nearestJunction, search, type Route } from './city'
import { STILL_AFTER, foundFor, frontAt, paint, project, type Palette, type Point, type Scene } from './paint'

/**
 * A city seen from above. Light spreads from the start exactly as Dijkstra settles each
 * junction, the route is traced back from the destination, and a pulse rides it once.
 * Scrolling in tips the ground from a map into perspective. The first time the stage comes
 * into view the city draws itself and finds one route on its own; after that any click
 * picks the next destination. Once nothing is moving, the stage stops drawing.
 */
const root = ref<HTMLElement | null>(null)
const canvas = ref<HTMLCanvasElement | null>(null)
const probe = ref<HTMLElement | null>(null)

const city = buildCity()
const START = nearestJunction(city, -1.5, 0.7)
const FIRST = nearestJunction(city, 1.3, 1.5)
const MAP_PITCH = 1.2
const VIEW_PITCH = 0.7
const APPEAR = 1.4

const reached = ref(0)

const pitch = new Spring(MAP_PITCH, springs.camera)
const previews = new Map<number, Route>()
let width = 0
let height = 0
let ratio = 1
let points: Point[] = []
let palette: Palette = { base: [128, 128, 128], label: [255, 255, 255], accent: [10, 132, 255], green: [48, 209, 88] }
let route: Route | null = null
let hovered = -1
let clock = 0
let appearFrom = 0
let waveFrom = 0
let frameId = 0
let last = 0
let visible = false
let entered = false

function rgb(color: string): number[] {
  return (color.match(/[\d.]+/g) ?? ['128', '128', '128']).slice(0, 3).map(Number)
}

function readPalette() {
  const el = probe.value
  if (!el) return
  const read = (token: string) => {
    el.style.color = `var(${token})`
    return rgb(getComputedStyle(el).color)
  }
  palette = {
    base: read('--lt-label-2'),
    label: read('--lt-label'),
    accent: read('--lt-accent'),
    green: read('--lt-green'),
  }
}

function layout() {
  const el = root.value
  const cv = canvas.value
  if (!el || !cv) return
  width = el.clientWidth
  height = el.clientHeight
  ratio = Math.min(window.devicePixelRatio || 1, 2)
  cv.width = Math.round(width * ratio)
  cv.height = Math.round(height * ratio)
  points = project(city, width, height, pitch.value)
}

const scene = (): Scene => ({
  city,
  points,
  route,
  preview: hovered >= 0 ? (previews.get(hovered) ?? null) : null,
  hovered,
  t: clock,
  appearFrom,
  waveFrom,
  palette,
  start: START,
})

function draw() {
  const ctx = canvas.value?.getContext('2d')
  if (!ctx) return
  ctx.setTransform(ratio, 0, 0, ratio, 0, 0)
  ctx.clearRect(0, 0, width, height)
  const current = scene()
  paint(ctx, current)
  const front = frontAt(current)
  reached.value = route ? route.distance.filter((d) => d <= front).length : 0
}

/** Nothing left to animate: the city is in, the tilt has settled and the route has been ridden. */
function still() {
  const current = scene()
  return pitch.settled && clock - appearFrom > 1.6 && (!route || foundFor(current) > STILL_AFTER)
}

/** Ground tilt follows how far the stage has come up the screen, through a camera spring. */
function onScroll() {
  const el = root.value
  if (!el) return
  const top = el.getBoundingClientRect().top
  const progress = Math.max(0, Math.min(1, (window.innerHeight - top) / (window.innerHeight * 0.8)))
  const eased = 1 - (1 - progress) ** 2
  pitch.set(MAP_PITCH + (VIEW_PITCH - MAP_PITCH) * eased)
  wake()
}

function frame(now: number) {
  const dt = Math.min((now - last) / 1000, 1 / 20)
  last = now
  clock += dt
  if (!pitch.settled) {
    pitch.step(dt)
    points = project(city, width, height, pitch.value)
  }
  draw()
  frameId = visible && !still() ? requestAnimationFrame(frame) : 0
}

function wake() {
  if (frameId || !visible) return
  last = performance.now()
  frameId = requestAnimationFrame(frame)
}

function go(target: number, delay = 0.12) {
  if (target === START) return
  route = search(city, START, target)
  waveFrom = clock + delay
  wake()
}

/** Only the first time the stage comes into view: the city draws itself in and finds one route. */
function enter() {
  if (entered) return
  entered = true
  appearFrom = clock
  go(FIRST, APPEAR)
}

function pick(event: MouseEvent) {
  const rect = canvas.value?.getBoundingClientRect()
  if (!rect) return -1
  const x = event.clientX - rect.left
  const y = event.clientY - rect.top
  let best = -1
  let bestDistance = 24
  points.forEach((p, i) => {
    const distance = Math.hypot(p.x - x, p.y - y)
    if (p.fog > 0.3 && distance < bestDistance) {
      best = i
      bestDistance = distance
    }
  })
  return best
}

function onMove(event: PointerEvent) {
  const node = pick(event)
  const next = node === START || node === route?.target ? -1 : node
  if (next === hovered) return
  hovered = next
  if (hovered >= 0 && !previews.has(hovered)) previews.set(hovered, search(city, START, hovered))
  if (canvas.value) canvas.value.style.cursor = hovered >= 0 ? 'pointer' : ''
  wake()
}

function onLeave() {
  if (hovered < 0) return
  hovered = -1
  wake()
}

function onClick(event: MouseEvent) {
  const node = pick(event)
  if (node < 0) return
  hovered = -1
  go(node)
}

let resizeObserver: ResizeObserver | null = null
let intersection: IntersectionObserver | null = null

onMounted(() => {
  readPalette()
  layout()
  onScroll()
  pitch.jump(pitch.target)
  points = project(city, width, height, pitch.value)
  resizeObserver = new ResizeObserver(() => {
    layout()
    draw()
  })
  if (root.value) resizeObserver.observe(root.value)
  intersection = new IntersectionObserver(
    ([entry]) => {
      const now = entry.intersectionRatio >= 0.35
      if (now === visible) return
      visible = now
      if (visible) {
        enter()
        wake()
      }
    },
    { threshold: [0, 0.35] },
  )
  if (root.value) intersection.observe(root.value)
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  visible = false
  cancelAnimationFrame(frameId)
  resizeObserver?.disconnect()
  intersection?.disconnect()
  window.removeEventListener('scroll', onScroll)
})
</script>

<template>
  <div ref="root" class="network-stage">
    <canvas
      ref="canvas"
      class="surface"
      aria-hidden="true"
      @pointermove="onMove"
      @pointerleave="onLeave"
      @click="onClick"
    />
    <dl class="readout" aria-live="off">
      <div>
        <dt>Junctions reached</dt>
        <dd>
          {{ reached }}<span class="of"> / {{ city.junctions.length }}</span>
        </dd>
      </div>
    </dl>
    <span ref="probe" class="probe" aria-hidden="true" />
  </div>
</template>

<style scoped>
.network-stage {
  position: relative;
  width: 100%;
  height: 100%;
}

/* The city runs off every edge and dissolves there instead of ending at a line. */
.surface {
  display: block;
  width: 100%;
  height: 100%;
  mask-image:
    linear-gradient(to bottom, transparent 0, black 22%, black 90%, transparent 100%),
    linear-gradient(to right, transparent 0, black 12%, black 88%, transparent 100%);
  mask-composite: intersect;
  touch-action: manipulation;
}

.readout {
  position: absolute;
  bottom: var(--lt-space-7);
  left: 50%;
  display: flex;
  gap: var(--lt-space-8);
  margin: 0;
  padding: 12px 24px;
  border-radius: var(--lt-radius-pill);
  background: color-mix(in srgb, var(--lt-canvas) 62%, transparent);
  backdrop-filter: blur(16px) saturate(1.4);
  box-shadow: 0 0 0 0.5px var(--lt-hairline);
  transform: translateX(-50%);
  pointer-events: none;
}

.readout div {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.readout dt {
  color: var(--lt-label-3);
  font-size: var(--lt-text-footnote);
}

.readout dd {
  min-width: 2ch;
  margin: 0;
  color: var(--lt-label);
  font-family: var(--lt-font-rounded);
  font-size: var(--lt-text-title3);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.of {
  color: var(--lt-label-3);
  font-size: var(--lt-text-subhead);
  font-weight: 500;
}

.probe {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
}
</style>
