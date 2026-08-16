<template>
  <section
    class="courseware"
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
      <el-button data-testid="previous-step" :disabled="currentStep === 0" @click="previous">上一步</el-button>
      <el-button data-testid="next-step" type="primary" :disabled="currentStep === states.length - 1" @click="next">
        下一步
      </el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
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
  padding: 20px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  background: var(--lp-surface-soft);
  outline: none;
}
.courseware:focus-visible {
  box-shadow: 0 0 0 3px var(--lp-primary-soft);
  border-color: var(--lp-primary);
}
.courseware-heading,
.array-meta,
.courseware-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.courseware-kicker {
  color: var(--lp-primary);
  font-size: 12px;
  font-weight: 700;
}
.courseware h3 {
  margin: 3px 0 0;
  color: var(--lp-text);
  font-size: 17px;
}
.courseware-progress,
.array-meta {
  color: var(--lp-text-secondary);
  font-size: 13px;
}
.courseware-description {
  min-height: 44px;
  margin: 16px 0;
  color: var(--lp-text-secondary);
  line-height: 1.65;
}
.array-shell {
  padding: 16px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  background: var(--lp-surface);
  overflow-x: auto;
}
.array-meta {
  margin-bottom: 10px;
  min-width: 420px;
}
.array-values {
  display: grid;
  grid-template-columns: repeat(v-bind('visualization.capacity'), minmax(64px, 1fr));
  gap: 8px;
  min-width: 420px;
}
.array-cell {
  display: grid;
  min-height: 66px;
  place-items: center;
  align-content: center;
  gap: 5px;
  border: 1px solid var(--lp-border-strong);
  border-radius: 6px;
  color: var(--lp-text);
  background: var(--lp-surface);
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    transform 180ms ease;
}
.array-cell--active {
  border-color: var(--lp-primary);
  background: var(--lp-primary-soft);
  transform: translateY(-2px);
}
.array-cell--empty {
  color: var(--lp-text-muted);
}
.array-cell small {
  color: var(--lp-text-muted);
  font-size: 11px;
}
.courseware-controls {
  justify-content: flex-end;
  margin-top: 16px;
}
@media (max-width: 767px) {
  .courseware {
    padding: 16px;
  }
  .courseware-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .courseware-controls {
    justify-content: stretch;
  }
  .courseware-controls .el-button {
    flex: 1;
    margin: 0;
  }
}
@media (prefers-reduced-motion: reduce) {
  .array-cell {
    transition: none;
  }
}
</style>
