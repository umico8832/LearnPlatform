<script setup lang="ts">
import { computed } from 'vue'
import { LtCard } from '@/components/living'
import type { VizSession } from '../useVizSession'

/** All checkpoints of the scene; any can be revisited, first-try answers are counted. */
const props = defineProps<{ session: VizSession }>()

const items = computed(() =>
  props.session.checkpoints.value.map((checkpoint) => ({
    ...checkpoint,
    state: props.session.checkpointState(checkpoint.index),
  })),
)
const firstTry = computed(() => {
  const answers = props.session.answers.value
  return props.session.checkpoints.value.filter((item) => {
    const answer = answers.get(item.index)
    return answer?.solved && answer.firstTry
  }).length
})
</script>

<template>
  <LtCard v-if="items.length" as="section" padding="compact" heading="h2" aria-label="检查点">
    <template #title>检查点</template>
    <template #meta>一次答对 {{ firstTry }} / {{ items.length }}</template>
    <ol class="viz-checks">
      <li v-for="item in items" :key="item.index">
        <button
          type="button"
          class="viz-check"
          :data-state="item.state"
          :aria-current="item.state === 'asking' ? 'step' : undefined"
          @click="session.player.revisit(item.index)"
        >
          <span class="viz-check-diamond" aria-hidden="true" />
          <span class="viz-check-text">{{ item.predict.prompt }}</span>
        </button>
      </li>
    </ol>
  </LtCard>
</template>

<style scoped>
.viz-checks {
  display: grid;
  gap: 2px;
  margin: 8px -8px 0;
  padding: 0;
  list-style: none;
}

.viz-check {
  display: flex;
  align-items: flex-start;
  gap: var(--lt-space-3);
  width: 100%;
  padding: var(--lt-space-2);
  border: 0;
  border-radius: var(--lt-radius-sm);
  background: transparent;
  color: var(--lt-label);
  font-size: var(--lt-text-footnote);
  line-height: 1.45;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--lt-dur-fade) var(--lt-ease-fade);
}

.viz-check-diamond {
  flex: none;
  width: 10px;
  height: 10px;
  margin: 5px 2px 0;
  border: 1.5px solid var(--lt-label-3);
  border-radius: 2.5px;
  transform: rotate(45deg);
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    border-color var(--lt-dur-fade) var(--lt-ease-fade);
}

.viz-check[data-state='right'] .viz-check-diamond {
  border-color: var(--lt-green);
  background: var(--lt-green);
}

.viz-check[data-state='retried'] .viz-check-diamond {
  border-color: var(--lt-orange);
  background: var(--lt-orange);
}

.viz-check[data-state='asking'] .viz-check-diamond {
  border-color: var(--lt-purple);
  background: var(--lt-purple);
}

.viz-check-text {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

@media (hover: hover) and (pointer: fine) {
  .viz-check:hover {
    background: var(--lt-fill-3);
  }
}
</style>
