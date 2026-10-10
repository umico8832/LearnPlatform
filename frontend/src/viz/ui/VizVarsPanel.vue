<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { LtCard } from '@/components/living'
import type { VarView } from '../engine/types'

/** Variable watch: a value that just changed flashes once in its identity colour. */
const props = defineProps<{ vars: VarView[] }>()

const previous = ref(new Map<string, string>())
let latest = new Map<string, string>()

watch(
  () => props.vars,
  (next) => {
    previous.value = latest
    latest = new Map(next.map((item) => [item.name, item.value]))
  },
  { immediate: true },
)

const rows = computed(() =>
  props.vars.map((item) => {
    const before = previous.value.get(item.name)
    const changed = before !== undefined && before !== item.value
    // A new key remounts a changed row so its flash animation plays again.
    return { ...item, changed, key: changed ? `${item.name}=${item.value}` : item.name }
  }),
)
</script>

<template>
  <LtCard as="section" padding="compact" heading="h2" aria-label="变量">
    <template #title>变量</template>
    <div class="viz-vars">
      <div v-for="row in rows" :key="row.key" class="viz-var" :data-color="row.color" :data-changed="row.changed">
        <span class="viz-var-name"><span v-if="row.color" class="viz-var-dot" />{{ row.name }}</span>
        <span class="viz-var-value">{{ row.value }}</span>
      </div>
    </div>
  </LtCard>
</template>

<style scoped>
.viz-vars {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  margin-top: 10px;
}

.viz-var {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-2);
  height: 32px;
  padding: 0 10px;
  border-radius: var(--lt-radius-sm);
  background: var(--lt-fill-3);
}

.viz-var[data-changed='true'] {
  animation: viz-flash 1s ease-out;
}

@keyframes viz-flash {
  0% {
    background: color-mix(in srgb, var(--var-color, var(--lt-accent)) 22%, transparent);
  }
  100% {
    background: var(--lt-fill-3);
  }
}

.viz-var-name {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--var-color, var(--lt-label));
  font: 600 var(--lt-text-footnote) / 1 var(--lt-font-mono);
}

.viz-var-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--var-color);
}

.viz-var-value {
  overflow: hidden;
  color: var(--lt-label-2);
  font: 500 var(--lt-text-footnote) / 1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
  .viz-var[data-changed='true'] {
    animation: none;
    background: color-mix(in srgb, var(--var-color, var(--lt-accent)) 14%, var(--lt-fill-3));
  }
}
</style>
