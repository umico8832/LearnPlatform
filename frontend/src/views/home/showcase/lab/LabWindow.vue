<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { CodeXml, Pause, Play, Shuffle } from '@lucide/vue'
import { useSpring } from '@/composables/useSpring'
import { springs } from '@/motion'
import { LtButton, LtIconButton } from '@/components/living'
import { findScene, type SceneModule, type SceneSpec, type Trace } from '@/viz'
import { Player, type PlayerState } from '@/viz/engine/player'
import { applyPauseAt } from '@/viz/spec'
import VizStage from '@/viz/ui/VizStage.vue'
import GhostCursor from '../hero/GhostCursor.vue'
import { Halt, type Ghost } from '../hero/ghost'
import { REVERSE_CODE } from './code'
import { beats, detailFrame } from './frames'
import LabCode from './LabCode.vue'
import LabTimeline from './LabTimeline.vue'

/**
 * A chain that turns itself around, one link at a time. The platform's own simulator
 * computes the run and its spring stage draws it; the homepage only strips away
 * everything that would need reading code. Drag time either way, or shuffle the data
 * and the whole run is worked out again. The code stays one press away: opening it
 * brings back every statement as its own step, with the variables riding the chain.
 */
const SPEED = 2
const START = [3, 7, 1, 9, 4]
/** Width of the code column when it is open. */
const CODE_WIDTH = 470

const player = new Player()
const state = shallowRef<PlayerState>(player.snapshot())
const values = shallowRef<number[]>(START)
/** The run as the visitor sees it first: only steps that change the picture. */
const bare = shallowRef<Trace | null>(null)
/** Every statement as a step, for when the code is open. */
const full = shallowRef<Trace | null>(null)
/** For each bare step, the statement it shows. */
const sources = shallowRef<number[]>([])
const detail = ref(false)
const hovered = ref<number | null>(null)
const panel = useSpring(0, springs.layout)
let scene: SceneModule | null = null

player.subscribe((next) => {
  state.value = next
})

const root = ref<HTMLElement | null>(null)
const timeline = ref<InstanceType<typeof LabTimeline> | null>(null)
const shuffleButton = ref<InstanceType<typeof LtButton> | null>(null)
const playButton = ref<InstanceType<typeof LtIconButton> | null>(null)
const ghost = ref<InstanceType<typeof GhostCursor> | null>(null)

const trace = computed(() => (detail.value ? full.value : bare.value))
const steps = computed(() => trace.value?.steps ?? [])
const index = computed(() => state.value.index)
const playing = computed(() => state.value.playing)
const frame = computed(() => steps.value[index.value]?.frame ?? null)
const groups = computed(() => steps.value.map((step) => step.phase ?? ''))
const keys = computed(() => (detail.value ? steps.value.map((_, i) => i) : sources.value))
/** The statement on screen, in either view, so the code bar stays put while the panel closes. */
const source = computed(() => (detail.value ? index.value : (sources.value[index.value] ?? 0)))
const line = computed(() => full.value?.steps[source.value]?.line ?? null)
const lit = computed(() =>
  detail.value && hovered.value !== null
    ? steps.value.flatMap((step, i) => (step.line === hovered.value ? [i] : []))
    : [],
)

/** The bare step showing a statement: the last one at or before it. */
function bareIndexOf(statement: number) {
  let found = 0
  sources.value.forEach((s, i) => {
    if (s <= statement) found = i
  })
  return found
}

/** Load the active view and put the playhead on a statement, keeping play or pause. */
function show(statement: number) {
  const next = trace.value
  if (!next) return
  const resume = player.playing
  player.load(next)
  player.seek(detail.value ? statement : bareIndexOf(statement))
  player.setSpeed(SPEED)
  if (resume) player.play()
}

/** Simulate the current data; with `keep` the playhead stays on the same fraction of the run. */
function simulate(keep: boolean) {
  if (!scene) return
  const total = full.value?.steps.length ?? 0
  const fraction = keep && total > 1 ? source.value / (total - 1) : 0
  const spec: SceneSpec = { scene: 'linked-list', op: 'reverse', values: values.value, pauseAt: [] }
  const run = applyPauseAt(scene.build(spec), [])
  const picked = beats(run.steps)
  bare.value = {
    ...run,
    steps: picked.map((beat) => ({
      frame: beat.frame,
      line: null,
      title: '',
      note: beat.note,
      vars: [],
      phase: beat.group,
    })),
  }
  sources.value = picked.map((beat) => beat.source)
  full.value = {
    ...run,
    code: REVERSE_CODE,
    steps: run.steps.map((step) => ({
      frame: detailFrame(step.frame),
      line: step.line,
      title: step.title,
      note: step.note,
      vars: [],
      phase: step.phase,
    })),
  }
  show(Math.round(fraction * (run.steps.length - 1)))
}

