<template>
  <div class="profile-page lt" data-appearance="dark" aria-labelledby="profile-title">
    <LtPageHeader title="个人中心" title-id="profile-title">
      <template #actions
        ><LtLinkButton to="/practice"
          ><template #icon><Pencil aria-hidden="true" /></template>继续刷题</LtLinkButton
        ></template
      >
    </LtPageHeader>
    <section class="profile-layout">
      <aside class="identity">
        <LtCard
          ><div class="avatar" aria-hidden="true">{{ avatarText }}</div>
          <h2 class="user-name">{{ displayName }}</h2>
          <p class="role tag">{{ roleLabel }}</p>
          <dl>
            <div>
              <dt><UserRound aria-hidden="true" />登录账号</dt>
              <dd>{{ userStore.userInfo?.username || '暂未记录' }}</dd>
            </div>
            <div>
              <dt><CalendarDays aria-hidden="true" />注册时间</dt>
              <dd>{{ registeredDate }}</dd>
            </div>
          </dl></LtCard
        ><LtCard
          ><template #title>常用入口</template>
          <nav class="shortcuts" aria-label="常用入口">
            <RouterLink v-for="item in shortcutItems" :key="item.label" :to="item.path"
              ><component :is="item.icon" aria-hidden="true" /><span>{{ item.label }}</span
              ><ChevronRight aria-hidden="true"
            /></RouterLink></nav
        ></LtCard>
      </aside>
      <section class="settings" aria-label="账户设置">
        <LtCard radius="xl"
          ><template #title>个人信息</template>
          <form @submit.prevent="handleUpdateProfile">
            <label>用户名<input :value="userStore.userInfo?.username" disabled /></label
            ><label
              >昵称<input
                v-model="profileForm.nickname"
                maxlength="30"
                :disabled="isSubmitting"
                placeholder="请输入昵称"
            /></label>
            <p v-if="profileError" class="form-error" role="alert">
              {{ profileError }}
              <button type="button" :disabled="isSubmitting" @click="handleUpdateProfile">重试</button>
            </p>
            <LtButton type="submit" variant="primary" :disabled="isSubmitting">{{
              profileLoading ? '保存中…' : '保存修改'
            }}</LtButton>
          </form></LtCard
        ><LtCard radius="xl"
          ><template #title>修改密码</template>
          <form @submit.prevent="handleUpdatePassword">
            <LtTextField
              v-model="passwordForm.oldPassword"
              label="原密码"
              type="password"
              autocomplete="current-password"
              :disabled="isSubmitting"
              placeholder="请输入原密码"
            />
            <LtTextField
              v-model="passwordForm.newPassword"
              label="新密码"
              type="password"
              autocomplete="new-password"
              :disabled="isSubmitting"
              placeholder="请输入新密码（6–50 位）"
            />
            <LtTextField
              v-model="passwordForm.confirmPassword"
              label="确认密码"
              type="password"
              autocomplete="new-password"
              :disabled="isSubmitting"
              placeholder="请再次输入新密码"
            />
            <p class="password-note"><LockKeyhole aria-hidden="true" />修改后需要重新登录。</p>
            <p v-if="passwordError" class="form-error" role="alert">
              {{ passwordError }}
              <button type="button" :disabled="isSubmitting" @click="handleUpdatePassword">重试</button>
            </p>
            <LtButton type="submit" variant="primary" :disabled="isSubmitting">{{
              passwordLoading ? '修改中…' : '修改密码'
            }}</LtButton>
          </form></LtCard
        ><ProfileGamification />
      </section>
    </section>
  </div>
