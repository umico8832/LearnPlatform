<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { LtCard } from '@/components/living'
import { useSpring } from '@/composables/useSpring'
import { blurIn, springs } from '@/motion'
import type { CodeListing } from '../engine/types'
import { tokenizeLine } from './codeTokens'

/**
 * Code listing with a highlight bar that springs to the running line. While a
 * prediction is open the line about to run is masked, so the code does not give the answer away.
 */
const props = defineProps<{ code: CodeListing; line: number | null; pending: boolean }>()

const list = ref<HTMLElement | null>(null)
const lineEls: HTMLElement[] = []
const top = useSpring(0, springs.snappy)
const opacity = useSpring(0, springs.fade)

const lines = computed(() => props.code.lines.map((text) => tokenizeLine(text, props.code.vars)))

const barStyle = computed(() => {
  const squash = Math.min(Math.abs(top.velocity.value) / 5000, 0.18)
  return {
    opacity: Math.max(0, Math.min(1, opacity.value.value)),
    transform: `translateY(${top.value.value}px) scaleX(${1 - squash * 0.15}) scaleY(${1 + squash})`,
  }
})

function place() {
  const target = props.line ? lineEls[props.line - 1] : undefined
  if (!target) {
    opacity.set(0)
    return
  }
  if (opacity.value.value < 0.05) top.jump(target.offsetTop)
  top.set(target.offsetTop)
  opacity.set(1)
}

watch(
  () => props.line,
  () => void nextTick(place),
  { immediate: true },
)
watch(
  () => props.code,
  () =>
    void nextTick(() => {
      if (list.value) blurIn(list.value, 2)
      place()
    }),
)
</script>

<template>
  <LtCard as="section" padding="compact" heading="h2" class="viz-code" :data-pending="pending" aria-label="代码">
    <template #title>{{ code.file }}</template>
    <template #meta>C</template>
    <div class="viz-code-body">
      <div class="viz-code-bar" :style="barStyle" aria-hidden="true" />
      <ol ref="list" class="viz-code-lines">
        <li
          v-for="(tokens, i) in lines"
          :key="i"
          :ref="(el) => (lineEls[i] = el as HTMLElement)"
          class="viz-code-line"
          :data-active="line === i + 1"
          :data-masked="pending && line === i + 1"
          :aria-current="line === i + 1 ? 'step' : undefined"
        >
          <span class="viz-code-ln">{{ i + 1 }}</span>
          <span v-if="pending && line === i + 1" class="lt-sr-only">这一行先遮住，预测后揭晓</span>
          <span class="viz-code-src" :aria-hidden="pending && line === i + 1">
            <span
              v-for="(token, k) in tokens"
              :key="k"
              :class="token.kind && `viz-tk-${token.kind}`"
              :data-color="token.color"
              >{{ token.text }}</span
            >
          </span>
        </li>
      </ol>
    </div>
  </LtCard>
</template>

<style scoped>
.viz-code-body {
  position: relative;
  margin: 10px -8px 0;
  overflow-x: auto;
  font: 12.5px / 22px var(--lt-font-mono);
}

.viz-code-lines {
  position: relative;
  z-index: 1;
  margin: 0;
  padding: 0;
  list-style: none;
}

.viz-code-line {
  display: flex;
  padding: 0 8px;
  white-space: pre;
}

.viz-code-ln {
  flex: none;
  width: 18px;
  margin-right: 12px;
  color: var(--lt-label-3);
  font-variant-numeric: tabular-nums;
  text-align: right;
  transition: color var(--lt-dur-fade) var(--lt-ease-fade);
}

.viz-code-line[data-active='true'] .viz-code-ln {
  color: var(--lt-accent);
}

.viz-code[data-pending='true'] .viz-code-line[data-active='true'] .viz-code-ln {
  color: var(--lt-purple-text);
}

.viz-code-src {
  transition:
    filter var(--lt-dur-fade) var(--lt-ease-fade),
    opacity var(--lt-dur-fade) var(--lt-ease-fade);
}

.viz-code-line[data-masked='true'] .viz-code-src {
  filter: blur(4px);
  opacity: 0.6;
  user-select: none;
}

.viz-code-bar {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 22px;
  border-radius: 7px;
  background: var(--lt-accent-soft);
  transform-origin: left center;
  transition: background-color var(--lt-dur-fade) var(--lt-ease-fade);
}

.viz-code[data-pending='true'] .viz-code-bar {
  background: var(--lt-purple-soft);
}

.viz-tk-keyword {
  color: var(--lt-syn-keyword);
}

.viz-tk-type {
  color: var(--lt-syn-type);
}

.viz-tk-number {
  color: var(--lt-syn-number);
}

.viz-tk-comment {
  color: var(--lt-syn-comment);
}

.viz-tk-var {
  color: var(--var-color);
  font-weight: 600;
}
</style>
