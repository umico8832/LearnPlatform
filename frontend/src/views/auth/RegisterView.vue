<template>
  <AuthLayout
    :class="['auth-enter', 'registration-page', step === 1 ? 'registration-page--entry' : 'registration-page--flow']"
    :show-symbol="false"
  >
    <div class="auth-feature-symbol" aria-hidden="true">
      <el-icon><UserFilled /></el-icon>
    </div>
    <div class="auth-card-header">
      <h1 id="auth-title">创建学习账号</h1>
      <p v-if="step === 1">填写用户名和邮箱</p>
      <p v-else-if="step === 2">
        验证码将发送至 <strong>{{ form.email }}</strong>
      </p>
      <p v-else>设置用于登录的密码</p>
    </div>
    <div class="registration-progress" role="status" aria-live="polite">
      <span>{{ stepLabels[step - 1] }} · {{ step }}/3</span>
      <div class="registration-progress__bars" aria-hidden="true">
        <i v-for="index in 3" :key="index" :class="{ 'is-active': index <= step }"></i>
      </div>
    </div>
    <div class="registration-body">
      <el-form
        ref="formRef"
        class="auth-form auth-form--minimal"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handlePrimary"
      >
        <Transition name="registration-step" mode="out-in">
          <div v-if="step === 1" key="account" class="registration-step">
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
          </div>
          <div v-else-if="step === 2" key="verification" class="registration-step">
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
          </div>
          <div v-else key="password" class="registration-step">
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
            <el-form-item
              ><div class="step-actions">
                <el-button native-type="button" class="auth-secondary" @click="step = 2">上一步</el-button
                ><el-button native-type="submit" type="primary" class="auth-primary" :loading="loading"
                  >创建账号</el-button
                >
              </div></el-form-item
            >
          </div>
        </Transition>
      </el-form>
      <AuthSocialOptions v-if="step === 1" :preview="previewMode" />
    </div>
    <template #footer>
      <div v-if="step === 1" class="auth-footer">已有账号？ <router-link to="/login">立即登录</router-link></div>
    </template>
    <TurnstileDialog ref="verificationRef" />
  </AuthLayout>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Key, Lock, Message, User, UserFilled } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import AuthLayout from '@/components/auth/AuthLayout.vue'
import AuthSocialOptions from '@/components/auth/AuthSocialOptions.vue'
import TurnstileDialog from '@/components/auth/TurnstileDialog.vue'
import { register, sendRegisterCode, verifyRegisterCode } from '@/api/auth'
import { getAuthPreviewState } from '@/utils/authPreview'
import '@/assets/styles/auth.css'

const router = useRouter(),
  route = useRoute(),
  formRef = ref<FormInstance>(),
  verificationRef = ref<InstanceType<typeof TurnstileDialog>>()
const previewState = getAuthPreviewState(route.query['auth-preview'], ['step-1', 'step-2', 'step-3'])
const previewMode = previewState !== undefined
const step = ref(previewState ? Number(previewState.at(-1)) : 1),
  sending = ref(false),
  verifying = ref(false),
  loading = ref(false),
  code = ref(''),
  countdown = ref(0)
let timer: number | undefined
const stepLabels = ['账户信息', '邮箱验证', '设置密码']
const form = reactive({
  username: previewMode ? 'learner' : '',
  email: previewMode ? 'learner@example.com' : '',
  password: '',
  confirmPassword: '',
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

<style scoped>
.registration-progress {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-5);
  margin-bottom: var(--lp-space-5);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-body);
}
.registration-progress__bars {
  display: grid;
  grid-template-columns: repeat(3, var(--lp-space-10));
  gap: var(--lp-space-2);
}
.registration-progress__bars i {
  height: var(--lp-space-1);
  border-radius: var(--lp-radius-full);
  background: var(--lp-surface-inset);
}
.registration-progress__bars i.is-active {
  background: var(--lp-primary);
}
.registration-step {
  display: flow-root;
}
.registration-body {
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.auth-card-header strong {
  color: var(--lp-text);
  font-weight: var(--lp-weight-semibold);
}
@media (min-width: 1280px) {
  .registration-page--entry :deep(.auth-card) {
    height: 656px;
  }
  .registration-page--entry .registration-body {
    height: 312px;
  }
  .registration-page--flow :deep(.auth-card) {
    height: 536px;
  }
  .registration-page--flow .registration-body {
    height: 192px;
  }
  .registration-progress {
    font-size: var(--lp-text-lg);
  }
}
@media (prefers-reduced-motion: no-preference) {
  .registration-page :deep(.auth-card) {
    transition: height var(--lp-duration-slow) var(--lp-ease-out);
  }
  .registration-step-enter-active,
  .registration-step-leave-active {
    transition:
      opacity var(--lp-duration-normal) var(--lp-ease-out),
      transform var(--lp-duration-normal) var(--lp-ease-out);
  }
  .registration-step-enter-from {
    opacity: 0;
    transform: translateY(var(--lp-space-2));
  }
  .registration-step-leave-to {
    opacity: 0;
  }
}
</style>
