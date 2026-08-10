<template>
  <section class="courseware" tabindex="0" aria-label="RootishArrayStack 递增块布局互动课件，使用左右方向键切换步骤" @keydown.left.prevent="previous" @keydown.right.prevent="next">
    <div class="heading"><div><span>互动课件</span><h3>RootishArrayStack 的递增块布局</h3></div><small>步骤 {{ currentStep + 1 }} / {{ states.length }}</small></div>
    <p>{{ currentState }}</p>
    <div class="blocks" aria-live="polite"><div v-for="(block, index) in visualization.blocks" :key="index" class="block"><b>块 {{ index }}（容量 {{ index + 1 }}）</b><div>{{ block.join(' → ') }}</div></div></div>
    <p v-if="currentStep > 0" class="order">逻辑顺序：{{ elements.join(' → ') }}</p>
    <div class="controls"><el-button data-testid="previous-step" :disabled="currentStep === 0" @click="previous">上一步</el-button><el-button data-testid="next-step" type="primary" :disabled="currentStep === states.length - 1" @click="next">下一步</el-button></div>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
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
function previous() { currentStep.value = Math.max(0, currentStep.value - 1) }
function next() { currentStep.value = Math.min(states.value.length - 1, currentStep.value + 1) }
</script>
<style scoped>
.courseware { padding: 20px; border: 1px solid var(--lp-border); border-radius: var(--lp-radius); background: var(--lp-surface-soft); outline: none; color: var(--lp-text-secondary); line-height: 1.65; }.courseware:focus-visible { box-shadow: 0 0 0 3px var(--lp-primary-soft); border-color: var(--lp-primary); }.heading, .controls { display: flex; justify-content: space-between; align-items: center; gap: 12px; }.heading span { color: var(--lp-primary); font-size: 12px; font-weight: 700; }.heading h3 { margin: 3px 0 0; color: var(--lp-text); font-size: 17px; }.blocks { display: grid; gap: 10px; }.block { padding: 12px 16px; border: 1px solid var(--lp-border); border-radius: var(--lp-radius); background: var(--lp-surface); }.block b { display: block; color: var(--lp-text); margin-bottom: 6px; }.order { font-weight: 600; }.controls { justify-content: flex-end; margin-top: 16px; }@media (max-width: 767px) { .courseware { padding: 16px; }.heading { align-items: flex-start; flex-direction: column; }.controls { justify-content: stretch; }.controls .el-button { flex: 1; margin: 0; } }@media (prefers-reduced-motion: reduce) { .courseware * { transition: none; } }
</style>
