<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'plain' | 'primary' | 'quiet'
    size?: 'sm' | 'md' | 'lg'
    type?: 'button' | 'submit' | 'reset'
    disabled?: boolean
  }>(),
  { variant: 'plain', size: 'md', type: 'button', disabled: false },
)
</script>

<template>
  <button class="lt-button" :type="type" :data-variant="variant" :data-size="size" :disabled="disabled">
    <slot name="icon" />
    <span v-if="$slots.default" class="lt-button-label"><slot /></span>
    <slot name="trailing" />
  </button>
</template>

<style scoped>
.lt-button {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: var(--lt-control-md);
  padding: 0 14px;
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font: 500 var(--lt-text-footnote) / 1 var(--lt-font);
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    box-shadow var(--lt-dur-fade) var(--lt-ease-fade),
    transform var(--lt-dur-snappy) var(--lt-ease-snappy);
}

.lt-button[data-size='sm'] {
  height: var(--lt-control-sm);
  padding: 0 12px;
  font-size: var(--lt-text-caption);
}

.lt-button[data-size='lg'] {
  height: var(--lt-control-lg);
  padding: 0 22px;
  font-size: var(--lt-text-body);
}

.lt-button[data-variant='primary'] {
  background: var(--lt-accent-fill);
  color: var(--lt-on-accent);
}

.lt-button[data-variant='quiet'] {
  background: transparent;
  color: var(--lt-accent);
}

.lt-button:active:not(:disabled) {
  transform: scale(0.96);
}

.lt-button[data-variant='primary']:active:not(:disabled) {
  background: var(--lt-accent-fill-pressed);
}

.lt-button:disabled {
  color: var(--lt-label-3);
  cursor: not-allowed;
}

.lt-button[data-variant='primary']:disabled {
  background: var(--lt-fill);
}

.lt-button :deep(svg) {
  flex: none;
  width: 16px;
  height: 16px;
}

@media (hover: hover) and (pointer: fine) {
  .lt-button[data-variant='plain']:hover:not(:disabled) {
    background: var(--lt-fill-hover);
  }

  .lt-button[data-variant='primary']:hover:not(:disabled) {
    background: var(--lt-accent-fill-pressed);
    box-shadow: 0 0 0 4px var(--lt-accent-soft);
  }

  .lt-button[data-variant='quiet']:hover:not(:disabled) {
    background: var(--lt-accent-soft);
  }
}
</style>
