<template>
  <AuthLayout alternate-to="/login" alternate-text="返回登录">
    <template v-if="!submitted">
      <div class="auth-card-header">
        <h1 id="auth-title">找回密码</h1>
        <p>输入已验证邮箱，我们会发送一次性重置链接</p>
      </div>
      <el-form
        ref="formRef"
        class="auth-form"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item label="注册邮箱" prop="email"
          ><el-input v-model="form.email" :prefix-icon="Message" placeholder="name@example.com" autocomplete="email"
        /></el-form-item>
        <el-form-item label="人机验证"
          ><TurnstileWidget ref="turnstileRef" v-model="form.turnstileToken"
        /></el-form-item>
        <el-form-item
          ><el-button
            native-type="submit"
            type="primary"
            class="auth-primary"
            :loading="loading"
            :disabled="!form.turnstileToken"
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
    <div class="auth-footer"><router-link to="/login">返回登录</router-link></div>
  </AuthLayout>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { CircleCheck, Message } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import AuthLayout from '@/components/auth/AuthLayout.vue'
import TurnstileWidget from '@/components/auth/TurnstileWidget.vue'
import { forgotPassword } from '@/api/auth'
import '@/assets/styles/auth.css'

const formRef = ref<FormInstance>(),
  turnstileRef = ref<{ reset: () => void }>(),
  loading = ref(false),
  submitted = ref(false)
const form = reactive({ email: '', turnstileToken: '' })
const rules: FormRules = {
  email: [
    { required: true, message: '请输入注册邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
}
async function submit() {
  if (!(await formRef.value?.validate().catch(() => false)) || !form.turnstileToken) return
  loading.value = true
  try {
    await forgotPassword(form.email, form.turnstileToken)
    submitted.value = true
  } catch {
    turnstileRef.value?.reset()
  } finally {
    loading.value = false
  }
}
</script>
