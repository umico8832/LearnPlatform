<template>
  <div class="auth-page">
    <RouterLink class="auth-home" to="/">LearnPlatform<span class="auth-home-label">返回首页</span></RouterLink>
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
import { RouterLink } from 'vue-router'
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
  background: var(--lp-bg);
  color: var(--lp-text);
}
.auth-page::before,
.auth-page::after {
  content: '';
  position: absolute;
  z-index: 0;
  inset: 0;
  pointer-events: none;
}
.auth-page::before {
  background:
    radial-gradient(ellipse 48% 50% at 0 0, color-mix(in srgb, var(--lp-blue-200) 30%, transparent), transparent 75%),
    radial-gradient(
      ellipse 45% 50% at 100% 100%,
      color-mix(in srgb, var(--lp-primary-soft) 35%, transparent),
      transparent 75%
    );
}
.auth-page::after {
  background-image: radial-gradient(color-mix(in srgb, var(--lp-ink-400) 10%, transparent) 1px, transparent 1px);
  background-size: 24px 24px;
  opacity: 0.3;
}
.auth-home {
  position: absolute;
  z-index: 2;
  top: var(--lp-space-6);
  left: var(--lp-space-6);
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  color: var(--lp-text);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
  text-decoration: none;
}
.auth-home-label {
  color: var(--lp-text-muted);
  font-weight: var(--lp-weight-regular);
}
.auth-home:focus-visible {
  outline: var(--lp-focus-width) solid var(--lp-focus-ring);
  outline-offset: var(--lp-focus-offset);
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
  padding: var(--lp-space-16) var(--lp-space-4) var(--lp-space-12);
}
.auth-card {
  width: 100%;
  max-width: var(--lp-auth-card-width);
  padding: var(--lp-space-10);
  border: 1px solid var(--lp-auth-border);
  border-radius: var(--lp-auth-card-radius);
  background: color-mix(in srgb, var(--lp-surface) 92%, transparent);
  box-shadow: var(--lp-shadow-md);
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
}
.auth-symbol svg {
  width: calc(var(--lp-space-8) + var(--lp-space-1));
  height: calc(var(--lp-space-8) + var(--lp-space-1));
}
.auth-below-card:empty {
  display: none;
}
@media (prefers-reduced-motion: no-preference) {
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
@media (min-width: 1280px) {
  .auth-card {
    padding: var(--lp-space-12);
  }
}
@media (max-width: 430px) {
  .auth-main {
    padding: var(--lp-space-16) var(--lp-space-4) var(--lp-space-6);
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
