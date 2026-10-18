<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight } from '@lucide/vue'
import { LtLinkButton } from '@/components/living'
import { isAuthenticated } from '@/utils/auth'
import { links, menus } from '../showcaseContent'

/**
 * The last screen: one large line, left-aligned so it doesn't echo the centred calendar
 * above, the way in, and the footer under a faint horizon of light. Nothing moves here.
 */
const start = computed(() => (isAuthenticated() ? '/my-courses' : '/register'))

const columns = [
  ...menus.map((menu) => ({ title: menu.label, items: menu.items.map(({ title, to }) => ({ label: title, to })) })),
  { title: 'Project', items: links },
]
</script>

<template>
  <div class="ending lt" data-appearance="dark">
    <section class="closing" aria-labelledby="closing-title">
      <div class="inner">
        <h2 id="closing-title" class="title">Start with the one<br /><em>you got wrong.</em></h2>
        <div class="cta">
          <span class="glow">
            <LtLinkButton variant="primary" size="lg" :to="start">
              Get started
              <ArrowRight :size="18" aria-hidden="true" />
            </LtLinkButton>
          </span>
          <LtLinkButton variant="plain" size="lg" to="/courses">Browse courses</LtLinkButton>
        </div>
      </div>
    </section>

    <footer class="footer">
      <div class="inner footer-grid">
        <div class="brand">
          <RouterLink class="name" to="/">LearnPlatform</RouterLink>
          <p class="motto">A textbook that moves.</p>
        </div>
        <nav v-for="column in columns" :key="column.title" class="column" :aria-label="column.title">
          <h3 class="heading">{{ column.title }}</h3>
          <RouterLink v-for="item in column.items" :key="item.label" class="link" :to="item.to">
            {{ item.label }}
          </RouterLink>
        </nav>
      </div>
      <div class="inner legal">© 2026 LearnPlatform</div>
    </footer>
  </div>
</template>

<style scoped>
/* The closing line and the footer share one screen. */
.ending {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background:
    radial-gradient(60% 45% at 50% 100%, color-mix(in oklab, var(--lt-accent) 14%, transparent), transparent 70%),
    var(--lt-canvas);
  color: var(--lt-label);
}

.inner {
  width: 100%;
  max-width: 1320px;
  margin: 0 auto;
  padding: 0 var(--lt-space-9);
}

.closing {
  display: flex;
  flex: 1;
  align-items: center;
  padding-top: 68px;
}

.title {
  margin: 0;
  font-family: var(--lt-font-display);
  font-size: 128px;
  font-weight: 400;
  letter-spacing: -0.025em;
  line-height: 0.98;
}

.title em {
  color: var(--lt-label-2);
  font-style: italic;
}

.cta {
  display: flex;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-9);
}

/* Same as the hero's way in: the primary button gathers a glow under the pointer. */
.glow {
  display: inline-flex;
  border-radius: var(--lt-radius-pill);
  transition: filter var(--lt-dur-fade) var(--lt-ease-fade);
}

@media (hover: hover) {
  .glow:hover {
    filter: drop-shadow(0 8px 22px color-mix(in srgb, var(--lt-accent) 55%, transparent));
  }
}

.footer {
  padding: 56px 0 36px;
  border-top: 0.5px solid var(--lt-separator);
}

.footer-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) repeat(3, 180px);
  column-gap: 40px;
}

.name {
  color: var(--lt-label);
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.01em;
  text-decoration: none;
}

.motto {
  margin: 10px 0 0;
  color: var(--lt-label-3);
  font-size: 14px;
}

.column {
  display: grid;
  align-content: start;
  gap: 10px;
}

.heading {
  margin: 0 0 4px;
  color: var(--lt-label-3);
  font-size: 13px;
  font-weight: 500;
}

.link {
  justify-self: start;
  border-radius: 4px;
  color: var(--lt-label-2);
  font-size: 14px;
  text-decoration: none;
  transition: color var(--lt-dur-fade) var(--lt-ease-fade);
}

.link:hover {
  color: var(--lt-label);
}

.link:focus-visible,
.name:focus-visible {
  outline: 2px solid var(--lt-accent);
  outline-offset: 3px;
}

.legal {
  margin-top: 48px;
  color: var(--lt-label-3);
  font-size: 12px;
}
</style>
