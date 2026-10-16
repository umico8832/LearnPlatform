<script setup lang="ts">
import { LtButton } from '@/components/living'
import { useOAuthProviders } from './useOAuthProviders'

/** Third-party sign-in on the new baseline; renders nothing when none is enabled. */
const props = withDefaults(defineProps<{ preview?: boolean; disabled?: boolean }>(), {
  preview: false,
  disabled: false,
})
const { providers, startGoogleLogin } = useOAuthProviders({
  preview: () => props.preview,
  disabled: () => props.disabled,
})
</script>

<template>
  <div v-if="providers.length" class="providers">
    <p class="divider"><span>或</span></p>
    <LtButton
      v-for="provider in providers"
      :key="provider.id"
      size="lg"
      class="provider"
      :disabled="disabled"
      @click="startGoogleLogin()"
    >
      <template #icon><img :src="provider.icon" alt="" width="20" height="20" /></template>
      使用 {{ provider.name }} 登录
    </LtButton>
  </div>
</template>

<style scoped>
.providers {
  margin-top: var(--lt-space-5);
}

.divider {
  display: flex;
  align-items: center;
  gap: var(--lt-space-3);
  margin: 0 0 var(--lt-space-5);
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  border-top: 1px solid var(--lt-separator);
}

.provider {
  width: 100%;
}
</style>
