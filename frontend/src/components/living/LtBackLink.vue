<script setup lang="ts">
import { ChevronLeft } from '@lucide/vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

defineProps<{
  to: RouteLocationRaw
  label: string
}>()
</script>

<template>
  <RouterLink class="lt-back-link" :to="to">
    <ChevronLeft :size="18" aria-hidden="true" />
    {{ label }}
  </RouterLink>
</template>

<style scoped>
.lt-back-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-left: -6px;
  padding: 6px 10px 6px 4px;
  border-radius: var(--lt-radius-pill);
  color: var(--lt-label-2);
  font-size: var(--lt-text-subhead);
  text-decoration: none;
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    color var(--lt-dur-fade) var(--lt-ease-fade);
}

.lt-back-link:hover {
  background: var(--lt-fill-3);
  color: var(--lt-label);
}

.lt-back-link:focus-visible {
  outline: 2px solid var(--lt-accent);
  outline-offset: 2px;
}
</style>
