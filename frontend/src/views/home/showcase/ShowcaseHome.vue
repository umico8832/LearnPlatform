<script setup lang="ts">
import '@fontsource/instrument-serif/latin-400.css'
import '@fontsource/instrument-serif/latin-400-italic.css'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowDown, ArrowRight } from '@lucide/vue'
import { LtLinkButton } from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'
import { springAnimate, springs } from '@/motion'
import { isAuthenticated } from '@/utils/auth'
import ClosingSection from './closing/ClosingSection.vue'
import HeroCard from './HeroCard.vue'
import HeroHeadline from './HeroHeadline.vue'
import LabSection from './lab/LabSection.vue'
import Magnetic from './Magnetic.vue'
import SeeItSection from './network/SeeItSection.vue'
import ShowcaseNav from './ShowcaseNav.vue'
import StatsMarquee from './StatsMarquee.vue'
import RememberSection from './remember/RememberSection.vue'
import { heroWords } from './showcaseContent'

/**
 * English showcase homepage (C14). The headline word and the card beside it move
 * together through what the platform does; the sections below expand each word.
 */
useLivingTheme()

const index = ref(0)
const word = computed(() => heroWords[index.value])
const start = computed(() => (isAuthenticated() ? '/my-courses' : '/register'))
const lower = ref<HTMLElement | null>(null)

/** The card decides when a word has been shown enough; the headline follows it. */
function advance() {
  index.value = (index.value + 1) % heroWords.length
}

function select(next: number) {
  index.value = next
}

onMounted(() => {
  // The homepage is dark only; the document follows so overscroll and scrollbars match.
  document.documentElement.dataset.appearance = 'dark'
  if (lower.value) {
    springAnimate(
      lower.value,
      [
        { opacity: 0, transform: 'translateY(16px)' },
        { opacity: 1, transform: 'none' },
      ],
      springs.camera,
      { delay: 420, fill: 'backwards' },
    )
  }
})

onBeforeUnmount(() => {
  delete document.documentElement.dataset.appearance
})
</script>

<template>
  <div class="showcase">
    <ShowcaseNav />

    <section class="hero">
      <div class="hero-inner">
        <div class="copy">
          <HeroHeadline :word="word" :all="heroWords" />
          <div ref="lower" class="lower">
            <p class="lede">
              A textbook that moves. Every step drawn out, every mistake explained, every lesson back right before you
              forget.
            </p>
            <div class="cta">
              <Magnetic class="glow">
                <LtLinkButton class="nudge-right" variant="primary" size="lg" :to="start">
                  Get started
                  <ArrowRight :size="18" aria-hidden="true" />
                </LtLinkButton>
              </Magnetic>
              <Magnetic>
                <LtLinkButton class="nudge-down" variant="plain" size="lg" href="#see-it">
                  See how it works
                  <ArrowDown :size="18" aria-hidden="true" />
                </LtLinkButton>
              </Magnetic>
            </div>
          </div>
        </div>
        <HeroCard class="card" :word="word" :words="heroWords" @select="select" @advance="advance" />
      </div>

      <StatsMarquee class="facts" />
    </section>

    <SeeItSection />
    <LabSection />
    <RememberSection />
    <ClosingSection />
  </div>
</template>

<style scoped>
.showcase {
  min-height: 100vh;
  background: var(--lt-canvas);
}

.hero {
  display: flex;
  flex-direction: column;
  min-height: max(100vh, 760px);
  padding-top: 84px;
}

/* One left edge for the bar, the copy and the facts; the copy and the card share a centre line. */
.hero-inner {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 1fr) 496px;
  column-gap: 72px;
  align-items: center;
  width: 100%;
  max-width: 1320px;
  margin: 0 auto;
  padding: var(--lt-space-9) var(--lt-space-9) var(--lt-space-10);
}

.lower {
  margin-top: var(--lt-space-8);
}

.lede {
  max-width: 30em;
  margin: 0;
  color: var(--lt-label-2);
  font-size: 21px;
  letter-spacing: -0.01em;
  line-height: 1.5;
}

.cta {
  display: flex;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-7);
}

/* The arrows point where each button goes and lean that way under the pointer. */
.cta svg {
  transition: translate var(--lt-dur-snappy) var(--lt-ease-snappy);
}

.glow {
  border-radius: var(--lt-radius-pill);
  transition: filter var(--lt-dur-fade) var(--lt-ease-fade);
}

@media (hover: hover) and (pointer: fine) {
  .nudge-right:hover svg {
    translate: 3px 0;
  }

  .nudge-down:hover svg {
    translate: 0 3px;
  }

  .glow:hover {
    filter: drop-shadow(0 8px 22px color-mix(in srgb, var(--lt-accent) 55%, transparent));
  }
}

.card {
  min-height: 380px;
  justify-content: center;
}

.facts {
  padding: var(--lt-space-6) 0 var(--lt-space-7);
  border-top: 0.5px solid var(--lt-separator);
}
</style>
