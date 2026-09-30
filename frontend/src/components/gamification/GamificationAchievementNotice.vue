<script setup lang="ts">
import type { GamificationAchievement } from './types'

defineProps<{ achievement: GamificationAchievement }>()
const emit = defineEmits<{ dismiss: [] }>()
</script>

<template>
  <article class="gamification-achievement" role="status" aria-live="polite">
    <span class="gamification-achievement__mark" aria-hidden="true">{{ achievement.icon || '✦' }}</span>
    <div>
      <p>成就已解锁</p>
      <h3>{{ achievement.title }}</h3>
      <small v-if="achievement.description">{{ achievement.description }}</small>
    </div>
    <button type="button" aria-label="关闭成就提示" @click="emit('dismiss')">×</button>
  </article>
</template>

<style scoped>
.gamification-achievement {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: var(--lp-space-3);
  align-items: start;
  width: min(360px, calc(100vw - var(--lp-space-8)));
  padding: var(--lp-space-4);
  color: var(--lp-text);
  background: var(--lp-surface);
  border: 1px solid var(--lp-reward-achievement);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-reward-shadow);
}
.gamification-achievement__mark {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  color: var(--lp-reward-achievement);
  background: var(--lp-reward-achievement-soft);
  border-radius: var(--lp-radius-full);
}
p,
h3,
small {
  margin: 0;
}
p,
small {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
h3 {
  margin-top: 2px;
  font-size: var(--lp-text-md);
}
button {
  padding: 0;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xl);
  line-height: 1;
  background: transparent;
  border: 0;
  cursor: pointer;
}
button:focus-visible {
  outline: 2px solid var(--lp-primary);
  outline-offset: 2px;
  border-radius: var(--lp-radius-xs);
}
</style>
