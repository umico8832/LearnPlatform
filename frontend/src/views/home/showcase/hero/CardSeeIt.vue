<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { Spring, springs } from '@/motion'
import type { Ghost } from './ghost'

/**
 * Code turning into a picture. One line of code sits in the middle of the card; pressed, each
 * number leaves it and grows into a bar, and the line shrinks to a caption above them. From
 * then on the bars and the caption are one thing: every number in the caption rides the same
 * spring as its bar and hangs from it by a thread, so sorting, dragging or shuffling the bars
 * rewrites the code as it moves. Whatever is moving or pointed at lights up at both ends.
 * A tap sorts the bars, or shuffles sorted ones.
 */
const VALUES = [5, 2, 8, 1, 9, 3, 7]
const WIDTH = 448
const HEIGHT = 236
const SLOT = 56
const BAR = 40
const FLOOR = HEIGHT - 6
const LEFT = (WIDTH - SLOT * VALUES.length) / 2 + SLOT / 2
const CODE_SIZE = 28
const LABEL_SIZE = 20
const LINE = 34
/** How far, in ems, a number in the code rises or sinks while it passes others. */
const HOP = 0.55
/** The line with blanks where the numbers go; the numbers ride on top in their own cells. */
const SKELETON = `[${VALUES.map(() => ' ').join(', ')}]`

interface Box {
  x: number
  y: number
  w: number
  h: number
}

const items = VALUES.map((value, i) => ({
  value,
  slot: new Spring(i, springs.layout),
  grow: new Spring(0, springs.pointer),
  lift: new Spring(0, springs.layout),
  glow: new Spring(0, springs.fade),
}))
const shrink = new Spring(0, springs.layout)
const stage = ref<HTMLElement | null>(null)
const code = ref<HTMLElement | null>(null)
const digits = ref<HTMLElement[]>([])
const bars = ref<HTMLElement[]>([])
const drawn = ref(false)
const lit = ref(-1)
const view = shallowRef(items.map(() => ({ slot: 0, box: { x: 0, y: 0, w: 0, h: 0 }, t: 0, a: 0, hop: 0, thread: '' })))
const caption = ref({ top: 0, scale: 1, digits: 1, floor: 0 })

let order = VALUES.map((_, i) => i)
let starts: Box[] = []
let held: { item: number; from: number } | null = null
let press: { x: number; moved: boolean } | null = null
let timers: number[] = []
let frame = 0
let last = 0
/** Width of one character of the code line, before it is scaled. */
let ch = 0

const clamp = (v: number) => Math.max(0, Math.min(1, v))
const lerp = (a: number, b: number, t: number) => a + (b - a) * t
const heightOf = (value: number) => 22 + value * 16

function barBox(item: (typeof items)[0]): Box {
  const h = heightOf(item.value)
  return { x: LEFT + item.slot.value * SLOT - BAR / 2, y: FLOOR - h + item.lift.value, w: BAR, h }
}

function tick(now: number) {
  const dt = Math.min((now - last) / 1000, 1 / 30)
  last = now
  shrink.step(dt)
  const s = shrink.value
  const scale = lerp(1, LABEL_SIZE / CODE_SIZE, s)
  const top = lerp(HEIGHT / 2 - LINE / 2, 4, s)
  if (!ch && code.value) ch = code.value.offsetWidth / SKELETON.length
  const cell = ch * scale
  const lineLeft = WIDTH / 2 - (SKELETON.length * cell) / 2
  caption.value = { top, scale, digits: drawn.value ? clamp((s - 0.8) / 0.2) : 1, floor: clamp(s) }
  view.value = items.map((item, i) => {
    item.slot.step(dt)
    item.grow.step(dt)
    item.lift.step(dt)
    const moving = Math.abs(item.slot.value - item.slot.target) > 0.03 || Math.abs(item.lift.value) > 1
    item.glow.set(moving || held?.item === i || lit.value === i ? 1 : 0)
    item.glow.step(dt)
    const to = barBox(item)
    const from = starts[i] ?? to
    const t = item.grow.value
    // The bar may overshoot in size, but its foot lands on the floor and stays there.
    const h = lerp(from.h, to.h, t)
    const foot = lerp(from.y + from.h, to.y + to.h, clamp(t))
    const box = { x: lerp(from.x, to.x, t), y: foot - h, w: lerp(from.w, to.w, t), h }
    // Numbers passing each other in the code split up: rightward ones rise, leftward ones sink.
    const ahead = item.slot.target - item.slot.value
    const hop = held?.item === i ? 0 : Math.sign(ahead) * clamp(Math.abs(ahead))
    // The thread runs from under the number in the code to the top of its bar.
    const dx = lineLeft + (1.5 + item.slot.value * 3) * cell
    const dy = top + (LINE - hop * HOP * CODE_SIZE) * scale + 2
    const bx = box.x + box.w / 2
    const by = box.y - 3
    const mid = (dy + by) / 2
    return {
      slot: item.slot.value,
      box,
      t: clamp(t),
      a: clamp(item.glow.value),
      hop,
      thread: `M${dx},${dy} C${dx},${mid} ${bx},${mid} ${bx},${by}`,
    }
  })
  frame = requestAnimationFrame(tick)
}

