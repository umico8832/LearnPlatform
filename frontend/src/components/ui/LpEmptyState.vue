<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    description?: string
    compact?: boolean
    kind?: 'book' | 'search'
  }>(),
  { description: '', compact: false, kind: 'book' },
)
</script>

<template>
  <div class="lp-empty" :class="{ 'is-compact': compact }">
    <div class="lp-empty-mark" aria-hidden="true">
      <slot name="mark">
        <svg class="lp-empty-illustration" width="96" height="72" viewBox="0 0 96 72" fill="none">
          <ellipse class="lp-empty-wash" cx="48" cy="59" rx="39" ry="5" />
          <g v-if="kind === 'search'">
            <path class="lp-empty-page" d="M23 14h37v39H23z" />
            <path class="lp-empty-detail" d="M30 23h22M30 30h13M30 37h9" />
            <circle class="lp-empty-page" cx="60" cy="43" r="12" />
            <path d="m69 52 10 10" />
          </g>
          <g v-else>
            <path class="lp-empty-page" d="M48 23c-9-6-19-7-30-5v34c11-2 21-1 30 5 9-6 19-7 30-5V18c-11-2-21-1-30 5Z" />
            <path d="M48 23v34" />
            <path class="lp-empty-detail" d="m27 28 12 2m-12 6 12 2m-12 6 7 1m23-15 12-2m-12 10 12-2m-12 10 7-1" />
          </g>
          <path class="lp-empty-detail" d="M10 31h3M81 18h4M13 43h2" />
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
  width: 96px;
  height: 72px;
  margin-bottom: var(--lp-space-1);
  color: var(--lp-text-muted);
}
.lp-empty.is-compact .lp-empty-mark {
  width: 64px;
  height: 48px;
}
.lp-empty-illustration {
  width: 100%;
  height: 100%;
  color: var(--lp-primary);
  stroke: currentColor;
  stroke-width: 1.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.lp-empty-page {
  fill: var(--lp-surface);
}
.lp-empty-wash {
  fill: var(--lp-primary-soft);
  stroke: none;
}
.lp-empty-detail {
  stroke: var(--lp-border-strong);
}
.lp-empty-title {
  margin: 0;
  overflow-wrap: anywhere;
  font-size: var(--lp-text-lg);
  font-weight: var(--lp-weight-semibold);
  color: var(--lp-text);
}
.lp-empty-desc {
  margin: 0;
  max-width: 340px;
  overflow-wrap: anywhere;
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
