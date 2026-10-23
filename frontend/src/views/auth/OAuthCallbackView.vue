<template>
  <AuthFrame class="lt" data-appearance="dark">
    <div class="oauth-result" role="status" aria-live="polite" :aria-busy="loading">
      <LoaderCircle v-if="loading" class="status-icon" aria-hidden="true" />
      <CircleAlert v-else class="status-icon error" aria-hidden="true" />
      <h1 id="auth-title">{{ loading ? '正在登录' : '登录未完成' }}</h1>
      <p>{{ message }}</p>
      <LtButton v-if="!loading" variant="primary" size="lg" @click="router.replace('/login')">返回登录</LtButton>
    </div>
  </AuthFrame>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LoaderCircle, CircleAlert } from '@lucide/vue'
import { LtButton } from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'
import { exchangeOAuthTicket } from '@/api/auth'
import AuthFrame from '@/components/auth/AuthFrame.vue'
import { useUserStore } from '@/stores/user'
import { getAuthPreviewState } from '@/utils/authPreview'
import { useAuthPageGuard } from './useAuthPageGuard'
import { errorMessage } from '@/utils/errors'
useLivingTheme({ dark: true })

const capture = useAuthPageGuard()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const previewState = getAuthPreviewState(route.query['auth-preview'], ['loading', 'error'])
const loading = ref(previewState === undefined || previewState === 'loading')
const message = ref(previewState === 'error' ? 'Google 登录未完成，请重新尝试。' : '正在确认 Google 账号…')

const errorMessages: Record<string, string> = {
  access_denied: '你取消了 Google 授权。',
  account_exists: '该邮箱已有账号，请先使用原登录方式。',
  account_disabled: '账号已被禁用。',
  email_required: 'Google 账号没有提供邮箱。',
  email_unverified: 'Google 邮箱尚未验证。',
  invalid_identity: '无法确认 Google 账号。',
  oauth_failed: 'Google 登录失败，请重试。',
}

onMounted(async () => {
  if (previewState) return
  const current = capture()
  const error = typeof route.query.error === 'string' ? route.query.error : ''
  const ticket = new URLSearchParams(route.hash.replace(/^#/, '')).get('ticket')
  window.history.replaceState(window.history.state, '', route.path)

  if (error || !ticket) {
    sessionStorage.removeItem('oauth_login_redirect')
    loading.value = false
    message.value = errorMessages[error] || '登录链接无效，请重新登录。'
    return
  }

  try {
    const response = await exchangeOAuthTicket(ticket, { errorDisplay: 'inline' })
    if (!current()) return
    userStore.setLoginInfo(response.data.token, response.data.user)
    const savedRedirect = sessionStorage.getItem('oauth_login_redirect')
    sessionStorage.removeItem('oauth_login_redirect')
    const target = savedRedirect?.startsWith('/') && !savedRedirect.startsWith('//') ? savedRedirect : '/my-courses'
    await router.replace(target)
  } catch (cause) {
    if (!current()) return
    sessionStorage.removeItem('oauth_login_redirect')
    loading.value = false
    message.value = errorMessage(cause, '暂时无法完成登录，请返回登录页重试。')
  }
})
</script>

<style scoped>
.oauth-result {
  display: grid;
  justify-items: start;
  gap: var(--lt-space-5);
}
h1 {
  margin: 0;
  font-size: var(--lt-text-title2);
}
p {
  margin: 0;
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
}
.status-icon {
  width: var(--lt-space-7);
  height: var(--lt-space-7);
  color: var(--lt-label-2);
}
.error {
  color: var(--lt-red-text);
}
</style>
