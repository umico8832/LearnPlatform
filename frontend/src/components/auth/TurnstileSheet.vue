<script setup lang="ts">
import { computed, shallowRef } from 'vue'
import { LtButton, LtSheet } from '@/components/living'
import TurnstileWidget from './TurnstileWidget.vue'
import { useVerificationGate } from './useVerificationGate'

/**
 * Human verification on the new baseline, with the same `verify()` contract as
 * TurnstileDialog. The sheet grows out of the control that asked for it and hands
 * focus back to it; closing it in any way cancels the request.
 */
const gate = useVerificationGate()
const { failed, attempt } = gate
const origin = shallowRef<HTMLElement | null>(null)

const open = computed({
  get: () => gate.visible.value,
  set: (value) => {
    if (!value) gate.finish(null)
  },
})

function verify(trigger?: HTMLElement) {
  if (!gate.visible.value) origin.value = trigger ?? null
  return gate.verify(trigger)
}

defineExpose({ verify })
</script>

<template>
  <LtSheet v-model:open="open" title="安全验证" :origin="origin" :width="400">
    <p class="lead">完成验证后会自动继续。</p>
    <TurnstileWidget
      v-if="open"
      :key="attempt"
      model-value=""
      theme="auto"
      @update:model-value="gate.onVerified"
      @error="failed = true"
      @expired="failed = true"
    />
    <div class="actions">
      <LtButton variant="quiet" @click="open = false">取消</LtButton>
      <LtButton v-if="failed" variant="primary" @click="gate.retry()">重新验证</LtButton>
    </div>
  </LtSheet>
</template>

<style scoped>
.lead {
  margin: 0 0 var(--lt-space-4);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--lt-space-2);
  margin-top: var(--lt-space-5);
}
</style>
