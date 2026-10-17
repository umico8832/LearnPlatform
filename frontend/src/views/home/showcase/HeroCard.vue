<script setup lang="ts">
import './hero/scene.css'
import { onBeforeUnmount, onMounted, provide, ref } from 'vue'
import { springAnimate, springs } from '@/motion'
import { useSpring } from '@/composables/useSpring'
import type { HeroWord } from './showcaseContent'
import CardExplain from './hero/CardExplain.vue'
import CardRemember from './hero/CardRemember.vue'
import CardSeeIt from './hero/CardSeeIt.vue'
import CardUnderstand from './hero/CardUnderstand.vue'
import GhostCursor from './hero/GhostCursor.vue'
import { Halt, type DemoScene } from './hero/ghost'
import { heroActive } from './hero/activity'

/**
 * The small card beside the headline. It shows a playable miniature of whatever the
 * headline word promises, and a scripted "Learner" pointer plays with it. The moment the
 * visitor clicks inside, the pointer bows out and the card stops turning; a few seconds
 * after the visitor leaves, the demo quietly picks up again with the next word.
 */
defineProps<{ word: HeroWord; words: readonly HeroWord[] }>()
const emit = defineEmits<{ select: [index: number]; advance: [] }>()

const scenes = { understand: CardUnderstand, 'see it': CardSeeIt, remember: CardRemember, explain: CardExplain }
const FIRST_LOOK = 1200
const LINGER = 1600
const RESUME = 6000

const h = useSpring(300, springs.layout)
const frame = ref<HTMLElement | null>(null)
const scene = ref<DemoScene | null>(null)
const ghost = ref<InstanceType<typeof GhostCursor> | null>(null)
const visitor = ref(false)
const active = ref(true)
provide(heroActive, active)
let visibility: IntersectionObserver | null = null
let observer: ResizeObserver | null = null
let sized = false
let inside = false
let first = true
let run: AbortController | null = null
let resumeTimer = 0

/** The frame springs to the height of whatever the scene shows right now. */
function measure(entries: ResizeObserverEntry[]) {
  const height = entries[0].borderBoxSize?.[0]?.blockSize ?? entries[0].target.getBoundingClientRect().height
  if (!height) return
  if (sized) h.set(height)
  else {
    h.jump(height)
    sized = true
  }
}

async function play() {
  run?.abort()
  if (!active.value || visitor.value || !scene.value || !ghost.value) return
  const controller = new AbortController()
  run = controller
  const actor = ghost.value.actor(controller.signal)
  try {
    if (first) await actor.rest(FIRST_LOOK)
    first = false
    ghost.value.show()
    await scene.value.demo(actor)
    await actor.rest(LINGER)
    while (inside) await actor.rest(400)
    emit('advance')
  } catch (error) {
    if (!(error instanceof Halt)) throw error
  }
}

function onEnter(el: Element, done: () => void) {
  observer?.observe(el)
  const animation = springAnimate(
    el,
    [
      { opacity: 0, filter: 'blur(8px)', transform: 'scale(1.03)' },
      { opacity: 1, filter: 'blur(0)', transform: 'none' },
    ],
    springs.layout,
  )
  const settled = () => {
    done()
    void play()
  }
  if (!animation) return settled()
  animation.finished.then(settled, settled)
}

function onLeave(el: Element, done: () => void) {
  run?.abort()
  observer?.unobserve(el)
  const animation = springAnimate(
    el,
    [
      { opacity: 1, filter: 'blur(0)', transform: 'none' },
      { opacity: 0, filter: 'blur(8px)', transform: 'scale(0.96)' },
    ],
    springs.fade,
    { fill: 'forwards' },
  )
  if (!animation) return done()
  animation.finished.then(done, done)
}

/** Any real press inside the card hands it to the visitor. */
function takeOver(event: PointerEvent) {
  if (!event.isTrusted || visitor.value) return
  visitor.value = true
  run?.abort()
  ghost.value?.hide()
}

function onPointerEnter() {
  inside = true
  window.clearTimeout(resumeTimer)
}

function onPointerLeave() {
  inside = false
  if (!visitor.value) return
  window.clearTimeout(resumeTimer)
  resumeTimer = window.setTimeout(() => {
    visitor.value = false
    emit('advance')
  }, RESUME)
}

onMounted(() => {
  visibility = new IntersectionObserver(([entry]) => {
    active.value = entry.isIntersecting
    if (active.value) void play()
    else {
      run?.abort()
      ghost.value?.hide()
    }
  })
  if (frame.value) visibility.observe(frame.value)
  observer = new ResizeObserver(measure)
  const current = frame.value?.querySelector('.hero-scene')
  if (current) observer.observe(current)
  void play()
})

onBeforeUnmount(() => {
  run?.abort()
  observer?.disconnect()
  visibility?.disconnect()
  window.clearTimeout(resumeTimer)
})
</script>

<template>
  <div class="hero-card" @pointerenter="onPointerEnter" @pointerleave="onPointerLeave">
    <div
      ref="frame"
      class="frame"
      :style="{ height: `${h.value.value}px` }"
      aria-hidden="true"
      @pointerdown.capture="takeOver"
    >
      <Transition mode="out-in" :css="false" @enter="onEnter" @leave="onLeave">
        <component :is="scenes[word]" :key="word" ref="scene" />
      </Transition>
      <GhostCursor ref="ghost" />
    </div>

    <div class="dots" role="group" aria-label="Choose what the card shows">
      <button
        v-for="(item, i) in words"
        :key="item"
        type="button"
        class="dot"
        :aria-label="`Show ${item}`"
        :aria-pressed="item === word"
        @click="emit('select', i)"
      />
    </div>
  </div>
</template>

<style scoped>
.hero-card {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--lt-space-4);
}

.frame {
  position: relative;
  width: 496px;
  overflow: hidden;
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface);
  box-shadow:
    0 0 0 0.5px var(--lt-hairline),
    0 18px 48px light-dark(rgba(0, 0, 0, 0.1), rgba(0, 0, 0, 0.32));
}

.dots {
  display: flex;
  gap: 8px;
  padding-right: 8px;
}

.dot {
  width: 8px;
  height: 8px;
  padding: 0;
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill);
  cursor: pointer;
  transition:
    width var(--lt-dur-snappy) var(--lt-ease-snappy),
    background-color var(--lt-dur-fade) var(--lt-ease-fade);
}

.dot[aria-pressed='true'] {
  width: 24px;
  background: var(--lt-label);
}

.dot:focus-visible {
  outline: 2px solid var(--lt-accent);
  outline-offset: 3px;
}
</style>
