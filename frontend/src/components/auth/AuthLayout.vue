<template>
  <div class="auth-page">
    <main class="auth-main">
      <section class="auth-card" :aria-labelledby="titleId">
        <div class="auth-symbol" aria-hidden="true">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3" />
            <path d="m10 8 4 4-4 4M4 12h10" />
          </svg>
        </div>
        <slot />
      </section>
      <div class="auth-below-card"><slot name="footer" /></div>
    </main>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ titleId?: string }>(), {
  titleId: 'auth-title',
})
</script>

<style scoped>
.auth-page {
  min-height: 100dvh;
  background: var(--lp-paper-0);
  color: var(--lp-text);
}
.auth-main {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--lp-space-6);
  padding: var(--lp-space-12) var(--lp-space-4);
}
.auth-card {
  width: 100%;
  max-width: var(--lp-auth-card-width);
  padding: var(--lp-space-10);
  border: 1px solid var(--lp-auth-border);
  border-radius: var(--lp-auth-card-radius);
  background: linear-gradient(to bottom, var(--lp-auth-surface-start), var(--lp-paper-0));
  box-shadow: var(--lp-auth-shadow);
}
.auth-symbol {
  display: grid;
  place-items: center;
  width: var(--lp-auth-symbol-size);
  height: var(--lp-auth-symbol-size);
  margin: 0 auto var(--lp-space-6);
  border-radius: var(--lp-radius-xl);
  background: var(--lp-paper-0);
  color: var(--lp-text);
  box-shadow: var(--lp-shadow-md);
}
.auth-symbol svg {
  width: var(--lp-space-8);
  height: var(--lp-space-8);
}
.auth-below-card:empty {
  display: none;
}
@media (prefers-reduced-motion: no-preference) {
  .auth-page.auth-enter.auth-motion-enter-active,
  .auth-page.auth-enter.auth-motion-leave-active {
    transition: opacity var(--lp-auth-duration-enter) var(--lp-ease-out);
  }
  .auth-page.auth-enter.auth-motion-enter-active .auth-main,
  .auth-page.auth-enter.auth-motion-leave-active .auth-main {
    transition: transform var(--lp-auth-duration-enter) var(--lp-ease-out);
  }
  .auth-page.auth-enter.auth-motion-leave-active,
  .auth-page.auth-enter.auth-motion-leave-active .auth-main {
    transition-duration: var(--lp-auth-duration-leave);
  }
  .auth-page.auth-enter.auth-motion-enter-from,
  .auth-page.auth-enter.auth-motion-leave-to {
    opacity: 0;
  }
  .auth-page.auth-enter.auth-motion-enter-from .auth-main,
  .auth-page.auth-enter.auth-motion-leave-to .auth-main {
    transform: translateY(var(--lp-space-4));
  }
}
@media (max-width: 430px) {
  .auth-main {
    padding: var(--lp-space-6) var(--lp-space-4);
    gap: var(--lp-space-4);
  }
  .auth-card {
    padding: var(--lp-space-8) var(--lp-space-5) var(--lp-space-6);
  }
  .auth-symbol {
    margin-bottom: var(--lp-space-6);
  }
}
</style>
