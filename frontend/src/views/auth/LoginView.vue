<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { CircleAlert } from '@lucide/vue'
import { LtButton, LtTextField } from '@/components/living'
import AuthFrame from '@/components/auth/AuthFrame.vue'
import AuthProviders from '@/components/auth/AuthProviders.vue'
import TurnstileSheet from '@/components/auth/TurnstileSheet.vue'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { getAuthPreviewState } from '@/utils/authPreview'
import { errorMessage } from '@/utils/errors'
import { useAuthPageGuard } from './useAuthPageGuard'

/**
 * 登录页（ADR-0011 新基线）：账号与密码 → 提交时才做人机验证 → 回到原本要去的页面。
 * 字段错误留在字段下方，服务端错误留在按钮上方，都不弹提示。
 */
defineOptions({ name: 'LoginView' })

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const previewMode = getAuthPreviewState(route.query['auth-preview'], ['default']) !== undefined
const capture = useAuthPageGuard()

const form = reactive({ account: '', password: '' })
const fieldErrors = reactive({ account: '', password: '' })
const error = ref('')
const loading = ref(false)
const accountField = ref<InstanceType<typeof LtTextField>>()
const passwordField = ref<InstanceType<typeof LtTextField>>()
const submitButton = ref<InstanceType<typeof LtButton>>()
const verification = ref<InstanceType<typeof TurnstileSheet>>()

function validate() {
  fieldErrors.account = form.account.trim() ? '' : '请输入用户名或邮箱'
  fieldErrors.password = form.password ? '' : '请输入密码'
  if (fieldErrors.account) accountField.value?.focus()
  else if (fieldErrors.password) passwordField.value?.focus()
  return !fieldErrors.account && !fieldErrors.password
}

function edited(key: 'account' | 'password') {
  fieldErrors[key] = ''
  error.value = ''
}

function safeRedirect() {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
    ? redirect
    : '/my-courses'
}

async function handleLogin() {
  if (loading.value) return
  error.value = ''
  if (!validate()) return
  const current = capture()
  const trigger = (submitButton.value?.$el as HTMLElement | undefined) ?? undefined
  loading.value = true
  try {
    const credentials = { account: form.account.trim(), password: form.password }
    const turnstileToken = await verification.value?.verify(trigger)
    if (!turnstileToken || !current()) return
    const res = await login({ ...credentials, turnstileToken }, { errorDisplay: 'inline' })
    if (!current()) return
    userStore.setLoginInfo(res.data.token, res.data.user)
    await router.push(safeRedirect())
  } catch (cause) {
    if (current()) error.value = errorMessage(cause, '暂时无法登录，请检查网络后重试。')
  } finally {
    if (current()) loading.value = false
  }
}
</script>

<template>
  <AuthFrame>
    <h1 id="auth-title" class="title">欢迎回来</h1>
    <p class="lead">登录后回到你的课程。</p>

    <form class="form" novalidate @submit.prevent="handleLogin">
      <LtTextField
        ref="accountField"
        v-model="form.account"
        label="用户名或邮箱"
        placeholder="请输入用户名或邮箱"
        autocomplete="username"
        :error="fieldErrors.account"
        :disabled="loading"
        @update:model-value="edited('account')"
      />
      <LtTextField
        ref="passwordField"
        v-model="form.password"
        label="密码"
        type="password"
        placeholder="请输入密码"
        autocomplete="current-password"
        :error="fieldErrors.password"
        :disabled="loading"
        @update:model-value="edited('password')"
      >
        <template #label-trailing>
          <RouterLink class="link" to="/forgot-password">忘记密码？</RouterLink>
        </template>
      </LtTextField>

      <p v-if="error" class="error" role="alert">
        <CircleAlert :size="16" aria-hidden="true" />
        <span>{{ error }}</span>
      </p>

      <LtButton ref="submitButton" type="submit" variant="primary" size="lg" class="submit" :disabled="loading">
        {{ loading ? '正在登录…' : '登录' }}
      </LtButton>
    </form>

    <AuthProviders :preview="previewMode" :disabled="loading" />
    <TurnstileSheet ref="verification" />

    <template #footer>还没有账号？<RouterLink to="/register">免费注册</RouterLink></template>
  </AuthFrame>
</template>

<style scoped>
.title {
  margin: 0;
  font-size: var(--lt-text-title2);
}

.lead {
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-label-2);
}

.form {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-5);
  margin-top: var(--lt-space-7);
}

.link {
  color: var(--lt-accent);
  font-size: var(--lt-text-caption);
  text-decoration: none;
}

.link:hover {
  text-decoration: underline;
}

.error {
  display: flex;
  align-items: flex-start;
  gap: var(--lt-space-2);
  margin: 0;
  padding: var(--lt-space-3) var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-red-soft);
  color: var(--lt-red-text);
  font-size: var(--lt-text-footnote);
  line-height: 1.45;
}

.error svg {
  flex: none;
  margin-top: 1px;
}

.submit {
  width: 100%;
  margin-top: var(--lt-space-1);
}
</style>
