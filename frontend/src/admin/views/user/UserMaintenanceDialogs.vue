<template>
  <el-dialog v-model="createDialogVisible" title="新增用户" width="500px" destroy-on-close>
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px" @submit.prevent>
      <el-form-item label="用户名" prop="username">
        <el-input v-model="createForm.username" placeholder="3-50个字符" maxlength="50" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="createForm.password"
          type="password"
          placeholder="至少6个字符"
          show-password
          maxlength="100"
        />
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

  <el-dialog v-model="aiQuotaDialogVisible" title="设置 AI 日配额" width="400px" destroy-on-close>
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
        <el-input
          v-model="aiQuotaReason"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="例如：按学习计划提升配额"
        />
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

  <el-dialog v-model="roleDialogVisible" title="修改角色" width="400px" destroy-on-close>
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

  <el-dialog v-model="resetPwdDialogVisible" title="重置密码" width="400px" destroy-on-close>
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
      <el-button type="primary" :loading="submitting" @click="handleResetPassword">确认重置</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  createAdminUser,
  getUserAiDailyQuotaAudits,
  resetUserPassword,
  updateUserAiDailyQuota,
  updateUserRole,
  type AdminUserVO,
  type AiQuotaAuditLog,
} from '@/api/adminUser'

const emit = defineEmits<{ refresh: [statsChanged: boolean] }>()

const submitting = ref(false)
const editingUser = ref<AdminUserVO | null>(null)

const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({ username: '', password: '', nickname: '', role: 'USER' })
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

const roleDialogVisible = ref(false)
const editingRole = ref('USER')
const resetPwdDialogVisible = ref(false)
const resetPwdFormRef = ref<FormInstance>()
const resetPwdForm = reactive({ newPassword: '' })
const resetPwdRules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度6-100个字符', trigger: 'blur' },
  ],
}

const aiQuotaDialogVisible = ref(false)
const aiQuotaMode = ref<'inherit' | 'custom'>('inherit')
const aiDailyQuota = ref(50)
const aiQuotaReason = ref('')
const aiQuotaAudits = ref<AiQuotaAuditLog[]>([])

function openCreate() {
  Object.assign(createForm, { username: '', password: '', nickname: '', role: 'USER' })
  createDialogVisible.value = true
}

function openRole(user: AdminUserVO) {
  editingUser.value = user
  editingRole.value = user.role
  roleDialogVisible.value = true
}

function openResetPassword(user: AdminUserVO) {
  editingUser.value = user
  resetPwdForm.newPassword = ''
  resetPwdDialogVisible.value = true
}

async function openQuota(user: AdminUserVO) {
  editingUser.value = user
  aiQuotaMode.value = user.aiDailyQuota == null ? 'inherit' : 'custom'
  aiDailyQuota.value = user.aiDailyQuota ?? 50
  aiQuotaReason.value = ''
  aiQuotaAudits.value = []
  aiQuotaDialogVisible.value = true
  try {
    aiQuotaAudits.value = (await getUserAiDailyQuotaAudits(user.id)).data.records
  } catch {
    return
  }
}

async function handleCreate() {
  if (!(await createFormRef.value?.validate().catch(() => false))) return
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
    emit('refresh', true)
  } catch {
    return
  } finally {
    submitting.value = false
  }
}

async function handleRoleChange() {
  if (!editingUser.value) return
  submitting.value = true
  try {
    await updateUserRole(editingUser.value.id, editingRole.value)
    ElMessage.success('角色修改成功')
    roleDialogVisible.value = false
    emit('refresh', true)
  } catch {
    return
  } finally {
    submitting.value = false
  }
}

async function handleResetPassword() {
  if (!(await resetPwdFormRef.value?.validate().catch(() => false)) || !editingUser.value) return
  submitting.value = true
  try {
    await resetUserPassword(editingUser.value.id, resetPwdForm.newPassword)
    ElMessage.success('密码重置成功')
    resetPwdDialogVisible.value = false
  } catch {
    return
  } finally {
    submitting.value = false
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
    emit('refresh', false)
  } catch {
    return
  } finally {
    submitting.value = false
  }
}

function formatQuota(quota: number | null) {
  if (quota == null) return '继承全局'
  return quota === 0 ? '不限次数' : `${quota} 次/日`
}

defineExpose({ openCreate, openRole, openResetPassword, openQuota })
</script>

<style scoped>
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
