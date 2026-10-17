<script setup lang="ts">
import LabWindow from './LabWindow.vue'

/** "Understand": the real visualization, handed over. It is simulated, so it can run backwards. */
</script>

<template>
  <section id="understand" class="lab lt" data-appearance="dark" aria-labelledby="lab-title">
    <div class="inner">
      <header class="intro">
        <h2 id="lab-title" class="title">Run it backwards.<br /><em>It still makes sense.</em></h2>
        <p class="line">Drag time either way. Shuffle the numbers and it works the whole thing out again.</p>
      </header>
      <LabWindow />
    </div>
  </section>
</template>

<style scoped>
/* One screen, like the sections above; the content centres below the floating bar. */
.lab {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 100vh;
  padding-top: 68px;
  background: var(--lt-canvas);
  color: var(--lt-label);
}

.inner {
  width: 100%;
  max-width: 1320px;
  margin: 0 auto;
  padding: 0 var(--lt-space-9);
}

/* Linear-style split: the claim large on the left, the plain explanation on the right, sharing a baseline. */
.intro {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  column-gap: 72px;
  align-items: end;
  margin-bottom: 56px;
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
  margin: 0 0 6px;
  color: var(--lt-label-2);
  font-size: 19px;
  letter-spacing: -0.01em;
  line-height: 1.5;
}
</style>
