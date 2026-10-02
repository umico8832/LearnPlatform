<script setup lang="ts">
withDefaults(
  defineProps<{
    as?: 'article' | 'div' | 'section'
    density?: 'compact' | 'comfortable'
  }>(),
  { as: 'section', density: 'comfortable' },
)
</script>

<template>
  <component :is="as" class="lp-card" :class="`is-${density}`">
    <header v-if="$slots.header" class="lp-card-header"><slot name="header" /></header>
    <div class="lp-card-body"><slot /></div>
    <footer v-if="$slots.footer" class="lp-card-footer"><slot name="footer" /></footer>
  </component>
</template>

<style scoped>
.lp-card {
  overflow: hidden;
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
  box-shadow: var(--lp-shadow-xs);
}
.lp-card-header,
.lp-card-footer {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  padding: var(--lp-space-4) var(--lp-space-5);
  background: var(--lp-surface-subtle);
}
.lp-card-header {
  border-bottom: var(--lp-border-hairline);
}
.lp-card-footer {
  border-top: var(--lp-border-hairline);
}
.lp-card-body {
  padding: var(--lp-space-5);
}
.lp-card.is-compact .lp-card-header,
.lp-card.is-compact .lp-card-footer,
.lp-card.is-compact .lp-card-body {
  padding: var(--lp-space-3) var(--lp-space-4);
}
</style>
