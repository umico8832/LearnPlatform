<script setup lang="ts">
withDefaults(
  defineProps<{
    kicker?: string
    title: string
    description?: string
    display?: boolean
  }>(),
  { kicker: '', description: '', display: false },
)
</script>

<template>
  <header class="lp-page-header">
    <div class="lp-page-header-copy">
      <LpKicker v-if="kicker">{{ kicker }}</LpKicker>
      <h1 class="lp-page-header-title" :class="{ 'is-display': display }">{{ title }}</h1>
      <p v-if="description" class="lp-page-header-desc">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="lp-page-header-actions" role="group" :aria-label="`${title}操作`">
      <slot name="actions" />
    </div>
    <slot v-if="$slots.default" />
  </header>
</template>

<style scoped>
.lp-page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--lp-space-5);
}
.lp-page-header-copy {
  min-width: 0;
  max-width: 720px;
}
.lp-page-header-title {
  margin: 0;
  font-size: var(--lp-text-4xl);
  font-weight: var(--lp-weight-semibold);
  line-height: var(--lp-leading-display);
  letter-spacing: var(--lp-tracking-tight);
  color: var(--lp-text);
  overflow-wrap: anywhere;
}
.lp-kicker + .lp-page-header-title {
  margin-top: var(--lp-space-2);
}
.lp-page-header-title.is-display {
  font-family: var(--lp-font-display);
  font-weight: var(--lp-weight-bold);
}
.lp-page-header-desc {
  margin: var(--lp-space-3) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-md);
  line-height: var(--lp-leading-relaxed);
}
.lp-page-header-actions {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}

@media (max-width: 767px) {
  .lp-page-header {
    align-items: stretch;
    flex-direction: column;
    gap: var(--lp-space-4);
  }
  .lp-page-header-title {
    font-size: var(--lp-text-3xl);
  }
  .lp-page-header-actions {
    justify-content: flex-start;
  }
}
</style>
