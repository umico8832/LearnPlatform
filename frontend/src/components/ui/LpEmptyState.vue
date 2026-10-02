<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    description?: string
    compact?: boolean
  }>(),
  { description: '', compact: false },
)
</script>

<template>
  <div class="lp-empty" :class="{ 'is-compact': compact }">
    <div class="lp-empty-mark" aria-hidden="true">
      <slot name="mark">
        <svg class="lp-empty-book" width="28" height="28" viewBox="0 0 32 32" fill="none">
          <path d="M16 10c-4-3-8-3-12-2v17c4-1 8-1 12 2 4-3 8-3 12-2V8c-4-1-8-1-12 2Z" />
          <path d="M16 10v17M8 13l4 1M8 18l4 1M20 14l4-1M20 19l4-1" />
          <path class="lp-empty-book-spark" d="M16 2v3M11 4l2 2M21 4l-2 2" />
        </svg>
      </slot>
    </div>
    <h3 class="lp-empty-title">{{ title }}</h3>
    <p v-if="description" class="lp-empty-desc">{{ description }}</p>
    <div v-if="$slots.actions" class="lp-empty-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.lp-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--lp-space-2);
  padding: var(--lp-space-10) var(--lp-space-6);
  text-align: center;
}
.lp-empty.is-compact {
  padding: var(--lp-space-6) var(--lp-space-4);
}
.lp-empty-mark {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  margin-bottom: var(--lp-space-1);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-full);
  background: var(--lp-surface-soft);
  color: var(--lp-text-muted);
}
.lp-empty-book {
  color: var(--lp-primary);
  stroke: currentColor;
  stroke-width: 1.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.lp-empty-book-spark {
  animation: empty-book-arrive var(--lp-duration-celebration) var(--lp-ease-out);
}
@keyframes empty-book-arrive {
  from {
    opacity: 0;
    transform: translateY(2px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .lp-empty-book-spark {
    animation: none;
  }
}
.lp-empty-title {
  font-size: var(--lp-text-lg);
  font-weight: var(--lp-weight-semibold);
  color: var(--lp-text);
}
.lp-empty-desc {
  margin: 0;
  max-width: 340px;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-body);
}
.lp-empty-actions {
  display: flex;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-2);
  flex-wrap: wrap;
  justify-content: center;
}
</style>
