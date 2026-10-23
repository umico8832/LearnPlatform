<template>
  <section
    class="courseware lt"
    data-appearance="dark"
    data-testid="courseware"
    tabindex="0"
    aria-label="ArrayDeque 循环数组访问互动课件，使用左右方向键切换步骤"
    @keydown.left.prevent="previous"
    @keydown.right.prevent="next"
  >
    <div class="courseware-heading">
      <div>
        <span class="courseware-kicker">互动课件</span>
        <h3>ArrayDeque 的循环数组表示与访问</h3>
      </div>
      <span class="courseware-progress">步骤 {{ currentStep + 1 }} / {{ states.length }}</span>
    </div>
    <p class="courseware-description">{{ currentState.description }}</p>
    <div class="array-shell" aria-live="polite">
      <div class="array-meta">
        <span>capacity = {{ visualization.capacity }}</span
        ><span>j = {{ visualization.headIndex }}</span
        ><span>n = {{ visualization.elements.length }}</span
        ><span>get({{ visualization.accessIndex }})</span>
      </div>
      <div class="array-values" data-testid="array-values">
        <div
          v-for="(value, index) in values"
          :key="index"
          class="array-cell"
          :class="{
            'array-cell--active': currentState.activeIndexes.includes(index),
            'array-cell--empty': value === null,
          }"
        >
          <strong>{{ value ?? '·' }}</strong
          ><small>a[{{ index }}]</small>
        </div>
      </div>
      <p v-if="currentState.showOrder" class="logical-order">
        逻辑 List 顺序：{{ visualization.elements.join(' → ') }}
      </p>
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
import { computed, ref } from 'vue'
import { LtButton } from '@/components/living'
import type { ArrayDequeRepresentationCourseware } from '@/api/course'

interface DequeState {
  activeIndexes: number[]
  description: string
  showOrder: boolean
}
const props = defineProps<{ visualization: ArrayDequeRepresentationCourseware }>()
const currentStep = ref(0)
const physicalAccessIndex = computed(
  () => (props.visualization.headIndex + props.visualization.accessIndex) % props.visualization.capacity,
)
const accessedElement = computed(() => props.visualization.elements[props.visualization.accessIndex])
const values = computed<Array<string | null>>(() => {
  const result = Array<string | null>(props.visualization.capacity).fill(null)
  props.visualization.elements.forEach((element, logicalIndex) => {
    result[(props.visualization.headIndex + logicalIndex) % props.visualization.capacity] = element
  })
  return result
})
const states = computed<DequeState[]>(() => [
  {
    activeIndexes: [props.visualization.headIndex],
    description: `j = ${props.visualization.headIndex}，n = ${props.visualization.elements.length}。逻辑下标 0 从 j 开始，不要求位于物理下标 0。`,
    showOrder: false,
  },
  {
    activeIndexes: props.visualization.elements.map(
      (_, logicalIndex) => (props.visualization.headIndex + logicalIndex) % props.visualization.capacity,
    ),
    description: 'ArrayDeque 复用循环数组布局；物理下标回绕不改变逻辑 List 的先后顺序。',
    showOrder: true,
  },
  {
    activeIndexes: [physicalAccessIndex.value],
    description: `get(${props.visualization.accessIndex}) 访问逻辑元素 ${accessedElement.value}：(${props.visualization.headIndex}+${props.visualization.accessIndex}) mod ${props.visualization.capacity} = ${physicalAccessIndex.value}，直接读取 a[${physicalAccessIndex.value}]。`,
    showOrder: true,
  },
])
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
.array-meta,
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
.courseware-progress,
.array-meta {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.courseware-description {
  min-height: var(--lt-control-lg);
  margin: var(--lt-space-4) 0;
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
}
.array-shell {
  padding: var(--lt-space-4);
  overflow-x: auto;
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-surface);
}
.array-meta,
.array-values {
  min-width: 100%;
}
.array-meta {
  margin-bottom: var(--lt-space-2);
  flex-wrap: wrap;
}
.array-values {
  display: grid;
  grid-template-columns: repeat(v-bind('visualization.capacity'), minmax(var(--lt-control-lg), 1fr));
  gap: var(--lt-space-2);
}
.array-cell {
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
.array-cell--active {
  border-color: var(--lt-accent);
  background: var(--lt-accent-soft);
}
.array-cell--empty,
.array-cell small {
  color: var(--lt-label-3);
}
.array-cell small {
  font-size: var(--lt-text-caption2);
}
.logical-order {
  min-width: 100%;
  margin: var(--lt-space-3) 0 0;
  color: var(--lt-label-2);
  font-weight: 600;
}
.courseware-controls {
  justify-content: flex-end;
  margin-top: var(--lt-space-4);
}
</style>
