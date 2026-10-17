<script setup lang="ts">
import { facts } from './showcaseContent'

/** Project facts drifting slowly along the bottom of the hero; hovering holds them still. */
</script>

<template>
  <div class="marquee" role="list" aria-label="Project facts">
    <div class="track">
      <template v-for="copy in 2" :key="copy">
        <div
          v-for="fact in facts"
          :key="`${copy}-${fact.label}`"
          class="fact"
          :role="copy === 1 ? 'listitem' : undefined"
          :aria-hidden="copy === 2 || undefined"
        >
          <span class="value">{{ fact.value }}</span>
          <span class="label">
            {{ fact.label }}
            <span class="note">{{ fact.note }}</span>
          </span>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.marquee {
  overflow: hidden;
  mask-image: linear-gradient(90deg, transparent, #000 12%, #000 88%, transparent);
}

.track {
  display: flex;
  gap: 72px;
  width: max-content;
  padding-right: 72px;
  animation: drift 48s linear infinite;
}

.marquee:hover .track {
  animation-play-state: paused;
}

@keyframes drift {
  to {
    transform: translateX(-50%);
  }
}

.fact {
  display: flex;
  align-items: baseline;
  gap: 14px;
  white-space: nowrap;
}

.value {
  color: var(--lt-label);
  font-family: var(--lt-font-display);
  font-size: 46px;
  line-height: 1;
}

.label {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: var(--lt-label-2);
  font-size: var(--lt-text-subhead);
}

.note {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}
</style>
