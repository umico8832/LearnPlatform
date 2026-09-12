<template>
  <main class="admin-login-page">
    <section class="admin-login-card" aria-labelledby="admin-login-title">
      <h1 id="admin-login-title">管理员登录</h1>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="用户名或邮箱" prop="account">
          <el-input v-model="form.account" placeholder="请输入用户名或邮箱" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button native-type="submit" type="primary" :loading="loading" :disabled="loading"> 登录管理系统 </el-button>
      </el-form>
      <a href="/login">返回学习端</a>
    </section>
    <TurnstileDialog ref="verificationRef" />
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import TurnstileDialog from '@/components/auth/TurnstileDialog.vue'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const formRef = ref<FormInstance>()
const verificationRef = ref<InstanceType<typeof TurnstileDialog>>()
const loading = ref(false)
const form = reactive({ account: '', password: '' })
const rules: FormRules = {
  account: [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

async function handleLogin() {
  if (loading.value) return
  const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : undefined
  loading.value = true
  try {
    if (!(await formRef.value?.validate().catch(() => false))) return
    const credentials = { ...form }
    const turnstileToken = await verificationRef.value?.verify(trigger)
    if (!turnstileToken) return
    const response = await login({ ...credentials, turnstileToken })
    if (response.data.user.role !== 'ADMIN') {
      userStore.clearLoginInfo()
      ElMessage.error('该账号没有管理权限')
      return
    }
    userStore.setLoginInfo(response.data.token, response.data.user)
    ElMessage.success('登录成功')
    await router.push((route.query.redirect as string) || '/')
  } catch {
  } finally {
    loading.value = false
  }
}
</script>
