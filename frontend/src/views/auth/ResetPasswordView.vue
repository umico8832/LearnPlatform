<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { CircleAlert } from '@lucide/vue'
import { LtButton, LtTextField } from '@/components/living'
import AuthFrame from '@/components/auth/AuthFrame.vue'
import { resetPassword, validateResetToken } from '@/api/auth'
import { getAuthPreviewState } from '@/utils/authPreview'
import { errorMessage } from '@/utils/errors'
import { validLength } from './authValidation'
import { useAuthPageGuard } from './useAuthPageGuard'

const route = useRoute()
const previewState = getAuthPreviewState(route.query['auth-preview'], ['checking', 'form', 'success', 'error'])
const routeState = {
  checking: previewState === undefined || previewState === 'checking',
  valid: previewState === 'form' || previewState === 'success',
  completed: previewState === 'success',
}
const passwordField = ref<InstanceType<typeof LtTextField>>(),
  confirmPasswordField = ref<InstanceType<typeof LtTextField>>(),
  checking = ref(routeState.checking),
  valid = ref(routeState.valid),
  completed = ref(routeState.completed),
  submitting = ref(false),
  email = ref('')
const capture = useAuthPageGuard()
const validationError = ref(''),
  submitError = ref('')
let generation = 0
const form = reactive({ password: '', confirmPassword: '' })
const fieldErrors = reactive({ password: '', confirmPassword: '' })
function edited(key: 'password' | 'confirmPassword') {
  fieldErrors[key] = ''
  submitError.value = ''
}
function validate() {
  fieldErrors.password = !form.password
    ? '请输入新密码'
    : !validLength(form.password, 8, 64)
      ? '密码长度 8-64 个字符'
      : ''
  fieldErrors.confirmPassword = form.confirmPassword === form.password ? '' : '两次输入的密码不一致'
  if (fieldErrors.password) passwordField.value?.focus()
  else if (fieldErrors.confirmPassword) confirmPasswordField.value?.focus()
  return !fieldErrors.password && !fieldErrors.confirmPassword
}
const maskedEmail = computed(() => {
  const [name, domain] = email.value.split('@')
  return name && domain ? `${name.slice(0, 2)}***@${domain}` : '该账号'
})
function isExpired(cause: unknown) {
  return errorMessage(cause, '') === '重置链接无效或已过期'
}
async function validateLink() {
  if (previewState) return
  const current = capture(),
    version = ++generation
  const token = typeof route.query.token === 'string' ? route.query.token : ''
  checking.value = true
  submitting.value = false
  valid.value = false
  completed.value = false
  validationError.value = ''
  submitError.value = ''
  email.value = ''
  form.password = ''
  form.confirmPassword = ''
  fieldErrors.password = ''
  fieldErrors.confirmPassword = ''
  if (!token) {
    checking.value = false
    return
  }
  try {
    const res = await validateResetToken(token, { errorDisplay: 'inline' })
    if (!current() || version !== generation) return
    email.value = res.data
    valid.value = true
  } catch (cause) {
    if (current() && version === generation && !isExpired(cause))
      validationError.value = errorMessage(cause, '请检查网络后重新验证，链接状态尚未确认。')
  } finally {
    if (current() && version === generation) checking.value = false
  }
}
watch(() => route.query.token, validateLink, { immediate: true })
async function submit() {
  if (submitting.value || !valid.value || completed.value) return
  submitError.value = ''
  if (!validate()) return
  const current = capture(),
    version = generation
  submitting.value = true
  submitError.value = ''
  try {
    await resetPassword(String(route.query.token), form.password, { errorDisplay: 'inline' })
    if (!current() || version !== generation) return
    completed.value = true
    form.password = ''
    form.confirmPassword = ''
  } catch (cause) {
    if (!current() || version !== generation) return
    if (isExpired(cause)) valid.value = false
    else submitError.value = errorMessage(cause, '暂时无法重置密码，输入已保留，请重试。')
  } finally {
    if (current() && version === generation) submitting.value = false
  }
}
</script>

<template>
  <AuthFrame>
    <div v-if="checking" role="status">
      <h1 id="auth-title" class="title">正在验证链接</h1>
    </div>
    <div v-else-if="validationError" class="status">
      <h1 id="auth-title" class="title">暂时无法验证链接</h1>
      <p class="error" role="alert">
        <CircleAlert :size="16" aria-hidden="true" /><span>{{ validationError }}</span>
      </p>
      <LtButton size="lg" @click="validateLink">重试验证</LtButton>
    </div>
    <template v-else-if="valid && !completed">
      <h1 id="auth-title" class="title">设置新密码</h1>
      <p v-if="email" class="lead">账号：{{ maskedEmail }}</p>
      <form class="form" novalidate @submit.prevent="submit">
        <LtTextField
          ref="passwordField"
          v-model="form.password"
          label="新密码"
          type="password"
          placeholder="8-64 个字符"
          autocomplete="new-password"
          :error="fieldErrors.password"
          :disabled="submitting"
          @update:model-value="edited('password')"
        />
        <LtTextField
          ref="confirmPasswordField"
          v-model="form.confirmPassword"
          label="确认新密码"
          type="password"
          placeholder="再次输入新密码"
          autocomplete="new-password"
          :error="fieldErrors.confirmPassword"
          :disabled="submitting"
          @update:model-value="edited('confirmPassword')"
        />
        <p v-if="submitError" class="error" role="alert">
          <CircleAlert :size="16" aria-hidden="true" /><span>{{ submitError }}</span>
        </p>
        <LtButton type="submit" variant="primary" size="lg" class="submit" :disabled="submitting">{{
          submitting ? '正在重置…' : '重置密码'
        }}</LtButton>
      </form>
    </template>
    <div v-else-if="completed" class="status" role="status">
      <h1 id="auth-title" class="title">密码已重置</h1>
      <LtButton size="lg" @click="$router.push('/login')">前往登录</LtButton>
    </div>
    <div v-else class="status" role="status">
      <h1 id="auth-title" class="title">链接无效或已过期</h1>
      <LtButton size="lg" @click="$router.push('/forgot-password')">重新申请</LtButton>
    </div>
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
  gap: var(--lt-space-5);
}
</style>
