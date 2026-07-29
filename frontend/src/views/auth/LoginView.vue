<template>
  <div class="login-container">
    <div class="login-card">
      <h2 class="login-title">AI 题库与错题复习系统</h2>
      <p class="login-subtitle">用户登录</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" placeholder="请输入计算结果" :prefix-icon="Key" @keyup.enter="handleLogin" />
            <div class="captcha-image" @click="refreshCaptcha" title="点击刷新验证码">
              <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
              <span v-else class="captcha-loading">加载中...</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="handleLogin">登 录</el-button>
        </el-form-item>
      </el-form>
      <div class="login-footer">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Lock, Key } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getCaptcha } from '@/api/user'
import request from '@/utils/request'
import type { LoginResponse } from '@/types/user'
import type { ApiResponse } from '@/types/api'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const captchaImage = ref('')
const captchaId = ref('')

const form = reactive({
  username: '',
  password: '',
  captchaCode: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码计算结果', trigger: 'blur' }],
}

/** 加载验证码 */
async function refreshCaptcha() {
  try {
    const res = await getCaptcha()
    const data = res.data
    captchaId.value = data.captchaId
    captchaImage.value = data.image
    form.captchaCode = ''
  } catch {
    // 验证码加载失败不影响登录页面
  }
}

onMounted(() => {
  refreshCaptcha()
})

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await request.post<unknown, ApiResponse<LoginResponse>>('/auth/login', {
      username: form.username,
      password: form.password,
      captchaId: captchaId.value,
      captchaCode: form.captchaCode,
    })
    const { token, user } = res.data
    userStore.setLoginInfo(token, user)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch {
    // 登录失败后刷新验证码
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}

.login-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.login-title {
  text-align: center;
  margin: 0 0 8px;
  font-size: 22px;
  color: #303133;
}

.login-subtitle {
  text-align: center;
  margin: 0 0 30px;
  font-size: 14px;
  color: #909399;
}

.captcha-row {
  display: flex;
  gap: 12px;
  width: 100%;
}

.captcha-row .el-input {
  flex: 1;
}

.captcha-image {
  flex-shrink: 0;
  width: 120px;
  height: 40px;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: center;
}

.captcha-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha-loading {
  font-size: 12px;
  color: #909399;
}

.login-footer {
  text-align: center;
  font-size: 14px;
  color: #606266;
}

.login-footer a {
  color: #409eff;
}
</style>
