<template>
  <AuthLayout class="auth-enter">
    <div class="auth-card-header">
      <h1 id="auth-title">欢迎回来</h1>
      <p>使用用户名或邮箱登录你的账号</p>
    </div>
    <el-form
      ref="formRef"
      class="auth-form auth-form--minimal"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="handleLogin"
    >
      <el-form-item label="用户名或邮箱" prop="account"
        ><el-input v-model="form.account" :prefix-icon="User" placeholder="请输入用户名或邮箱" autocomplete="username"
      /></el-form-item>
      <el-form-item label="密码" prop="password"
        ><el-input
          v-model="form.password"
          :prefix-icon="Lock"
          type="password"
          show-password
          placeholder="请输入密码"
          autocomplete="current-password"
      /></el-form-item>
      <div class="form-row-between">
        <router-link class="auth-inline-link" to="/forgot-password">忘记密码？</router-link>
      </div>
      <el-form-item
        ><el-button native-type="submit" type="primary" class="auth-primary" :loading="loading" :disabled="loading"
          >登录</el-button
        ></el-form-item
      >
    </el-form>
    <AuthSocialOptions />
    <template #footer>
      <div class="auth-footer">还没有账号？ <router-link to="/register">免费注册</router-link></div>
    </template>
    <TurnstileDialog ref="verificationRef" />
  </AuthLayout>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import AuthLayout from '@/components/auth/AuthLayout.vue'
import AuthSocialOptions from '@/components/auth/AuthSocialOptions.vue'
import TurnstileDialog from '@/components/auth/TurnstileDialog.vue'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import '@/assets/styles/auth.css'

const router = useRouter(),
  route = useRoute(),
  userStore = useUserStore()
const formRef = ref<FormInstance>(),
  verificationRef = ref<InstanceType<typeof TurnstileDialog>>(),
  loading = ref(false)
const form = reactive({ account: '', password: '' })
const rules: FormRules = {
  account: [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
async function handleLogin() {
  if (loading.value) return
  const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : undefined
  loading.value = true
  try {
    if (!(await formRef.value?.validate().catch(() => false))) return
    const credentials = { ...form }
    const turnstileToken = await verificationRef.value?.verify(trigger)
    if (!turnstileToken) return
    const res = await login({ ...credentials, turnstileToken })
    userStore.setLoginInfo(res.data.token, res.data.user)
    ElMessage.success('登录成功')
    await router.push((route.query.redirect as string) || '/my-courses')
  } catch {
  } finally {
    loading.value = false
  }
}
</script>
