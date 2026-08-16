<script setup lang="ts">
/**
 * 安静骨架屏：加载时以柔和块状占位，避免闪烁与跳动。
 */
withDefaults(
  defineProps<{
    rows?: number
    /** 是否渲染为卡片式（带边框与圆角） */
    card?: boolean
  }>(),
  { rows: 3, card: false },
)

const rowWidths = ['92%', '100%', '78%', '96%', '84%']
</script>

<template>
  <div class="lp-skeleton" :class="{ 'is-card': card }" :aria-hidden="true">
    <div
      v-for="index in rows"
      :key="index"
      class="lp-skeleton-line"
      :style="{ width: rowWidths[(index - 1) % rowWidths.length] }"
    />
  </div>
</template>

<style scoped>
.lp-skeleton {
  display: grid;
  gap: var(--lp-space-3);
  padding: var(--lp-space-2) 0;
}
.lp-skeleton.is-card {
  padding: var(--lp-space-5);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
}
.lp-skeleton-line {
  height: 14px;
  border-radius: var(--lp-radius-xs);
  background: linear-gradient(90deg, var(--lp-paper-200) 25%, var(--lp-paper-100) 50%, var(--lp-paper-200) 75%);
  background-size: 200% 100%;
  animation: lp-skeleton-shimmer 1.4s ease-in-out infinite;
}
@keyframes lp-skeleton-shimmer {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}
@media (prefers-reduced-motion: reduce) {
  .lp-skeleton-line {
    animation: none;
  }
}
</style>