function local(el: Element): Box {
  const r = el.getBoundingClientRect()
  const box = stage.value!.getBoundingClientRect()
  const k = WIDTH / box.width
  return { x: (r.left - box.left) * k, y: (r.top - box.top) * k, w: r.width * k, h: r.height * k }
}

/** Every number leaves the line, one after another, and grows into its bar. */
function draw() {
  if (drawn.value) return
  starts = digits.value.map(local)
  drawn.value = true
  shrink.set(1)
  items.forEach((item, i) => later(i * 55, () => item.grow.set(1)))
}

function later(ms: number, run: () => void) {
  timers.push(window.setTimeout(run, ms))
}

/** Bars slide into a new order one by one; the ones heading right hop over the others. */
function arrange(next: number[]) {
  order = next
  next.forEach((index, slot) => {
    const item = items[index]
    const distance = slot - item.slot.target
    later(Math.abs(distance) > 0 ? slot * 45 : 0, () => {
      item.slot.set(slot)
      if (distance > 0) item.lift.kick(-90 * Math.sqrt(distance))
    })
  })
}

function sorted() {
  return [...order].sort((a, b) => items[a].value - items[b].value)
}

function shuffle() {
  let next = order
  while (next.every((index, slot) => index === order[slot])) next = [...order].sort(() => Math.random() - 0.5)
  return next
}

function tap() {
  if (!drawn.value) return draw()
  const want = sorted()
  arrange(want.every((index, slot) => index === order[slot]) ? shuffle() : want)
}

/** Hold a bar: it follows the pointer, and the others step aside to make room. */
function hold(item: number) {
  held = { item, from: items[item].slot.value }
  lit.value = item
  items[item].lift.set(-10)
  items[item].slot.config = springs.snappy
}

function slide(position: number) {
  if (!held) return
  const at = Math.max(0, Math.min(VALUES.length - 1, position))
  items[held.item].slot.set(at)
  const rest = order.filter((index) => index !== held!.item)
  rest.splice(Math.round(at), 0, held.item)
  rest.forEach((index, slot) => index !== held!.item && items[index].slot.set(slot))
  order = rest
}

function letGo() {
  if (!held) return
  const item = items[held.item]
  item.slot.config = springs.layout
  item.slot.set(order.indexOf(held.item))
  item.lift.set(0)
  held = null
}

function pointAt(event: PointerEvent) {
  const box = stage.value!.getBoundingClientRect()
  return ((event.clientX - box.left) * WIDTH) / box.width
}

function onDown(event: PointerEvent) {
  stage.value?.setPointerCapture(event.pointerId)
  press = { x: event.clientX, moved: false }
  const target = (event.target as Element).closest<HTMLElement>('.bar')
  if (target) hold(Number(target.dataset.index))
}

function onMove(event: PointerEvent) {
  if (!press) {
    const target = (event.target as Element).closest<HTMLElement>('[data-index]')
    lit.value = target ? Number(target.dataset.index) : -1
    return
  }
  if (Math.abs(event.clientX - press.x) > 4) press.moved = true
  if (held && press.moved) slide((pointAt(event) - LEFT) / SLOT)
}

function onUp() {
  if (press && !press.moved) tap()
  letGo()
  press = null
}

function onLeave() {
  if (!press) lit.value = -1
}

async function demo(ghost: Ghost) {
  await ghost.rest(900)
  const line = code.value?.getBoundingClientRect()
  await ghost.click(code.value)
  draw()
  // Step aside to the empty top right so the bars grow in full view.
  const box = stage.value?.getBoundingClientRect()
  if (line && box)
    await ghost.moveBy(box.right - 30 - (line.x + line.width / 2), box.top + 40 - (line.y + line.height / 2))
  await ghost.rest(900)
  await ghost.click(stage.value)
  tap()
  await ghost.moveBy(0, -30)
  await ghost.rest(1500)
  // Pull the tallest bar back three places; the caption rewrites itself as it goes.
  const tallest = items.reduce((best, item, i) => (item.value > items[best].value ? i : best), 0)
  const scale = (box?.width ?? WIDTH) / WIDTH
  try {
    await ghost.drag(bars.value[tallest], -3 * SLOT * scale, (offset) => {
      if (held?.item !== tallest) hold(tallest)
      slide(held!.from + offset / scale / SLOT)
    })
  } finally {
    letGo()
    lit.value = -1
  }
  await ghost.rest(1300)
}

defineExpose({ demo })

