<script setup lang="ts">
defineProps<{ level: number; xpGained?: number }>()
const emit = defineEmits<{ dismiss: [] }>()
</script>

<template>
  <article class="gamification-level" role="status" aria-live="assertive">
    <p>等级提升</p>
    <h2>达到 Lv.{{ level }}</h2>
    <span v-if="xpGained">本次获得 {{ xpGained }} 经验</span>
    <button type="button" @click="emit('dismiss')">收下这份鼓励</button>
  </article>
</template>

<style scoped>
.gamification-level {
  width: min(420px, calc(100vw - var(--lp-space-8)));
  padding: var(--lp-space-8);
  text-align: center;
  color: var(--lp-on-primary);
  background: var(--lp-reward-level);
  border-radius: var(--lp-radius-xl);
  box-shadow: var(--lp-reward-shadow);
}
p,
h2,
span {
  margin: 0;
}
p {
  font-size: var(--lp-text-sm);
  opacity: 0.8;
}
h2 {
  margin-top: var(--lp-space-2);
  color: var(--lp-on-primary);
  font-size: var(--lp-text-4xl);
}
span {
  display: block;
  margin-top: var(--lp-space-2);
  font-size: var(--lp-text-sm);
  font-variant-numeric: tabular-nums;
}
button {
  margin-top: var(--lp-space-5);
  padding: var(--lp-space-2) var(--lp-space-4);
  color: var(--lp-reward-level);
  font: inherit;
  font-weight: var(--lp-weight-semibold);
  background: var(--lp-paper-0);
  border: 0;
  border-radius: var(--lp-radius-full);
  cursor: pointer;
}
button:focus-visible {
  outline: 3px solid var(--lp-home-yellow);
  outline-offset: 3px;
}
</style>
