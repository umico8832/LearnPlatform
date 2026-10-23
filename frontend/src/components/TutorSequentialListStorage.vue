<template>
  <section
    class="courseware lt"
    data-appearance="dark"
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
  padding: var(--lt-space-5);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-well);
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
  outline: none;
}
.courseware:focus-visible {
  box-shadow: var(--lt-shadow-accent);
  border-color: var(--lt-accent);
}
.heading,
.controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-3);
}
.heading span {
  color: var(--lt-accent);
  font-size: var(--lt-text-caption);
  font-weight: 700;
}
.heading h3 {
  margin: var(--lt-space-1) 0 0;
  color: var(--lt-label);
  font-size: var(--lt-text-headline);
}
.cells {
  display: grid;
  grid-template-columns: repeat(v-bind('visualization.elements.length'), minmax(calc(var(--lt-control-lg) * 2), 1fr));
  gap: var(--lt-space-2);
  overflow-x: auto;
}
.cell {
  min-width: calc(var(--lt-control-lg) * 2);
  padding: var(--lt-space-3);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-sm);
  background: var(--lt-surface);
}
.cell strong {
  display: block;
  color: var(--lt-label);
}
.cell small {
  color: var(--lt-label-3);
}
.cell.active {
  border-color: var(--lt-accent);
  background: var(--lt-accent-soft);
}
.formula {
  font-weight: 600;
}
.controls {
  justify-content: flex-end;
  margin-top: var(--lt-space-4);
}
</style>
