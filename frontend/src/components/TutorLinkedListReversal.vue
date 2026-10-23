<template>
  <section
    class="courseware lt"
    data-appearance="dark"
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
.lists {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lt-space-2);
}
.list {
  min-width: 0;
  padding: var(--lt-space-3);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-sm);
  background: var(--lt-surface);
}
.list small {
  display: block;
  color: var(--lt-label-3);
}
.list strong {
  display: block;
  margin-top: var(--lt-space-1);
  overflow-wrap: anywhere;
  color: var(--lt-label);
}
.pointer {
  font-weight: 600;
}
.controls {
  justify-content: flex-end;
  margin-top: var(--lt-space-4);
}
</style>
