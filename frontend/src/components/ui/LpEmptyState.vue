<script setup lang="ts">
/**
 * 空状态：标题 + 描述 + 可选动作。比 el-empty 更符合教材式安静表达。
 */
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
      <slot name="mark"><span class="lp-empty-mark-glyph">∅</span></slot>
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
.lp-empty-mark-glyph {
  font-family: var(--lp-font-display);
  font-size: var(--lp-text-xl);
  line-height: 1;
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
