<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { springAnimate, springEasing, springs } from '@/motion'
import { useSpring } from '@/composables/useSpring'

/**
 * “Learn / to *word.*” — the last word is retyped letter by letter whenever `word`
 * changes: the old letters lift away blurred, the new ones rise into focus one after
 * another, and the highlight under the word stretches to its new length.
 */
const props = defineProps<{ word: string; all: readonly string[] }>()

const slot = ref<HTMLElement | null>(null)
const lines: HTMLElement[] = []
const band = useSpring(0, springs.layout)
const chars = computed(() => [...`${props.word}.`].map((c) => (c === ' ' ? '\u00a0' : c)))
const spoken = computed(() => `Learn to ${props.all.slice(0, -1).join(', ')} and ${props.all.at(-1)}.`)

function letters(el: Element) {
  return [...el.querySelectorAll<HTMLElement>('.char')]
}

function setLine(index: number, el: unknown) {
  if (el instanceof HTMLElement) lines[index] = el
}

function onEnter(el: Element, done: () => void) {
  const all = letters(el)
  if (!all.length) return done()
  const { duration } = springEasing(springs.layout)
  all.forEach((char, i) =>
    springAnimate(
      char,
      [
        { opacity: 0, filter: 'blur(14px)', transform: 'translateY(55%)' },
        { opacity: 1, filter: 'blur(0)', transform: 'none' },
      ],
      springs.layout,
      { delay: i * 42, fill: 'backwards' },
    ),
  )
  window.setTimeout(done, duration + all.length * 42)
}

function onLeave(el: Element, done: () => void) {
  const all = letters(el)
  if (!all.length) return done()
  const animations = all.map((char, i) =>
    springAnimate(
      char,
      [
        { opacity: 1, filter: 'blur(0)', transform: 'none' },
        { opacity: 0, filter: 'blur(10px)', transform: 'translateY(-38%)' },
      ],
      springs.fade,
      { delay: i * 16, fill: 'forwards' },
    ),
  )
  Promise.all(animations.map((a) => a?.finished)).then(done, done)
}

// The highlight follows the word's real width: on a new word, and when the display
// face arrives after first paint (it is narrower than the fallback).
let fitted = false
let resize: ResizeObserver | null = null

onBeforeUnmount(() => resize?.disconnect())

onMounted(() => {
  resize = new ResizeObserver(([entry]) => {
    const width = entry.contentRect.width
    if (fitted) band.set(width)
    else band.jump(width)
    fitted = true
  })
  if (slot.value) resize.observe(slot.value)
  lines.forEach((line, i) =>
    springAnimate(
      line,
      [
        { opacity: 0, filter: 'blur(18px)', transform: 'translateY(24px)' },
        { opacity: 1, filter: 'blur(0)', transform: 'none' },
      ],
      springs.camera,
      { delay: 80 + i * 140, fill: 'backwards' },
    ),
  )
})
</script>

<template>
  <h1 class="headline">
    <span class="sr-only">{{ spoken }}</span>
    <span :ref="(el) => setLine(0, el)" class="line" aria-hidden="true">Learn</span>
    <span :ref="(el) => setLine(1, el)" class="line" aria-hidden="true">
      to
      <span ref="slot" class="slot">
        <span class="band" :style="{ width: `${band.value.value}px` }" />
        <Transition mode="out-in" :css="false" @enter="onEnter" @leave="onLeave">
          <span :key="word" class="word">
            <span v-for="(char, i) in chars" :key="i" class="char">{{ char }}</span>
          </span>
        </Transition>
      </span>
    </span>
  </h1>
</template>

<style scoped>
.headline {
  margin: 0;
  color: var(--lt-label);
  font-family: var(--lt-font-display);
  font-size: clamp(80px, 9.2vw, 136px);
  font-weight: 400;
  letter-spacing: -0.02em;
  line-height: 0.94;
}

.line {
  display: block;
  white-space: nowrap;
}

.slot {
  position: relative;
  display: inline-block;
  isolation: isolate;
}

.band {
  position: absolute;
  z-index: -1;
  bottom: 0.04em;
  left: 0;
  height: 0.045em;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-accent);
}

.word {
  display: inline-block;
  font-style: italic;
  white-space: nowrap;
}

.char {
  display: inline-block;
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
