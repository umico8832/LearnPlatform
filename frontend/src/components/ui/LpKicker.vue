<script setup lang="ts">
/** 章节眉标：安静的小号大写标签，用于页面与区块标题上方。 */
withDefaults(defineProps<{ tone?: 'primary' | 'default' | 'success' | 'warning' | 'danger' }>(), {
  tone: 'primary',
})
</script>

<template>
  <span class="lp-kicker" :data-tone="tone"><slot /></span>
</template>

<style scoped>
.lp-kicker {
  display: inline-flex;
  align-items: center;
  gap: var(--lp-space-2);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-heavy);
  letter-spacing: var(--lp-tracking-wide);
  text-transform: uppercase;
  color: var(--lp-primary);
}
.lp-kicker[data-tone='default'] {
  color: var(--lp-text-muted);
}
.lp-kicker[data-tone='success'] {
  color: var(--lp-success);
}
.lp-kicker[data-tone='warning'] {
  color: var(--lp-warning);
}
.lp-kicker[data-tone='danger'] {
  color: var(--lp-danger);
}
</style>
