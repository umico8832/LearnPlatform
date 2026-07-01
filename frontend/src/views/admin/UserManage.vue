<template>
  <div class="user-manage admin-page">
    <header class="admin-page-header">
      <div>
        <p class="admin-page-kicker">ACCESS CONTROL</p>
        <h2>用户管理</h2>
        <p class="admin-page-description">统一维护账号状态、角色权限与 AI 日配额，所有配额调整都会留下审计记录。</p>
      </div>
      <div class="admin-header-actions">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog()">新增用户</el-button>
      </div>
    </header>

    <section class="admin-summary-grid">
      <el-card v-for="item in statCards" :key="item.label" shadow="never" class="admin-summary-card">
        <span class="admin-summary-icon" :class="item.className">
          <el-icon><component :is="item.icon" /></el-icon>
        </span>
        <div class="admin-summary-copy">
          <p class="admin-summary-label">{{ item.label }}</p>
          <div class="admin-summary-value">{{ item.value }}</div>
          <div class="admin-summary-note">{{ item.note }}</div>
        </div>
      </el-card>
    </section>

    <el-card shadow="never" class="admin-table-card">
      <div class="admin-toolbar">
        <div class="admin-filter-group">
          <el-input
            v-model="keyword"
            placeholder="搜索用户名/昵称"
            :prefix-icon="Search"
            clearable
            style="width: 240px"
            @clear="fetchUsers"
            @keyup.enter="fetchUsers"
          />
          <el-select v-model="filterRole" placeholder="角色" clearable style="width: 130px" @change="fetchUsers">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
          <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 120px" @change="fetchUsers">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
          <el-button :icon="Search" @click="fetchUsers">查询</el-button>
        </div>
        <span class="table-summary">当前筛选 {{ total }} 位用户</span>
      </div>

      <!-- 用户表格 -->
      <div v-if="selectedUsers.length" class="admin-bulk-bar">
        <span class="admin-bulk-copy">已选择 <strong>{{ selectedUsers.length }}</strong> 位用户</span>
        <div class="admin-bulk-actions">
          <el-button size="small" :icon="CircleCheck" @click="handleBulkStatus(1)">批量启用</el-button>
          <el-button size="small" :icon="CircleClose" @click="handleBulkStatus(0)">批量禁用</el-button>
          <el-button size="small" @click="clearUserSelection">清空选择</el-button>
        </div>
      </div>

      <el-table
        ref="userTableRef"
        :data="users"
        v-loading="loading"
        stripe
        class="admin-data-table"
        @selection-change="handleUserSelectionChange"
      >
        <el-table-column type="selection" width="44" />
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
        <el-table-column label="AI 日配额" width="130" align="center">
          <template #default="{ row }">
            <span v-if="(row as AdminUserVO).aiDailyQuota == null">继承全局</span>
            <el-tag v-else-if="(row as AdminUserVO).aiDailyQuota === 0" type="success" size="small">不限次数</el-tag>
            <span v-else>{{ (row as AdminUserVO).aiDailyQuota }} 次</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="170" />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <div class="admin-row-actions">
              <el-button type="primary" link size="small" :icon="UserFilled" @click="openRoleDialog(row as AdminUserVO)">改角色</el-button>
              <el-button
                :type="(row as AdminUserVO).status === 1 ? 'warning' : 'success'"
                link size="small"
                :icon="SwitchButton"
                @click="toggleStatus(row as AdminUserVO)"
              >
                {{ (row as AdminUserVO).status === 1 ? '禁用' : '启用' }}
              </el-button>
              <el-dropdown trigger="click" @command="command => handleUserRowCommand(command as string, row as AdminUserVO)">
                <el-button link size="small" :icon="MoreFilled">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="reset" :icon="Key">重置密码</el-dropdown-item>
                    <el-dropdown-item command="quota" :icon="Cpu">AI 配额</el-dropdown-item>
                    <el-dropdown-item command="delete" :icon="Delete" class="danger-dropdown-item">删除用户</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty class="admin-table-empty" description="没有匹配的用户">
            <el-button type="primary" :icon="Plus" @click="openCreateDialog()">新增用户</el-button>
          </el-empty>
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="admin-pagination">
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

    <!-- AI 配额弹窗 -->
    <el-dialog
      v-model="aiQuotaDialogVisible"
      title="设置 AI 日配额"
      width="400px"
      destroy-on-close
    >
      <el-form label-width="100px">
        <el-form-item label="用户">
          <el-input :model-value="editingUser?.username" disabled />
        </el-form-item>
        <el-form-item label="配额策略">
          <el-radio-group v-model="aiQuotaMode">
            <el-radio value="inherit">继承全局</el-radio>
            <el-radio value="custom">自定义</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="aiQuotaMode === 'custom'" label="每日次数">
          <el-input-number v-model="aiDailyQuota" :min="0" :max="10000" :step="10" />
          <div class="form-tip">0 表示不限次数</div>
        </el-form-item>
        <el-form-item label="调整原因" required>
          <el-input v-model="aiQuotaReason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="例如：按学习计划提升配额" />
        </el-form-item>
        <el-form-item v-if="aiQuotaAudits.length" label="最近记录">
          <div class="quota-audit-list">
            <div v-for="audit in aiQuotaAudits" :key="audit.id" class="quota-audit-item">
              <span>{{ formatQuota(audit.previousDailyQuota) }} → {{ formatQuota(audit.newDailyQuota) }}</span>
              <span>{{ audit.reason }}</span>
              <small>{{ audit.createTime }}</small>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="aiQuotaDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAiQuotaChange">保存</el-button>
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
import { computed, ref, reactive, onMounted } from 'vue'
import { CircleClose, CircleCheck, Cpu, Delete, Key, MoreFilled, Plus, Search, SwitchButton, User, UserFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, TableInstance } from 'element-plus'
import {
  getAdminUserList,
  createAdminUser,
  updateUserRole,
  updateUserStatus,
  resetUserPassword,
  updateUserAiDailyQuota,
  getUserAiDailyQuotaAudits,
  deleteAdminUser,
  getAdminUserStats,
  type AdminUserVO,
  type AdminUserStats,
  type AiQuotaAuditLog,
} from '@/api/adminUser'

