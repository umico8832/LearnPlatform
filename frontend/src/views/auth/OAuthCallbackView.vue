<template>
  <AuthLayout class="auth-enter">
    <div class="oauth-result" role="status" aria-live="polite">
      <el-icon v-if="loading" class="is-loading" :size="32"><Loading /></el-icon>
      <el-icon v-else :size="32"><WarningFilled /></el-icon>
      <h1 id="auth-title">{{ loading ? '正在登录' : '登录未完成' }}</h1>
      <p>{{ message }}</p>
      <el-button v-if="!loading" type="primary" class="auth-primary" @click="router.replace('/login')">
        返回登录
      </el-button>
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
import '@/assets/styles/auth.css'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(true)
const message = ref('正在确认 Google 账号…')

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
  display: grid;
  justify-items: center;
  gap: var(--lp-space-4);
  padding: var(--lp-space-8) 0 var(--lp-space-4);
  text-align: center;
}
.oauth-result h1,
.oauth-result p {
  margin: 0;
}
.oauth-result p {
  color: var(--lp-text-secondary);
}
.oauth-result .auth-primary {
  margin-top: var(--lp-space-4);
}
</style>
