<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { LtButton, LtSheet } from '@/components/living'

const props = withDefaults(
  defineProps<{
    visible: boolean
    starting: boolean
    error: string
    knowledgePoints: { id: number; title: string }[]
  }>(),
  { visible: false, starting: false },
)

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'start', knowledgePointId: number): void
}>()

const sheetOpen = computed({
  get: () => props.visible,
  set: (value: boolean) => {
    if (value || !props.starting) emit('update:visible', value)
  },
})

const knowledgePointId = ref(0)

watch(
  () => props.visible,
  (value) => {
    if (value) knowledgePointId.value = 0
  },
)
</script>

<template>
  <LtSheet v-model:open="sheetOpen" title="开始阶段测评">
    <form class="assessment-setup" :aria-busy="starting" @submit.prevent="emit('start', knowledgePointId)">
      <p class="assessment-setup-intro">从课程范围中选择 5 道题；系统会保留未完成测评供你继续作答。</p>
      <p v-if="error" class="assessment-setup-error" role="alert">{{ error }}</p>
      <label class="assessment-field">
        <span>知识点范围</span>
        <select v-model.number="knowledgePointId" :disabled="starting" name="knowledge-point-scope">
          <option :value="0">课程整体测评</option>
          <option v-for="item in knowledgePoints" :key="item.id" :value="item.id">{{ item.title }}</option>
        </select>
      </label>
      <div class="assessment-actions">
        <LtButton :disabled="starting" @click="sheetOpen = false">取消</LtButton>
        <LtButton type="submit" variant="primary" :disabled="starting">{{
          starting ? '正在开始…' : '开始测评'
        }}</LtButton>
      </div>
    </form>
  </LtSheet>
</template>

<style scoped>
.assessment-setup {
  display: grid;
  gap: var(--lt-space-4);
}
.assessment-setup-intro,
.assessment-setup-error {
  margin: 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-subhead);
  line-height: var(--lt-leading-reading);
}
.assessment-setup-error {
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-sm);
  background: var(--lt-red-soft);
  color: var(--lt-red-text);
}
.assessment-field {
  display: grid;
  gap: var(--lt-space-2);
  color: var(--lt-label);
  font-size: var(--lt-text-footnote);
  font-weight: 600;
}
.assessment-field select {
  width: 100%;
  min-height: var(--lt-control-lg);
  padding: 0 var(--lt-space-3);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font: inherit;
}
.assessment-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--lt-space-2);
}
</style>