const users = ref<AdminUserVO[]>([])
const userTableRef = ref<TableInstance>()
const selectedUsers = ref<AdminUserVO[]>([])
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

// AI 配额
const aiQuotaDialogVisible = ref(false)
const aiQuotaMode = ref<'inherit' | 'custom'>('inherit')
const aiDailyQuota = ref(50)
const aiQuotaReason = ref('')
const aiQuotaAudits = ref<AiQuotaAuditLog[]>([])

const submitting = ref(false)

const statCards = computed(() => [
  {
    label: '用户总数',
    value: userStats.total,
    note: '平台注册账号',
    icon: User,
    className: 'summary-total',
  },
  {
    label: '已启用',
    value: userStats.active,
    note: `${activationRate.value}% 账号可登录`,
    icon: CircleCheck,
    className: 'summary-active',
  },
  {
    label: '已禁用',
    value: userStats.disabled,
    note: '需管理员重新启用',
    icon: CircleClose,
    className: 'summary-disabled',
  },
  {
    label: '管理员',
    value: userStats.admins,
    note: '拥有后台权限',
    icon: UserFilled,
    className: 'summary-admin',
  },
])

const activationRate = computed(() => (
  userStats.total > 0 ? Math.round((userStats.active / userStats.total) * 100) : 0
))

const handleUserRowCommand = async (command: string, user: AdminUserVO) => {
  if (command === 'reset') {
    openResetPwdDialog(user)
    return
  }
  if (command === 'quota') {
    openAiQuotaDialog(user)
    return
  }
  if (command === 'delete') {
    try {
      await ElMessageBox.confirm('确定删除该用户？此操作不可恢复。', '删除用户', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      })
      await handleDelete(user.id)
    } catch {
      // 用户取消确认时不提示错误。
    }
  }
}

const handleUserSelectionChange = (selection: AdminUserVO[]) => {
  selectedUsers.value = selection
}

const clearUserSelection = () => {
  userTableRef.value?.clearSelection()
}

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
    selectedUsers.value = []
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

async function handleBulkStatus(status: number) {
  if (!selectedUsers.value.length) return
  const action = status === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${action}选中的 ${selectedUsers.value.length} 位用户？`, `批量${action}`, {
      type: 'warning',
      confirmButtonText: action,
      cancelButtonText: '取消',
    })
    const targets = selectedUsers.value.filter(user => user.status !== status)
    if (!targets.length) {
      ElMessage.info(`选中用户已全部处于${action}状态`)
      clearUserSelection()
      return
    }
    loading.value = true
    const results = await Promise.allSettled(targets.map(user => updateUserStatus(user.id, status)))
    const failed = results.filter(result => result.status === 'rejected').length
    if (failed > 0) {
      ElMessage.warning(`已${action} ${targets.length - failed} 位用户，${failed} 位处理失败`)
    } else {
      ElMessage.success(`已${action} ${targets.length} 位用户`)
    }
    await fetchUsers()
    await fetchStats()
  } catch {
    // 用户取消确认时不提示错误。
  } finally {
    loading.value = false
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

async function openAiQuotaDialog(user: AdminUserVO) {
  editingUser.value = user
  aiQuotaMode.value = user.aiDailyQuota == null ? 'inherit' : 'custom'
  aiDailyQuota.value = user.aiDailyQuota ?? 50
  aiQuotaReason.value = ''
  aiQuotaAudits.value = []
  aiQuotaDialogVisible.value = true
  try {
    const res: any = await getUserAiDailyQuotaAudits(user.id)
    aiQuotaAudits.value = res.data.records
  } catch {
    // 不影响管理员继续调整配额；请求拦截器会提示错误。
  }
}

async function handleAiQuotaChange() {
  if (!editingUser.value) return
  if (!aiQuotaReason.value.trim()) {
    ElMessage.warning('请填写调整原因')
    return
  }
  submitting.value = true
  try {
    await updateUserAiDailyQuota(
      editingUser.value.id,
      aiQuotaMode.value === 'inherit' ? null : aiDailyQuota.value,
      aiQuotaReason.value.trim(),
    )
    ElMessage.success('AI 日配额已更新')
    aiQuotaDialogVisible.value = false
    fetchUsers()
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
  }
}

function formatQuota(quota: number | null) {
  if (quota == null) return '继承全局'
  return quota === 0 ? '不限次数' : `${quota} 次/日`
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
.summary-active {
  color: var(--lp-success);
  background: #eef8f2;
}

.summary-disabled {
  color: var(--lp-warning);
  background: #fff7e6;
}

.summary-admin {
  color: var(--lp-danger);
  background: #fff1f0;
}

.table-summary {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.quota-audit-list {
  width: 100%;
  max-height: 160px;
  overflow-y: auto;
  color: #606266;
  font-size: 12px;
}

.quota-audit-item {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 4px 10px;
  padding: 6px 0;
  border-bottom: 1px solid #ebeef5;
}

.quota-audit-item small {
  grid-column: 1 / -1;
  color: #909399;
}

.form-tip {
  width: 100%;
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
</style>
