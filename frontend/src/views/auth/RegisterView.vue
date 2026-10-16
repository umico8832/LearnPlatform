<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { CircleAlert } from '@lucide/vue'
import { LtButton, LtTextField } from '@/components/living'
import AuthFrame from '@/components/auth/AuthFrame.vue'
import AuthProviders from '@/components/auth/AuthProviders.vue'
import TurnstileSheet from '@/components/auth/TurnstileSheet.vue'
import { validEmail, validLength } from './authValidation'
import { register, sendRegisterCode, verifyRegisterCode } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { getAuthPreviewState } from '@/utils/authPreview'
import { useAuthPageGuard } from './useAuthPageGuard'
import { errorMessage } from '@/utils/errors'

const router = useRouter(),
  route = useRoute(),
  userStore = useUserStore(),
  verificationRef = ref<InstanceType<typeof TurnstileSheet>>()
const previewState = getAuthPreviewState(route.query['auth-preview'], ['step-1', 'step-2', 'step-3'])
const previewMode = previewState !== undefined
const step = ref(previewState ? Number(previewState.at(-1)) : 1),
  sending = ref(false),
  verifying = ref(false),
  loading = ref(false),
  code = ref(''),
  countdown = ref(0)
let timer: number | undefined
const capture = useAuthPageGuard()
const error = ref('')
let stageVersion = 0
const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  verificationTicket: '',
})
const fieldErrors = reactive({ username: '', email: '', code: '', password: '', confirmPassword: '' })
type Field = keyof typeof fieldErrors
const usernameField = ref<InstanceType<typeof LtTextField>>()
const emailField = ref<InstanceType<typeof LtTextField>>()
const codeField = ref<InstanceType<typeof LtTextField>>()
const passwordField = ref<InstanceType<typeof LtTextField>>()
const confirmPasswordField = ref<InstanceType<typeof LtTextField>>()
const busy = computed(() => sending.value || verifying.value || loading.value)
const sent = ref(false)
function edited(key: Field) {
  fieldErrors[key] = ''
  error.value = ''
}
function validate() {
  let keys: Field[]
  if (step.value === 1) {
    fieldErrors.username = !form.username
      ? '请输入用户名'
      : !validLength(form.username, 3, 50)
        ? '用户名长度 3-50 个字符'
        : ''
    fieldErrors.email = !form.email ? '请输入邮箱' : !validEmail(form.email) ? '邮箱格式不正确' : ''
    keys = ['username', 'email']
  } else if (step.value === 2) {
    fieldErrors.code = /^\d{6}$/.test(code.value) ? '' : '请输入 6 位数字验证码'
    keys = ['code']
  } else {
    fieldErrors.password = !form.password
      ? '请输入密码'
      : !validLength(form.password, 8, 64)
        ? '密码长度 8-64 个字符'
        : ''
    fieldErrors.confirmPassword = form.confirmPassword === form.password ? '' : '两次输入的密码不一致'
    keys = ['password', 'confirmPassword']
  }
  const first = keys.find((key) => fieldErrors[key])
  if (first)
    ({
      username: usernameField,
      email: emailField,
      code: codeField,
      password: passwordField,
      confirmPassword: confirmPasswordField,
    })[first].value?.focus()
  return !first
}
function clearErrors() {
  for (const key of Object.keys(fieldErrors) as Field[]) fieldErrors[key] = ''
  error.value = ''
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
  if (sending.value || verifying.value || loading.value) return
  error.value = ''
  if (!validate()) return
  if (step.value === 2) return verifyCode()
  const current = capture(),
    version = stageVersion
  loading.value = true
  error.value = ''
  let authenticated = false
  try {
    if (step.value === 1) {
      step.value = 2
      return
    }
    const response = await register(
      {
        username: form.username,
        email: form.email,
        password: form.password,
        verificationTicket: form.verificationTicket,
      },
      { errorDisplay: 'inline' },
    )
    if (!current() || version !== stageVersion) return
    userStore.setLoginInfo(response.data.token, response.data.user)
    authenticated = true
    await router.push('/my-courses')
  } catch (cause) {
    if (current() && version === stageVersion)
      error.value = errorMessage(cause, '暂时无法创建账号，输入已保留，请重试。')
  } finally {
    if ((current() && version === stageVersion) || authenticated) loading.value = false
  }
}
async function sendCode() {
  if (busy.value || countdown.value > 0 || step.value !== 2) return
  const current = capture(),
    version = stageVersion
  const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : undefined
  sending.value = true
  error.value = ''
  try {
    const email = form.email
    const turnstileToken = await verificationRef.value?.verify(trigger)
    if (!turnstileToken || !current() || version !== stageVersion) return
    await sendRegisterCode(email, turnstileToken, { errorDisplay: 'inline' })
    if (!current() || version !== stageVersion) return
    sent.value = true
    const deadline = Date.now() + 60_000
    countdown.value = 60
    if (timer) clearInterval(timer)
    timer = window.setInterval(() => {
      countdown.value = Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
      if (countdown.value <= 0 && timer) clearInterval(timer)
    }, 1000)
  } catch (cause) {
    if (current() && version === stageVersion) error.value = errorMessage(cause, '验证码暂时无法发送，请重试。')
  } finally {
    if (current() && version === stageVersion) sending.value = false
  }
}
async function verifyCode() {
  if (verifying.value || sending.value || step.value !== 2 || !/^\d{6}$/.test(code.value)) return
  const current = capture(),
    version = stageVersion
  verifying.value = true
  error.value = ''
  try {
    const res = await verifyRegisterCode(form.email, code.value, { errorDisplay: 'inline' })
    if (!current() || version !== stageVersion) return
    form.verificationTicket = res.data.verificationTicket
    step.value = 3
  } catch (cause) {
    if (current() && version === stageVersion) error.value = errorMessage(cause, '验证码暂时无法验证，请重试。')
  } finally {
    if (current() && version === stageVersion) verifying.value = false
  }
}
function backToAccount() {
  if (sending.value || verifying.value || loading.value) return
  stageVersion++
  step.value = 1
  sent.value = false
  clearErrors()
  code.value = ''
  form.verificationTicket = ''
}
function backToVerification() {
  if (loading.value) return
  stageVersion++
  step.value = 2
  clearErrors()
  form.verificationTicket = ''
  code.value = ''
}
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <AuthFrame>
    <h1 id="auth-title" class="title">创建学习账号</h1>
    <p v-if="step === 2" class="lead">
      <template v-if="form.email"
        >验证码将发送至 <strong>{{ form.email }}</strong></template
      >
      <template v-else>验证码将发送至注册邮箱</template>
    </p>
    <p class="progress" role="status" aria-live="polite">步骤 {{ step }}/3</p>
    <form class="form" novalidate @submit.prevent="handlePrimary">
      <template v-if="step === 1">
        <LtTextField
          ref="usernameField"
          v-model="form.username"
          label="用户名"
          placeholder="用户名（3-50 个字符）"
          autocomplete="username"
          :error="fieldErrors.username"
          :disabled="busy"
          @update:model-value="edited('username')"
        />
        <LtTextField
          ref="emailField"
          v-model="form.email"
          label="邮箱"
          placeholder="邮箱"
          autocomplete="email"
          :error="fieldErrors.email"
          :disabled="busy"
          @update:model-value="edited('email')"
        />
      </template>
      <template v-else-if="step === 2">
        <LtTextField
          ref="codeField"
          v-model="code"
          label="邮箱验证码"
          placeholder="6 位数字"
          inputmode="numeric"
          :maxlength="6"
          autocomplete="one-time-code"
          :error="fieldErrors.code"
          :disabled="busy"
          @update:model-value="edited('code')"
        >
          <template #label-trailing>
            <LtButton :disabled="busy || countdown > 0" size="sm" @click="sendCode">{{
              sending ? '正在发送…' : countdown > 0 ? `${countdown}s 后重发` : '获取验证码'
            }}</LtButton>
          </template>
        </LtTextField>
        <p v-if="sent" class="sent" role="status">验证码已发送</p>
      </template>
      <template v-else>
        <LtTextField
          ref="passwordField"
          v-model="form.password"
          label="密码"
          type="password"
          placeholder="密码（8-64 个字符）"
          autocomplete="new-password"
          :error="fieldErrors.password"
          :disabled="busy"
          @update:model-value="edited('password')"
        />
        <p v-if="form.password" class="strength">密码强度：{{ passwordStrengthLabel }}</p>
        <LtTextField
          ref="confirmPasswordField"
          v-model="form.confirmPassword"
          label="确认密码"
          type="password"
          placeholder="确认密码"
          autocomplete="new-password"
          :error="fieldErrors.confirmPassword"
          :disabled="busy"
          @update:model-value="edited('confirmPassword')"
        />
      </template>
      <p v-if="error" class="error" role="alert">
        <CircleAlert :size="16" aria-hidden="true" /><span>{{ error }}</span>
      </p>
      <div class="actions">
        <LtButton
          v-if="step > 1"
          size="lg"
          :disabled="busy"
          @click="step === 2 ? backToAccount() : backToVerification()"
          >上一步</LtButton
        >
        <LtButton type="submit" variant="primary" size="lg" class="submit" :disabled="busy">{{
          loading
            ? '正在创建…'
            : verifying
              ? '正在验证…'
              : sending
                ? '正在发送…'
                : step === 1
                  ? '下一步'
                  : step === 2
                    ? '验证并继续'
                    : '创建账号'
        }}</LtButton>
      </div>
    </form>
    <AuthProviders v-if="step === 1" :preview="previewMode" :disabled="busy" />
    <TurnstileSheet ref="verificationRef" />
    <template v-if="step === 1" #footer>已有账号？<RouterLink to="/login">立即登录</RouterLink></template>
  </AuthFrame>
</template>

<style scoped>
.title {
  margin: 0;
  font-size: var(--lt-text-title2);
}
.lead {
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-label-2);
  overflow-wrap: anywhere;
}
.form {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-5);
  margin-top: var(--lt-space-7);
}
.error {
  display: flex;
  align-items: flex-start;
  gap: var(--lt-space-2);
  margin: 0;
  padding: var(--lt-space-3) var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-red-soft);
  color: var(--lt-red-text);
  font-size: var(--lt-text-footnote);
  line-height: var(--lt-leading-ui);
}
.error svg {
  flex: none;
}
.submit {
  width: 100%;
  margin-top: var(--lt-space-1);
}

.progress,
.strength,
.sent {
  margin: var(--lt-space-4) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.form {
  margin-top: var(--lt-space-5);
}
.strength,
.sent {
  margin: 0;
}
.actions {
  display: flex;
  gap: var(--lt-space-3);
}
.actions .submit {
  flex: 1;
  width: auto;
  margin: 0;
}
</style>
