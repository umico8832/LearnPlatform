<template>
  <section
    class="courseware"
    tabindex="0"
    aria-label="单链表逆置互动课件，使用左右方向键切换步骤"
    @keydown.left.prevent="previous"
    @keydown.right.prevent="next"
  >
    <div class="heading">
      <div>
        <span>互动课件</span>
        <h3>单链表的就地逆置</h3>
      </div>
      <small>步骤 {{ currentStep + 1 }} / {{ states.length }}</small>
    </div>
    <p>{{ currentState.description }}</p>
    <div class="lists" aria-live="polite">
      <div class="list">
        <small>已逆置：</small><strong>{{ reversedText }}</strong>
      </div>
      <div class="list">
        <small>待处理</small><strong>{{ remainingText }}</strong>
      </div>
    </div>
    <p v-if="currentStep > 0 && currentStep <= visualization.elements.length" class="pointer">
      本轮先保存 next = {{ visualization.elements[currentStep] ?? 'null' }}，再令 cur.next = prev。
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
import type { LinkedListReversalCourseware } from '@/api/course'
const props = defineProps<{ visualization: LinkedListReversalCourseware }>()
const currentStep = ref(0)
const states = computed(() => [
  { description: `初始化：prev = null，cur = ${props.visualization.elements[0]}。每轮都必须先保存 cur 的原后继。` },
  ...props.visualization.elements.map((element, index) => ({
    description: `处理结点 ${element}：先保存 next = ${props.visualization.elements[index + 1] ?? 'null'}，再让 ${element}.next 指向当前 prev，随后 prev 前进到 ${element}。${index === props.visualization.elements.length - 1 ? `此时新表头为 ${element}。` : ''}`,
  })),
  {
    description: `所有结点处理完毕，prev 成为新表头 ${props.visualization.elements[props.visualization.elements.length - 1]}；逆置只扫描一次且只使用常数个指针。`,
  },
])
const processedCount = computed(() => Math.min(currentStep.value, props.visualization.elements.length))
const reversedText = computed(
  () =>
    `${props.visualization.elements.slice(0, processedCount.value).reverse().join(' → ')}${processedCount.value ? ' → null' : 'null'}`,
)
const remainingText = computed(
  () => `${props.visualization.elements.slice(processedCount.value).join(' → ') || 'null'}`,
)
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
.lists {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.list {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--lp-border);
  border-radius: 6px;
  background: var(--lp-surface);
}
.list small {
  display: block;
  color: var(--lp-text-muted);
}
.list strong {
  display: block;
  margin-top: 4px;
  overflow-wrap: anywhere;
  color: var(--lp-text);
}
.pointer {
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
  .lists {
    grid-template-columns: 1fr;
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
  .list {
    transition: none;
  }
}
</style>
