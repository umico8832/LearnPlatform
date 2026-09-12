<template>
  <AuthLayout>
    <template v-if="!submitted">
      <div class="auth-card-header">
        <h1 id="auth-title">找回密码</h1>
        <p>输入已验证邮箱，我们会发送一次性重置链接</p>
      </div>
      <el-form
        ref="formRef"
        class="auth-form auth-form--stable-errors"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item label="注册邮箱" prop="email"
          ><el-input v-model="form.email" :prefix-icon="Message" placeholder="name@example.com" autocomplete="email"
        /></el-form-item>
        <el-form-item
          ><el-button native-type="submit" type="primary" class="auth-primary" :loading="loading" :disabled="loading"
            >发送重置链接</el-button
          ></el-form-item
        >
      </el-form>
    </template>
    <div v-else class="status-panel">
      <el-icon><CircleCheck /></el-icon>
      <h2 id="auth-title">请检查邮箱</h2>
      <p>如果该邮箱已注册，重置链接已经发送。为保护账号安全，我们不会披露邮箱是否存在。</p>
    </div>
    <div class="auth-footer recovery-actions"><router-link to="/login">返回登录</router-link></div>
    <TurnstileDialog ref="verificationRef" />
  </AuthLayout>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { CircleCheck, Message } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import AuthLayout from '@/components/auth/AuthLayout.vue'
import TurnstileDialog from '@/components/auth/TurnstileDialog.vue'
import { forgotPassword } from '@/api/auth'
import '@/assets/styles/auth.css'

const formRef = ref<FormInstance>(),
  verificationRef = ref<InstanceType<typeof TurnstileDialog>>(),
  loading = ref(false),
  submitted = ref(false)
const form = reactive({ email: '' })
const rules: FormRules = {
  email: [
    { required: true, message: '请输入注册邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
}
async function submit() {
  if (loading.value) return
  const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : undefined
  loading.value = true
  try {
    if (!(await formRef.value?.validate().catch(() => false))) return
    const email = form.email
    const turnstileToken = await verificationRef.value?.verify(trigger)
    if (!turnstileToken) return
    await forgotPassword(email, turnstileToken)
    submitted.value = true
  } catch {
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.recovery-actions {
  margin-top: var(--lp-space-4);
}
.recovery-actions a {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: var(--lp-space-10);
  padding: var(--lp-space-2) var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: transparent;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-medium);
}
.recovery-actions a:hover {
  border-color: var(--lp-border-strong);
  color: var(--lp-text);
  text-decoration: none;
}
</style>
