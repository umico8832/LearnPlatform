<template>
  <section
    class="courseware lt"
    data-appearance="dark"
    data-testid="courseware"
    tabindex="0"
    aria-label="ArrayStack 按位插入互动课件，使用左右方向键切换步骤"
    @keydown.left.prevent="previous"
    @keydown.right.prevent="next"
  >
    <div class="courseware-heading">
      <div>
        <span class="courseware-kicker">互动课件</span>
        <h3>ArrayStack 的按位插入</h3>
      </div>
      <span class="courseware-progress">步骤 {{ currentStep + 1 }} / {{ states.length }}</span>
    </div>

    <p class="courseware-description">{{ currentState.description }}</p>

    <div class="array-shell" aria-live="polite">
      <div class="array-meta">
        <span>capacity = {{ visualization.capacity }}</span
        ><span>n = {{ currentState.size }}</span>
      </div>
      <div class="array-values" data-testid="array-values">
        <div
          v-for="(value, index) in currentState.values"
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
    </div>

    <div class="courseware-controls" role="group" aria-label="课件步骤控制">
      <LtButton data-testid="previous-step" :disabled="currentStep === 0" @click="previous">上一步</LtButton>
      <LtButton data-testid="next-step" variant="primary" :disabled="currentStep === states.length - 1" @click="next">
        下一步
      </LtButton>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { LtButton } from '@/components/living'
import type { ArrayStackInsertionCourseware } from '@/api/course'

interface ArrayState {
  values: Array<string | null>
  size: number
  activeIndexes: number[]
  description: string
}

const props = defineProps<{ visualization: ArrayStackInsertionCourseware }>()
const currentStep = ref(0)

const states = computed<ArrayState[]>(() => {
  const { capacity, initialElements, insertIndex, insertValue } = props.visualization
  const values: Array<string | null> = Array.from({ length: capacity }, (_, index) => initialElements[index] ?? null)
  const result: ArrayState[] = [
    {
      values: [...values],
      size: initialElements.length,
      activeIndexes: [insertIndex],
      description: `准备插入 ${insertValue} 到 a[${insertIndex}]；先从末尾向右搬移，避免覆盖未处理元素。`,
    },
  ]

  for (let source = initialElements.length - 1; source >= insertIndex; source -= 1) {
    values[source + 1] = values[source]
    values[source] = null
    result.push({
      values: [...values],
      size: initialElements.length,
      activeIndexes: [source, source + 1],
      description: `将 ${initialElements[source]} 从 a[${source}] 移到 a[${source + 1}]。`,
    })
  }

  values[insertIndex] = insertValue
  result.push({
    values: [...values],
    size: initialElements.length + 1,
    activeIndexes: [insertIndex],
    description: `写入 a[${insertIndex}] = ${insertValue}，更新 n = ${initialElements.length + 1}；插入完成。`,
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
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-surface);
  overflow-x: auto;
}
.array-meta {
  margin-bottom: var(--lt-space-2);
  min-width: 100%;
}
.array-values {
  display: grid;
  grid-template-columns: repeat(v-bind('visualization.capacity'), minmax(var(--lt-control-lg), 1fr));
  gap: var(--lt-space-2);
  min-width: 100%;
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
.array-cell--empty {
  color: var(--lt-label-3);
}
.array-cell small {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption2);
}
.courseware-controls {
  justify-content: flex-end;
  margin-top: var(--lt-space-4);
}
</style>
