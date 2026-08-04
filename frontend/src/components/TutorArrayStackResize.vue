<template>
  <section
    class="courseware"
    data-testid="courseware"
    tabindex="0"
    aria-label="ArrayStack 容量调整互动课件，使用左右方向键切换步骤"
    @keydown.left.prevent="previous"
    @keydown.right.prevent="next"
  >
    <div class="courseware-heading">
      <div><span class="courseware-kicker">互动课件</span><h3>ArrayStack 的容量调整</h3></div>
      <span class="courseware-progress">步骤 {{ currentStep + 1 }} / {{ states.length }}</span>
    </div>
    <p class="courseware-description">{{ currentState.description }}</p>
    <div class="arrays" aria-live="polite">
      <ArraySnapshot label="旧数组 a" array-name="a" :capacity="visualization.previousCapacity" :values="currentState.oldValues" :active-indexes="currentState.activeIndexes" />
      <ArraySnapshot label="新数组 b" array-name="b" :capacity="nextCapacity" :values="currentState.newValues" :active-indexes="currentState.activeIndexes" />
    </div>
    <div class="courseware-controls" role="group" aria-label="课件步骤控制">
      <el-button data-testid="previous-step" :disabled="currentStep === 0" @click="previous">上一步</el-button>
      <el-button data-testid="next-step" type="primary" :disabled="currentStep === states.length - 1" @click="next">下一步</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, ref, type PropType } from 'vue'
import type { ArrayStackResizeCourseware } from '@/api/course'

interface ResizeState { oldValues: Array<string | null>; newValues: Array<string | null>; activeIndexes: number[]; description: string }
const ArraySnapshot = defineComponent({
  props: {
    label: { type: String, required: true }, arrayName: { type: String, required: true }, capacity: { type: Number, required: true },
    values: { type: Array as PropType<Array<string | null>>, required: true }, activeIndexes: { type: Array as PropType<number[]>, required: true },
  },
  setup(props) {
    return () => h('div', { class: 'array-shell' }, [
      h('div', { class: 'array-meta' }, [`${props.label}：capacity = ${props.capacity}`]),
      h('div', { class: 'array-values', style: { gridTemplateColumns: `repeat(${props.capacity}, minmax(64px, 1fr))` } }, props.values.map((value, index) =>
        h('div', { class: ['array-cell', { 'array-cell--active': props.activeIndexes.includes(index), 'array-cell--empty': value === null }] }, [h('strong', value ?? '·'), h('small', `${props.arrayName}[${index}]`)]),
      )),
    ])
  },
})

const props = defineProps<{ visualization: ArrayStackResizeCourseware }>()
const currentStep = ref(0)
const nextCapacity = computed(() => Math.max(1, props.visualization.initialElements.length * 2))
const states = computed<ResizeState[]>(() => {
  const oldValues = [...props.visualization.initialElements]
  const newValues = Array<string | null>(nextCapacity.value).fill(null)
  const result: ResizeState[] = [{ oldValues: [...oldValues], newValues: [...newValues], activeIndexes: [], description: `旧数组容量为 ${props.visualization.previousCapacity}，其中 n = ${oldValues.length} 个槽位是有效元素。` }]
  result.push({ oldValues: [...oldValues], newValues: [...newValues], activeIndexes: [], description: `分配容量为 ${nextCapacity.value} 的新数组 b；容量按 max(1, 2n) 计算。` })
  oldValues.forEach((value, index) => {
    newValues[index] = value
    result.push({ oldValues: [...oldValues], newValues: [...newValues], activeIndexes: [index], description: `将 ${value} 从旧数组 a[${index}] 复制到新数组 b[${index}]，保持逻辑顺序。` })
  })
  result.push({ oldValues: [...oldValues], newValues: [...newValues], activeIndexes: [], description: `令 a 指向新数组 b；n 保持为 ${oldValues.length}，新的空闲槽位可供后续操作使用。` })
  return result
})
const currentState = computed(() => states.value[currentStep.value])
function previous() { currentStep.value = Math.max(0, currentStep.value - 1) }
function next() { currentStep.value = Math.min(states.value.length - 1, currentStep.value + 1) }
</script>

<style scoped>
.courseware { padding: 20px; border: 1px solid var(--lp-border); border-radius: var(--lp-radius); background: var(--lp-surface-soft); outline: none; }
.courseware:focus-visible { box-shadow: 0 0 0 3px var(--lp-primary-soft); border-color: var(--lp-primary); }
.courseware-heading, .courseware-controls { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.courseware-kicker { color: var(--lp-primary); font-size: 12px; font-weight: 700; }
.courseware h3 { margin: 3px 0 0; color: var(--lp-text); font-size: 17px; }.courseware-progress { color: var(--lp-text-secondary); font-size: 13px; }
.courseware-description { min-height: 44px; margin: 16px 0; color: var(--lp-text-secondary); line-height: 1.65; }.arrays { display: grid; gap: 12px; }
:deep(.array-shell) { padding: 16px; overflow-x: auto; border: 1px solid var(--lp-border); border-radius: var(--lp-radius); background: var(--lp-surface); }
:deep(.array-meta) { min-width: 420px; margin-bottom: 10px; color: var(--lp-text-secondary); font-size: 13px; }
:deep(.array-values) { display: grid; min-width: 420px; gap: 8px; }
:deep(.array-cell) { display: grid; min-height: 66px; place-items: center; align-content: center; gap: 5px; border: 1px solid var(--lp-border-strong); border-radius: 6px; color: var(--lp-text); background: var(--lp-surface); transition: background-color 180ms ease, border-color 180ms ease, transform 180ms ease; }
:deep(.array-cell--active) { border-color: var(--lp-primary); background: var(--lp-primary-soft); transform: translateY(-2px); }:deep(.array-cell--empty), :deep(.array-cell small) { color: var(--lp-text-muted); }:deep(.array-cell small) { font-size: 11px; }
.courseware-controls { justify-content: flex-end; margin-top: 16px; }
@media (max-width: 767px) { .courseware { padding: 16px; }.courseware-heading { align-items: flex-start; flex-direction: column; }.courseware-controls { justify-content: stretch; }.courseware-controls .el-button { flex: 1; margin: 0; } }
@media (prefers-reduced-motion: reduce) { :deep(.array-cell) { transition: none; } }
</style>
