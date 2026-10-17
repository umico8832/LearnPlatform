<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { springAnimate, springs } from '@/motion'

/**
 * "Explain": a still frame, the quiet section between the moving ones. One moment is
 * shown as if photographed: the tutor's first draft gives the answer away, both checks
 * stop it, and what reaches the learner is a nudge. It settles in once, left to right,
 * the way the reply travels, and then holds still.
 */
const root = ref<HTMLElement | null>(null)
const waiting = ref(true)
let observer: IntersectionObserver | null = null

function enter() {
  waiting.value = false
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return
  root.value?.querySelectorAll('[data-beat]').forEach((el) => {
    springAnimate(
      el,
      [
        { opacity: 0, transform: 'translateY(14px)' },
        { opacity: 1, transform: 'none' },
      ],
      springs.camera,
      { delay: Number((el as HTMLElement).dataset.beat) * 160, fill: 'backwards' },
    )
  })
}

onMounted(() => {
  observer = new IntersectionObserver(
    ([entry]) => {
      if (!entry.isIntersecting) return
      observer?.disconnect()
      enter()
    },
    { threshold: 0.45 },
  )
  if (root.value) observer.observe(root.value)
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <section
    id="explain"
    ref="root"
    class="explain lt"
    data-appearance="dark"
    :data-waiting="waiting || undefined"
    aria-labelledby="explain-title"
  >
    <header class="intro">
      <h2 id="explain-title" class="title">It won’t give it away.<br /><em>Not until you’ve tried.</em></h2>
      <p class="line">Every reply is checked twice before you see it.</p>
    </header>

    <figure
      class="moment"
      aria-label="A request for the answer, a draft that would reveal it, and the hint sent instead"
    >
      <div class="wire" aria-hidden="true" />
      <p class="bubble ask" data-beat="0">Just tell me the answer.</p>
      <p class="bubble draft" data-beat="1">
        <s class="leak">The answer is C,</s>
        because…
      </p>
      <div class="checks" data-beat="2" aria-hidden="true">
        <span class="check" />
        <span class="check" />
      </div>
      <p class="bubble reply" data-beat="3">Not yet. Give it one try first, and I’ll meet you there.</p>
    </figure>
  </section>
</template>

<style scoped>
/* One screen, like the sections above; the content centres below the floating bar. */
.explain {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 100vh;
  padding: 68px var(--lt-space-9) 0;
  background: var(--lt-canvas);
  color: var(--lt-label);
}

.explain[data-waiting] [data-beat] {
  opacity: 0;
}

.intro {
  text-align: center;
}

.title {
  margin: 0;
  font-family: var(--lt-font-display);
  font-size: 80px;
  font-weight: 400;
  letter-spacing: -0.02em;
  line-height: 1;
}

.title em {
  color: var(--lt-label-2);
  font-style: italic;
}

.line {
  margin: var(--lt-space-5) 0 0;
  color: var(--lt-label-2);
  font-size: 19px;
  letter-spacing: -0.01em;
  line-height: 1.5;
}

/* Request, draft, the two checks, the reply: read left to right along one faint wire. */
.moment {
  position: relative;
  display: grid;
  grid-template-columns: 260px 340px 120px 340px;
  column-gap: 40px;
  align-items: center;
  justify-content: center;
  margin: 112px 0 0;
}

.wire {
  position: absolute;
  top: 50%;
  right: 8%;
  left: 8%;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent,
    var(--lt-separator) 20%,
    var(--lt-separator) 60%,
    color-mix(in oklab, var(--lt-accent) 60%, transparent) 80%,
    transparent
  );
}

.bubble {
  position: relative;
  margin: 0;
  padding: 18px 22px;
  border-radius: 22px;
  font-size: 19px;
  letter-spacing: -0.01em;
  line-height: 1.45;
}

.ask {
  justify-self: end;
  background: var(--lt-fill-2);
  color: var(--lt-label);
}

/* Never sent: no fill, a dashed edge, and the part that would give it away struck out. */
.draft {
  border: 1px dashed color-mix(in oklab, var(--lt-label) 22%, transparent);
  background: var(--lt-canvas);
  color: var(--lt-label-3);
}

.leak {
  color: color-mix(in oklab, var(--lt-red-text) 80%, var(--lt-label-3));
  text-decoration-color: color-mix(in oklab, var(--lt-red) 85%, transparent);
  text-decoration-thickness: 1.5px;
}

/* The two checks: thin planes of light the reply has to pass through. */
.checks {
  display: flex;
  justify-content: center;
  gap: 26px;
  height: 220px;
}

.check {
  width: 1.5px;
  border-radius: 1px;
  background: linear-gradient(180deg, transparent, color-mix(in oklab, var(--lt-accent) 85%, white) 50%, transparent);
  box-shadow: 0 0 18px color-mix(in oklab, var(--lt-accent) 70%, transparent);
}

.reply {
  background: color-mix(in oklab, var(--lt-accent) 16%, var(--lt-surface));
  color: var(--lt-label);
  box-shadow:
    0 0 0 1px color-mix(in oklab, var(--lt-accent) 55%, transparent),
    0 0 48px color-mix(in oklab, var(--lt-accent) 24%, transparent);
}
</style>
