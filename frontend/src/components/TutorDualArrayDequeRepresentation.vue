<template>
  <section
    class="courseware"
    data-testid="courseware"
    tabindex="0"
    aria-label="DualArrayDeque 双栈表示互动课件，使用左右方向键切换步骤"
    @keydown.left.prevent="previous"
    @keydown.right.prevent="next"
  >
    <div class="courseware-heading">
      <div>
        <span class="courseware-kicker">互动课件</span>
        <h3>DualArrayDeque 的双栈表示</h3>
      </div>
      <span class="courseware-progress">步骤 {{ currentStep + 1 }} / {{ states.length }}</span>
    </div>
    <p class="courseware-description">{{ currentState.description }}</p>
    <div class="stacks" aria-live="polite">
      <div class="stack">
        <span>front（栈底 → 栈顶）</span>
        <div class="values">{{ visualization.front.join(' → ') }}</div>
      </div>
      <div class="stack">
        <span>back（栈底 → 栈顶）</span>
        <div class="values">{{ visualization.back.join(' → ') }}</div>
      </div>
    </div>
    <p v-if="currentState.showOrder" class="logical-order">逻辑 List 顺序：{{ logicalElements.join(' → ') }}</p>
    <div class="courseware-controls" role="group" aria-label="课件步骤控制">
      <el-button data-testid="previous-step" :disabled="currentStep === 0" @click="previous">上一步</el-button
      ><el-button data-testid="next-step" type="primary" :disabled="currentStep === states.length - 1" @click="next"
        >下一步</el-button
      >
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import type { DualArrayDequeRepresentationCourseware } from '@/api/course'
const props = defineProps<{ visualization: DualArrayDequeRepresentationCourseware }>()
const currentStep = ref(0)
const logicalElements = computed(() => [...props.visualization.front].reverse().concat(props.visualization.back))
const states = computed(() => {
  const frontSize = props.visualization.front.length
  const index = props.visualization.accessIndex
  const description =
    index < frontSize
      ? `逻辑下标 ${index} 映射到 front[${frontSize - index - 1}]；front 以逆序保存逻辑前缀。`
      : `逻辑下标 ${index} 映射到 back[${index - frontSize}]；back 以正序保存逻辑后缀。`
  return [
    {
      description: `front 以逆序保存逻辑前缀，back 以正序保存逻辑后缀；n = ${frontSize} + ${props.visualization.back.length}。`,
      showOrder: false,
    },
    { description: '读取逻辑序列时，先反向读取 front，再正向读取 back。两个内部栈不要求大小相等。', showOrder: true },
    { description, showOrder: true },
  ]
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
.courseware-progress {
  color: var(--lp-text-secondary);
  font-size: 13px;
}
.courseware-description {
  min-height: 44px;
  margin: 16px 0;
  color: var(--lp-text-secondary);
  line-height: 1.65;
}
.stacks {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.stack {
  padding: 16px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  background: var(--lp-surface);
  color: var(--lp-text-secondary);
  font-size: 13px;
}
.values {
  margin-top: 10px;
  color: var(--lp-text);
  font-weight: 700;
  line-height: 1.65;
  word-break: break-word;
}
.logical-order {
  margin: 14px 0 0;
  color: var(--lp-text-secondary);
  font-weight: 600;
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
  .stacks {
    grid-template-columns: 1fr;
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
  .courseware * {
    transition: none;
  }
}
</style>
