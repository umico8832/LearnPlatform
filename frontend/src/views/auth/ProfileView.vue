<template>
  <div class="profile-container page-container">
    <section class="profile-hero">
      <div class="hero-copy">
        <span class="section-kicker">账户与学习档案</span>
        <h1>个人中心</h1>
        <p>管理展示昵称与登录密码，同时快速回到刷题练习和收藏题复盘。</p>
      </div>
      <div class="hero-actions">
        <el-button :icon="EditPen" @click="router.push('/practice')"> 继续刷题 </el-button>
      </div>
    </section>

    <section class="profile-layout">
      <aside class="identity-panel">
        <el-card shadow="never" class="identity-card">
          <div class="user-card">
            <el-avatar :size="88" class="user-avatar">
              {{ avatarText }}
            </el-avatar>
            <h3 class="user-name">{{ displayName }}</h3>
            <el-tag size="small" :type="userStore.userInfo?.role === 'ADMIN' ? 'warning' : 'success'">
              {{ roleLabel }}
            </el-tag>
          </div>

          <div class="profile-meta-list">
            <div class="profile-meta-item">
              <span class="meta-icon"
                ><el-icon><User /></el-icon
              ></span>
              <div>
                <small>登录账号</small>
                <strong>{{ userStore.userInfo?.username || '-' }}</strong>
              </div>
            </div>
            <div class="profile-meta-item">
              <span class="meta-icon accent"
                ><el-icon><Clock /></el-icon
              ></span>
              <div>
                <small>注册时间</small>
                <strong>{{ registeredDate }}</strong>
              </div>
            </div>
            <div class="profile-meta-item">
              <span class="meta-icon safe"
                ><el-icon><Lock /></el-icon
              ></span>
              <div>
                <small>账户安全</small>
                <strong>密码可随时更新</strong>
              </div>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="shortcut-card">
          <div class="shortcut-title">
            <strong>常用入口</strong>
            <span>继续最近的学习动作</span>
          </div>
          <button
            v-for="item in shortcutItems"
            :key="item.label"
            class="shortcut-item"
            type="button"
            @click="router.push(item.path)"
          >
            <span class="shortcut-icon"
              ><el-icon><component :is="item.icon" /></el-icon
            ></span>
            <span>{{ item.label }}</span>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </el-card>
      </aside>

      <main class="settings-panel">
        <el-card shadow="never" class="settings-card">
          <template #header>
            <div class="card-header">
              <span class="header-icon"
                ><el-icon><EditPen /></el-icon
              ></span>
              <div>
                <strong>个人信息</strong>
                <small>昵称会显示在学习记录、评论和个人档案中。</small>
              </div>
            </div>
          </template>
          <el-form
            ref="profileFormRef"
            :model="profileForm"
            :rules="profileRules"
            label-position="top"
            @submit.prevent="handleUpdateProfile"
          >
            <el-form-item label="用户名">
              <el-input :model-value="userStore.userInfo?.username" disabled />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="profileForm.nickname" placeholder="请输入昵称" maxlength="30" show-word-limit />
            </el-form-item>
            <el-form-item class="form-actions">
              <el-button type="primary" :loading="profileLoading" @click="handleUpdateProfile"> 保存修改 </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="settings-card">
          <template #header>
            <div class="card-header">
              <span class="header-icon warning"
                ><el-icon><Key /></el-icon
              ></span>
              <div>
                <strong>修改密码</strong>
                <small>建议使用 6-50 位密码，并避免和其他网站重复。</small>
              </div>
            </div>
          </template>
          <el-form
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordRules"
            label-position="top"
            @submit.prevent="handleUpdatePassword"
          >
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="passwordForm.newPassword"
                type="password"
                show-password
                placeholder="请输入新密码（6-50 位）"
              />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="passwordForm.confirmPassword"
                type="password"
                show-password
                placeholder="请再次输入新密码"
              />
            </el-form-item>
            <div class="password-note">
              <el-icon><Lock /></el-icon>
              修改成功后请使用新密码重新登录，当前表单不会保存原密码。
            </div>
            <el-form-item class="form-actions">
              <el-button type="primary" :loading="passwordLoading" @click="handleUpdatePassword"> 修改密码 </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { ArrowRight, Clock, EditPen, Key, Lock, Reading, Star, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { updateProfile, updatePassword } from '@/api/user'

const router = useRouter()
const userStore = useUserStore()

const displayName = computed(() => userStore.userInfo?.nickname || userStore.userInfo?.username || '学习者')
const roleLabel = computed(() => (userStore.userInfo?.role === 'ADMIN' ? '管理员' : '普通用户'))
const registeredDate = computed(() => userStore.userInfo?.createTime?.slice(0, 10) || '暂未记录')

const avatarText = computed(() => {
  return displayName.value.charAt(0).toUpperCase()
})

const shortcutItems = [
  { label: '智能复习', path: '/review', icon: Reading },
  { label: '我的收藏', path: '/favorites', icon: Star },
]

// ========== 修改昵称 ==========
const profileFormRef = ref<FormInstance>()
const profileLoading = ref(false)
const profileForm = reactive({
  nickname: '',
})

const profileRules: FormRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 1, max: 30, message: '昵称长度为 1-30 个字符', trigger: 'blur' },
  ],
}

async function handleUpdateProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return

  profileLoading.value = true
  try {
    const res = await updateProfile({ nickname: profileForm.nickname })
    if (userStore.userInfo && res.data) {
      userStore.userInfo.nickname = res.data.nickname
    }
    ElMessage.success('昵称修改成功')
  } catch {
    // 错误已由拦截器处理
  } finally {
    profileLoading.value = false
  }
}

