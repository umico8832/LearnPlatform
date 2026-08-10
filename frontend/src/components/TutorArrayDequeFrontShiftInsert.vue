<template>
  <section class="courseware" data-testid="courseware" tabindex="0" aria-label="ArrayDeque 向较近前端搬移互动课件，使用左右方向键切换步骤" @keydown.left.prevent="previous" @keydown.right.prevent="next">
    <div class="courseware-heading"><div><span class="courseware-kicker">互动课件</span><h3>ArrayDeque 向较近端搬移</h3></div><span class="courseware-progress">步骤 {{ currentStep + 1 }} / {{ states.length }}</span></div>
    <p class="courseware-description">{{ currentState.description }}</p>
    <div class="array-shell" aria-live="polite">
      <div class="array-meta"><span>capacity = {{ visualization.capacity }}</span><span>j = {{ currentState.headIndex }}</span><span>n = {{ currentState.size }}</span><span>add({{ visualization.insertIndex }}, {{ visualization.insertValue }})</span></div>
      <div class="array-values" data-testid="array-values">
        <div v-for="(value, index) in currentState.values" :key="index" class="array-cell" :class="{ 'array-cell--active': currentState.activeIndexes.includes(index), 'array-cell--empty': value === null }"><strong>{{ value ?? '·' }}</strong><small>a[{{ index }}]</small></div>
      </div>
      <p v-if="currentState.showOrder" class="logical-order">逻辑 List 顺序：{{ currentState.logicalElements.join(' → ') }}</p>
    </div>
    <div class="courseware-controls" role="group" aria-label="课件步骤控制"><el-button data-testid="previous-step" :disabled="currentStep === 0" @click="previous">上一步</el-button><el-button data-testid="next-step" type="primary" :disabled="currentStep === states.length - 1" @click="next">下一步</el-button></div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ArrayDequeFrontShiftInsertCourseware } from '@/api/course'

interface DequeState { values: Array<string | null>; headIndex: number; size: number; logicalElements: string[]; activeIndexes: number[]; description: string; showOrder: boolean }
const props = defineProps<{ visualization: ArrayDequeFrontShiftInsertCourseware }>()
const currentStep = ref(0)
const states = computed<DequeState[]>(() => {
  const { capacity, headIndex, elements, insertIndex, insertValue } = props.visualization
  const values = Array<string | null>(capacity).fill(null)
  elements.forEach((element, logicalIndex) => { values[(headIndex + logicalIndex) % capacity] = element })
  const nextHeadIndex = (headIndex - 1 + capacity) % capacity
  const result: DequeState[] = [
    { values: [...values], headIndex, size: elements.length, logicalElements: [...elements], activeIndexes: [headIndex + insertIndex], description: `i = ${insertIndex} < n / 2 = ${elements.length / 2}，插入位置更靠近逻辑前端；只搬移前缀中的 ${insertIndex} 个元素。`, showOrder: true },
    { values: [...values], headIndex: nextHeadIndex, size: elements.length, logicalElements: [...elements], activeIndexes: [nextHeadIndex], description: `先将 j 从 ${headIndex} 回绕到 ${nextHeadIndex}，为逻辑前端腾出一个物理槽位。`, showOrder: false },
  ]
  for (let logicalIndex = 0; logicalIndex < insertIndex; logicalIndex += 1) {
    const sourceIndex = (headIndex + logicalIndex) % capacity
    const destinationIndex = (nextHeadIndex + logicalIndex) % capacity
    values[destinationIndex] = values[sourceIndex]
    values[sourceIndex] = null
    result.push({ values: [...values], headIndex: nextHeadIndex, size: elements.length, logicalElements: [...elements], activeIndexes: [sourceIndex, destinationIndex], description: `将 ${elements[logicalIndex]} 从 a[${sourceIndex}] 移到 a[${destinationIndex}]；只移动插入点之前的前缀。`, showOrder: false })
  }
  const insertPhysicalIndex = (nextHeadIndex + insertIndex) % capacity
  values[insertPhysicalIndex] = insertValue
  result.push({ values: [...values], headIndex: nextHeadIndex, size: elements.length + 1, logicalElements: [...elements.slice(0, insertIndex), insertValue, ...elements.slice(insertIndex)], activeIndexes: [insertPhysicalIndex], description: `写入 a[${insertPhysicalIndex}] = ${insertValue}，令 n = ${elements.length + 1}；前端分支插入完成。`, showOrder: true })
  return result
})
const currentState = computed(() => states.value[currentStep.value])
function previous() { currentStep.value = Math.max(0, currentStep.value - 1) }
function next() { currentStep.value = Math.min(states.value.length - 1, currentStep.value + 1) }
</script>

<style scoped>
.courseware { padding: 20px; border: 1px solid var(--lp-border); border-radius: var(--lp-radius); background: var(--lp-surface-soft); outline: none; }.courseware:focus-visible { box-shadow: 0 0 0 3px var(--lp-primary-soft); border-color: var(--lp-primary); }.courseware-heading, .array-meta, .courseware-controls { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.courseware-kicker { color: var(--lp-primary); font-size: 12px; font-weight: 700; }.courseware h3 { margin: 3px 0 0; color: var(--lp-text); font-size: 17px; }.courseware-progress, .array-meta { color: var(--lp-text-secondary); font-size: 13px; }.courseware-description { min-height: 44px; margin: 16px 0; color: var(--lp-text-secondary); line-height: 1.65; }.array-shell { padding: 16px; overflow-x: auto; border: 1px solid var(--lp-border); border-radius: var(--lp-radius); background: var(--lp-surface); }.array-meta, .array-values { min-width: 420px; }.array-meta { margin-bottom: 10px; flex-wrap: wrap; }.array-values { display: grid; grid-template-columns: repeat(v-bind('visualization.capacity'), minmax(64px, 1fr)); gap: 8px; }.array-cell { display: grid; min-height: 66px; place-items: center; align-content: center; gap: 5px; border: 1px solid var(--lp-border-strong); border-radius: 6px; color: var(--lp-text); background: var(--lp-surface); transition: background-color 180ms ease, border-color 180ms ease, transform 180ms ease; }.array-cell--active { border-color: var(--lp-primary); background: var(--lp-primary-soft); transform: translateY(-2px); }.array-cell--empty, .array-cell small { color: var(--lp-text-muted); }.array-cell small { font-size: 11px; }.logical-order { min-width: 420px; margin: 14px 0 0; color: var(--lp-text-secondary); font-weight: 600; }.courseware-controls { justify-content: flex-end; margin-top: 16px; }@media (max-width: 767px) { .courseware { padding: 16px; }.courseware-heading { align-items: flex-start; flex-direction: column; }.courseware-controls { justify-content: stretch; }.courseware-controls .el-button { flex: 1; margin: 0; } }@media (prefers-reduced-motion: reduce) { .array-cell { transition: none; } }
</style>
