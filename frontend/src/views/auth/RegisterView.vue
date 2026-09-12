<template>
  <AuthLayout class="auth-enter">
    <div class="auth-card-header">
      <h1 id="auth-title">创建学习账号</h1>
      <p>使用邮箱创建你的学习账号</p>
    </div>
    <p class="auth-visually-hidden" role="status">{{ stepLabels[step - 1] }} · {{ step }}/3</p>
    <el-form
      ref="formRef"
      class="auth-form auth-form--minimal"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="handlePrimary"
    >
      <template v-if="step === 1">
        <el-form-item label="用户名" prop="username"
          ><el-input
            v-model="form.username"
            :prefix-icon="User"
            placeholder="用户名（3-50 个字符）"
            autocomplete="username"
        /></el-form-item>
        <el-form-item label="邮箱" prop="email"
          ><el-input v-model="form.email" :prefix-icon="Message" placeholder="邮箱" autocomplete="email"
        /></el-form-item>
        <el-form-item
          ><el-button native-type="submit" type="primary" class="auth-primary">下一步</el-button></el-form-item
        >
      </template>
      <template v-else-if="step === 2">
        <p class="verification-note">
          验证码将发送至 <strong>{{ form.email }}</strong
          >。如需修改，请返回上一步。
        </p>
        <el-form-item label="邮箱验证码">
          <div class="code-row">
            <el-input
              v-model="code"
              :prefix-icon="Key"
              placeholder="6 位数字"
              inputmode="numeric"
              maxlength="6"
              autocomplete="one-time-code"
            /><el-button
              native-type="button"
              :loading="sending"
              :disabled="sending || countdown > 0"
              @click="sendCode"
              >{{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}</el-button
            >
          </div>
        </el-form-item>
        <el-form-item
          ><div class="step-actions">
            <el-button native-type="button" class="auth-secondary" @click="backToAccount">上一步</el-button
            ><el-button
              native-type="submit"
              type="primary"
              class="auth-primary"
              :loading="verifying"
              :disabled="code.length !== 6"
              >验证并继续</el-button
            >
          </div></el-form-item
        >
      </template>
      <template v-else>
        <el-form-item label="密码" prop="password"
          ><el-input
            v-model="form.password"
            :prefix-icon="Lock"
            type="password"
            show-password
            placeholder="密码（8-64 个字符）"
            autocomplete="new-password"
          />
          <div v-if="form.password" class="password-meter">
            <div><i :style="{ width: `${passwordStrength}%` }"></i></div>
            <span>{{ passwordStrengthLabel }}</span>
          </div></el-form-item
        >
        <el-form-item label="确认密码" prop="confirmPassword"
          ><el-input
            v-model="form.confirmPassword"
            :prefix-icon="Lock"
            type="password"
            show-password
            placeholder="确认密码"
            autocomplete="new-password"
        /></el-form-item>
        <el-form-item label="昵称（可选）"
          ><el-input v-model="form.nickname" :prefix-icon="UserFilled" placeholder="昵称（可选）"
        /></el-form-item>
        <el-form-item
          ><div class="step-actions">
            <el-button native-type="button" class="auth-secondary" @click="step = 2">上一步</el-button
            ><el-button native-type="submit" type="primary" class="auth-primary" :loading="loading">创建账号</el-button>
          </div></el-form-item
        >
      </template>
    </el-form>
    <AuthSocialOptions />
    <template #footer>
      <div class="auth-footer">已有账号？ <router-link to="/login">立即登录</router-link></div>
    </template>
    <TurnstileDialog ref="verificationRef" />
  </AuthLayout>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Key, Lock, Message, User, UserFilled } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import AuthLayout from '@/components/auth/AuthLayout.vue'
import AuthSocialOptions from '@/components/auth/AuthSocialOptions.vue'
import TurnstileDialog from '@/components/auth/TurnstileDialog.vue'
import { register, sendRegisterCode, verifyRegisterCode } from '@/api/auth'
import '@/assets/styles/auth.css'

const router = useRouter(),
  formRef = ref<FormInstance>(),
  verificationRef = ref<InstanceType<typeof TurnstileDialog>>()
const step = ref(1),
  sending = ref(false),
  verifying = ref(false),
  loading = ref(false),
  code = ref(''),
  countdown = ref(0)
let timer: number | undefined
const stepLabels = ['账户信息', '邮箱验证', '设置密码']
const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  verificationTicket: '',
})
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度 3-50 个字符', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度 8-64 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { validator: (_r, v, cb) => (v === form.password ? cb() : cb(new Error('两次输入的密码不一致'))), trigger: 'blur' },
  ],
}
const passwordStrength = computed(() => {
  let n = 0
  if (form.password.length >= 8) n += 25
  if (form.password.length >= 12) n += 25
  if (/[A-Z]/.test(form.password) && /[a-z]/.test(form.password)) n += 25
  if (/\d/.test(form.password) && /[^\w]/.test(form.password)) n += 25
  return n
})
const passwordStrengthLabel = computed(() =>
  passwordStrength.value < 50
    ? '较弱'
    : passwordStrength.value < 75
      ? '一般'
      : passwordStrength.value < 100
        ? '良好'
        : '强',
)
async function handlePrimary() {
  if (step.value === 1) {
    if (
      await formRef.value
        ?.validateField(['username', 'email'])
        .then(() => true)
        .catch(() => false)
    )
      step.value = 2
    return
  }
  if (step.value === 2) {
    await verifyCode()
    return
  }
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    await register({
      username: form.username,
      email: form.email,
      password: form.password,
      nickname: form.nickname || undefined,
      verificationTicket: form.verificationTicket,
    })
    ElMessage.success('注册成功，请登录')
    await router.push('/login')
  } finally {
    loading.value = false
  }
}
async function sendCode() {
  if (sending.value || countdown.value > 0 || step.value !== 2) return
  const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : undefined
  sending.value = true
  try {
    const email = form.email
    const turnstileToken = await verificationRef.value?.verify(trigger)
    if (!turnstileToken) return
    await sendRegisterCode(email, turnstileToken)
    ElMessage.success('验证码已发送')
    countdown.value = 60
    timer = window.setInterval(() => {
      countdown.value--
      if (countdown.value <= 0 && timer) clearInterval(timer)
    }, 1000)
  } catch {
  } finally {
    sending.value = false
  }
}
async function verifyCode() {
  verifying.value = true
  try {
    const res = await verifyRegisterCode(form.email, code.value)
    form.verificationTicket = res.data.verificationTicket
    step.value = 3
  } catch {
  } finally {
    verifying.value = false
  }
}
function backToAccount() {
  step.value = 1
  code.value = ''
  form.verificationTicket = ''
}
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>
