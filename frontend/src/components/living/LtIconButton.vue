<script setup lang="ts">
withDefaults(
  defineProps<{
    /** Accessible name; icon-only buttons have no visible text. */
    label: string
    variant?: 'plain' | 'primary' | 'quiet'
    size?: 'sm' | 'md' | 'lg'
    disabled?: boolean
    pressed?: boolean
  }>(),
  { variant: 'plain', size: 'md', disabled: false, pressed: undefined },
)
</script>

<template>
  <button
    type="button"
    class="lt-icon-button"
    :data-variant="variant"
    :data-size="size"
    :aria-label="label"
    :title="label"
    :aria-pressed="pressed"
    :disabled="disabled"
  >
    <slot />
  </button>
</template>

<style scoped>
.lt-icon-button {
  display: inline-grid;
  flex: none;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: transparent;
  color: var(--lt-label);
  cursor: pointer;
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    color var(--lt-dur-fade) var(--lt-ease-fade),
    box-shadow var(--lt-dur-fade) var(--lt-ease-fade),
    transform var(--lt-dur-snappy) var(--lt-ease-snappy);
}

.lt-icon-button[data-size='sm'] {
  width: var(--lt-control-sm);
  height: var(--lt-control-sm);
}

.lt-icon-button[data-size='lg'] {
  width: var(--lt-control-lg);
  height: var(--lt-control-lg);
}

.lt-icon-button[data-variant='primary'] {
  background: var(--lt-accent-fill);
  color: var(--lt-on-accent);
  box-shadow: var(--lt-shadow-accent);
}

.lt-icon-button[data-variant='quiet'] {
  color: var(--lt-label-3);
}

.lt-icon-button[aria-pressed='true'] {
  background: var(--lt-accent-soft);
  color: var(--lt-accent);
}

.lt-icon-button:active:not(:disabled) {
  transform: scale(0.9);
}

.lt-icon-button:disabled {
  color: var(--lt-label-3);
  cursor: not-allowed;
  opacity: 0.55;
}

.lt-icon-button[data-variant='primary']:disabled {
  background: var(--lt-fill);
  box-shadow: none;
}

.lt-icon-button :deep(svg) {
  width: 20px;
  height: 20px;
}

.lt-icon-button[data-size='sm'] :deep(svg) {
  width: 16px;
  height: 16px;
}

@media (hover: hover) and (pointer: fine) {
  .lt-icon-button:not([data-variant='primary'], [aria-pressed='true']):hover:not(:disabled) {
    background: var(--lt-fill-2);
    color: var(--lt-label);
  }

  .lt-icon-button[data-variant='primary']:hover:not(:disabled) {
    background: var(--lt-accent-fill-pressed);
    box-shadow:
      0 0 0 4px var(--lt-accent-soft),
      var(--lt-shadow-accent);
  }
}
</style>
