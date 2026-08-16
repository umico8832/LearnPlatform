<template>
  <section
    class="courseware"
    tabindex="0"
    aria-label="顺序表连续存储互动课件，使用左右方向键切换步骤"
    @keydown.left.prevent="previous"
    @keydown.right.prevent="next"
  >
    <div class="heading">
      <div>
        <span>互动课件</span>
        <h3>顺序表的连续存储</h3>
      </div>
      <small>步骤 {{ currentStep + 1 }} / {{ states.length }}</small>
    </div>
    <p>{{ currentState.description }}</p>
    <div class="cells" aria-live="polite">
      <div
        v-for="(element, index) in visualization.elements"
        :key="index"
        class="cell"
        :class="{ active: currentStep === 2 && index === visualization.accessIndex }"
      >
        <strong>a{{ index + 1 }} = {{ element }}</strong
        ><small>地址 {{ addressAt(index) }}</small>
      </div>
    </div>
    <p v-if="currentStep === 2" class="formula">
      LOC(a{{ visualization.accessIndex + 1 }}) = {{ visualization.baseAddress }} + {{ visualization.accessIndex }} ×
      {{ visualization.elementWidth }} = {{ addressAt(visualization.accessIndex) }}
    </p>
    <div class="controls">
      <el-button data-testid="previous-step" :disabled="currentStep === 0" @click="previous">上一步</el-button
      ><el-button data-testid="next-step" type="primary" :disabled="currentStep === states.length - 1" @click="next"
        >下一步</el-button
      >
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import type { SequentialListStorageCourseware } from '@/api/course'
const props = defineProps<{ visualization: SequentialListStorageCourseware }>()
const currentStep = ref(0)
const states = computed(() => [
  {
    description: `首元素地址 LOC(a1) = ${props.visualization.baseAddress}，每个元素固定占 ${props.visualization.elementWidth} 个字节。`,
  },
  { description: '顺序表把逻辑相邻元素放在物理连续单元，因此只需首地址、元素宽度和下标即可定位。' },
  {
    description: `访问第 ${props.visualization.accessIndex + 1} 个元素时，直接计算其地址；a${props.visualization.accessIndex + 1} = ${props.visualization.elements[props.visualization.accessIndex]}。`,
  },
])
const currentState = computed(() => states.value[currentStep.value])
function addressAt(index: number) {
  return props.visualization.baseAddress + index * props.visualization.elementWidth
}
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
  color: var(--lp-text-secondary);
  line-height: 1.65;
  outline: none;
}
.courseware:focus-visible {
  box-shadow: 0 0 0 3px var(--lp-primary-soft);
  border-color: var(--lp-primary);
}
.heading,
.controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.heading span {
  color: var(--lp-primary);
  font-size: 12px;
  font-weight: 700;
}
.heading h3 {
  margin: 3px 0 0;
  color: var(--lp-text);
  font-size: 17px;
}
.cells {
  display: grid;
  grid-template-columns: repeat(v-bind('visualization.elements.length'), minmax(110px, 1fr));
  gap: 8px;
  overflow-x: auto;
}
.cell {
  min-width: 110px;
  padding: 12px;
  border: 1px solid var(--lp-border);
  border-radius: 6px;
  background: var(--lp-surface);
}
.cell strong {
  display: block;
  color: var(--lp-text);
}
.cell small {
  color: var(--lp-text-muted);
}
.cell.active {
  border-color: var(--lp-primary);
  background: var(--lp-primary-soft);
}
.formula {
  font-weight: 600;
}
.controls {
  justify-content: flex-end;
  margin-top: 16px;
}
@media (max-width: 767px) {
  .courseware {
    padding: 16px;
  }
  .heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .controls {
    justify-content: stretch;
  }
  .controls .el-button {
    flex: 1;
    margin: 0;
  }
}
@media (prefers-reduced-motion: reduce) {
  .cell {
    transition: none;
  }
}
</style>
