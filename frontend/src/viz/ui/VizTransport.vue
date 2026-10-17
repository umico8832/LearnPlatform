<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Pause, Play, RotateCcw, SkipBack, SkipForward } from '@lucide/vue'
import { LtIconButton, LtSegmented } from '@/components/living'
import { useSpring } from '@/composables/useSpring'
import { springs } from '@/motion'
import type { VizSession } from '../useVizSession'

/** Playback controls and a scrubbable timeline with checkpoint diamonds. */
const props = withDefaults(defineProps<{ session: VizSession; compact?: boolean }>(), { compact: false })

const track = ref<HTMLElement | null>(null)
const diamonds = new Map<number, HTMLButtonElement>()
const hover = ref<{ index: number; title: string } | null>(null)
const dragging = ref(false)
const position = useSpring(0, springs.snappy)

const player = props.session.player
const state = computed(() => props.session.state.value)
const length = computed(() => props.session.trace.value?.steps.length ?? 0)
const playing = computed(() => state.value.playing && state.value.asking === null)
const current = computed(() => props.session.step.value)
const speed = computed({
  get: () => String(state.value.speed) as '0.5' | '1' | '2',
  set: (value) => player.setSpeed(Number(value)),
})
const ticks = computed(() => Array.from({ length: length.value }, (_, i) => ratio(i)))

function ratio(index: number): number {
  const n = length.value
  return n <= 1 ? 0 : Math.max(0, Math.min(1, index / (n - 1)))
}

function indexAt(event: PointerEvent): number {
  const rect = track.value?.getBoundingClientRect()
  if (!rect || !rect.width) return 0
  const r = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width))
  return Math.round(r * (length.value - 1))
}

function onDown(event: PointerEvent) {
  if ((event.target as Element).closest('.viz-diamond')) return
  dragging.value = true
  track.value?.setPointerCapture(event.pointerId)
  player.seek(indexAt(event))
}

function onMove(event: PointerEvent) {
  const index = indexAt(event)
  const step = props.session.trace.value?.steps[index]
  hover.value = step ? { index, title: step.title } : null
  if (dragging.value && index !== state.value.index) player.seek(index)
}

function onUp(event: PointerEvent) {
  dragging.value = false
  if (track.value?.hasPointerCapture(event.pointerId)) track.value.releasePointerCapture(event.pointerId)
}

function onKey(event: KeyboardEvent) {
  const moves: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1, ArrowDown: -1, ArrowUp: 1 }
  if (event.key in moves) player.seek(state.value.index + moves[event.key])
  else if (event.key === 'Home') player.seek(0)
  else if (event.key === 'End') player.seek(length.value - 1)
  else return
  event.preventDefault()
  event.stopPropagation()
}

function bindDiamond(index: number, el: unknown) {
  if (el instanceof HTMLButtonElement) diamonds.set(index, el)
  else diamonds.delete(index)
}

const knobStyle = computed(() => {
  const squish = Math.min(Math.abs(position.velocity.value) * 0.6, 0.35)
  return {
    left: `${position.value.value * 100}%`,
    transform: `translate(-50%, -50%) scaleX(${1 + squish}) scaleY(${1 - squish * 0.4})`,
  }
})

watch(
  () => props.session.trace.value,
  () => position.jump(ratio(state.value.index)),
)
watch(state, (next) => position.set(next.asking !== null ? ratio(next.asking - 0.5) : ratio(next.index)))

defineExpose({
  /** Screen position of a checkpoint diamond, used as the prediction panel's zoom origin. */
  diamondRect: (index: number) => diamonds.get(index)?.getBoundingClientRect() ?? null,
})
</script>

