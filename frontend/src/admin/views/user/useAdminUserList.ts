import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type TableInstance } from 'element-plus'
import {
  deleteAdminUser,
  getAdminUserList,
  getAdminUserStats,
  updateUserStatus,
  type AdminUserStats,
  type AdminUserVO,
} from '@/api/adminUser'

export function useAdminUserList() {
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
  const activationRate = computed(() =>
    userStats.total > 0 ? Math.round((userStats.active / userStats.total) * 100) : 0,
  )

  async function fetchUsers() {
    loading.value = true
    try {
      const response = await getAdminUserList({
        page: currentPage.value,
        size: pageSize.value,
        keyword: keyword.value || undefined,
        role: filterRole.value || undefined,
        status: filterStatus.value !== '' ? filterStatus.value : undefined,
      })
      users.value = response.data.records
      total.value = response.data.total
      selectedUsers.value = []
    } catch {
      return
    } finally {
      loading.value = false
    }
  }

  async function fetchStats() {
    try {
      Object.assign(userStats, (await getAdminUserStats()).data)
    } catch {
      return
    }
  }

  const handleUserSelectionChange = (selection: AdminUserVO[]) => {
    selectedUsers.value = selection
  }
  const clearUserSelection = () => userTableRef.value?.clearSelection()

  async function toggleStatus(user: AdminUserVO) {
    const newStatus = user.status === 1 ? 0 : 1
    const action = newStatus === 0 ? '禁用' : '启用'
    try {
      await updateUserStatus(user.id, newStatus)
      ElMessage.success(`已${action}`)
      await Promise.all([fetchUsers(), fetchStats()])
    } catch {
      return
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
      const targets = selectedUsers.value.filter((user) => user.status !== status)
      if (!targets.length) {
        ElMessage.info(`选中用户已全部处于${action}状态`)
        clearUserSelection()
        return
      }
      loading.value = true
      const results = await Promise.allSettled(targets.map((user) => updateUserStatus(user.id, status)))
      const failed = results.filter((result) => result.status === 'rejected').length
      if (failed > 0) ElMessage.warning(`已${action} ${targets.length - failed} 位用户，${failed} 位处理失败`)
      else ElMessage.success(`已${action} ${targets.length} 位用户`)
      await Promise.all([fetchUsers(), fetchStats()])
    } catch {
      return
    } finally {
      loading.value = false
    }
  }

  async function handleDelete(id: number) {
    try {
      await deleteAdminUser(id)
      ElMessage.success('删除成功')
      await Promise.all([fetchUsers(), fetchStats()])
    } catch {
      return
    }
  }

  onMounted(() => {
    void fetchUsers()
    void fetchStats()
  })

  return {
    users,
    userTableRef,
    selectedUsers,
    loading,
    keyword,
    filterRole,
    filterStatus,
    currentPage,
    pageSize,
    total,
    userStats,
    activationRate,
    fetchUsers,
    fetchStats,
    handleUserSelectionChange,
    clearUserSelection,
    toggleStatus,
    handleBulkStatus,
    handleDelete,
  }
}
