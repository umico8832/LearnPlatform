import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getOAuthProviders } from '@/api/auth'
import googleIcon from '@/assets/auth/google.png'

/**
 * Third-party sign-in methods the backend has enabled, and how to start one. Preview mode
 * shows Google without asking the backend. The guarded redirect survives the round trip.
 */
export function useOAuthProviders(options: { preview: () => boolean; disabled: () => boolean }) {
  const route = useRoute()
  const googleEnabled = ref(options.preview())
  const providers = computed(() =>
    googleEnabled.value ? [{ id: 'google' as const, name: 'Google', icon: googleIcon, enabled: true }] : [],
  )
  let alive = true
  onBeforeUnmount(() => {
    alive = false
  })

  onMounted(async () => {
    if (options.preview()) return
    try {
      const response = await getOAuthProviders()
      if (alive) googleEnabled.value = response.data.google
    } catch {
      if (alive) googleEnabled.value = false
    }
  })

  function startGoogleLogin() {
    if (!googleEnabled.value || options.disabled()) return
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    if (redirect.startsWith('/') && !redirect.startsWith('//')) {
      sessionStorage.setItem('oauth_login_redirect', redirect)
    } else {
      sessionStorage.removeItem('oauth_login_redirect')
    }
    const apiBaseUrl = String(import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
    window.location.assign(`${apiBaseUrl}/auth/oauth/authorization/google`)
  }

  return { providers, startGoogleLogin }
}
