<template>
  <section
    class="courseware lt"
    data-appearance="dark"
    data-testid="courseware"
    tabindex="0"
    aria-label="ArrayStack 容量调整互动课件，使用左右方向键切换步骤"
    @keydown.left.prevent="previous"
    @keydown.right.prevent="next"
  >
    <div class="courseware-heading">
      <div>
        <span class="courseware-kicker">互动课件</span>
        <h3>ArrayStack 的容量调整</h3>
      </div>
      <span class="courseware-progress">步骤 {{ currentStep + 1 }} / {{ states.length }}</span>
    </div>
    <p class="courseware-description">{{ currentState.description }}</p>
    <div class="arrays" aria-live="polite">
      <ArraySnapshot
        label="旧数组 a"
        array-name="a"
        :capacity="visualization.previousCapacity"
        :values="currentState.oldValues"
        :active-indexes="currentState.activeIndexes"
      />
      <ArraySnapshot
        label="新数组 b"
        array-name="b"
        :capacity="nextCapacity"
        :values="currentState.newValues"
        :active-indexes="currentState.activeIndexes"
      />
    </div>
    <div class="courseware-controls" role="group" aria-label="课件步骤控制">
      <LtButton data-testid="previous-step" :disabled="currentStep === 0" @click="previous">上一步</LtButton>
      <LtButton data-testid="next-step" variant="primary" :disabled="currentStep === states.length - 1" @click="next"
        >下一步</LtButton
      >
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, ref, type PropType } from 'vue'
import { LtButton } from '@/components/living'
import type { ArrayStackResizeCourseware } from '@/api/course'

interface ResizeState {
  oldValues: Array<string | null>
  newValues: Array<string | null>
  activeIndexes: number[]
  description: string
}
const ArraySnapshot = defineComponent({
  props: {
    label: { type: String, required: true },
    arrayName: { type: String, required: true },
    capacity: { type: Number, required: true },
    values: { type: Array as PropType<Array<string | null>>, required: true },
    activeIndexes: { type: Array as PropType<number[]>, required: true },
  },
  setup(props) {
    return () =>
      h('div', { class: 'array-shell' }, [
        h('div', { class: 'array-meta' }, [`${props.label}：capacity = ${props.capacity}`]),
        h(
          'div',
          {
            class: 'array-values',
            style: { gridTemplateColumns: `repeat(${props.capacity}, minmax(var(--lt-control-lg), 1fr))` },
          },
          props.values.map((value, index) =>
            h(
              'div',
              {
                class: [
                  'array-cell',
                  { 'array-cell--active': props.activeIndexes.includes(index), 'array-cell--empty': value === null },
                ],
              },
              [h('strong', value ?? '·'), h('small', `${props.arrayName}[${index}]`)],
            ),
          ),
        ),
      ])
  },
})

const props = defineProps<{ visualization: ArrayStackResizeCourseware }>()
const currentStep = ref(0)
const nextCapacity = computed(() => Math.max(1, props.visualization.initialElements.length * 2))
const states = computed<ResizeState[]>(() => {
  const oldValues = [...props.visualization.initialElements]
  const newValues = Array<string | null>(nextCapacity.value).fill(null)
  const result: ResizeState[] = [
    {
      oldValues: [...oldValues],
      newValues: [...newValues],
      activeIndexes: [],
      description: `旧数组容量为 ${props.visualization.previousCapacity}，其中 n = ${oldValues.length} 个槽位是有效元素。`,
    },
  ]
  result.push({
    oldValues: [...oldValues],
    newValues: [...newValues],
    activeIndexes: [],
    description: `分配容量为 ${nextCapacity.value} 的新数组 b；容量按 max(1, 2n) 计算。`,
  })
  oldValues.forEach((value, index) => {
    newValues[index] = value
    result.push({
      oldValues: [...oldValues],
      newValues: [...newValues],
      activeIndexes: [index],
      description: `将 ${value} 从旧数组 a[${index}] 复制到新数组 b[${index}]，保持逻辑顺序。`,
    })
  })
  result.push({
    oldValues: [...oldValues],
    newValues: [...newValues],
    activeIndexes: [],
    description: `令 a 指向新数组 b；n 保持为 ${oldValues.length}，新的空闲槽位可供后续操作使用。`,
  })
  return result
})
const currentState = computed(() => states.value[currentStep.value])
function previous() {
  currentStep.value = Math.max(0, currentStep.value - 1)
}
function next() {
  currentStep.value = Math.min(states.value.length - 1, currentStep.value + 1)
}
</script>

<style scoped>
.courseware {
  padding: var(--lt-space-5);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-well);
  outline: none;
}
.courseware:focus-visible {
  box-shadow: var(--lt-shadow-accent);
  border-color: var(--lt-accent);
}
.courseware-heading,
.courseware-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-3);
}
.courseware-kicker {
  color: var(--lt-accent);
  font-size: var(--lt-text-caption);
  font-weight: 700;
}
.courseware h3 {
  margin: var(--lt-space-1) 0 0;
  color: var(--lt-label);
  font-size: var(--lt-text-headline);
}
.courseware-progress {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.courseware-description {
  min-height: var(--lt-control-lg);
  margin: var(--lt-space-4) 0;
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
}
.arrays {
  display: grid;
  gap: var(--lt-space-3);
}
:deep(.array-shell) {
  padding: var(--lt-space-4);
  overflow-x: auto;
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-surface);
}
:deep(.array-meta) {
  min-width: 100%;
  margin-bottom: var(--lt-space-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
:deep(.array-values) {
  display: grid;
  min-width: 100%;
  gap: var(--lt-space-2);
}
:deep(.array-cell) {
  display: grid;
  min-height: var(--lt-control-lg);
  place-items: center;
  align-content: center;
  gap: var(--lt-space-1);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-sm);
  color: var(--lt-label);
  background: var(--lt-surface);
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    border-color var(--lt-dur-fade) var(--lt-ease-fade),
    transform var(--lt-dur-fade) var(--lt-ease-fade);
}
:deep(.array-cell--active) {
  border-color: var(--lt-accent);
  background: var(--lt-accent-soft);
}
:deep(.array-cell--empty),
:deep(.array-cell small) {
  color: var(--lt-label-3);
}
:deep(.array-cell small) {
  font-size: var(--lt-text-caption2);
}
.courseware-controls {
  justify-content: flex-end;
  margin-top: var(--lt-space-4);
}
</style>
