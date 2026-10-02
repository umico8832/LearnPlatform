<script setup lang="ts">
withDefaults(
  defineProps<{
    state: 'ready' | 'loading' | 'error' | 'empty'
    title?: string
    description?: string
    loadingLabel?: string
    retryLabel?: string
    retrying?: boolean
  }>(),
  { title: '', description: '', loadingLabel: '正在加载', retryLabel: '重试', retrying: false },
)

const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <section class="lp-state-panel" :data-state="state">
    <div v-if="state === 'loading'" class="lp-state-panel-loading" role="status" :aria-label="loadingLabel">
      <LpSkeleton :label="loadingLabel" :announce="false" />
    </div>
    <div v-else-if="state === 'error'" class="lp-state-panel-error" role="alert">
      <strong class="lp-state-panel-title">{{ title || '暂时无法加载' }}</strong>
      <p v-if="description" class="lp-state-panel-description">{{ description }}</p>
      <div v-if="$slots.retry" class="lp-state-panel-actions"><slot name="retry" /></div>
      <button
        v-else
        type="button"
        class="lp-state-panel-retry"
        :disabled="retrying"
        :aria-busy="retrying"
        @click="!retrying && emit('retry')"
      >
        {{ retrying ? '正在重试' : retryLabel }}
      </button>
    </div>
    <LpEmptyState v-else-if="state === 'empty'" :title="title || '暂无内容'" :description="description">
      <template v-if="$slots.actions" #actions><slot name="actions" /></template>
    </LpEmptyState>
    <slot v-else />
  </section>
</template>

<style scoped>
.lp-state-panel {
  min-width: 0;
}
.lp-state-panel-loading,
.lp-state-panel-error {
  padding: var(--lp-space-5);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
}
.lp-state-panel-error {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--lp-space-3);
  border-inline-start: 3px solid var(--lp-danger);
  background: var(--lp-surface-subtle);
}
.lp-state-panel-title {
  overflow-wrap: anywhere;
  color: var(--lp-text);
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-snug);
}
.lp-state-panel-description {
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}
.lp-state-panel-actions {
  display: flex;
  gap: var(--lp-space-2);
}
.lp-state-panel-retry {
  padding: var(--lp-space-2) var(--lp-space-3);
  border: 0;
  border-radius: var(--lp-radius-sm);
  background: var(--lp-primary);
  color: var(--lp-on-primary);
  font: inherit;
  cursor: pointer;
}
.lp-state-panel-retry:focus-visible {
  outline: var(--lp-focus-width) solid var(--lp-focus-ring);
  outline-offset: var(--lp-focus-offset);
  box-shadow: var(--lp-shadow-focus);
}
.lp-state-panel-retry:disabled {
  cursor: wait;
  opacity: 0.6;
}
</style>
