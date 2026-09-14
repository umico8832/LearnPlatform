<template>
  <AuthLayout class="recovery-page" :show-symbol="false">
    <template v-if="!submitted">
      <div class="recovery-symbol recovery-symbol--mail" aria-hidden="true">
        <el-icon><Message /></el-icon>
      </div>
      <div class="auth-card-header recovery-header">
        <h1 id="auth-title">重置密码</h1>
        <p>输入注册邮箱，我们会发送重置链接。</p>
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
    <div v-else class="recovery-status" role="status">
      <div class="recovery-symbol recovery-symbol--success" aria-hidden="true">
        <el-icon><CircleCheckFilled /></el-icon>
      </div>
      <h2 id="auth-title">邮件已发送</h2>
      <p>
        重置链接 30 分钟内有效。{{ resendCount === 0 ? '没有收到邮件？' : '仍未收到？'
        }}<button
          v-if="resendCount < maxResendAttempts"
          type="button"
          class="recovery-help-link recovery-resend-link"
          :disabled="loading"
          @click="resend"
        >
          {{ loading ? '正在发送…' : resendCount === 0 ? '重新发送' : '再次发送' }}</button
        ><router-link v-else class="recovery-help-link recovery-support-link" to="/">联系支持</router-link>
      </p>
    </div>
    <div class="auth-footer recovery-actions"><router-link to="/login">返回登录</router-link></div>
    <TurnstileDialog ref="verificationRef" />
  </AuthLayout>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { CircleCheckFilled, Message } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import AuthLayout from '@/components/auth/AuthLayout.vue'
import TurnstileDialog from '@/components/auth/TurnstileDialog.vue'
import { forgotPassword } from '@/api/auth'
import { getAuthPreviewState } from '@/utils/authPreview'
import '@/assets/styles/auth.css'

const route = useRoute()
const previewState = getAuthPreviewState(route.query['auth-preview'], ['form', 'sent', 'resent', 'support'])
const formRef = ref<FormInstance>(),
  verificationRef = ref<InstanceType<typeof TurnstileDialog>>(),
  loading = ref(false),
  submitted = ref(previewState === 'sent' || previewState === 'resent' || previewState === 'support'),
  resendCount = ref(previewState === 'support' ? 2 : previewState === 'resent' ? 1 : 0)
const maxResendAttempts = 2
const form = reactive({ email: previewState ? 'learner@example.com' : '' })
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
    submitted.value = await requestReset(form.email, trigger)
  } catch {
  } finally {
    loading.value = false
  }
}

async function resend() {
  if (loading.value || resendCount.value >= maxResendAttempts) return
  const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : undefined
  loading.value = true
  try {
    if (await requestReset(form.email, trigger)) resendCount.value++
  } catch {
  } finally {
    loading.value = false
  }
}

async function requestReset(email: string, trigger?: HTMLElement) {
  const turnstileToken = await verificationRef.value?.verify(trigger)
  if (!turnstileToken) return false
  await forgotPassword(email, turnstileToken)
  return true
}
</script>

<style scoped>
.recovery-symbol {
  display: grid;
  place-items: center;
  margin: 0 auto var(--lp-space-6);
  border-radius: 50%;
}
.recovery-symbol--mail {
  width: 72px;
  height: 72px;
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
}
.recovery-symbol--mail .el-icon {
  font-size: var(--lp-text-5xl);
}
.recovery-header {
  margin-bottom: var(--lp-space-6);
}
.recovery-status {
  padding: var(--lp-space-2) 0 var(--lp-space-1);
  text-align: center;
}
.recovery-symbol--success {
  width: 88px;
  height: 88px;
  margin-bottom: var(--lp-space-6);
  color: var(--lp-success);
}
.recovery-symbol--success .el-icon {
  font-size: 88px;
}
.recovery-status h2 {
  margin: 0;
  color: var(--lp-text);
  font-size: var(--lp-text-4xl);
  font-weight: var(--lp-weight-bold);
  line-height: var(--lp-leading-tight);
  letter-spacing: var(--lp-tracking-tight);
}
.recovery-status p {
  margin: var(--lp-space-3) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-body);
  white-space: nowrap;
}
.recovery-help-link {
  appearance: none;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--lp-link);
  font: inherit;
  font-weight: var(--lp-weight-semibold);
  cursor: pointer;
}
.recovery-help-link:disabled {
  color: var(--lp-text-muted);
  cursor: default;
}
.recovery-help-link:hover {
  color: var(--lp-link-hover);
  text-decoration: underline;
}
.recovery-help-link:hover:disabled {
  color: var(--lp-text-muted);
  text-decoration: none;
}
.recovery-help-link:focus-visible {
  border-radius: var(--lp-radius-xs);
  outline: 2px solid var(--lp-primary);
  outline-offset: var(--lp-space-1);
}
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
@media (min-width: 1280px) {
  .recovery-header h1,
  .recovery-status h2 {
    font-size: var(--lp-text-5xl);
  }
  .recovery-header p,
  .recovery-status p {
    font-size: var(--lp-text-lg);
  }
  .recovery-page :deep(.auth-form .el-form-item__label) {
    font-size: var(--lp-text-base);
  }
  .recovery-page :deep(.auth-form .el-input__inner),
  .recovery-page :deep(.auth-primary),
  .recovery-actions a {
    font-size: var(--lp-text-lg);
  }
  .recovery-actions a {
    min-height: var(--lp-space-12);
  }
}
@media (max-width: 340px) {
  .recovery-status p {
    white-space: normal;
  }
}
</style>
