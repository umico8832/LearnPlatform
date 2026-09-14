<template>
  <div class="auth-social" aria-label="第三方登录">
    <div class="auth-social-divider"><span>其他登录方式</span></div>
    <div class="auth-social-buttons">
      <button
        v-for="provider in providers"
        :key="provider.id"
        type="button"
        :disabled="!provider.enabled"
        :class="{ 'is-enabled': provider.enabled }"
        :aria-label="provider.enabled ? `使用 ${provider.name} 登录` : `${provider.name} 登录，暂未开放`"
        :title="provider.enabled ? `使用 ${provider.name} 登录` : `${provider.name} 登录暂未开放`"
        @click="provider.id === 'google' && startGoogleLogin()"
      >
        <img :src="provider.icon" alt="" width="24" height="24" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getOAuthProviders } from '@/api/auth'
import googleIcon from '@/assets/auth/google.png'
import facebookIcon from '@/assets/auth/facebook.svg'
import appleIcon from '@/assets/auth/apple.svg'

const props = withDefaults(defineProps<{ preview?: boolean }>(), {
  preview: false,
})
const route = useRoute()
const googleEnabled = ref(props.preview)
const providers = computed(() => [
  { id: 'google', name: 'Google', icon: googleIcon, enabled: googleEnabled.value },
  { id: 'facebook', name: 'Facebook', icon: facebookIcon, enabled: false },
  { id: 'apple', name: 'Apple', icon: appleIcon, enabled: false },
])

onMounted(async () => {
  if (props.preview) return
  try {
    const response = await getOAuthProviders()
    googleEnabled.value = response.data.google
  } catch {
    googleEnabled.value = false
  }
})

function startGoogleLogin() {
  if (!googleEnabled.value) return
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--lp-space-3);
}
.auth-social-buttons button {
  display: grid;
  place-items: center;
  height: calc(var(--lp-space-12) + var(--lp-space-2));
  padding: var(--lp-space-3);
  border: 1px solid var(--lp-ink-600);
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
  transform: translateY(-1px);
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
