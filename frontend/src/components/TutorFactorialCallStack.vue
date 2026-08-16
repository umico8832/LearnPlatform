<template>
  <section
    class="courseware"
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
      <el-button data-testid="previous-step" :disabled="currentStep === 0" @click="previous">上一步</el-button
      ><el-button data-testid="next-step" type="primary" :disabled="currentStep === states.length - 1" @click="next"
        >下一步</el-button
      >
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
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
.stack {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 520px;
  margin: 0 auto;
}
.frame {
  min-height: 52px;
  padding: 10px 14px;
  border: 1px solid var(--lp-border);
  border-radius: 6px;
  background: var(--lp-surface);
}
.frame strong,
.frame small {
  display: block;
}
.frame strong {
  color: var(--lp-text);
}
.frame small {
  color: var(--lp-text-muted);
}
.frame.active {
  border-color: var(--lp-primary);
  background: var(--lp-primary-soft);
}
.legend {
  text-align: center;
  color: var(--lp-text-muted);
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
  .stack {
    max-width: none;
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
  .frame {
    transition: none;
  }
}
</style>
