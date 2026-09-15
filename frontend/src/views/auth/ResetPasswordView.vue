<template>
  <AuthLayout class="auth-enter reset-password-page" :show-symbol="false">
    <div v-if="checking" class="status-panel">
      <div class="status-panel__icon status-panel__icon--loading" aria-hidden="true">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>
      <h2 id="auth-title">正在验证链接</h2>
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
          ><el-button native-type="submit" type="primary" class="auth-primary" :loading="submitting"
            >重置密码</el-button
          ></el-form-item
        >
      </el-form>
    </template>
    <div v-else-if="completed" class="status-panel">
      <div class="status-panel__icon status-panel__icon--success" aria-hidden="true">
        <el-icon><CircleCheckFilled /></el-icon>
      </div>
      <h2 id="auth-title">密码已重置</h2>
      <el-button class="auth-secondary" @click="$router.push('/login')">前往登录</el-button>
    </div>
    <div v-else class="status-panel">
      <div class="status-panel__icon status-panel__icon--error" aria-hidden="true">
        <el-icon><CircleCloseFilled /></el-icon>
      </div>
      <h2 id="auth-title">链接无效或已过期</h2>
      <el-button class="auth-secondary" @click="$router.push('/forgot-password')">重新申请</el-button>
    </div>
  </AuthLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { CircleCheckFilled, CircleCloseFilled, Loading, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import AuthLayout from '@/components/auth/AuthLayout.vue'
import { resetPassword, validateResetToken } from '@/api/auth'
import { getAuthPreviewState } from '@/utils/authPreview'
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
onMounted(async () => {
  if (previewState) return
  const token = String(route.query.token || '')
  if (!token) {
    checking.value = false
    return
  }
  try {
    const res = await validateResetToken(token)
    email.value = res.data
    valid.value = true
  } catch {
    valid.value = false
  } finally {
    checking.value = false
  }
})
async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  submitting.value = true
  try {
    await resetPassword(String(route.query.token), form.password)
    completed.value = true
  } catch {
    valid.value = false
  } finally {
    submitting.value = false
  }
}
</script>
