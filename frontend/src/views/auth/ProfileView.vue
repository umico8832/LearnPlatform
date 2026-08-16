<template>
  <div class="profile-container page-container">
    <section class="profile-hero">
      <div class="hero-copy">
        <span class="section-kicker">账户与学习档案</span>
        <h2>个人中心</h2>
        <p>管理展示昵称与登录密码，同时快速回到学习报告、刷题练习和收藏题复盘。</p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" :icon="DataLine" @click="router.push('/learning-report')"> 查看学习报告 </el-button>
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
import {
  ArrowRight,
  Clock,
  DataLine,
  EditPen,
  Key,
  Lock,
  Reading,
  Star,
  TrendCharts,
  User,
} from '@element-plus/icons-vue'
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
  { label: '学习报告', path: '/learning-report', icon: TrendCharts },
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
  padding: 24px;
}

.profile-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
  padding: 24px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  background: linear-gradient(135deg, rgba(23, 105, 170, 0.09), rgba(47, 133, 90, 0.08)), var(--lp-surface);
}

.section-kicker {
  display: inline-block;
  margin-bottom: 8px;
  color: var(--lp-primary);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.profile-hero h2 {
  margin: 0;
  color: var(--lp-text);
  font-size: 24px;
  font-weight: 850;
}

.profile-hero p {
  margin: 8px 0 0;
  max-width: 640px;
  color: var(--lp-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.profile-layout {
  display: grid;
  grid-template-columns: minmax(260px, 340px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.identity-panel,
.settings-panel {
  display: grid;
  gap: 16px;
}

.identity-card :deep(.el-card__body) {
  padding: 22px !important;
}

.user-card {
  text-align: center;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--lp-border);
}

.user-avatar {
  background: linear-gradient(135deg, var(--lp-primary), var(--lp-success));
  color: #fff;
  font-size: 34px;
  font-weight: 850;
}

.user-name {
  margin: 16px 0 8px;
  color: var(--lp-text);
  font-size: 20px;
  font-weight: 850;
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.profile-meta-list {
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.profile-meta-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
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
  border-radius: 8px;
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
  font-size: 18px;
}

.meta-icon.accent {
  color: var(--lp-warning);
  background: #fff7e6;
}

.meta-icon.safe {
  color: var(--lp-success);
  background: #edf8f2;
}

.profile-meta-item small,
.card-header small,
.shortcut-title span {
  display: block;
  color: var(--lp-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.profile-meta-item strong {
  display: block;
  margin-top: 3px;
  color: var(--lp-text);
  font-size: 14px;
  font-weight: 800;
  overflow-wrap: anywhere;
}

.shortcut-title {
  margin-bottom: 12px;
}

.shortcut-title strong {
  display: block;
  color: var(--lp-text);
  font-size: 15px;
  font-weight: 850;
}

.shortcut-title span {
  margin-top: 4px;
}

.shortcut-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-top: 10px;
  padding: 10px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  background: var(--lp-surface);
  color: var(--lp-text);
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.18s ease,
    transform 0.18s ease,
    box-shadow 0.18s ease;
}

.shortcut-item:hover {
  border-color: var(--lp-primary);
  box-shadow: var(--lp-shadow-sm);
  transform: translateY(-1px);
}

.shortcut-item span:nth-child(2) {
  font-size: 14px;
  font-weight: 750;
}

.settings-card :deep(.el-card__header) {
  background: var(--lp-surface-soft);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-header strong {
  display: block;
  color: var(--lp-text);
  font-size: 16px;
  font-weight: 850;
}

.header-icon.warning {
  color: var(--lp-warning);
  background: #fff7e6;
}

.settings-card :deep(.el-form-item__label) {
  color: var(--lp-text-secondary);
  font-weight: 750;
}

.form-actions {
  margin-bottom: 0;
}

.password-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 4px 0 18px;
  padding: 11px 12px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  background: #fffdf5;
  color: var(--lp-text-secondary);
  font-size: 13px;
  line-height: 1.6;
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
    padding: 16px;
  }

  .profile-hero {
    align-items: stretch;
    flex-direction: column;
    padding: 18px;
  }

  .profile-hero h2 {
    font-size: 21px;
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
