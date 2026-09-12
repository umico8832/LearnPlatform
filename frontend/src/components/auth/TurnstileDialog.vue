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
import { onBeforeUnmount, ref } from 'vue'
import TurnstileWidget from './TurnstileWidget.vue'

const visible = ref(false)
const failed = ref(false)
const attempt = ref(0)
let pending: Promise<string | null> | undefined
let resolveVerification: ((token: string | null) => void) | undefined
let disposed = false
let returnFocus: HTMLElement | undefined

function verify(trigger?: HTMLElement): Promise<string | null> {
  if (disposed) return Promise.resolve(null)
  if (pending) return pending
  returnFocus = trigger
  pending = new Promise((resolve) => {
    resolveVerification = resolve
  })
  failed.value = false
  attempt.value++
  visible.value = true
  return pending
}

function finish(token: string | null) {
  visible.value = false
  const resolve = resolveVerification
  resolveVerification = undefined
  pending = undefined
  resolve?.(token)
}

function onVerified(token: string) {
  if (token && visible.value) finish(token)
}

function onVisibilityChange(value: boolean) {
  if (!value) finish(null)
}

function retry() {
  failed.value = false
  attempt.value++
}

function restoreFocus() {
  requestAnimationFrame(() => {
    if (visible.value || disposed) return
    if (returnFocus?.isConnected) returnFocus.focus({ preventScroll: true })
    returnFocus = undefined
  })
}

onBeforeUnmount(() => {
  disposed = true
  finish(null)
})

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
