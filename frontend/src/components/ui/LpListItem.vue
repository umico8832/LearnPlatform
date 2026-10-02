<script setup lang="ts">
import { RouterLink, type RouteLocationRaw } from 'vue-router'

const props = withDefaults(
  defineProps<{
    to?: RouteLocationRaw
    disabled?: boolean
    active?: boolean
    status?: 'default' | 'success' | 'warning' | 'danger' | 'info'
  }>(),
  { to: undefined, disabled: false, active: undefined, status: 'default' },
)

const emit = defineEmits<{ click: [event: MouseEvent] }>()

function onClick(event: MouseEvent) {
  if (!props.disabled) emit('click', event)
}
</script>

<template>
  <button
    v-if="!to || disabled"
    type="button"
    class="lp-list-item"
    :class="{ 'is-active': active, 'is-disabled': disabled }"
    :data-status="status"
    :disabled="disabled"
    :aria-pressed="active"
    @click="onClick"
  >
    <span v-if="$slots.leading" class="lp-list-item-leading"><slot name="leading" /></span>
    <span class="lp-list-item-main"><slot /></span>
    <span v-if="$slots.trailing" class="lp-list-item-trailing"><slot name="trailing" /></span>
  </button>
  <RouterLink
    v-else
    :to="to"
    class="lp-list-item"
    :class="{ 'is-active': active }"
    :data-status="status"
    :aria-current="active ? 'page' : undefined"
    @click="onClick"
  >
    <span v-if="$slots.leading" class="lp-list-item-leading"><slot name="leading" /></span>
    <span class="lp-list-item-main"><slot /></span>
    <span v-if="$slots.trailing" class="lp-list-item-trailing"><slot name="trailing" /></span>
  </RouterLink>
</template>

<style scoped>
.lp-list-item {
  display: flex;
  align-items: center;
  width: 100%;
  gap: var(--lp-space-3);
  padding: var(--lp-space-3) var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface);
  color: var(--lp-text);
  font: inherit;
  line-height: var(--lp-leading-snug);
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  transition:
    background-color var(--lp-duration-fast) var(--lp-ease-out),
    border-color var(--lp-duration-fast) var(--lp-ease-out);
}
.lp-list-item:not(:disabled):hover {
  border-color: var(--lp-border-strong);
  background: var(--lp-surface-subtle);
}
.lp-list-item:focus-visible {
  outline: var(--lp-focus-width) solid var(--lp-focus-ring);
  outline-offset: var(--lp-focus-offset);
  box-shadow: var(--lp-shadow-focus);
}
.lp-list-item.is-active {
  border-color: var(--lp-primary);
  background: var(--lp-primary-soft);
}
.lp-list-item.is-disabled {
  color: var(--lp-text-muted);
  cursor: not-allowed;
  opacity: 0.7;
}
.lp-list-item-leading,
.lp-list-item-trailing {
  flex: 0 0 auto;
}
.lp-list-item-main {
  min-width: 0;
  flex: 1;
  overflow-wrap: anywhere;
}
.lp-list-item-trailing {
  color: var(--lp-text-secondary);
}
.lp-list-item[data-status='success'] .lp-list-item-leading {
  color: var(--lp-success);
}
.lp-list-item[data-status='warning'] .lp-list-item-leading {
  color: var(--lp-warning);
}
.lp-list-item[data-status='danger'] .lp-list-item-leading {
  color: var(--lp-danger);
}
.lp-list-item[data-status='info'] .lp-list-item-leading {
  color: var(--lp-info);
}
@media (prefers-reduced-motion: reduce) {
  .lp-list-item {
    transition: none;
  }
}
</style>
