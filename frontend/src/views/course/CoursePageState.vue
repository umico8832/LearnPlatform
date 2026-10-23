<script setup lang="ts">
import { LtButton, LtCard } from '@/components/living'
defineProps<{ state: 'loading' | 'error' | 'empty'; title: string; description?: string; retryLabel?: string }>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <LtCard
    radius="xl"
    class="course-state"
    :aria-busy="state === 'loading'"
    :role="state === 'error' ? 'alert' : 'status'"
  >
    <h2>{{ title }}</h2>
    <p v-if="description">{{ description }}</p>
    <LtButton v-if="state === 'error'" variant="primary" @click="$emit('retry')">{{ retryLabel || '重试' }}</LtButton
    ><slot />
  </LtCard>
</template>
<style scoped>
.course-state {
  display: grid;
  justify-items: start;
  gap: var(--lt-space-4);
  padding: var(--lt-space-8);
}
h2 {
  margin: 0;
  font-size: var(--lt-text-title3);
}
p {
  margin: 0;
  color: var(--lt-label-2);
}
</style>
