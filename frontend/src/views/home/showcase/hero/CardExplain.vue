<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useSpring } from '@/composables/useSpring'
import { springs } from '@/motion'
import type { Ghost } from './ghost'

/**
 * How the tutor helps, rather than what it says. A ladder above the chat climbs one rung per
 * follow-up, from a nudge to a full walkthrough; the last rung, the answer, stays locked and
 * shakes when asked for. Every reply streams in word by word, and the chat keeps scrolling up
 * like a real conversation.
 */
const RUNGS = ['Nudge', 'Hint', 'Walkthrough', 'Answer'] as const
const HINTS = [
  "Start from the part you're already sure of.",
  "Then let's walk it through together, one step at a time.",
  "That's every rung. The last step is yours.",
]
const NUDGE = 'Not yet. Try a hint first, and I’ll meet you there.'
const LOCKED = 'Try the hints, then reveal it when you’re ready.'
const ASK_GAP = 350
const THINK = 650
const WORD = 55
/** A beat after a reply has finished, before the next question can come. */
const SETTLE = 400

interface Message {
  id: number
  mine: boolean
  words: string[]
}

const messages = ref<Message[]>([])
const typing = ref(false)
const ready = ref(false)
const shake = ref(0)
const level = ref(0)
const fill = useSpring(0, springs.layout)
const hintChip = ref<HTMLElement | null>(null)
const answerChip = ref<HTMLElement | null>(null)
let nextId = 0
let busy = false
let hints = 0
let timers: number[] = []

const wait = (ms: number) => new Promise<void>((resolve) => timers.push(window.setTimeout(resolve, ms)))
const words = (text: string) => text.split(' ')

/** How long one exchange takes, from the question to the reply's last word. */
const span = (reply: string) => ASK_GAP + THINK + words(reply).length * WORD + SETTLE

function post(mine: boolean, text: string) {
  messages.value = [...messages.value, { id: (nextId += 1), mine, words: words(text) }]
}

async function exchange(ask: string, reply: string, rung?: number) {
  busy = true
  post(true, ask)
  await wait(ASK_GAP)
  typing.value = true
  await wait(THINK)
  typing.value = false
  post(false, reply)
  if (rung !== undefined) {
    level.value = Math.max(level.value, rung)
    fill.set(level.value)
  } else shake.value += 1
  await wait(words(reply).length * WORD + SETTLE)
  busy = false
}

/** A follow-up; returns how long its exchange will take, so the demo can wait it out. */
function ask(kind: 'hint' | 'answer') {
  if (busy) return 0
  if (kind === 'answer') {
    void exchange('Show me the answer', LOCKED)
    return span(LOCKED)
  }
  const reply = HINTS[Math.min(hints, HINTS.length - 1)]
  hints += 1
  void exchange(hints === 1 ? 'Give me a hint' : 'Another hint?', reply, Math.min(hints + 1, RUNGS.length - 1))
  return span(reply)
}

let opened: Promise<void> | null = null

async function demo(ghost: Ghost) {
  await opened
  await ghost.rest(700)
  await ghost.click(hintChip.value)
  await ghost.rest(ask('hint') + 500)
  await ghost.click(answerChip.value)
  await ghost.rest(ask('answer') + 1200)
}

defineExpose({ demo })

onMounted(() => {
  opened = wait(400)
    .then(() => exchange('Just tell me the answer?', NUDGE, 1))
    .then(() => {
      ready.value = true
    })
})

onBeforeUnmount(() => {
  timers.forEach((timer) => window.clearTimeout(timer))
  timers = []
})
</script>

<template>
  <div class="hero-scene" :style="{ '--word': `${WORD}ms` }">
    <ol class="ladder">
      <li
        v-for="(rung, i) in RUNGS"
        :key="rung"
        class="rung"
        :data-lit="(i < level && i < RUNGS.length - 1) || undefined"
        :data-current="i === level - 1 || undefined"
        :style="{ '--p': Math.max(0, Math.min(1, fill.value.value - i)) }"
      >
        <span class="track"><span class="bar" /></span>
        <span class="name">
          <svg
            v-if="i === RUNGS.length - 1"
            :key="shake"
            class="lock"
            :data-shake="shake > 0 || undefined"
            viewBox="0 0 12 14"
            aria-hidden="true"
          >
            <rect x="1.5" y="6" width="9" height="7" rx="2" />
            <path d="M3.5 6V4.2a2.5 2.5 0 0 1 5 0V6" />
          </svg>
          {{ rung }}
        </span>
      </li>
    </ol>

    <div class="log">
      <TransitionGroup name="message">
        <div v-for="message in messages" :key="message.id" class="line" :data-mine="message.mine || undefined">
          <p class="bubble">
            <template v-for="(word, i) in message.words" :key="i">
              <span class="word" :style="{ '--i': i }">{{ word }}</span
              >{{ ' ' }}
            </template>
          </p>
        </div>
        <div v-if="typing" key="typing" class="line">
          <p class="bubble dots"><span class="pulse" /><span class="pulse" /><span class="pulse" /></p>
        </div>
      </TransitionGroup>
    </div>

    <div class="chips" :data-ready="ready || undefined">
      <button ref="hintChip" type="button" tabindex="-1" class="chip" @click="ask('hint')">Give me a hint</button>
      <button ref="answerChip" type="button" tabindex="-1" class="chip" @click="ask('answer')">
        Show me the answer
      </button>
    </div>
  </div>
