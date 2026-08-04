<template>
  <section
    class="courseware"
    data-testid="courseware"
    tabindex="0"
    aria-label="ArrayQueue 循环数组互动课件，使用左右方向键切换步骤"
    @keydown.left.prevent="previous"
    @keydown.right.prevent="next"
  >
    <div class="courseware-heading">
      <div><span class="courseware-kicker">互动课件</span><h3>ArrayQueue 的循环数组表示</h3></div>
      <span class="courseware-progress">步骤 {{ currentStep + 1 }} / {{ states.length }}</span>
    </div>
    <p class="courseware-description">{{ currentState.description }}</p>
    <div class="array-shell" aria-live="polite">
      <div class="array-meta"><span>capacity = {{ visualization.capacity }}</span><span>j = {{ visualization.headIndex }}</span><span>n = {{ visualization.elements.length }}</span></div>
      <div class="array-values" data-testid="array-values">
        <div
          v-for="(value, index) in values"
          :key="index"
          class="array-cell"
          :class="{ 'array-cell--active': currentState.activeIndexes.includes(index), 'array-cell--empty': value === null }"
        >
          <strong>{{ value ?? '·' }}</strong><small>a[{{ index }}]</small>
        </div>
      </div>
      <p v-if="currentState.showOrder" class="logical-order">逻辑 FIFO 顺序：{{ visualization.elements.join(' → ') }}</p>
    </div>
    <div class="courseware-controls" role="group" aria-label="课件步骤控制">
      <el-button data-testid="previous-step" :disabled="currentStep === 0" @click="previous">上一步</el-button>
      <el-button data-testid="next-step" type="primary" :disabled="currentStep === states.length - 1" @click="next">下一步</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ArrayQueueRepresentationCourseware } from '@/api/course'

interface QueueState { activeIndexes: number[]; description: string; showOrder: boolean }
const props = defineProps<{ visualization: ArrayQueueRepresentationCourseware }>()
const currentStep = ref(0)
const values = computed<Array<string | null>>(() => {
  const result = Array<string | null>(props.visualization.capacity).fill(null)
  props.visualization.elements.forEach((element, logicalIndex) => {
    result[(props.visualization.headIndex + logicalIndex) % props.visualization.capacity] = element
  })
  return result
})
const states = computed<QueueState[]>(() => [
  { activeIndexes: [props.visualization.headIndex], description: `j = ${props.visualization.headIndex}，n = ${props.visualization.elements.length}。队首不必位于下标 0；j 指向当前第一个逻辑元素。`, showOrder: false },
  ...props.visualization.elements.map((element, logicalIndex) => {
    const physicalIndex = (props.visualization.headIndex + logicalIndex) % props.visualization.capacity
    return { activeIndexes: [physicalIndex], description: `${element} 位于 (${props.visualization.headIndex}+${logicalIndex}) mod ${props.visualization.capacity} = ${physicalIndex}。`, showOrder: false }
  }),
  { activeIndexes: props.visualization.elements.map((_, logicalIndex) => (props.visualization.headIndex + logicalIndex) % props.visualization.capacity), description: '物理下标可以回绕，但逻辑顺序始终从队首 j 开始。', showOrder: true },
])
const currentState = computed(() => states.value[currentStep.value])
function previous() { currentStep.value = Math.max(0, currentStep.value - 1) }
function next() { currentStep.value = Math.min(states.value.length - 1, currentStep.value + 1) }
</script>

<style scoped>
.courseware { padding: 20px; border: 1px solid var(--lp-border); border-radius: var(--lp-radius); background: var(--lp-surface-soft); outline: none; }
.courseware:focus-visible { box-shadow: 0 0 0 3px var(--lp-primary-soft); border-color: var(--lp-primary); }
.courseware-heading, .array-meta, .courseware-controls { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.courseware-kicker { color: var(--lp-primary); font-size: 12px; font-weight: 700; }.courseware h3 { margin: 3px 0 0; color: var(--lp-text); font-size: 17px; }.courseware-progress, .array-meta { color: var(--lp-text-secondary); font-size: 13px; }.courseware-description { min-height: 44px; margin: 16px 0; color: var(--lp-text-secondary); line-height: 1.65; }
.array-shell { padding: 16px; overflow-x: auto; border: 1px solid var(--lp-border); border-radius: var(--lp-radius); background: var(--lp-surface); }.array-meta, .array-values { min-width: 420px; }.array-meta { margin-bottom: 10px; }.array-values { display: grid; grid-template-columns: repeat(v-bind('visualization.capacity'), minmax(64px, 1fr)); gap: 8px; }.array-cell { display: grid; min-height: 66px; place-items: center; align-content: center; gap: 5px; border: 1px solid var(--lp-border-strong); border-radius: 6px; color: var(--lp-text); background: var(--lp-surface); transition: background-color 180ms ease, border-color 180ms ease, transform 180ms ease; }.array-cell--active { border-color: var(--lp-primary); background: var(--lp-primary-soft); transform: translateY(-2px); }.array-cell--empty, .array-cell small { color: var(--lp-text-muted); }.array-cell small { font-size: 11px; }.logical-order { min-width: 420px; margin: 14px 0 0; color: var(--lp-text-secondary); font-weight: 600; }.courseware-controls { justify-content: flex-end; margin-top: 16px; }
@media (max-width: 767px) { .courseware { padding: 16px; }.courseware-heading { align-items: flex-start; flex-direction: column; }.courseware-controls { justify-content: stretch; }.courseware-controls .el-button { flex: 1; margin: 0; } }
@media (prefers-reduced-motion: reduce) { .array-cell { transition: none; } }
</style>
