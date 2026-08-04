<template>
  <section class="courseware" data-testid="courseware" tabindex="0" aria-label="ArrayQueue 调整容量互动课件，使用左右方向键切换步骤" @keydown.left.prevent="previous" @keydown.right.prevent="next">
    <div class="courseware-heading"><div><span class="courseware-kicker">互动课件</span><h3>ArrayQueue 调整容量时的线性化复制</h3></div><span class="courseware-progress">步骤 {{ currentStep + 1 }} / {{ states.length }}</span></div>
    <p class="courseware-description">{{ currentState.description }}</p>
    <div class="arrays" aria-live="polite">
      <div class="array-shell"><div class="array-meta"><span>旧数组容量为 {{ visualization.previousCapacity }}</span><span>j = {{ visualization.headIndex }}</span><span>n = {{ visualization.elements.length }}</span></div><div class="array-values" data-testid="old-array-values"><div v-for="(value, index) in oldValues" :key="index" class="array-cell" :class="{ 'array-cell--active': currentState.copying && value !== null, 'array-cell--empty': value === null }"><strong>{{ value ?? '·' }}</strong><small>a[{{ index }}]</small></div></div></div>
      <div class="array-shell"><div class="array-meta"><span>新数组容量为 {{ nextCapacity }}</span><span>j = {{ currentState.resetHead ? 0 : '待切换' }}</span></div><div class="array-values" data-testid="new-array-values"><div v-for="(value, index) in newValues" :key="index" class="array-cell" :class="{ 'array-cell--active': currentState.copying && value !== null, 'array-cell--empty': value === null }"><strong>{{ value ?? '·' }}</strong><small>b[{{ index }}]</small></div></div></div>
    </div>
    <p v-if="currentState.copied" class="logical-order">逻辑 FIFO 顺序：{{ visualization.elements.join(' → ') }}</p>
    <div class="courseware-controls" role="group" aria-label="课件步骤控制"><el-button data-testid="previous-step" :disabled="currentStep === 0" @click="previous">上一步</el-button><el-button data-testid="next-step" type="primary" :disabled="currentStep === states.length - 1" @click="next">下一步</el-button></div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ArrayQueueResizeCourseware } from '@/api/course'

interface ResizeState { description: string; copying: boolean; copied: boolean; resetHead: boolean }
const props = defineProps<{ visualization: ArrayQueueResizeCourseware }>()
const currentStep = ref(0)
const nextCapacity = computed(() => Math.max(1, props.visualization.elements.length * 2))
const states = computed<ResizeState[]>(() => [
  { description: `旧数组容量为 ${props.visualization.previousCapacity}，j = ${props.visualization.headIndex}，队列跨越数组末端；本切片只讨论复制后的线性化布局。`, copying: false, copied: false, resetHead: false },
  { description: `分配容量为 max(1, 2n) = ${nextCapacity.value} 的新数组 b，尚未改变旧数组中的逻辑队列。`, copying: false, copied: false, resetHead: false },
  { description: `按逻辑顺序复制：b[k] = a[(j+k) mod capacity]。即使旧数组回绕，A 到 E 仍按 FIFO 顺序写入 b[0] 到 b[4]。`, copying: true, copied: true, resetHead: false },
  { description: '令 a 指向新数组 b，并将 j 重置为 0；队列从新数组开头连续排列，逻辑顺序不变。', copying: false, copied: true, resetHead: true },
])
const currentState = computed(() => states.value[currentStep.value])
const oldValues = computed<Array<string | null>>(() => {
  const result = Array<string | null>(props.visualization.previousCapacity).fill(null)
  props.visualization.elements.forEach((element, index) => { result[(props.visualization.headIndex + index) % props.visualization.previousCapacity] = element })
  return result
})
const newValues = computed<Array<string | null>>(() => {
  const result = Array<string | null>(nextCapacity.value).fill(null)
  if (currentState.value.copied) props.visualization.elements.forEach((element, index) => { result[index] = element })
  return result
})
function previous() { currentStep.value = Math.max(0, currentStep.value - 1) }
function next() { currentStep.value = Math.min(states.value.length - 1, currentStep.value + 1) }
</script>

<style scoped>
.courseware { padding: 20px; border: 1px solid var(--lp-border); border-radius: var(--lp-radius); background: var(--lp-surface-soft); outline: none; }.courseware:focus-visible { box-shadow: 0 0 0 3px var(--lp-primary-soft); border-color: var(--lp-primary); }.courseware-heading, .array-meta, .courseware-controls { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.courseware-kicker { color: var(--lp-primary); font-size: 12px; font-weight: 700; }.courseware h3 { margin: 3px 0 0; color: var(--lp-text); font-size: 17px; }.courseware-progress, .array-meta { color: var(--lp-text-secondary); font-size: 13px; }.courseware-description { min-height: 44px; margin: 16px 0; color: var(--lp-text-secondary); line-height: 1.65; }.arrays { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }.array-shell { padding: 16px; overflow-x: auto; border: 1px solid var(--lp-border); border-radius: var(--lp-radius); background: var(--lp-surface); }.array-meta, .array-values, .logical-order { min-width: 420px; }.array-meta { margin-bottom: 10px; }.array-values { display: grid; grid-template-columns: repeat(v-bind('visualization.previousCapacity'), minmax(64px, 1fr)); gap: 8px; }.array-shell:last-child .array-values { grid-template-columns: repeat(v-bind('nextCapacity'), minmax(64px, 1fr)); }.array-cell { display: grid; min-height: 66px; place-items: center; align-content: center; gap: 5px; border: 1px solid var(--lp-border-strong); border-radius: 6px; color: var(--lp-text); background: var(--lp-surface); transition: background-color 180ms ease, border-color 180ms ease, transform 180ms ease; }.array-cell--active { border-color: var(--lp-primary); background: var(--lp-primary-soft); transform: translateY(-2px); }.array-cell--empty, .array-cell small { color: var(--lp-text-muted); }.array-cell small { font-size: 11px; }.logical-order { margin: 14px 0 0; color: var(--lp-text-secondary); font-weight: 600; }.courseware-controls { justify-content: flex-end; margin-top: 16px; }@media (max-width: 767px) { .courseware { padding: 16px; }.courseware-heading { align-items: flex-start; flex-direction: column; }.arrays { grid-template-columns: 1fr; }.courseware-controls { justify-content: stretch; }.courseware-controls .el-button { flex: 1; margin: 0; } }@media (prefers-reduced-motion: reduce) { .array-cell { transition: none; } }
</style>
