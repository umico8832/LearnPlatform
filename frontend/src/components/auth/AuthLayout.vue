<template>
  <div class="auth-page">
    <main class="auth-main">
      <section class="auth-card" :aria-labelledby="titleId">
        <div v-if="showSymbol" class="auth-symbol" aria-hidden="true">
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
withDefaults(defineProps<{ titleId?: string; showSymbol?: boolean }>(), {
  titleId: 'auth-title',
  showSymbol: true,
})
</script>

<style scoped>
.auth-page {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  min-height: 100dvh;
  background:
    url('@/assets/auth/auth-background-mist-blue.png') center / cover no-repeat,
    linear-gradient(135deg, var(--lp-blue-200), var(--lp-blue-100) 52%, var(--lp-paper-50));
  color: var(--lp-text);
}
.auth-page::before {
  content: '';
  position: absolute;
  z-index: 0;
  inset: -18%;
  pointer-events: none;
  background:
    radial-gradient(
      ellipse 46% 58% at 4% 58%,
      color-mix(in srgb, var(--lp-blue-500) 34%, transparent),
      transparent 72%
    ),
    radial-gradient(
      ellipse 52% 48% at 88% 94%,
      color-mix(in srgb, var(--lp-blue-400) 24%, transparent),
      transparent 74%
    );
  filter: blur(var(--lp-space-10));
  opacity: 0.34;
  transform: translate3d(-1.5%, -0.5%, 0) scale(1.03);
}
.auth-main {
  position: relative;
  z-index: 1;
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
  width: calc(var(--lp-space-8) + var(--lp-space-1));
  height: calc(var(--lp-space-8) + var(--lp-space-1));
}
.auth-below-card:empty {
  display: none;
}
@media (prefers-reduced-motion: no-preference) {
  .auth-page::before {
    animation: auth-background-drift 56s ease-in-out infinite;
    will-change: transform, opacity;
  }
  .auth-page.auth-enter.auth-motion-enter-active .auth-card,
  .auth-page.auth-enter.auth-motion-enter-active .auth-below-card,
  .auth-page.auth-enter.auth-motion-leave-active .auth-card,
  .auth-page.auth-enter.auth-motion-leave-active .auth-below-card {
    transition:
      opacity var(--lp-auth-duration-enter) var(--lp-ease-out),
      transform var(--lp-auth-duration-enter) var(--lp-ease-out);
  }
  .auth-page.auth-enter.auth-motion-leave-active .auth-card,
  .auth-page.auth-enter.auth-motion-leave-active .auth-below-card {
    transition-duration: var(--lp-auth-duration-leave);
  }
  .auth-page.auth-enter.auth-motion-enter-from .auth-card,
  .auth-page.auth-enter.auth-motion-enter-from .auth-below-card,
  .auth-page.auth-enter.auth-motion-leave-to .auth-card,
  .auth-page.auth-enter.auth-motion-leave-to .auth-below-card {
    opacity: 0;
    transform: translateY(var(--lp-space-4));
  }
}
@keyframes auth-background-drift {
  0%,
  100% {
    opacity: 0.32;
    transform: translate3d(-1.5%, -0.5%, 0) scale(1.03);
  }
  28% {
    opacity: 0.38;
    transform: translate3d(2.5%, 1%, 0) scale(1.055);
  }
  61% {
    opacity: 0.34;
    transform: translate3d(0.5%, -2%, 0) scale(1.04);
  }
  84% {
    opacity: 0.36;
    transform: translate3d(-2.5%, 1.5%, 0) scale(1.05);
  }
}
@media (min-width: 1280px) {
  .auth-card {
    padding: var(--lp-space-12);
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
