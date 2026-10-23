<script setup lang="ts">
/**
 * 学员端的请求失败提示：底部居中的浮层，几秒后自动消失。
 * 页面里的业务错误仍然就地显示，这里只接 request.ts 里没有页面接手的失败与登录过期。
 */
import { X } from '@lucide/vue'
import { dismissToast, toasts } from './learnerToasts'
</script>

<template>
  <div class="lt toast-host" data-appearance="dark" role="alert" aria-live="assertive">
    <TransitionGroup name="toast">
      <div v-for="toast in toasts" :key="toast.id" class="toast">
        <span class="dot" aria-hidden="true" />
        <span class="message">{{ toast.message }}</span>
        <button type="button" class="close" aria-label="关闭提示" @click="dismissToast(toast.id)">
          <X aria-hidden="true" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  z-index: 3000;
  bottom: calc(var(--lt-space-6) + env(safe-area-inset-bottom));
  left: 50%;
  display: grid;
  justify-items: center;
  gap: var(--lt-space-2);
  width: max-content;
  max-width: calc(100vw - var(--lt-space-4) * 2);
  translate: -50% 0;
  background: none;
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: var(--lt-space-3);
  max-width: 100%;
  min-height: var(--lt-control-lg);
  box-sizing: border-box;
  padding: var(--lt-space-2) var(--lt-space-2) var(--lt-space-2) var(--lt-space-4);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-surface-raised);
  box-shadow: var(--lt-shadow-float);
  color: var(--lt-label);
  font: var(--lt-text-footnote) / 1.4 var(--lt-font);
  backdrop-filter: blur(40px) saturate(180%);
  pointer-events: auto;
}

.dot {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--lt-red);
}

.message {
  min-width: 0;
  overflow-wrap: anywhere;
}

.close {
  display: grid;
  flex: none;
  place-items: center;
  width: var(--lt-control-sm);
  height: var(--lt-control-sm);
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--lt-label-2);
  cursor: pointer;
}

.close:hover {
  background: var(--lt-fill-hover);
  color: var(--lt-label);
}

.close svg {
  width: 16px;
  height: 16px;
}

.toast-enter-active,
.toast-leave-active,
.toast-move {
  transition:
    opacity var(--lt-dur-fade) var(--lt-ease-fade),
    transform var(--lt-dur-snappy) var(--lt-ease-snappy);
}

.toast-leave-active {
  position: absolute;
}

.toast-enter-from {
  opacity: 0;
  transform: translateY(12px) scale(0.96);
}

.toast-leave-to {
  opacity: 0;
  transform: scale(0.96);
}
</style>