</template>

<style scoped>
.ladder {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
  animation: hero-appear var(--lt-dur-fade) var(--lt-ease-fade) backwards 120ms;
}

.rung {
  display: grid;
  gap: 7px;
}

.track {
  height: 4px;
  overflow: hidden;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
}

.bar {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--lt-accent);
  transform: scaleX(var(--p));
  transform-origin: left;
}

.name {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--lt-label-3);
  font-family: var(--lt-font-rounded);
  font-size: var(--lt-text-caption);
  font-weight: 600;
  transition: color var(--lt-dur-fade) var(--lt-ease-fade);
}

.rung[data-lit] .name {
  color: var(--lt-label-2);
}

.rung[data-current] .name {
  color: var(--lt-accent);
}

.lock {
  width: 11px;
  height: 13px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.5;
}

.lock rect {
  fill: currentColor;
  fill-opacity: 0.2;
}

.lock[data-shake] {
  color: var(--lt-orange-text);
  animation: nope 520ms var(--lt-ease-snappy) 1ms;
}

@keyframes nope {
  20% {
    translate: -3px 0;
    rotate: -12deg;
  }
  40% {
    translate: 3px 0;
    rotate: 10deg;
  }
  60% {
    translate: -2px 0;
    rotate: -6deg;
  }
  80% {
    translate: 1px 0;
    rotate: 3deg;
  }
}

/* The newest line sits at the bottom; older ones scroll up and fade out under the ladder. */
.log {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 8px;
  height: 152px;
  margin-top: 12px;
  overflow: hidden;
  mask-image: linear-gradient(to bottom, transparent, black 26px);
}

.line {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.line[data-mine] {
  align-items: flex-end;
}

.bubble {
  max-width: 82%;
  margin: 0;
  padding: 8px 13px;
  border-radius: 17px;
  border-bottom-left-radius: 6px;
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font-size: var(--lt-text-subhead);
  line-height: 1.4;
}

.line[data-mine] .bubble {
  border-bottom-right-radius: 6px;
  border-bottom-left-radius: 17px;
  background: var(--lt-accent-fill);
  color: var(--lt-on-accent);
}

/* Replies stream in a word at a time; a question arrives whole. */
.line:not([data-mine]) .word {
  display: inline-block;
  animation: word var(--lt-dur-fade) var(--lt-ease-fade) backwards;
  animation-delay: calc(var(--i) * var(--word));
}

@keyframes word {
  from {
    opacity: 0;
    filter: blur(4px);
    transform: translateY(3px);
  }
}

.dots {
  display: flex;
  gap: 5px;
  padding: 12px 14px;
}

.pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--lt-label-3);
  animation: pulse 900ms var(--lt-ease-fade) infinite;
}

.pulse:nth-child(2) {
  animation-delay: 150ms;
}

.pulse:nth-child(3) {
  animation-delay: 300ms;
}

@keyframes pulse {
  50% {
    opacity: 0.3;
    transform: translateY(-3px);
  }
}

.message-enter-active {
  transition:
    opacity var(--lt-dur-fade) var(--lt-ease-fade),
    transform var(--lt-dur-layout) var(--lt-ease-layout);
}

.message-enter-from {
  opacity: 0;
  transform: translateY(10px) scale(0.96);
}

.line[data-mine].message-enter-from {
  transform-origin: right bottom;
}

.message-leave-active {
  display: none;
}

.message-move {
  transition: transform var(--lt-dur-layout) var(--lt-ease-layout);
}

.chips {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  opacity: 0;
  transform: translateY(6px);
  transition:
    opacity var(--lt-dur-fade) var(--lt-ease-fade),
    transform var(--lt-dur-layout) var(--lt-ease-layout);
}

.chips[data-ready] {
  opacity: 1;
  transform: none;
}

.chip {
  height: 32px;
  padding: 0 14px;
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 1px var(--lt-separator);
  color: var(--lt-accent);
  font-size: var(--lt-text-footnote);
  font-weight: 600;
  cursor: pointer;
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    scale var(--lt-dur-snappy) var(--lt-ease-snappy);
}

.chip:hover {
  background: var(--lt-fill-2);
}

.chip:active {
  scale: 0.96;
}
</style>
