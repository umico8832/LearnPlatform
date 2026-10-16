<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useLivingTheme } from '@/composables/useLivingTheme'

/**
 * Shell for the sign-in pages on the new baseline (ADR-0011): the product name back to
 * the home page, one centred card for the form, and a line of alternatives under it.
 */
withDefaults(defineProps<{ titleId?: string }>(), { titleId: 'auth-title' })
useLivingTheme()
</script>

<template>
  <div class="frame">
    <RouterLink class="home" to="/">LearnPlatform</RouterLink>
    <main class="main">
      <section class="card" :aria-labelledby="titleId">
        <slot />
      </section>
      <div v-if="$slots.footer" class="below"><slot name="footer" /></div>
    </main>
  </div>
</template>

<style scoped>
.frame {
  min-height: 100dvh;
  background: var(--lt-canvas);
  color: var(--lt-label);
}

.home {
  position: absolute;
  top: var(--lt-space-6);
  left: var(--lt-space-7);
  color: var(--lt-label);
  font-size: var(--lt-text-headline);
  font-weight: 600;
  letter-spacing: var(--lt-tracking-title);
  text-decoration: none;
}

.main {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--lt-space-6);
  min-height: 100dvh;
  padding: var(--lt-space-10) var(--lt-space-4);
}

.card {
  width: 100%;
  max-width: 420px;
  padding: var(--lt-space-8) var(--lt-space-7) var(--lt-space-7);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-card);
}

.below {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}

.below :deep(a) {
  color: var(--lt-accent);
  text-decoration: none;
}

.below :deep(a:hover) {
  text-decoration: underline;
}
</style>