onMounted(() => {
  last = performance.now()
  frame = requestAnimationFrame(tick)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  timers.forEach((timer) => window.clearTimeout(timer))
  timers = []
})
</script>

<template>
  <div class="hero-scene">
    <div
      ref="stage"
      class="stage"
      :data-drawn="drawn || undefined"
      @pointerdown="onDown"
      @pointermove="onMove"
      @pointerup="onUp"
      @pointercancel="onUp"
      @pointerleave="onLeave"
    >
      <span class="floor" :style="{ opacity: caption.floor }" />
      <svg v-if="drawn" class="threads" :viewBox="`0 0 ${WIDTH} ${HEIGHT}`" aria-hidden="true">
        <path
          v-for="(item, i) in items"
          :key="i"
          :d="view[i].thread"
          :style="{ '--f': (item.value - 1) / 8, '--a': view[i].a, opacity: caption.digits }"
        />
      </svg>
      <div
        v-for="(item, i) in items"
        v-show="drawn"
        :key="i"
        :ref="(el) => (bars[i] = el as HTMLElement)"
        class="bar"
        :data-index="i"
        :style="{
          '--a': view[i].a,
          left: `${view[i].box.x}px`,
          top: `${view[i].box.y}px`,
          width: `${view[i].box.w}px`,
          height: `${view[i].box.h}px`,
          '--t': view[i].t,
          '--f': (item.value - 1) / 8,
          fontSize: `${lerp(CODE_SIZE, 15, view[i].t)}px`,
          lineHeight: `${lerp(LINE, 26, view[i].t)}px`,
        }"
      >
        {{ item.value }}
      </div>
      <p
        ref="code"
        class="code"
        :style="{ top: `${caption.top}px`, transform: `translateX(-50%) scale(${caption.scale})` }"
      >
        <span class="skeleton">{{ SKELETON }}</span>
        <span
          v-for="(item, i) in items"
          :key="i"
          :ref="(el) => (digits[i] = el as HTMLElement)"
          class="digit"
          :data-index="i"
          :style="{
            '--at': view[i].slot,
            '--f': (item.value - 1) / 8,
            '--a': view[i].a,
            '--hop': view[i].hop,
            opacity: caption.digits,
          }"
          >{{ item.value }}</span
        >
      </p>
    </div>
  </div>
</template>

<style scoped>
.stage {
  position: relative;
  height: 236px;
  cursor: pointer;
  touch-action: none;
  user-select: none;
  animation: hero-appear var(--lt-dur-fade) var(--lt-ease-fade) backwards 120ms;
}

.floor {
  position: absolute;
  right: 8px;
  bottom: 5px;
  left: 8px;
  height: 1px;
  background: var(--lt-separator);
}

.code {
  position: absolute;
  left: 50%;
  margin: 0;
  color: var(--lt-label-2);
  font-family: var(--lt-font-mono);
  font-size: 28px;
  line-height: 34px;
  white-space: pre;
  transform-origin: 50% 0;
}

.skeleton {
  color: var(--lt-label-3);
}

/* Each number sits in its own character cell and slides between cells with its bar. */
.digit {
  position: absolute;
  top: 0;
  left: calc((1 + var(--at) * 3) * 1ch);
  translate: 0 calc(var(--hop) * -0.55em);
  width: 1ch;
  border-radius: 4px;
  background: color-mix(in oklab, var(--hue) calc(var(--a) * 24%), transparent);
  box-shadow: 0 0 0 4px color-mix(in oklab, var(--hue) calc(var(--a) * 24%), transparent);
  color: color-mix(in oklab, var(--hue) calc(var(--a) * 100%), var(--lt-label));
  text-align: center;
}

.threads {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
}

.threads path {
  fill: none;
  stroke: var(--hue);
  stroke-linecap: round;
  stroke-opacity: calc(0.26 + var(--a) * 0.64);
  stroke-width: calc(1px + var(--a) * 0.8px);
}

.bar,
.digit,
.threads path {
  --hue: color-mix(in oklab, var(--lt-var-indigo) calc(var(--f) * 100%), var(--lt-var-teal));
}

.bar {
  position: absolute;
  border-radius: calc(var(--t) * 9px) calc(var(--t) * 9px) calc(var(--t) * 4px) calc(var(--t) * 4px);
  background: color-mix(in oklab, var(--hue) calc(var(--t) * 100%), transparent);
  color: color-mix(in oklab, var(--lt-on-accent) calc(var(--t) * 100%), var(--lt-label));
  font-family: var(--lt-font-mono);
  font-weight: 600;
  text-align: center;
  box-shadow: 0 10px 26px color-mix(in oklab, var(--hue) calc(var(--a) * 45%), transparent);
  cursor: grab;
  filter: brightness(calc(1 + var(--a) * 0.14));
}

.stage:active .bar {
  cursor: grabbing;
}
</style>
