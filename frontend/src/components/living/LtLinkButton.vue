<script setup lang="ts">
import { RouterLink, type RouteLocationRaw } from 'vue-router'

/**
 * A link that looks like LtButton: same sizes and variants, but it navigates, so it stays
 * a real link for assistive technology and middle-click. Pass `to` for a route, `href` for
 * an in-page anchor or another site.
 */
withDefaults(
  defineProps<{
    to?: RouteLocationRaw
    href?: string
    variant?: 'plain' | 'primary' | 'quiet'
    size?: 'sm' | 'md' | 'lg'
  }>(),
  { to: undefined, href: undefined, variant: 'plain', size: 'md' },
)
</script>

<template>
  <RouterLink v-if="to" class="lt-link-button" :to="to" :data-variant="variant" :data-size="size">
    <slot />
  </RouterLink>
  <a v-else class="lt-link-button" :href="href" :data-variant="variant" :data-size="size"><slot /></a>
</template>

<style scoped>
.lt-link-button {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: var(--lt-control-md);
  padding: 0 14px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font: 500 var(--lt-text-footnote) / 1 var(--lt-font);
  white-space: nowrap;
  text-decoration: none;
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    transform var(--lt-dur-snappy) var(--lt-ease-snappy);
}

.lt-link-button[data-size='sm'] {
  height: var(--lt-control-sm);
  padding: 0 12px;
  font-size: var(--lt-text-caption);
}

.lt-link-button[data-size='lg'] {
  height: var(--lt-control-lg);
  padding: 0 22px;
  font-size: var(--lt-text-body);
}

.lt-link-button[data-variant='primary'] {
  background: var(--lt-accent-fill);
  color: var(--lt-on-accent);
}

.lt-link-button[data-variant='quiet'] {
  background: transparent;
  color: var(--lt-accent);
}

.lt-link-button:active {
  transform: scale(0.96);
}

.lt-link-button[data-variant='primary']:active {
  background: var(--lt-accent-fill-pressed);
}

.lt-link-button :deep(svg) {
  flex: none;
  width: 16px;
  height: 16px;
}

@media (hover: hover) and (pointer: fine) {
  .lt-link-button[data-variant='plain']:hover {
    background: var(--lt-fill);
  }

  .lt-link-button[data-variant='primary']:hover {
    background: var(--lt-accent-fill-pressed);
  }

  .lt-link-button[data-variant='quiet']:hover {
    background: var(--lt-accent-soft);
  }
}
</style>
