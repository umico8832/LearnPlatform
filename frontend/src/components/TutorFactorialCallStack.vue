<template>
  <section
    class="courseware lt"
    data-appearance="dark"
    tabindex="0"
    aria-label="阶乘递归调用栈互动课件，使用左右方向键切换步骤"
    @keydown.left.prevent="previous"
    @keydown.right.prevent="next"
  >
    <div class="heading">
      <div>
        <span>互动课件</span>
        <h3>阶乘递归的调用栈</h3>
      </div>
      <small>步骤 {{ currentStep + 1 }} / {{ states.length }}</small>
    </div>
    <p>{{ currentState.description }}</p>
    <div class="stack" aria-live="polite" aria-label="当前调用栈，最上方为栈顶">
      <div
        v-for="frame in visibleFrames"
        :key="frame.argument"
        data-testid="call-frame"
        class="frame"
        :class="{ active: frame.argument === activeArgument }"
      >
        <strong>factorial({{ frame.argument }})</strong><small>{{ frameLabel(frame.argument) }}</small>
      </div>
    </div>
    <p class="legend">栈顶在上方；内层活动记录先返回，外层调用随后恢复。</p>
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
import type { FactorialCallStackCourseware } from '@/api/course'

const props = defineProps<{ visualization: FactorialCallStackCourseware }>()
const currentStep = ref(0)
const factorial = (value: number): number => (value <= 1 ? 1 : value * factorial(value - 1))
const argumentsDescending = computed(() =>
  Array.from({ length: props.visualization.startValue }, (_, index) => props.visualization.startValue - index),
)
const states = computed(() => [
  ...argumentsDescending.value.slice(0, -1).map((argument, index) => ({
    visibleArguments: argumentsDescending.value.slice(0, index + 1),
    activeArgument: argument,
    resolvedArgument: null as number | null,
    description:
      index === 0
        ? `调用 factorial(${argument})，创建首个活动记录。`
        : `factorial(${argument + 1}) 暂停在调用点，压入 factorial(${argument})。`,
  })),
  {
    visibleArguments: argumentsDescending.value,
    activeArgument: 1,
    resolvedArgument: 1,
    description: '到达基例 factorial(1) = 1，不再压入新的活动记录。',
  },
  ...argumentsDescending.value
    .slice(0, -1)
    .reverse()
    .map((argument) => ({
      visibleArguments: argumentsDescending.value.filter((value) => value >= argument),
      activeArgument: argument,
      resolvedArgument: argument,
      description: `内层结果返回后，恢复 factorial(${argument}) 并得到 factorial(${argument}) = ${factorial(argument)}。`,
    })),
])
const currentState = computed(() => states.value[currentStep.value])
const visibleFrames = computed(() =>
  [...currentState.value.visibleArguments].reverse().map((argument) => ({ argument })),
)
const activeArgument = computed(() => currentState.value.activeArgument)
function frameLabel(argument: number) {
  if (currentState.value.resolvedArgument === argument) return `已得到结果 ${factorial(argument)}`
  if (argument === activeArgument.value) return '当前执行'
  return '暂停，等待内层返回'
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
.stack {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-2);
  max-width: min(100%, calc(var(--lt-space-10) * 7));
  margin: 0 auto;
}
.frame {
  min-height: var(--lt-control-lg);
  padding: var(--lt-space-2) var(--lt-space-3);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-sm);
  background: var(--lt-surface);
}
.frame strong,
.frame small {
  display: block;
}
.frame strong {
  color: var(--lt-label);
}
.frame small {
  color: var(--lt-label-3);
}
.frame.active {
  border-color: var(--lt-accent);
  background: var(--lt-accent-soft);
}
.legend {
  text-align: center;
  color: var(--lt-label-3);
}
.controls {
  justify-content: flex-end;
  margin-top: var(--lt-space-4);
}
</style>
