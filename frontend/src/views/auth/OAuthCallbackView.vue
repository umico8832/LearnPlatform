<template>
  <AuthLayout class="auth-enter" :show-symbol="false">
    <div class="status-panel oauth-result" role="status" aria-live="polite">
      <div
        class="status-panel__icon"
        :class="loading ? 'status-panel__icon--loading' : 'status-panel__icon--error'"
        aria-hidden="true"
      >
        <el-icon v-if="loading" class="is-loading"><Loading /></el-icon>
        <el-icon v-else><WarningFilled /></el-icon>
      </div>
      <h1 id="auth-title">{{ loading ? '正在登录' : '登录未完成' }}</h1>
      <p v-if="!loading">{{ message }}</p>
      <el-button v-if="!loading" class="auth-secondary" @click="router.replace('/login')"> 返回登录 </el-button>
    </div>
  </AuthLayout>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading, WarningFilled } from '@element-plus/icons-vue'
import { exchangeOAuthTicket } from '@/api/auth'
import AuthLayout from '@/components/auth/AuthLayout.vue'
import { useUserStore } from '@/stores/user'
import { getAuthPreviewState } from '@/utils/authPreview'
import '@/assets/styles/auth.css'

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
    const response = await exchangeOAuthTicket(ticket)
    userStore.setLoginInfo(response.data.token, response.data.user)
    const savedRedirect = sessionStorage.getItem('oauth_login_redirect')
    sessionStorage.removeItem('oauth_login_redirect')
    const target = savedRedirect?.startsWith('/') && !savedRedirect.startsWith('//') ? savedRedirect : '/my-courses'
    await router.replace(target)
  } catch {
    loading.value = false
    message.value = '登录票据无效或已过期，请重新登录。'
  }
})
</script>

<style scoped>
.oauth-result {
  padding-bottom: var(--lp-space-1);
}
</style>
