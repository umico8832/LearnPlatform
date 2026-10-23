<template>
  <section
    class="courseware lt"
    data-appearance="dark"
    tabindex="0"
    aria-label="RootishArrayStack 递增块布局互动课件，使用左右方向键切换步骤"
    @keydown.left.prevent="previous"
    @keydown.right.prevent="next"
  >
    <div class="heading">
      <div>
        <span>互动课件</span>
        <h3>RootishArrayStack 的递增块布局</h3>
      </div>
      <small>步骤 {{ currentStep + 1 }} / {{ states.length }}</small>
    </div>
    <p>{{ currentState }}</p>
    <div class="blocks" aria-live="polite">
      <div v-for="(block, index) in visualization.blocks" :key="index" class="block">
        <b>块 {{ index }}（容量 {{ index + 1 }}）</b>
        <div>{{ block.join(' → ') }}</div>
      </div>
    </div>
    <p v-if="currentStep > 0" class="order">逻辑顺序：{{ elements.join(' → ') }}</p>
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
import type { RootishArrayStackLayoutCourseware } from '@/api/course'
const props = defineProps<{ visualization: RootishArrayStackLayoutCourseware }>()
const currentStep = ref(0)
const elements = computed(() => props.visualization.blocks.flat())
const capacityExpression = computed(() => props.visualization.blocks.map((_, index) => index + 1).join(' + '))
const states = computed(() => [
  '块 0 的容量为 1；之后每个块的容量比前一块多 1。块彼此独立，但逻辑元素按全局下标连续排列。',
  `当前共有 ${props.visualization.blocks.length} 个块，元素从块 0 开始依次填入下一个可用槽位。`,
  `总容量 ${capacityExpression.value} = ${elements.value.length}；一般地，r 个块的总容量为 r(r+1)/2。`,
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
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
}
.courseware:focus-visible {
  box-shadow: var(--lt-shadow-accent);
  border-color: var(--lt-accent);
}
.heading,
.controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
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
.blocks {
  display: grid;
  gap: var(--lt-space-2);
}
.block {
  padding: var(--lt-space-3) var(--lt-space-4);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-surface);
}
.block b {
  display: block;
  color: var(--lt-label);
  margin-bottom: var(--lt-space-1);
}
.order {
  font-weight: 600;
}
.controls {
  justify-content: flex-end;
  margin-top: var(--lt-space-4);
}
</style>
