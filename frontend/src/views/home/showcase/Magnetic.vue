<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useSpring } from '@/composables/useSpring'
import { springs } from '@/motion'

/**
 * Lets whatever it wraps drift toward a nearby mouse, as if pulled by it, and spring back
 * once the mouse moves away. The pull fades in over REACH pixels outside the edge and never
 * moves the content more than MAX pixels, so the target stays where the eye expects it.
 */
const REACH = 64
const PULL = 0.3
const MAX = 8

const box = ref<HTMLElement | null>(null)
const x = useSpring(0, springs.pointer)
const y = useSpring(0, springs.pointer)
let pointer: { x: number; y: number; over: Element | null } | null = null
let pending = 0

const clamp = (value: number) => Math.max(-MAX, Math.min(MAX, value))

function pull() {
  pending = 0
  const el = box.value
  if (!el || !pointer) return
  // A neighbour the mouse is already on keeps it; this one lets go.
  const holder = pointer.over?.closest('.magnetic')
  if (holder && holder !== el) {
    x.set(0)
    y.set(0)
    return
  }
  const rect = el.getBoundingClientRect()
  // Measure from the resting position, not from where the pull has already moved it.
  const dx = pointer.x - (rect.left + rect.width / 2 - x.value.value)
  const dy = pointer.y - (rect.top + rect.height / 2 - y.value.value)
  const gap = Math.hypot(Math.max(Math.abs(dx) - rect.width / 2, 0), Math.max(Math.abs(dy) - rect.height / 2, 0))
  const strength = Math.max(0, 1 - gap / REACH)
  x.set(clamp(dx * PULL * strength))
  y.set(clamp(dy * PULL * strength))
}

function onMove(event: PointerEvent) {
  if (event.pointerType !== 'mouse') return
  pointer = { x: event.clientX, y: event.clientY, over: event.target instanceof Element ? event.target : null }
  pending ||= requestAnimationFrame(pull)
}

function release(event: PointerEvent) {
  if (event.relatedTarget) return
  pointer = null
  x.set(0)
  y.set(0)
}

onMounted(() => {
  window.addEventListener('pointermove', onMove, { passive: true })
  document.addEventListener('pointerout', release)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(pending)
  window.removeEventListener('pointermove', onMove)
  document.removeEventListener('pointerout', release)
})
</script>

<template>
  <span ref="box" class="magnetic" :style="{ transform: `translate3d(${x.value.value}px, ${y.value.value}px, 0)` }">
    <slot />
  </span>
</template>

<style scoped>
.magnetic {
  display: inline-flex;
}
</style>