// ========== 修改密码 ==========
const passwordFormRef = ref<FormInstance>()
const passwordLoading = ref(false)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码长度为 6-50 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function handleUpdatePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return

  passwordLoading.value = true
  try {
    await updatePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    ElMessage.success('密码修改成功，请重新登录')
    passwordFormRef.value?.resetFields()
  } catch {
    // 错误已由拦截器处理
  } finally {
    passwordLoading.value = false
  }
}

onMounted(() => {
  if (userStore.userInfo) {
    profileForm.nickname = userStore.userInfo.nickname || ''
  }
})
</script>

<style scoped>
.profile-container {
  padding: var(--lp-space-6);
}

.profile-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--lp-space-5);
  margin-bottom: var(--lp-space-5);
  padding: var(--lp-space-6);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: linear-gradient(135deg, var(--lp-primary-soft) 0%, var(--lp-surface) 58%), var(--lp-surface);
}

.section-kicker {
  display: inline-block;
  margin-bottom: var(--lp-space-2);
  color: var(--lp-primary);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-heavy);
  letter-spacing: var(--lp-tracking-wide);
}

.profile-hero h1 {
  margin: 0;
  color: var(--lp-text);
  font-size: var(--lp-text-3xl);
  font-weight: var(--lp-weight-heavy);
  letter-spacing: var(--lp-tracking-tight);
}

.profile-hero p {
  margin: var(--lp-space-2) 0 0;
  max-width: var(--lp-reading-measure);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-relaxed);
}

.hero-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--lp-space-3);
  flex-wrap: wrap;
}

.profile-layout {
  display: grid;
  grid-template-columns: minmax(260px, 340px) minmax(0, 1fr);
  gap: var(--lp-space-5);
  align-items: start;
}

.identity-panel,
.settings-panel {
  display: grid;
  gap: var(--lp-space-4);
}

.identity-card :deep(.el-card__body) {
  padding: var(--lp-space-5) !important;
}

.user-card {
  text-align: center;
  padding-bottom: var(--lp-space-5);
  border-bottom: var(--lp-border-hairline);
}

.user-avatar {
  background: linear-gradient(135deg, var(--lp-primary), var(--lp-success));
  color: var(--lp-on-primary);
  font-size: var(--lp-text-5xl);
  font-weight: var(--lp-weight-heavy);
}

.user-name {
  margin: var(--lp-space-4) 0 var(--lp-space-2);
  color: var(--lp-text);
  font-size: var(--lp-text-2xl);
  font-weight: var(--lp-weight-heavy);
  line-height: var(--lp-leading-tight);
  overflow-wrap: anywhere;
}

.profile-meta-list {
  display: grid;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-5);
}

.profile-meta-item {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  padding: var(--lp-space-3);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface-soft);
}

.meta-icon,
.header-icon,
.shortcut-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  border-radius: var(--lp-radius-sm);
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
  font-size: var(--lp-text-xl);
}

.meta-icon.accent {
  color: var(--lp-warning);
  background: var(--lp-warning-soft);
}

.meta-icon.safe {
  color: var(--lp-success);
  background: var(--lp-success-soft);
}

.profile-meta-item small,
.card-header small,
.shortcut-title span {
  display: block;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  line-height: var(--lp-leading-snug);
}

.profile-meta-item strong {
  display: block;
  margin-top: var(--lp-space-1);
  color: var(--lp-text);
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-heavy);
  overflow-wrap: anywhere;
}

.shortcut-title {
  margin-bottom: var(--lp-space-3);
}

.shortcut-title strong {
  display: block;
  color: var(--lp-text);
  font-size: var(--lp-text-md);
  font-weight: var(--lp-weight-heavy);
}

.shortcut-title span {
  margin-top: var(--lp-space-1);
}

.shortcut-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--lp-space-3);
  width: 100%;
  margin-top: var(--lp-space-3);
  padding: var(--lp-space-3);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface);
  color: var(--lp-text);
  cursor: pointer;
  text-align: left;
  transition:
    border-color var(--lp-duration-normal) var(--lp-ease-out),
    transform var(--lp-duration-normal) var(--lp-ease-out),
    box-shadow var(--lp-duration-normal) var(--lp-ease-out);
}

.shortcut-item:hover {
  border-color: var(--lp-primary);
  box-shadow: var(--lp-shadow-sm);
  transform: translateY(-1px);
}

.shortcut-item span:nth-child(2) {
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-bold);
}

.settings-card :deep(.el-card__header) {
  background: var(--lp-surface-soft);
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
}

.card-header strong {
  display: block;
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
  font-weight: var(--lp-weight-heavy);
}

.header-icon.warning {
  color: var(--lp-warning);
  background: var(--lp-warning-soft);
}

.settings-card :deep(.el-form-item__label) {
  color: var(--lp-text-secondary);
  font-weight: var(--lp-weight-bold);
}

.form-actions {
  margin-bottom: 0;
}

.password-note {
  display: flex;
  align-items: flex-start;
  gap: var(--lp-space-2);
  margin: var(--lp-space-1) 0 var(--lp-space-5);
  padding: var(--lp-space-3);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-warning-soft);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}

.password-note .el-icon {
  margin-top: 2px;
  color: var(--lp-warning);
}

@media (max-width: 900px) {
  .profile-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .profile-container {
    padding: var(--lp-space-4);
  }

  .profile-hero {
    align-items: stretch;
    flex-direction: column;
    padding: var(--lp-space-4);
  }

  .profile-hero h1 {
    font-size: var(--lp-text-2xl);
  }

  .hero-actions,
  .hero-actions .el-button,
  .form-actions .el-button {
    width: 100%;
  }

  .hero-actions {
    justify-content: flex-start;
  }
}
</style>
