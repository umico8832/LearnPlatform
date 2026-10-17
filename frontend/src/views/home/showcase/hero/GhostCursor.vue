<script setup lang="ts">
import { computed, ref } from 'vue'
import { useSpring } from '@/composables/useSpring'
import { Spring, springAnimate, springs } from '@/motion'
import { Halt, nextFrame, rest, type Ghost } from './ghost'

/**
 * A second, scripted pointer that plays with the card until the visitor does. Its look
 * and body language follow the agent cursor in ChatGPT's Codex browser: a rounded arrow
 * with a blue glow that leans into a move and stretches along it, and scales up out of a
 * blur when it appears. x and y run on different springs, so a long move bends slightly.
 */
const x = useSpring(0, springs.layout)
const y = useSpring(0, springs.camera)
const layer = ref<HTMLElement | null>(null)
const ripple = ref<HTMLElement | null>(null)
const pressed = useSpring(1, springs.snappy)
const shown = useSpring(0, springs.layout)
let visible = false

/** Lean into the direction of travel and stretch along it, both from the current velocity. */
const pose = computed(() => {
  const vx = x.velocity.value
  const vy = y.velocity.value
  const speed = Math.hypot(vx, vy)
  const lean = Math.max(-1, Math.min(1, (vx * 0.75 - vy * 0.62) / 900)) * 26
  const axis = (Math.atan2(vy, vx) * 180) / Math.PI
  const pull = Math.min(speed / 4000, 0.14)
  const v = Math.max(0, Math.min(1, shown.value.value))
  const size = (0.4 + 0.6 * v) * pressed.value.value
  return {
    opacity: v,
    filter: `blur(${(1 - v) * 5}px)`,
    transform:
      `rotate(${axis}deg) scale(${1 + pull * 0.6}, ${1 - pull}) rotate(${-axis}deg) ` +
      `rotate(${lean}deg) scale(${size})`,
  }
})

function centre(target: Element): [number, number] {
  const box = target.getBoundingClientRect()
  const origin = layer.value!.getBoundingClientRect()
  return [box.left + box.width / 2 - origin.left, box.top + box.height / 2 - origin.top]
}

async function moveTo(px: number, py: number, signal: AbortSignal) {
  x.set(px)
  y.set(py)
  while (Math.hypot(x.value.value - px, y.value.value - py) > 1.5) await nextFrame(signal)
}

function splash() {
  const el = ripple.value
  if (!el) return
  el.style.left = `${x.value.value}px`
  el.style.top = `${y.value.value}px`
  springAnimate(
    el,
    [
      { opacity: 0.4, transform: 'translate(-50%, -50%) scale(0.2)' },
      { opacity: 0, transform: 'translate(-50%, -50%) scale(1)' },
    ],
    springs.camera,
  )
}

async function press(signal: AbortSignal) {
  pressed.set(0.86)
  splash()
  try {
    await rest(120, signal)
  } finally {
    pressed.set(1)
  }
}

function actor(signal: AbortSignal): Ghost {
  return {
    async click(target) {
      if (!target) throw new Halt()
      await moveTo(...centre(target), signal)
      await rest(90, signal)
      await press(signal)
    },
    async clickAt(px, py) {
      await moveTo(px, py, signal)
      await rest(90, signal)
      await press(signal)
    },
    moveBy: (dx, dy) => moveTo(x.value.value + dx, y.value.value + dy, signal),
    async drag(target, dx, move) {
      if (!target) throw new Halt()
      const [sx, sy] = centre(target)
      await moveTo(sx, sy, signal)
      pressed.set(0.86)
      try {
        await rest(160, signal)
        const pull = new Spring(0, springs.layout)
        pull.set(dx)
        let last = await nextFrame(signal)
        while (!pull.settled) {
          const now = await nextFrame(signal)
          pull.step(Math.min((now - last) / 1000, 1 / 30))
          last = now
          x.jump(sx + pull.value)
          y.jump(sy + Math.abs(pull.value) * 0.06)
          move(pull.value)
        }
        await rest(140, signal)
      } finally {
        pressed.set(1)
      }
    },
    rest: (ms) => rest(ms, signal),
  }
}

/** Fade in from just outside the bottom-right corner, unless already on the card. */
function show() {
  if (visible || !layer.value) return
  visible = true
  x.jump(layer.value.offsetWidth - 36)
  y.jump(layer.value.offsetHeight + 24)
  shown.set(1)
}

function hide() {
  visible = false
  shown.set(0)
  pressed.set(1)
}

defineExpose({ actor, show, hide })
</script>

<template>
  <div ref="layer" class="ghost-layer" aria-hidden="true">
    <span ref="ripple" class="ripple" />
    <div class="ghost" :style="{ transform: `translate3d(${x.value.value}px, ${y.value.value}px, 0)` }">
      <svg class="arrow" :style="pose" viewBox="2 2 21 21" width="26" height="26">
        <path
          d="M3.1 6.29C2.4 4.31 4.31 2.4 6.29 3.1L18.87 7.54C20.92 8.26 21.11 11.04 19.28 12.07L19.1 12.16L14.54 14.29C14.46 14.33 14.39 14.39 14.34 14.46L14.29 14.54L12.16 19.1C11.22 21.13 8.29 20.98 7.54 18.87L3.1 6.29Z"
        />
      </svg>
    </div>
  </div>
</template>

<style scoped>
.ghost-layer {
  position: absolute;
  z-index: 2;
  inset: 0;
  pointer-events: none;
}

.ghost {
  position: absolute;
  top: 0;
  left: 0;
}

/* Codex's agent-cursor blue; the tip of the arrow sits exactly on the ghost's point. */
.arrow {
  --glow: #339cff;

  display: block;
  overflow: visible;
  translate: -2px -2px;
  transform-origin: 2px 2px;
  will-change: transform, filter;
}

.arrow path {
  fill: #0d0d0d;
  stroke: #ffffff;
  stroke-width: 2.2;
  stroke-linejoin: round;
  paint-order: stroke;
  filter: drop-shadow(0 0 6px color-mix(in srgb, var(--glow) 90%, transparent))
    drop-shadow(0 0 15px color-mix(in srgb, var(--glow) 48%, transparent));
}

.ripple {
  position: absolute;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #339cff;
  opacity: 0;
}
</style>
