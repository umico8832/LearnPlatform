<script setup lang="ts">
import NetworkStage from './NetworkStage.vue'

/** "See it": the animation is the algorithm. Always dark, so the light has something to glow against. */
</script>

<template>
  <section id="see-it" class="see-it lt" data-appearance="dark" aria-labelledby="see-it-title">
    <header class="intro">
      <h2 id="see-it-title" class="title">Every step, <em>drawn.</em></h2>
      <p class="line">Click any junction. Light finds the shortest way there, exactly as Dijkstra would.</p>
    </header>
    <NetworkStage class="stage" />
  </section>
</template>

<style scoped>
/* Every section is exactly one screen: whatever stops on screen is one whole chapter. */
.see-it {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 100vh;
  overflow: hidden;
  /* Clears the floating bar so the content centres in the space below it. */
  padding-top: 68px;
  background: var(--lt-canvas);
  color: var(--lt-label);
}

.intro {
  max-width: 820px;
  margin: 0 auto;
  padding: 0 var(--lt-space-9);
  text-align: center;
}

.title {
  margin: 0;
  font-family: var(--lt-font-display);
  font-size: 96px;
  font-weight: 400;
  letter-spacing: -0.02em;
  line-height: 1;
}

.title em {
  font-style: italic;
}

.line {
  margin: var(--lt-space-5) 0 0;
  color: var(--lt-label-2);
  font-size: 21px;
  letter-spacing: -0.01em;
  line-height: 1.5;
}

.stage {
  height: 600px;
  margin-top: -40px;
}
</style>
