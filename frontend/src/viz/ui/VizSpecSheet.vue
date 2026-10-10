<script setup lang="ts">
import { computed } from 'vue'
import { Check, X } from '@lucide/vue'
import { LtSheet } from '@/components/living'
import type { SceneSpec, Trace } from '../engine/types'
import { tokenizeJson } from './codeTokens'

/** Shows the pipeline behind the animation: the AI writes only the spec, the simulator computes every step. */
const props = defineProps<{ spec: SceneSpec; trace: Trace; origin: HTMLElement | null }>()
const open = defineModel<boolean>('open', { required: true })

const intro =
  'AI 导师只决定“展示什么”：用哪种结构、哪组数据、在哪里停下来提问。每一步怎么变，由确定性的模拟器算出来；播放器只负责把结果演出来。'
const checkpoints = computed(() => props.trace.steps.flatMap((step) => (step.predict ? [step.predict.id] : [])))
const tokens = computed(() =>
  tokenizeJson({ ...props.spec, pauseAt: (props.spec.pauseAt as string[] | undefined) ?? checkpoints.value }),
)
</script>

<template>
  <LtSheet v-model:open="open" title="AI 场景描述" :origin="origin">
    <p class="viz-sheet-intro">{{ intro }}</p>
    <ol class="viz-pipeline">
      <li><strong>AI 描述</strong><span>一小段 JSON</span></li>
      <li>
        <strong>模拟器</strong><span>{{ trace.steps.length }} 步 · {{ checkpoints.length }} 个检查点</span>
      </li>
      <li><strong>播放器</strong><span>弹簧动画逐帧渲染</span></li>
    </ol>
    <pre class="viz-json"><span
      v-for="(token, i) in tokens"
      :key="i"
      :class="token.kind && `viz-json-${token.kind}`"
    >{{ token.text }}</span></pre>
    <p class="viz-check" :data-ok="trace.check.ok">
      <Check v-if="trace.check.ok" :size="16" aria-hidden="true" />
      <X v-else :size="16" aria-hidden="true" />
      {{ trace.check.ok ? '模拟器自检通过' : '模拟器自检失败' }}：{{ trace.check.detail }}
    </p>
  </LtSheet>
</template>

<style scoped>
.viz-sheet-intro {
  margin: 0 0 18px;
  color: var(--lt-label-2);
  font-size: var(--lt-text-subhead);
  line-height: 1.65;
}

.viz-pipeline {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin: 0 0 16px;
  padding: 0;
  list-style: none;
}

.viz-pipeline li {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 0.5px var(--lt-separator);
}

.viz-pipeline li + li::before {
  content: '';
  position: absolute;
  top: 50%;
  left: -11px;
  width: 7px;
  height: 7px;
  margin-top: -4px;
  border-top: 1.5px solid var(--lt-label-3);
  border-right: 1.5px solid var(--lt-label-3);
  transform: rotate(45deg);
}

.viz-pipeline strong {
  font-size: var(--lt-text-footnote);
}

.viz-pipeline span {
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}

.viz-json {
  margin: 0;
  padding: 14px 16px;
  overflow: auto;
  border-radius: var(--lt-radius-md);
  background: var(--lt-code-bg);
  font: 12.5px / 1.65 var(--lt-font-mono);
}

.viz-json-key {
  color: var(--lt-syn-key);
}

.viz-json-string {
  color: var(--lt-syn-string);
}

.viz-json-number {
  color: var(--lt-syn-number);
}

.viz-check {
  display: flex;
  align-items: center;
  gap: var(--lt-space-2);
  margin: 14px 0 0;
  color: var(--lt-green-text);
  font-size: var(--lt-text-footnote);
}

.viz-check[data-ok='false'] {
  color: var(--lt-red-text);
}
</style>
