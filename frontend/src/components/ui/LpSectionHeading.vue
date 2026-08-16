<script setup lang="ts">
/**
 * 区块标题：kicker + 标题 + 可选描述，右侧可放操作。用于页面内的分区。
 */
withDefaults(
  defineProps<{
    kicker?: string
    title?: string
    description?: string
    aside?: string
  }>(),
  { kicker: '', title: '', description: '', aside: '' },
)
</script>

<template>
  <div class="lp-section-heading">
    <div class="lp-section-heading-copy">
      <LpKicker v-if="kicker">{{ kicker }}</LpKicker>
      <h2 v-if="title" class="lp-section-heading-title">{{ title }}</h2>
      <p v-if="description" class="lp-section-heading-desc">{{ description }}</p>
    </div>
    <div v-if="aside || $slots.aside" class="lp-section-heading-aside">
      <slot name="aside">{{ aside }}</slot>
    </div>
  </div>
</template>

<style scoped>
.lp-section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--lp-space-4);
}
.lp-section-heading-copy {
  min-width: 0;
}
.lp-section-heading-title {
  margin-top: var(--lp-space-1);
  font-size: var(--lp-text-xl);
  font-weight: var(--lp-weight-bold);
  line-height: var(--lp-leading-snug);
  color: var(--lp-text);
}
.lp-section-heading-desc {
  margin: var(--lp-space-1) 0 0;
  max-width: 560px;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}
.lp-section-heading-aside {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  flex-shrink: 0;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}
</style>