<template>
  <div class="viz-transport" :data-compact="compact">
    <div class="viz-transport-buttons">
      <LtIconButton label="从头开始" size="sm" @click="player.restart()"><RotateCcw aria-hidden="true" /></LtIconButton>
      <LtIconButton label="上一步（←）" @click="player.prev()"><SkipBack aria-hidden="true" /></LtIconButton>
      <LtIconButton
        :label="playing ? '暂停（空格）' : '播放（空格）'"
        variant="primary"
        size="lg"
        @click="player.toggle()"
      >
        <Pause v-if="playing" fill="currentColor" aria-hidden="true" />
        <Play v-else class="viz-play-icon" fill="currentColor" aria-hidden="true" />
      </LtIconButton>
      <LtIconButton label="下一步（→）" @click="player.next()"><SkipForward aria-hidden="true" /></LtIconButton>
    </div>

    <div class="viz-timeline">
      <div
        ref="track"
        class="viz-track"
        role="slider"
        tabindex="0"
        aria-label="时间轴"
        aria-valuemin="1"
        :aria-valuemax="length"
        :aria-valuenow="state.index + 1"
        :aria-valuetext="current ? `第 ${state.index + 1} 步：${current.title}` : undefined"
        :data-dragging="dragging"
        @pointerdown="onDown"
        @pointermove="onMove"
        @pointerup="onUp"
        @pointerleave="hover = null"
        @keydown="onKey"
      >
        <div class="viz-rail"><div class="viz-fill" :style="{ width: `${position.value.value * 100}%` }" /></div>
        <span v-for="(at, i) in ticks" :key="i" class="viz-tick" :style="{ left: `${at * 100}%` }" />
        <div class="viz-knob" :style="knobStyle" />
        <div v-if="hover" class="viz-hover" :style="{ left: `${ratio(hover.index) * 100}%` }">{{ hover.title }}</div>
      </div>
      <div class="viz-marks">
        <button
          v-for="checkpoint in session.checkpoints.value"
          :key="checkpoint.index"
          :ref="(el) => bindDiamond(checkpoint.index, el)"
          type="button"
          class="viz-diamond"
          :data-state="session.checkpointState(checkpoint.index)"
          :style="{ left: `${ratio(checkpoint.index - 0.5) * 100}%` }"
          :aria-label="`检查点：${checkpoint.predict.prompt}`"
          :title="`检查点：${checkpoint.predict.prompt}`"
          @click="player.revisit(checkpoint.index)"
        />
      </div>
    </div>

    <span class="viz-counter">{{ state.index + 1 }} / {{ length }}</span>
    <LtSegmented
      v-if="!compact"
      v-model="speed"
      :options="[
        { value: '0.5', label: '0.5×' },
        { value: '1', label: '1×' },
        { value: '2', label: '2×' },
      ]"
      label="播放速度"
      size="sm"
    />
  </div>
</template>

<style scoped>
.viz-transport {
  display: flex;
  align-items: center;
  gap: 14px;
}

.viz-transport-buttons {
  display: flex;
  align-items: center;
  gap: 2px;
}

.viz-play-icon {
  transform: translateX(1px);
}

.viz-timeline {
  position: relative;
  flex: 1;
  min-width: 120px;
  height: 36px;
}

.viz-track {
  position: absolute;
  inset: 0;
  outline: none;
  cursor: pointer;
  touch-action: none;
}

.viz-rail {
  position: absolute;
  top: 50%;
  right: 0;
  left: 0;
  height: 6px;
  margin-top: -3px;
  overflow: hidden;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
}

.viz-fill {
  height: 100%;
  border-radius: inherit;
  background: var(--lt-accent);
}

.viz-tick {
  position: absolute;
  top: 50%;
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: var(--lt-label-3);
  pointer-events: none;
  transform: translate(-50%, -50%);
}

.viz-knob {
  position: absolute;
  top: 50%;
  width: 18px;
  height: 18px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-thumb);
  box-shadow: var(--lt-shadow-thumb);
  pointer-events: none;
}

.viz-track:focus-visible .viz-knob {
  box-shadow:
    0 0 0 4px var(--lt-accent-soft),
    0 0 0 1.5px var(--lt-accent),
    var(--lt-shadow-thumb);
}

.viz-marks {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.viz-diamond {
  position: absolute;
  top: 50%;
  width: 12px;
  height: 12px;
  margin: -6px 0 0 -6px;
  padding: 0;
  border: 2px solid var(--lt-purple);
  border-radius: 3px;
  background: var(--lt-surface);
  cursor: pointer;
  pointer-events: auto;
  transform: rotate(45deg);
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    border-color var(--lt-dur-fade) var(--lt-ease-fade),
    transform var(--lt-dur-snappy) var(--lt-ease-snappy);
}

.viz-diamond[data-state='right'] {
  border-color: var(--lt-green);
  background: var(--lt-green);
}

.viz-diamond[data-state='retried'] {
  border-color: var(--lt-orange);
  background: var(--lt-orange);
}

/* 等待作答的检查点有一个表示“待回答”的脉冲，答完即停。 */
.viz-diamond[data-state='asking'] {
  background: var(--lt-purple);
  animation: viz-ring 1.6s ease-out infinite;
}

@keyframes viz-ring {
  0% {
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--lt-purple) 45%, transparent);
  }
  100% {
    box-shadow: 0 0 0 9px transparent;
  }
}

.viz-hover {
  position: absolute;
  bottom: calc(100% - 2px);
  padding: 4px 8px;
  border-radius: 8px;
  background: var(--lt-tooltip-bg);
  color: var(--lt-tooltip-fg);
  font: 500 var(--lt-text-caption) / 1.3 var(--lt-font-mono);
  white-space: nowrap;
  pointer-events: none;
  transform: translateX(-50%);
}

.viz-counter {
  min-width: 52px;
  color: var(--lt-label-2);
  font: 500 var(--lt-text-caption) / 1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.viz-transport[data-compact='true'] {
  gap: 10px;
}

@media (hover: hover) and (pointer: fine) {
  .viz-diamond:hover {
    transform: rotate(45deg) scale(1.25);
  }
}
</style>