function setDetail(open: boolean) {
  const statement = source.value
  hovered.value = null
  detail.value = open
  show(statement)
}

/** Jump to where a line runs next, wrapping round to its first run. */
function pick(target: number) {
  const runs = steps.value.flatMap((step, i) => (step.line === target ? [i] : []))
  if (!runs.length) return
  player.seek(runs.find((i) => i > index.value) ?? runs[0])
}

watch(detail, (open) => panel.set(open ? CODE_WIDTH : 0))

let wasPlaying = false

function seek(i: number) {
  player.seek(i)
}

function onScrub(active: boolean) {
  if (active) {
    wasPlaying = player.playing
    player.pause()
  } else if (wasPlaying && !player.atEnd) player.play()
}

function toggle() {
  player.setSpeed(SPEED)
  player.toggle()
}

/** New numbers and a new length; the run is simulated again and the stage springs to it. */
function shuffle() {
  const size = 4 + Math.floor(Math.random() * 3)
  const pool = [1, 2, 3, 4, 5, 6, 7, 8, 9]
  for (let i = pool.length - 1; i > 0; i--) {
    const k = Math.floor(Math.random() * (i + 1))
    ;[pool[i], pool[k]] = [pool[k], pool[i]]
  }
  values.value = pool.slice(0, size)
  simulate(true)
}

/* ------------------------------------------------------------------ demo */

let run: AbortController | null = null
let visitor = false
let visible = false

async function until(actor: Ghost, done: () => boolean) {
  while (!done()) await actor.rest(100)
}

/** Drag the playhead to a step, the way a hand would. */
async function scrub(actor: Ghost, to: number) {
  const bar = timeline.value
  if (!bar) return
  const from = bar.xOf(index.value)
  player.pause()
  onScrub(true)
  await actor.drag(bar.thumb, bar.xOf(to) - from, (offset) => bar.scrubTo(from + offset))
  bar.settle()
  onScrub(false)
}

async function demo() {
  run?.abort()
  const controller = new AbortController()
  run = controller
  const actor = ghost.value?.actor(controller.signal)
  if (!actor || !scene) return
  try {
    values.value = START
    simulate(false)
    await actor.rest(700)
    player.play()
    await until(actor, () => index.value >= Math.floor(steps.value.length * 0.45))
    ghost.value?.show()
    await scrub(actor, 0)
    await actor.rest(900)
    await scrub(actor, steps.value.length - 1)
    await actor.rest(1500)
    await actor.click(shuffleButton.value?.$el)
    shuffle()
    await actor.rest(1300)
    await actor.click(playButton.value?.$el)
    player.play()
    await actor.moveBy(-80, 60)
    await actor.rest(600)
    ghost.value?.hide()
  } catch (error) {
    if (!(error instanceof Halt)) throw error
  }
}

/** Any real press inside the window hands the lab to the visitor. */
function takeOver(event: PointerEvent | KeyboardEvent) {
  if (!event.isTrusted || visitor) return
  visitor = true
  run?.abort()
  ghost.value?.hide()
}

let intersection: IntersectionObserver | null = null

onMounted(async () => {
  scene = (await findScene('linked-list')?.load()) ?? null
  simulate(false)
  intersection = new IntersectionObserver(
    ([entry]) => {
      const now = entry.intersectionRatio >= 0.4
      if (now === visible) return
      visible = now
      if (visible && !visitor) void demo()
      if (!visible) {
        run?.abort()
        ghost.value?.hide()
        player.pause()
      }
    },
    { threshold: [0, 0.4] },
  )
  if (root.value) intersection.observe(root.value)
})

onBeforeUnmount(() => {
  run?.abort()
  intersection?.disconnect()
  player.destroy()
})
</script>

