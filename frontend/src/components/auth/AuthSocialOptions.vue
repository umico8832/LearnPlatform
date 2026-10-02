<template>
  <div v-if="providers.length" class="auth-social" aria-label="第三方登录">
    <div class="auth-social-divider"><span>其他登录方式</span></div>
    <div class="auth-social-buttons">
      <button
        v-for="provider in providers"
        :key="provider.id"
        type="button"
        :disabled="disabled || !provider.enabled"
        :class="{ 'is-enabled': provider.enabled }"
        :aria-label="provider.enabled ? `使用 ${provider.name} 登录` : `${provider.name} 登录，暂未开放`"
        :title="provider.enabled ? `使用 ${provider.name} 登录` : `${provider.name} 登录暂未开放`"
        @click="provider.id === 'google' && startGoogleLogin()"
      >
        <img :src="provider.icon" alt="" width="24" height="24" /><span>使用 {{ provider.name }} 登录</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getOAuthProviders } from '@/api/auth'
import googleIcon from '@/assets/auth/google.png'

const props = withDefaults(defineProps<{ preview?: boolean; disabled?: boolean }>(), {
  preview: false,
  disabled: false,
})
const route = useRoute()
const googleEnabled = ref(props.preview)
const providers = computed(() =>
  googleEnabled.value ? [{ id: 'google', name: 'Google', icon: googleIcon, enabled: true }] : [],
)
let alive = true
onBeforeUnmount(() => {
  alive = false
})

onMounted(async () => {
  if (props.preview) return
  try {
    const response = await getOAuthProviders()
    if (alive) googleEnabled.value = response.data.google
  } catch {
    if (alive) googleEnabled.value = false
  }
})

function startGoogleLogin() {
  if (!googleEnabled.value || props.disabled) return
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  if (redirect.startsWith('/') && !redirect.startsWith('//')) {
    sessionStorage.setItem('oauth_login_redirect', redirect)
  } else {
    sessionStorage.removeItem('oauth_login_redirect')
  }
  const apiBaseUrl = String(import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
  window.location.assign(`${apiBaseUrl}/auth/oauth/authorization/google`)
}
</script>

<style scoped>
.auth-social {
  margin-top: var(--lp-space-6);
}
.auth-social-divider {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  margin-bottom: var(--lp-space-5);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
}
.auth-social-divider::before,
.auth-social-divider::after {
  content: '';
  flex: 1;
  border-top: 1px dashed var(--lp-auth-input-border);
}
.auth-social-buttons {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--lp-space-3);
}
.auth-social-buttons button {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: var(--lp-space-3);
  font: inherit;
  color: var(--lp-text);
  height: calc(var(--lp-space-12) + var(--lp-space-2));
  padding: var(--lp-space-3);
  border: 1px solid var(--lp-border-strong);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-paper-0);
  cursor: not-allowed;
  opacity: 0.52;
}
.auth-social-buttons button.is-enabled {
  cursor: pointer;
  opacity: 1;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}
.auth-social-buttons button.is-enabled:hover {
  border-color: var(--lp-primary);
  box-shadow: var(--lp-shadow-sm);
}
.auth-social-buttons button.is-enabled:focus-visible {
  outline: 2px solid var(--lp-primary);
  outline-offset: 2px;
}
.auth-social-buttons img {
  display: block;
  width: calc(var(--lp-space-6) + var(--lp-space-1));
  height: calc(var(--lp-space-6) + var(--lp-space-1));
}
@media (min-width: 1280px) {
  .auth-social-divider {
    font-size: var(--lp-text-base);
  }
}
</style>
