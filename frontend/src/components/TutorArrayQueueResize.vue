<template>
  <section
    class="courseware lt"
    data-appearance="dark"
    data-testid="courseware"
    tabindex="0"
    aria-label="ArrayQueue 调整容量互动课件，使用左右方向键切换步骤"
    @keydown.left.prevent="previous"
    @keydown.right.prevent="next"
  >
    <div class="courseware-heading">
      <div>
        <span class="courseware-kicker">互动课件</span>
        <h3>ArrayQueue 调整容量时的线性化复制</h3>
      </div>
      <span class="courseware-progress">步骤 {{ currentStep + 1 }} / {{ states.length }}</span>
    </div>
    <p class="courseware-description">{{ currentState.description }}</p>
    <div class="arrays" aria-live="polite">
      <div class="array-shell">
        <div class="array-meta">
          <span>旧数组容量为 {{ visualization.previousCapacity }}</span
          ><span>j = {{ visualization.headIndex }}</span
          ><span>n = {{ visualization.elements.length }}</span>
        </div>
        <div class="array-values" data-testid="old-array-values">
          <div
            v-for="(value, index) in oldValues"
            :key="index"
            class="array-cell"
            :class="{
              'array-cell--active': currentState.copying && value !== null,
              'array-cell--empty': value === null,
            }"
          >
            <strong>{{ value ?? '·' }}</strong
            ><small>a[{{ index }}]</small>
          </div>
        </div>
      </div>
      <div class="array-shell">
        <div class="array-meta">
          <span>新数组容量为 {{ nextCapacity }}</span
          ><span>j = {{ currentState.resetHead ? 0 : '待切换' }}</span>
        </div>
        <div class="array-values" data-testid="new-array-values">
          <div
            v-for="(value, index) in newValues"
            :key="index"
            class="array-cell"
            :class="{
              'array-cell--active': currentState.copying && value !== null,
              'array-cell--empty': value === null,
            }"
          >
            <strong>{{ value ?? '·' }}</strong
            ><small>b[{{ index }}]</small>
          </div>
        </div>
      </div>
    </div>
    <p v-if="currentState.copied" class="logical-order">逻辑 FIFO 顺序：{{ visualization.elements.join(' → ') }}</p>
    <div class="courseware-controls" role="group" aria-label="课件步骤控制">
      <LtButton data-testid="previous-step" :disabled="currentStep === 0" @click="previous">上一步</LtButton
      ><LtButton data-testid="next-step" variant="primary" :disabled="currentStep === states.length - 1" @click="next"
        >下一步</LtButton
      >
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { LtButton } from '@/components/living'
import type { ArrayQueueResizeCourseware } from '@/api/course'

interface ResizeState {
  description: string
  copying: boolean
  copied: boolean
  resetHead: boolean
}
const props = defineProps<{ visualization: ArrayQueueResizeCourseware }>()
const currentStep = ref(0)
const nextCapacity = computed(() => Math.max(1, props.visualization.elements.length * 2))
const states = computed<ResizeState[]>(() => [
  {
    description: `旧数组容量为 ${props.visualization.previousCapacity}，j = ${props.visualization.headIndex}，队列跨越数组末端；本切片只讨论复制后的线性化布局。`,
    copying: false,
    copied: false,
    resetHead: false,
  },
  {
    description: `分配容量为 max(1, 2n) = ${nextCapacity.value} 的新数组 b，尚未改变旧数组中的逻辑队列。`,
    copying: false,
    copied: false,
    resetHead: false,
  },
  {
    description: `按逻辑顺序复制：b[k] = a[(j+k) mod capacity]。即使旧数组回绕，A 到 E 仍按 FIFO 顺序写入 b[0] 到 b[4]。`,
    copying: true,
    copied: true,
    resetHead: false,
  },
  {
    description: '令 a 指向新数组 b，并将 j 重置为 0；队列从新数组开头连续排列，逻辑顺序不变。',
    copying: false,
    copied: true,
    resetHead: true,
  },
])
const currentState = computed(() => states.value[currentStep.value])
const oldValues = computed<Array<string | null>>(() => {
  const result = Array<string | null>(props.visualization.previousCapacity).fill(null)
  props.visualization.elements.forEach((element, index) => {
    result[(props.visualization.headIndex + index) % props.visualization.previousCapacity] = element
  })
  return result
})
const newValues = computed<Array<string | null>>(() => {
  const result = Array<string | null>(nextCapacity.value).fill(null)
  if (currentState.value.copied)
    props.visualization.elements.forEach((element, index) => {
      result[index] = element
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
.arrays {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lt-space-3);
}
.array-shell {
  padding: var(--lt-space-4);
  overflow-x: auto;
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-surface);
}
.array-meta,
.array-values,
.logical-order {
  min-width: 100%;
}
.array-meta {
  margin-bottom: var(--lt-space-2);
}
.array-values {
  display: grid;
  grid-template-columns: repeat(v-bind('visualization.previousCapacity'), minmax(var(--lt-control-lg), 1fr));
  gap: var(--lt-space-2);
}
.array-shell:last-child .array-values {
  grid-template-columns: repeat(v-bind('nextCapacity'), minmax(var(--lt-control-lg), 1fr));
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
  margin: var(--lt-space-3) 0 0;
  color: var(--lt-label-2);
  font-weight: 600;
}
.courseware-controls {
  justify-content: flex-end;
  margin-top: var(--lt-space-4);
}
</style>
