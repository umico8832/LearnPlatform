<template>
  <div class="profile-container">
    <el-row :gutter="24">
      <!-- 左侧：用户信息卡片 -->
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover">
          <div class="user-card">
            <el-avatar :size="80" class="user-avatar">
              {{ avatarText }}
            </el-avatar>
            <h3 class="user-name">{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}</h3>
            <el-tag size="small">{{ userStore.userInfo?.role === 'ADMIN' ? '管理员' : '普通用户' }}</el-tag>
            <div class="user-meta">
              <p><el-icon><User /></el-icon> {{ userStore.userInfo?.username }}</p>
              <p v-if="userStore.userInfo?.createTime">
                <el-icon><Clock /></el-icon> 注册于 {{ userStore.userInfo.createTime?.slice(0, 10) }}
              </p>
            </div>
            <el-button class="report-btn" type="primary" plain @click="$router.push('/learning-report')">
              <el-icon><DataLine /></el-icon> 查看学习报告
            </el-button>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：修改信息 -->
      <el-col :xs="24" :sm="16">
        <!-- 修改昵称 -->
        <el-card shadow="hover" class="section-card">
          <template #header>
            <span class="section-title">个人信息</span>
          </template>
          <el-form
            ref="profileFormRef"
            :model="profileForm"
            :rules="profileRules"
            label-width="80px"
            @submit.prevent="handleUpdateProfile"
          >
            <el-form-item label="用户名">
              <el-input :model-value="userStore.userInfo?.username" disabled />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="profileForm.nickname" placeholder="请输入昵称" maxlength="30" show-word-limit />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="profileLoading" @click="handleUpdateProfile">
                保存修改
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 修改密码 -->
        <el-card shadow="hover" class="section-card">
          <template #header>
            <span class="section-title">修改密码</span>
          </template>
          <el-form
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordRules"
            label-width="80px"
            @submit.prevent="handleUpdatePassword"
          >
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码（6-50 位）" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="passwordLoading" @click="handleUpdatePassword">
                修改密码
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Clock, DataLine } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { updateProfile, updatePassword } from '@/api/user'

const userStore = useUserStore()

// 头像文字（取昵称或用户名首字）
const avatarText = computed(() => {
  const name = userStore.userInfo?.nickname || userStore.userInfo?.username || '?'
  return name.charAt(0).toUpperCase()
})

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
    // 更新 store 中的用户信息
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
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' },
  ],
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
    // 清空表单
    passwordFormRef.value?.resetFields()
  } catch {
    // 错误已由拦截器处理
  } finally {
    passwordLoading.value = false
  }
}

// 初始化
onMounted(() => {
  if (userStore.userInfo) {
    profileForm.nickname = userStore.userInfo.nickname || ''
  }
})
</script>

<style scoped>
.profile-container {
  max-width: 900px;
}

.user-card {
  text-align: center;
  padding: 20px 0;
}

.user-avatar {
  background: #409eff;
  font-size: 32px;
  font-weight: 600;
  color: #fff;
}

.user-name {
  margin: 16px 0 8px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.user-meta {
  margin-top: 20px;
  text-align: left;
  padding: 0 20px;
}

.user-meta p {
  margin: 8px 0;
  color: #909399;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.report-btn {
  margin-top: 20px;
  width: calc(100% - 40px);
}

.section-card {
  margin-bottom: 20px;
}

.section-title {
  font-weight: 600;
  font-size: 16px;
}
</style>