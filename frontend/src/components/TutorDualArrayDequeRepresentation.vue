<template>
  <section
    class="courseware lt"
    data-appearance="dark"
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
.stacks {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lt-space-3);
}
.stack {
  padding: var(--lt-space-4);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-surface);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.values {
  margin-top: var(--lt-space-2);
  color: var(--lt-label);
  font-weight: 700;
  line-height: var(--lt-leading-reading);
  word-break: break-word;
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