<template>
  <div ref="root" class="window" @pointerdown.capture="takeOver" @keydown.capture="takeOver">
    <div class="top">
      <div class="stage-wrap">
        <VizStage
          v-if="frame"
          class="stage"
          :frame="frame"
          :ghost="null"
          :label="`A chain of ${values.length} links turning around, step ${index + 1} of ${steps.length}`"
        />
        <div class="tools">
          <LtButton size="sm" variant="quiet" :aria-pressed="detail" @click="setDetail(!detail)">
            <template #icon><CodeXml :size="15" aria-hidden="true" /></template>
            {{ detail ? 'Hide code' : 'Show code' }}
          </LtButton>
          <LtButton ref="shuffleButton" size="sm" @click="shuffle">
            <template #icon><Shuffle :size="15" aria-hidden="true" /></template>
            Shuffle
          </LtButton>
        </div>
      </div>
      <div
        v-if="panel.value.value > 0.5 || detail"
        class="code-col"
        :style="{ width: `${Math.max(0, panel.value.value)}px` }"
      >
        <div
          class="code-inner"
          :style="{ width: `${CODE_WIDTH}px`, opacity: Math.min(1, Math.max(0, panel.value.value / CODE_WIDTH)) }"
        >
          <LabCode :code="REVERSE_CODE" :line="line" @hover="hovered = $event" @pick="pick" />
        </div>
      </div>
    </div>

    <div class="transport">
      <LtIconButton ref="playButton" :label="playing ? 'Pause' : 'Play'" variant="primary" @click="toggle">
        <Pause v-if="playing" :size="18" fill="currentColor" aria-hidden="true" />
        <Play v-else :size="18" fill="currentColor" aria-hidden="true" />
      </LtIconButton>
      <LabTimeline
        ref="timeline"
        class="timeline"
        :count="steps.length"
        :index="index"
        :groups="groups"
        :keys="keys"
        :lit="lit"
        @seek="seek"
        @scrub="onScrub"
      />
    </div>

    <GhostCursor ref="ghost" />
  </div>
</template>

<style scoped>
/* One surface with a faint blue rim, so it reads as lit from inside like the network above. */
.window {
  position: relative;
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  height: 480px;
  overflow: hidden;
  border-radius: var(--lt-radius-xl);
  background:
    radial-gradient(70% 90% at 50% 45%, color-mix(in oklab, var(--lt-accent) 9%, transparent), transparent 70%),
    var(--lt-surface);
  box-shadow:
    0 0 0 0.5px color-mix(in oklab, var(--lt-accent) 35%, var(--lt-separator)),
    0 30px 90px rgba(0, 0, 0, 0.55),
    0 0 120px color-mix(in oklab, var(--lt-accent) 10%, transparent);
}

.top {
  display: flex;
  min-height: 0;
}

.stage-wrap {
  position: relative;
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

/* The code column grows out of the right edge; its content keeps a fixed width and is revealed. */
.code-col {
  flex: none;
  overflow: hidden;
  border-left: 0.5px solid color-mix(in oklab, var(--lt-accent) 22%, var(--lt-separator));
  background: color-mix(in oklab, var(--lt-label) 3%, transparent);
}

.code-inner {
  height: 100%;
}

/* The engine caps its zoom for embedded use; the showcase wants the chain larger than that. */
.stage {
  position: absolute;
  top: 50%;
  left: 50%;
  width: calc(100% / 1.4);
  height: calc(100% / 1.4);
  transform: translate(-50%, -50%) scale(1.4);
}

.tools {
  position: absolute;
  top: 20px;
  right: 20px;
  display: flex;
  gap: 8px;
}

.transport {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 32px 26px 26px;
}

.timeline {
  flex: 1;
  min-width: 0;
}

/* The stage keeps its engine; here it loses the dotted well and its links glow. */
.window :deep(.vz-stage) {
  background: none;
}

.window :deep(.vz-node) {
  --vz-node-bg: color-mix(in oklab, var(--lt-label) 7%, var(--lt-surface));
}

/* A link that points nowhere simply has no dot; the textbook's ∧ mark stays in the textbook. */
.window :deep(.vz-ptr-mark) {
  display: none;
}

.window :deep(.vz-data) {
  font-size: 22px;
}

.window :deep(.vz-node[data-tone='success']) {
  background: color-mix(in oklab, var(--lt-accent) 16%, var(--vz-node-bg));
  box-shadow:
    0 0 0 1px color-mix(in oklab, var(--lt-accent) 55%, transparent),
    0 0 24px color-mix(in oklab, var(--lt-accent) 22%, transparent);
}

.window :deep(.vz-node[data-tone='accent']) {
  box-shadow:
    0 0 0 2px var(--lt-accent),
    0 0 32px color-mix(in oklab, var(--lt-accent) 45%, transparent);
}

.window :deep(.vz-edge[data-tone='accent'] .vz-edge-line) {
  filter: drop-shadow(0 0 5px var(--lt-accent));
}
</style>
