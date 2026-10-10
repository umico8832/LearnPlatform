<script setup lang="ts">
/**
 * One step of "从错到懂" with its margin note: a label and a dot whose colour
 * keeps the baseline's meaning (orange needs attention, purple predicts,
 * green is correct). A hairline joins each step to the next one.
 */
withDefaults(
  defineProps<{
    label: string
    tone?: 'accent' | 'orange' | 'purple' | 'green' | 'muted'
    current?: boolean
  }>(),
  { tone: 'muted', current: false },
)
</script>

<template>
  <section class="step" :data-current="current || undefined">
    <div class="step-rail" aria-hidden="true">
      <span class="step-dot" :data-tone="tone" />
      <span class="step-label">{{ label }}</span>
    </div>
    <div class="step-body"><slot /></div>
  </section>
</template>

<style scoped>
.step {
  --step-gap: var(--lt-space-9);

  position: relative;
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  column-gap: var(--lt-space-7);
}

.step + .step {
  margin-top: var(--step-gap);
}

/* 从本步的圆点连到下一步的圆点，两端各留一点空隙；最后一步不画。 */
.step:not(:last-child)::after {
  position: absolute;
  top: calc(var(--rail-offset, 0px) + 25px);
  bottom: calc(-1 * var(--step-gap) - 4px);
  left: 4px;
  width: 1px;
  background: var(--lt-separator);
  content: '';
}

.step-rail {
  display: flex;
  align-items: center;
  align-self: start;
  gap: 8px;
  height: 28px;
  margin-top: var(--rail-offset, 0px);
}

.step-dot {
  flex: none;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--lt-label-3);
  transition: background-color var(--lt-dur-fade) var(--lt-ease-fade);
}

.step-dot[data-tone='accent'] {
  background: var(--lt-accent);
}

.step-dot[data-tone='orange'] {
  background: var(--lt-orange);
}

.step-dot[data-tone='purple'] {
  background: var(--lt-purple);
}

.step-dot[data-tone='green'] {
  background: var(--lt-green);
}

.step-label {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
  font-weight: 500;
}

.step[data-current] .step-label {
  color: var(--lt-label);
  font-weight: 600;
}

.step-body {
  min-width: 0;
}
</style>
