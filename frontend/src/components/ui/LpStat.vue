<script setup lang="ts">
/**
 * 学习事实统计：一个安静的数值 + 标签 + 说明。
 * 只展示真实计数，不允许包装成掌握度或能力评分。
 */
withDefaults(
  defineProps<{
    label: string
    value: number | string
    note?: string
    tone?: 'default' | 'emphasis' | 'warning' | 'danger'
  }>(),
  { note: '', tone: 'default' },
)
</script>

<template>
  <article class="lp-stat" :data-tone="tone">
    <span class="lp-stat-value">{{ value }}</span>
    <span class="lp-stat-label">{{ label }}</span>
    <small v-if="note" class="lp-stat-note">{{ note }}</small>
  </article>
</template>

<style scoped>
.lp-stat {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-1);
  min-width: 0;
  padding: var(--lp-space-4);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
}
.lp-stat-value {
  color: var(--lp-text);
  font-size: var(--lp-text-3xl);
  font-weight: var(--lp-weight-heavy);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.lp-stat[data-tone='emphasis'] .lp-stat-value {
  color: var(--lp-primary);
}
.lp-stat[data-tone='warning'] .lp-stat-value {
  color: var(--lp-warning);
}
.lp-stat[data-tone='danger'] .lp-stat-value {
  color: var(--lp-danger);
}
.lp-stat-label {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
}
.lp-stat-note {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  line-height: 1.45;
}
</style>
