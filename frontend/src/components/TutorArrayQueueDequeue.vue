<template>
  <section
    class="courseware lt"
    data-appearance="dark"
    data-testid="courseware"
    tabindex="0"
    aria-label="ArrayQueue 出队互动课件，使用左右方向键切换步骤"
    @keydown.left.prevent="previous"
    @keydown.right.prevent="next"
  >
    <div class="courseware-heading">
      <div>
        <span class="courseware-kicker">互动课件</span>
        <h3>ArrayQueue 的出队</h3>
      </div>
      <span class="courseware-progress">步骤 {{ currentStep + 1 }} / {{ states.length }}</span>
    </div>
    <p class="courseware-description">{{ currentState.description }}</p>
    <div class="array-shell" aria-live="polite">
      <div class="array-meta">
        <span>capacity = {{ visualization.capacity }}</span
        ><span>j = {{ displayedHead }}</span
        ><span>n = {{ displayedCount }}</span>
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
      <p v-if="currentState.decremented" class="logical-order">
        逻辑 FIFO 顺序：{{ visualization.elements.slice(1).join(' → ') }}
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
import type { ArrayQueueDequeueCourseware } from '@/api/course'

interface QueueState {
  activeIndexes: number[]
  description: string
  advanced: boolean
  decremented: boolean
}
const props = defineProps<{ visualization: ArrayQueueDequeueCourseware }>()
const currentStep = ref(0)
const nextHead = computed(() => (props.visualization.headIndex + 1) % props.visualization.capacity)
const states = computed<QueueState[]>(() => [
  {
    activeIndexes: [props.visualization.headIndex],
    description: `队首位于 a[${props.visualization.headIndex}]，本切片只讨论非空队列且不触发 resize 的出队。`,
    advanced: false,
    decremented: false,
  },
  {
    activeIndexes: [props.visualization.headIndex],
    description: `取出队首元素 ${props.visualization.elements[0]} 并保存；其余元素不需要搬移。`,
    advanced: false,
    decremented: false,
  },
  {
    activeIndexes: [nextHead.value],
    description: `将 j 更新为 (j+1) mod capacity = (${props.visualization.headIndex}+1) mod ${props.visualization.capacity} = ${nextHead.value}，下一个元素成为队首。`,
    advanced: true,
    decremented: false,
  },
  {
    activeIndexes: [nextHead.value],
    description: `将 n 从 ${props.visualization.elements.length} 减少到 ${props.visualization.elements.length - 1}，队列保留其余元素的 FIFO 顺序。`,
    advanced: true,
    decremented: true,
  },
])
const currentState = computed(() => states.value[currentStep.value])
const displayedHead = computed(() => (currentState.value.advanced ? nextHead.value : props.visualization.headIndex))
const displayedCount = computed(() => props.visualization.elements.length - (currentState.value.decremented ? 1 : 0))
const values = computed<Array<string | null>>(() => {
  const result = Array<string | null>(props.visualization.capacity).fill(null)
  const visibleElements = currentState.value.advanced
    ? props.visualization.elements.slice(1)
    : props.visualization.elements
  visibleElements.forEach((element, index) => {
    result[(displayedHead.value + index) % props.visualization.capacity] = element
  })
  return result
})
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
