<template>
  <div class="user-manage">
    <div class="page-header">
      <h2>用户管理</h2>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog()">新增用户</el-button>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">用户总数</div>
          <div class="stat-value">{{ userStats.total }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card stat-active">
          <div class="stat-label">已启用</div>
          <div class="stat-value">{{ userStats.active }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card stat-disabled">
          <div class="stat-label">已禁用</div>
          <div class="stat-value">{{ userStats.disabled }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card stat-admin">
          <div class="stat-label">管理员</div>
          <div class="stat-value">{{ userStats.admins }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索用户名/昵称"
          :prefix-icon="Search"
          clearable
          style="width: 240px"
          @clear="fetchUsers"
          @keyup.enter="fetchUsers"
        />
        <el-select v-model="filterRole" placeholder="角色" clearable style="width: 120px; margin-left: 12px" @change="fetchUsers">
          <el-option label="管理员" value="ADMIN" />
          <el-option label="普通用户" value="USER" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 120px; margin-left: 12px" @change="fetchUsers">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </div>

      <!-- 用户表格 -->
      <el-table :data="users" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120">
          <template #default="{ row }">
            {{ (row as AdminUserVO).nickname || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="role" label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(row as AdminUserVO).role === 'ADMIN' ? 'danger' : 'info'" size="small">
              {{ (row as AdminUserVO).role === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="(row as AdminUserVO).status === 1 ? 'success' : 'warning'" size="small">
              {{ (row as AdminUserVO).status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="170" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openRoleDialog(row as AdminUserVO)">改角色</el-button>
            <el-button
              :type="(row as AdminUserVO).status === 1 ? 'warning' : 'success'"
              link size="small"
              @click="toggleStatus(row as AdminUserVO)"
            >
              {{ (row as AdminUserVO).status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button type="info" link size="small" @click="openResetPwdDialog(row as AdminUserVO)">重置密码</el-button>
            <el-popconfirm title="确定删除该用户？此操作不可恢复。" @confirm="handleDelete((row as AdminUserVO).id)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchUsers"
          @current-change="fetchUsers"
        />
      </div>
    </el-card>

    <!-- 新增用户弹窗 -->
    <el-dialog
      v-model="createDialogVisible"
      title="新增用户"
      width="500px"
      destroy-on-close
    >
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="3-50个字符" maxlength="50" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="createForm.password" type="password" placeholder="至少6个字符" show-password maxlength="100" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="createForm.nickname" placeholder="可选，默认为用户名" maxlength="50" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="createForm.role" style="width: 100%">
            <el-option label="普通用户" value="USER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 修改角色弹窗 -->
    <el-dialog
      v-model="roleDialogVisible"
      title="修改角色"
      width="400px"
      destroy-on-close
    >
      <el-form label-width="80px">
        <el-form-item label="用户">
          <el-input :model-value="editingUser?.username" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editingRole" style="width: 100%">
            <el-option label="普通用户" value="USER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleRoleChange">确认</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog
      v-model="resetPwdDialogVisible"
      title="重置密码"
      width="400px"
      destroy-on-close
    >
      <el-form ref="resetPwdFormRef" :model="resetPwdForm" :rules="resetPwdRules" label-width="80px" @submit.prevent>
        <el-form-item label="用户">
          <el-input :model-value="editingUser?.username" disabled />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="resetPwdForm.newPassword" type="password" placeholder="至少6个字符" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetPwdDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleResetPwd">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getAdminUserList,
  createAdminUser,
  updateUserRole,
  updateUserStatus,
  resetUserPassword,
  deleteAdminUser,
  getAdminUserStats,
  type AdminUserVO,
  type AdminUserStats,
} from '@/api/adminUser'

const users = ref<AdminUserVO[]>([])
const loading = ref(false)
const keyword = ref('')
const filterRole = ref('')
const filterStatus = ref<number | string>('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const userStats = reactive<AdminUserStats>({ total: 0, active: 0, disabled: 0, admins: 0 })

// 新增用户
const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  username: '',
  password: '',
  nickname: '',
  role: 'USER',
})
const createRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度3-50个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度6-100个字符', trigger: 'blur' },
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

// 修改角色
const roleDialogVisible = ref(false)
const editingUser = ref<AdminUserVO | null>(null)
const editingRole = ref('USER')

// 重置密码
const resetPwdDialogVisible = ref(false)
const resetPwdFormRef = ref<FormInstance>()
const resetPwdForm = reactive({ newPassword: '' })
const resetPwdRules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度6-100个字符', trigger: 'blur' },
  ],
}

const submitting = ref(false)

async function fetchUsers() {
  loading.value = true
  try {
    const res: any = await getAdminUserList({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value || undefined,
      role: filterRole.value || undefined,
      status: filterStatus.value !== '' ? filterStatus.value : undefined,
    })
    const data = res.data
    users.value = data.records
    total.value = data.total
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

async function fetchStats() {
  try {
    const res: any = await getAdminUserStats()
    const data = res.data
    userStats.total = data.total
    userStats.active = data.active
    userStats.disabled = data.disabled
    userStats.admins = data.admins
  } catch {
    // error handled by interceptor
  }
}

function openCreateDialog() {
  createForm.username = ''
  createForm.password = ''
  createForm.nickname = ''
  createForm.role = 'USER'
  createDialogVisible.value = true
}

async function handleCreate() {
  if (!createFormRef.value) return
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await createAdminUser({
      username: createForm.username,
      password: createForm.password,
      nickname: createForm.nickname || undefined,
      role: createForm.role,
    })
    ElMessage.success('创建成功')
    createDialogVisible.value = false
    fetchUsers()
    fetchStats()
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
  }
}

function openRoleDialog(user: AdminUserVO) {
  editingUser.value = user
  editingRole.value = user.role
  roleDialogVisible.value = true
}

async function handleRoleChange() {
  if (!editingUser.value) return
  submitting.value = true
  try {
    await updateUserRole(editingUser.value.id, editingRole.value)
    ElMessage.success('角色修改成功')
    roleDialogVisible.value = false
    fetchUsers()
    fetchStats()
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(user: AdminUserVO) {
  const newStatus = user.status === 1 ? 0 : 1
  const action = newStatus === 0 ? '禁用' : '启用'
  try {
    await updateUserStatus(user.id, newStatus)
    ElMessage.success(`已${action}`)
    fetchUsers()
    fetchStats()
  } catch {
    // error handled by interceptor
  }
}

function openResetPwdDialog(user: AdminUserVO) {
  editingUser.value = user
  resetPwdForm.newPassword = ''
  resetPwdDialogVisible.value = true
}

async function handleResetPwd() {
  if (!resetPwdFormRef.value) return
  const valid = await resetPwdFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (!editingUser.value) return

  submitting.value = true
  try {
    await resetUserPassword(editingUser.value.id, resetPwdForm.newPassword)
    ElMessage.success('密码重置成功')
    resetPwdDialogVisible.value = false
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await deleteAdminUser(id)
    ElMessage.success('删除成功')
    fetchUsers()
    fetchStats()
  } catch {
    // error handled by interceptor
  }
}

onMounted(() => {
  fetchUsers()
  fetchStats()
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.stat-active .stat-value {
  color: #67c23a;
}

.stat-disabled .stat-value {
  color: #e6a23c;
}

.stat-admin .stat-value {
  color: #f56c6c;
}

.toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>