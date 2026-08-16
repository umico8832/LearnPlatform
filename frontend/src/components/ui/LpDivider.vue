<script setup lang="ts">
/** 细分隔线：用于列表项或区块之间的轻量分隔。 */
withDefaults(defineProps<{ spacing?: 'sm' | 'md' | 'lg' }>(), { spacing: 'md' })
</script>

<template>
  <hr class="lp-divider" :data-spacing="spacing" />
</template>

<style scoped>
.lp-divider {
  margin: 0;
  border: 0;
  border-top: var(--lp-border-hairline);
}
.lp-divider[data-spacing='sm'] {
  margin-block: var(--lp-space-2);
}
.lp-divider[data-spacing='md'] {
  margin-block: var(--lp-space-4);
}
.lp-divider[data-spacing='lg'] {
  margin-block: var(--lp-space-6);
}
</style>