</template>
<script setup lang="ts">
import LtPageHeader from '@/components/living/LtPageHeader.vue'
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { CalendarDays, ChevronRight, LockKeyhole, Pencil, Star, BookOpen, UserRound } from '@lucide/vue'
import { LtButton, LtCard, LtLinkButton, LtTextField } from '@/components/living'
import { useUserStore } from '@/stores/user'
import { updateProfile, updatePassword } from '@/api/user'
import ProfileGamification from '@/components/gamification/ProfileGamification.vue'
import { errorMessage } from '@/utils/errors'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
const router = useRouter()
const userStore = useUserStore()
const displayName = computed(() => userStore.userInfo?.nickname || userStore.userInfo?.username || '学习者')
const roleLabel = computed(() => (userStore.userInfo?.role === 'ADMIN' ? '管理员' : '普通用户'))
const registeredDate = computed(() => userStore.userInfo?.createTime?.slice(0, 10) || '暂未记录')
const avatarText = computed(() => displayName.value.charAt(0).toUpperCase())
const shortcutItems = [
  { label: '智能复习', path: '/review', icon: BookOpen },
  { label: '我的收藏', path: '/favorites', icon: Star },
]
const profileLoading = ref(false)
const profileError = ref('')
const profileForm = reactive({ nickname: '' })
const passwordLoading = ref(false)
const passwordError = ref('')
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const isSubmitting = computed(() => profileLoading.value || passwordLoading.value)
let alive = true
let requestVersion = 0
function isCurrent(request: number, session: number) {
  return alive && request === requestVersion && session === getAuthSessionVersion() && isAuthenticated()
}
function profileValid() {
  if (!profileForm.nickname.trim()) {
    profileError.value = '请输入昵称'
    return false
  }
  if (profileForm.nickname.length > 30) {
    profileError.value = '昵称长度为 1–30 个字符'
    return false
  }
  return true
}
function passwordValid() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
    passwordError.value = '请填写全部密码字段'
    return false
  }
  if (passwordForm.newPassword.length < 6 || passwordForm.newPassword.length > 50) {
    passwordError.value = '密码长度为 6–50 位'
    return false
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    passwordError.value = '两次输入的密码不一致'
    return false
  }
  return true
}
async function handleUpdateProfile() {
  if (isSubmitting.value) return
  const session = getAuthSessionVersion()
  const request = ++requestVersion
  profileError.value = ''
  profileLoading.value = true
  try {
    await Promise.resolve()
    if (!profileValid() || !isCurrent(request, session)) return
    const res = await updateProfile({ nickname: profileForm.nickname }, { errorDisplay: 'inline' })
    if (isCurrent(request, session) && userStore.userInfo && res.data) userStore.userInfo.nickname = res.data.nickname
  } catch (e) {
    if (isCurrent(request, session)) profileError.value = errorMessage(e, '昵称保存失败，请重试')
  } finally {
    if (isCurrent(request, session)) profileLoading.value = false
  }
}
async function handleUpdatePassword() {
  if (isSubmitting.value) return
  const session = getAuthSessionVersion()
  const request = ++requestVersion
  passwordError.value = ''
  passwordLoading.value = true
  try {
    await Promise.resolve()
    if (!passwordValid() || !isCurrent(request, session)) return
    await updatePassword(
      { oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword },
      { errorDisplay: 'inline' },
    )
    if (!isCurrent(request, session)) return
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    userStore.clearLoginInfo()
    await router.replace('/login')
  } catch (e) {
    if (isCurrent(request, session)) passwordError.value = errorMessage(e, '密码修改失败，请重试')
  } finally {
    if (isCurrent(request, session)) passwordLoading.value = false
  }
}
const unsubscribeAuth = onAuthSessionChange(() => {
  requestVersion++
  profileLoading.value = false
  passwordLoading.value = false
  profileError.value = ''
  passwordError.value = ''
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
})
watch(
  () => userStore.userInfo?.id,
  () => {
    profileForm.nickname = userStore.userInfo?.nickname || ''
  },
  { immediate: true },
)
onUnmounted(() => {
  alive = false
  requestVersion++
  unsubscribeAuth()
})
</script>
<style scoped>
.settings form > .lt-button {
  justify-self: end;
  width: max-content;
}

.profile-layout {
  display: grid;
  grid-template-columns: calc(var(--lt-space-10) * 4) minmax(0, 1fr);
  gap: var(--lt-space-6);
  align-items: start;
}
.identity,
.settings {
  display: grid;
  gap: var(--lt-space-5);
}
.avatar {
  display: grid;
  place-items: center;
  width: var(--lt-space-10);
  height: var(--lt-space-10);
  margin: 0 auto var(--lt-space-4);
  border-radius: 50%;
  background: var(--lt-accent-soft);
  color: var(--lt-accent);
  font: var(--lt-text-title1)/1 var(--lt-font-rounded);
}
.identity h2,
.role {
  margin: 0;
  text-align: center;
}
.identity h2 {
  font-size: var(--lt-text-title2);
}
.role {
  margin-top: var(--lt-space-1);
  color: var(--lt-label-3);
  font-size: var(--lt-text-footnote);
}
dl {
  display: grid;
  gap: var(--lt-space-3);
  margin: var(--lt-space-5) 0 0;
}
dl div {
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-2);
}
dt {
  display: flex;
  align-items: center;
  gap: var(--lt-space-2);
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}
dd {
  margin: var(--lt-space-1) 0 0;
  overflow-wrap: anywhere;
}
.shortcuts {
  display: grid;
  gap: var(--lt-space-2);
}
.shortcuts a {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: var(--lt-space-2);
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-md);
  color: var(--lt-label);
  text-decoration: none;
}
.shortcuts a:hover {
  background: var(--lt-fill-2);
  color: var(--lt-accent);
}
form {
  display: grid;
  gap: var(--lt-space-4);
}
form label {
  display: grid;
  gap: var(--lt-space-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
form input {
  height: var(--lt-control-lg);
  box-sizing: border-box;
  padding: 0 var(--lt-space-3);
  border: 0;
  border-radius: var(--lt-radius-md);
  outline: 0;
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 1px var(--lt-separator);
  color: var(--lt-label);
  font: var(--lt-text-body)/1 var(--lt-font);
}
form input:focus-visible {
  box-shadow: inset 0 0 0 2px var(--lt-accent);
}
.form-error {
  margin: 0;
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-md);
  background: var(--lt-red-soft);
  color: var(--lt-red-text);
  font-size: var(--lt-text-footnote);
}
.form-error button {
  border: 0;
  background: none;
  color: inherit;
  font: inherit;
  text-decoration: underline;
}
.password-note {
  display: flex;
  align-items: center;
  gap: var(--lt-space-2);
  margin: 0;
  color: var(--lt-label-3);
  font-size: var(--lt-text-footnote);
}
.profile-page :deep(input::placeholder) {
  color: var(--lt-label-2);
  opacity: 1;
}
@media (max-width: 720px) {
  .profile-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
