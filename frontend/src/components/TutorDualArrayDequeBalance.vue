<template>
  <section class="courseware" data-testid="courseware" tabindex="0" aria-label="DualArrayDeque 再平衡互动课件，使用左右方向键切换步骤" @keydown.left.prevent="previous" @keydown.right.prevent="next">
    <div class="courseware-heading">
      <div><span class="courseware-kicker">互动课件</span><h3>DualArrayDeque 的再平衡</h3></div>
      <span class="courseware-progress">步骤 {{ currentStep + 1 }} / {{ states.length }}</span>
    </div>
    <p class="courseware-description">{{ currentState.description }}</p>
    <div class="stacks" aria-live="polite">
      <div class="stack"><span>front（栈底 → 栈顶）</span><div class="values">{{ displayedFront.join(' → ') || '∅' }}</div></div>
      <div class="stack"><span>back（栈底 → 栈顶）</span><div class="values">{{ displayedBack.join(' → ') || '∅' }}</div></div>
    </div>
    <p v-if="currentStep > 0" class="logical-order">逻辑 List 顺序始终保持：{{ logicalElements.join(' → ') }}</p>
    <div class="courseware-controls" role="group" aria-label="课件步骤控制">
      <el-button data-testid="previous-step" :disabled="currentStep === 0" @click="previous">上一步</el-button>
      <el-button data-testid="next-step" type="primary" :disabled="currentStep === states.length - 1" @click="next">下一步</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { DualArrayDequeBalanceCourseware } from '@/api/course'

const props = defineProps<{ visualization: DualArrayDequeBalanceCourseware }>()
const currentStep = ref(0)
const logicalElements = computed(() => [...props.visualization.front].reverse().concat(props.visualization.back))
const midpoint = computed(() => Math.floor(logicalElements.value.length / 2))
const balancedFront = computed(() => logicalElements.value.slice(0, midpoint.value).reverse())
const balancedBack = computed(() => logicalElements.value.slice(midpoint.value))
const isBackHeavy = computed(() => props.visualization.back.length > 3 * props.visualization.front.length)
const displayedFront = computed(() => currentStep.value === 2 ? balancedFront.value : props.visualization.front)
const displayedBack = computed(() => currentStep.value === 2 ? balancedBack.value : props.visualization.back)
const states = computed(() => [
  { description: isBackHeavy.value ? 'back 的大小超过 front 的三倍，满足 balance 的重建条件。' : 'front 的大小超过 back 的三倍，满足 balance 的重建条件。' },
  { description: `按逻辑顺序取出全部 ${logicalElements.value.length} 个元素；前 ${midpoint.value} 个将放入 front，但必须逆序保存。` },
  { description: `重建完成：front 保存前 ${midpoint.value} 个元素的逆序，back 保存剩余 ${logicalElements.value.length - midpoint.value} 个元素的正序；这次重建移动 O(n) 个元素。` },
])
const currentState = computed(() => states.value[currentStep.value])
function previous() { currentStep.value = Math.max(0, currentStep.value - 1) }
function next() { currentStep.value = Math.min(states.value.length - 1, currentStep.value + 1) }
</script>

<style scoped>
.courseware { padding: 20px; border: 1px solid var(--lp-border); border-radius: var(--lp-radius); background: var(--lp-surface-soft); outline: none; }
.courseware:focus-visible { box-shadow: 0 0 0 3px var(--lp-primary-soft); border-color: var(--lp-primary); }
.courseware-heading, .courseware-controls { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.courseware-kicker { color: var(--lp-primary); font-size: 12px; font-weight: 700; }
.courseware h3 { margin: 3px 0 0; color: var(--lp-text); font-size: 17px; }
.courseware-progress { color: var(--lp-text-secondary); font-size: 13px; }
.courseware-description { min-height: 44px; margin: 16px 0; color: var(--lp-text-secondary); line-height: 1.65; }
.stacks { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.stack { padding: 16px; border: 1px solid var(--lp-border); border-radius: var(--lp-radius); background: var(--lp-surface); color: var(--lp-text-secondary); font-size: 13px; }
.values { margin-top: 10px; color: var(--lp-text); font-weight: 700; line-height: 1.65; word-break: break-word; }
.logical-order { margin: 14px 0 0; color: var(--lp-text-secondary); font-weight: 600; }
.courseware-controls { justify-content: flex-end; margin-top: 16px; }
@media (max-width: 767px) { .courseware { padding: 16px; }.courseware-heading { align-items: flex-start; flex-direction: column; }.stacks { grid-template-columns: 1fr; }.courseware-controls { justify-content: stretch; }.courseware-controls .el-button { flex: 1; margin: 0; } }
@media (prefers-reduced-motion: reduce) { .courseware * { transition: none; } }
</style>
