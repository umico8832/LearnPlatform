<template>
  <el-dialog
    :model-value="visible"
    title="安全验证"
    class="turnstile-dialog"
    width="min(400px, calc(100vw - 32px))"
    align-center
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @update:model-value="onVisibilityChange"
    @closed="restoreFocus"
  >
    <p class="verification-description">完成验证后将自动继续。</p>
    <TurnstileWidget
      v-if="visible"
      :key="attempt"
      model-value=""
      theme="light"
      @update:model-value="onVerified"
      @error="failed = true"
      @expired="failed = true"
    />
    <template #footer>
      <el-button @click="finish(null)">取消</el-button>
      <el-button v-if="failed" type="primary" @click="retry">重新验证</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import TurnstileWidget from './TurnstileWidget.vue'
import { useVerificationGate } from './useVerificationGate'

const { visible, failed, attempt, verify, finish, onVerified, retry, restoreFocus } = useVerificationGate()

function onVisibilityChange(value: boolean) {
  if (!value) finish(null)
}

defineExpose({ verify })
</script>

<style>
.turnstile-dialog.el-dialog {
  margin: auto !important;
  padding: 0;
}
.turnstile-dialog .el-dialog__body {
  padding-inline: var(--lp-space-4);
}
.turnstile-dialog .verification-description {
  margin: 0 0 var(--lp-space-4);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-relaxed);
}
</style>
