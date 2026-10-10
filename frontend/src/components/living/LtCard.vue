<script setup lang="ts">
withDefaults(
  defineProps<{
    /** Root element, e.g. 'section' with an aria-label for a landmark. */
    as?: string
    padding?: 'none' | 'compact' | 'regular'
    radius?: 'lg' | 'xl'
    /** Heading element for the title slot, matching the page outline. */
    heading?: 'h2' | 'h3' | 'h4'
  }>(),
  { as: 'div', padding: 'regular', radius: 'lg', heading: 'h2' },
)
</script>

<template>
  <component :is="as" class="lt-card" :data-padding="padding" :data-radius="radius">
    <header v-if="$slots.title || $slots.meta" class="lt-card-head">
      <component :is="heading" v-if="$slots.title" class="lt-card-title"><slot name="title" /></component>
      <span v-if="$slots.meta" class="lt-card-meta"><slot name="meta" /></span>
    </header>
    <slot />
  </component>
</template>

<style scoped>
.lt-card {
  border-radius: var(--lt-radius-lg);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-card);
}

.lt-card[data-radius='xl'] {
  border-radius: var(--lt-radius-xl);
}

.lt-card[data-padding='compact'] {
  padding: 14px 16px 16px;
}

.lt-card[data-padding='regular'] {
  padding: var(--lt-space-5) var(--lt-space-6) var(--lt-space-6);
}

.lt-card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--lt-space-3);
  margin-bottom: var(--lt-space-2);
}

.lt-card-title {
  margin: 0;
  font-size: var(--lt-text-footnote);
  letter-spacing: 0;
}

.lt-card-meta {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}
</style>
