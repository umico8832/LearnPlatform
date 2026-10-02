<template>
  <AuthLayout class="auth-enter reset-password-page" :show-symbol="false">
    <div v-if="checking" class="status-panel" role="status">
      <div class="status-panel__icon status-panel__icon--loading" aria-hidden="true">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>
      <h1 id="auth-title">正在验证链接</h1>
    </div>
    <div v-else-if="validationError" class="status-panel" role="alert">
      <h1 id="auth-title">暂时无法验证链接</h1>
      <p>{{ validationError }}</p>
      <el-button class="auth-secondary" @click="validateLink">重试验证</el-button>
    </div>
    <template v-else-if="valid && !completed">
      <div class="auth-feature-symbol" aria-hidden="true">
        <el-icon><Lock /></el-icon>
      </div>
      <div class="auth-card-header">
        <h1 id="auth-title">设置新密码</h1>
        <p v-if="email">账号：{{ maskedEmail }}</p>
      </div>
      <el-form
        ref="formRef"
        class="auth-form auth-form--stable-errors"
        :model="form"
        :rules="rules"
        :disabled="submitting"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item label="新密码" prop="password"
          ><el-input
            v-model="form.password"
            :prefix-icon="Lock"
            type="password"
            show-password
            placeholder="8-64 个字符"
            autocomplete="new-password"
        /></el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword"
          ><el-input
            v-model="form.confirmPassword"
            :prefix-icon="Lock"
            type="password"
            show-password
            placeholder="再次输入新密码"
            autocomplete="new-password"
        /></el-form-item>
        <el-form-item
          ><el-button
            native-type="submit"
            type="primary"
            class="auth-primary"
            :loading="submitting"
            :disabled="submitting"
            >重置密码</el-button
          ></el-form-item
        >
      </el-form>
      <p v-if="submitError" class="auth-inline-error" role="alert">{{ submitError }}</p>
    </template>
    <div v-else-if="completed" class="status-panel" role="status">
      <div class="status-panel__icon status-panel__icon--success" aria-hidden="true">
        <el-icon><CircleCheckFilled /></el-icon>
      </div>
      <h1 id="auth-title">密码已重置</h1>
      <el-button class="auth-secondary" @click="$router.push('/login')">前往登录</el-button>
    </div>
    <div v-else class="status-panel" role="status">
      <div class="status-panel__icon status-panel__icon--error" aria-hidden="true">
        <el-icon><CircleCloseFilled /></el-icon>
      </div>
      <h1 id="auth-title">链接无效或已过期</h1>
      <el-button class="auth-secondary" @click="$router.push('/forgot-password')">重新申请</el-button>
    </div>
  </AuthLayout>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { CircleCheckFilled, CircleCloseFilled, Loading, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import AuthLayout from '@/components/auth/AuthLayout.vue'
import { resetPassword, validateResetToken } from '@/api/auth'
import { getAuthPreviewState } from '@/utils/authPreview'
import { errorMessage } from '@/utils/errors'
import { useAuthPageGuard } from './useAuthPageGuard'
import '@/assets/styles/auth.css'

const route = useRoute()
const previewState = getAuthPreviewState(route.query['auth-preview'], ['checking', 'form', 'success', 'error'])
const routeState = {
  checking: previewState === undefined || previewState === 'checking',
  valid: previewState === 'form' || previewState === 'success',
  completed: previewState === 'success',
}
const formRef = ref<FormInstance>(),
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
const rules: FormRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度 8-64 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { validator: (_r, v, cb) => (v === form.password ? cb() : cb(new Error('两次输入的密码不一致'))), trigger: 'blur' },
  ],
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
  const current = capture(),
    version = generation
  submitting.value = true
  submitError.value = ''
  try {
    if (!(await formRef.value?.validate().catch(() => false)) || !current() || version !== generation) return
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
