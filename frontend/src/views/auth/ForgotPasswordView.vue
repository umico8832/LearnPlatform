<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { CircleAlert } from '@lucide/vue'
import { LtButton, LtTextField } from '@/components/living'
import AuthFrame from '@/components/auth/AuthFrame.vue'
import TurnstileSheet from '@/components/auth/TurnstileSheet.vue'
import { validEmail } from './authValidation'
import { forgotPassword } from '@/api/auth'
import { getAuthPreviewState } from '@/utils/authPreview'
import { useAuthPageGuard } from './useAuthPageGuard'
import { errorMessage } from '@/utils/errors'

const route = useRoute()
const previewState = getAuthPreviewState(route.query['auth-preview'], ['form', 'sent', 'resent', 'support'])
const emailField = ref<InstanceType<typeof LtTextField>>(),
  verificationRef = ref<InstanceType<typeof TurnstileSheet>>(),
  loading = ref(false),
  submitted = ref(previewState === 'sent' || previewState === 'resent' || previewState === 'support'),
  resendCount = ref(previewState === 'support' ? 2 : previewState === 'resent' ? 1 : 0)
const maxResendAttempts = 2
const error = ref('')
const capture = useAuthPageGuard()
function editEmail() {
  submitted.value = false
  resendCount.value = 0
  error.value = ''
}
const form = reactive({ email: '' })
const fieldError = ref('')
function edited() {
  fieldError.value = ''
  error.value = ''
}
function validate() {
  fieldError.value = !form.email ? '请输入注册邮箱' : !validEmail(form.email) ? '邮箱格式不正确' : ''
  if (fieldError.value) emailField.value?.focus()
  return !fieldError.value
}
async function submit() {
  if (loading.value) return
  error.value = ''
  if (!validate()) return
  const current = capture()
  const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : undefined
  loading.value = true
  try {
    const sent = await requestReset(form.email, current, trigger)
    if (current()) submitted.value = sent
  } catch (cause) {
    if (current()) error.value = errorMessage(cause, '暂时无法发送邮件，请检查网络后重试。')
  } finally {
    if (current()) loading.value = false
  }
}

async function resend() {
  if (loading.value || resendCount.value >= maxResendAttempts) return
  const current = capture()
  error.value = ''
  const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : undefined
  loading.value = true
  try {
    if (await requestReset(form.email, current, trigger)) {
      if (current()) resendCount.value++
    }
  } catch (cause) {
    if (current()) error.value = errorMessage(cause, '暂时无法发送邮件，请检查网络后重试。')
  } finally {
    if (current()) loading.value = false
  }
}

async function requestReset(email: string, current: () => boolean, trigger?: HTMLElement) {
  const turnstileToken = await verificationRef.value?.verify(trigger)
  if (!turnstileToken || !current()) return false
  await forgotPassword(email, turnstileToken, { errorDisplay: 'inline' })
  return true
}
</script>

<template>
  <AuthFrame>
    <template v-if="!submitted">
      <h1 id="auth-title" class="title">重置密码</h1>
      <form class="form" novalidate @submit.prevent="submit">
        <LtTextField
          ref="emailField"
          v-model="form.email"
          label="注册邮箱"
          placeholder="name@example.com"
          autocomplete="email"
          :error="fieldError"
          :disabled="loading"
          @update:model-value="edited"
        />
        <p v-if="error" class="error" role="alert">
          <CircleAlert :size="16" aria-hidden="true" /><span>{{ error }}</span>
        </p>
        <LtButton type="submit" variant="primary" size="lg" class="submit" :disabled="loading">{{
          loading ? '正在发送…' : '发送重置链接'
        }}</LtButton>
      </form>
    </template>
    <div v-else class="status" role="status">
      <h1 id="auth-title" class="title">查看重置邮件</h1>
      <p class="lead">如果该邮箱已注册，你会收到一封重置邮件。</p>
      <p class="lead">重置链接 30 分钟内有效。{{ resendCount === 0 ? '没有收到邮件？' : '仍未收到？' }}</p>
      <p v-if="error" class="error" role="alert">
        <CircleAlert :size="16" aria-hidden="true" /><span>{{ error }}</span>
      </p>
      <LtButton
        v-if="resendCount < maxResendAttempts"
        class="recovery-resend-link"
        :disabled="loading"
        @click="resend"
        >{{ loading ? '正在发送…' : resendCount === 0 ? '重新发送' : '再次发送' }}</LtButton
      >
      <LtButton v-else class="recovery-support-link" @click="editEmail">重新填写邮箱</LtButton>
    </div>
    <TurnstileSheet ref="verificationRef" />
    <template #footer><router-link to="/login">返回登录</router-link></template>
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
  overflow-wrap: anywhere;
}
.form {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-5);
  margin-top: var(--lt-space-7);
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
  line-height: var(--lt-leading-ui);
}
.error svg {
  flex: none;
}
.submit {
  width: 100%;
  margin-top: var(--lt-space-1);
}

.status {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-4);
}
</style>
