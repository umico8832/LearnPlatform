<script setup lang="ts">
import { computed, nextTick, onMounted, watch } from 'vue'
import { useSpring } from '@/composables/useSpring'
import { springs } from '@/motion'
import type { CodeListing } from '@/viz/engine/types'
import { tokenizeLine } from '@/viz/ui/codeTokens'

/**
 * The program behind the chain, for whoever opens it. A soft bar springs to the
 * statement that just ran; pointing at a line asks the timeline where it ran, and
 * pressing it jumps there.
 */
const props = defineProps<{ code: CodeListing; line: number | null }>()

const emit = defineEmits<{ hover: [line: number | null]; pick: [line: number] }>()

const lineEls: HTMLElement[] = []
const top = useSpring(0, springs.snappy)
const shown = useSpring(0, springs.fade)

const lines = computed(() => props.code.lines.map((text) => tokenizeLine(text, props.code.vars)))

const barStyle = computed(() => ({
  opacity: Math.max(0, Math.min(1, shown.value.value)),
  transform: `translateY(${top.value.value}px)`,
}))

function place() {
  const target = props.line ? lineEls[props.line - 1] : undefined
  if (!target) {
    shown.set(0)
    return
  }
  if (shown.value.value < 0.05) top.jump(target.offsetTop)
  top.set(target.offsetTop)
  shown.set(1)
}

watch(
  () => props.line,
  () => void nextTick(place),
)

onMounted(place)
</script>

<template>
  <section class="code" aria-label="Source code">
    <header class="file">{{ code.file }}</header>
    <div class="body" @pointerleave="emit('hover', null)">
      <div class="bar" :style="barStyle" aria-hidden="true" />
      <ol class="lines">
        <li
          v-for="(tokens, i) in lines"
          :key="i"
          :ref="(el) => (lineEls[i] = el as HTMLElement)"
          class="line"
          :data-active="line === i + 1 || undefined"
          :aria-current="line === i + 1 ? 'step' : undefined"
          @pointerenter="emit('hover', i + 1)"
          @click="emit('pick', i + 1)"
        >
          <span class="ln">{{ i + 1 }}</span>
          <span class="src">
            <span
              v-for="(token, k) in tokens"
              :key="k"
              :class="token.kind && `tk-${token.kind}`"
              :data-color="token.color"
              >{{ token.text }}</span
            >
          </span>
        </li>
      </ol>
    </div>
  </section>
</template>

<style scoped>
.code {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 100%;
  padding: 22px 22px 0 24px;
}

.file {
  color: var(--lt-label-3);
  font: 500 12px / 1 var(--lt-font-mono);
  letter-spacing: 0.02em;
}

.body {
  position: relative;
  font: 12.5px / 26px var(--lt-font-mono);
}

.lines {
  position: relative;
  z-index: 1;
  margin: 0;
  padding: 0;
  list-style: none;
}

.line {
  display: flex;
  padding: 0 10px 0 6px;
  border-radius: 7px;
  color: var(--lt-label-2);
  white-space: pre;
  cursor: pointer;
  transition: color var(--lt-dur-fade) var(--lt-ease-fade);
}

.line:hover,
.line[data-active] {
  color: var(--lt-label);
}

.line:hover .ln {
  color: var(--lt-label-2);
}

.ln {
  flex: none;
  width: 18px;
  margin-right: 14px;
  color: var(--lt-label-3);
  font-variant-numeric: tabular-nums;
  text-align: right;
  transition: color var(--lt-dur-fade) var(--lt-ease-fade);
}

.line[data-active] .ln {
  color: var(--lt-accent);
}

.bar {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 26px;
  border-radius: 7px;
  background: color-mix(in oklab, var(--lt-accent) 16%, transparent);
  box-shadow: inset 2px 0 0 var(--lt-accent);
}

.tk-keyword {
  color: var(--lt-syn-keyword);
}

.tk-type {
  color: var(--lt-syn-type);
}

.tk-number {
  color: var(--lt-syn-number);
}

.tk-comment {
  color: var(--lt-syn-comment);
}

.tk-var {
  color: var(--var-color);
  font-weight: 600;
}
</style>
