<template>
  <main class="admin-login-page">
    <section class="admin-login-card" aria-labelledby="admin-login-title">
      <p class="admin-login-kicker">LEARNPLATFORM ADMIN</p>
      <h1 id="admin-login-title">管理员登录</h1>
      <p>管理入口与学习端独立构建，权限仍由后端校验。</p>
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
        <el-form-item>
          <TurnstileWidget ref="turnstileRef" v-model="form.turnstileToken" @expired="form.turnstileToken = ''" />
        </el-form-item>
        <el-button native-type="submit" type="primary" :loading="loading" :disabled="loading || !form.turnstileToken">
          登录管理系统
        </el-button>
      </el-form>
      <a href="/login">返回学习端</a>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import TurnstileWidget from '@/components/auth/TurnstileWidget.vue'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const formRef = ref<FormInstance>()
const turnstileRef = ref<{ reset: () => void }>()
const loading = ref(false)
const form = reactive({ account: '', password: '', turnstileToken: '' })
const rules: FormRules = {
  account: [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

async function handleLogin() {
  if (!(await formRef.value?.validate().catch(() => false)) || !form.turnstileToken) return
  loading.value = true
  try {
    const response = await login(form)
    if (response.data.user.role !== 'ADMIN') {
      userStore.clearLoginInfo()
      ElMessage.error('该账号没有管理权限')
      return
    }
    userStore.setLoginInfo(response.data.token, response.data.user)
    ElMessage.success('登录成功')
    await router.push((route.query.redirect as string) || '/')
  } catch {
    turnstileRef.value?.reset()
  } finally {
    loading.value = false
  }
}
</script>
