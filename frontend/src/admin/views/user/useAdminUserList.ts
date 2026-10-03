import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type TableInstance } from 'element-plus'
import {
  deleteAdminUser,
  getAdminUserList,
  getAdminUserStats,
  updateUserStatus,
  type AdminUserStats,
  type AdminUserVO,
} from '@/api/adminUser'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import { errorMessage } from '@/utils/errors'

export function useAdminUserList() {
  const users = ref<AdminUserVO[]>([])
  const userTableRef = ref<TableInstance>()
  const selectedUsers = ref<AdminUserVO[]>([])
  const loading = ref(true)
  const listError = ref('')
  const statsError = ref('')
  const statsLoading = ref(true)
  const statsLoaded = ref(false)
  const hasLoaded = ref(false)
  const actionPending = ref(false)
  const actionError = ref('')
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
  let alive = true
  let listVersion = 0
  let statsVersion = 0
  let actionVersion = 0
  const listCurrent = (version: number, session: number) =>
    alive && version === listVersion && session === getAuthSessionVersion()
  const statsCurrent = (version: number, session: number) =>
    alive && version === statsVersion && session === getAuthSessionVersion()
  const actionCurrent = (version: number, session: number) =>
    alive && version === actionVersion && session === getAuthSessionVersion()

  async function fetchUsers() {
    const version = ++listVersion
    const session = getAuthSessionVersion()
    loading.value = true
    listError.value = ''
    try {
      const response = await getAdminUserList(
        {
          page: currentPage.value,
          size: pageSize.value,
          keyword: keyword.value || undefined,
          role: filterRole.value || undefined,
          status: filterStatus.value !== '' ? filterStatus.value : undefined,
        },
        { errorDisplay: 'inline' },
      )
      if (!listCurrent(version, session)) return
      users.value = response.data.records
      total.value = response.data.total
      selectedUsers.value = []
      hasLoaded.value = true
    } catch (cause) {
      if (listCurrent(version, session)) listError.value = errorMessage(cause, '用户列表暂时无法读取，请重试。')
    } finally {
      if (listCurrent(version, session)) loading.value = false
    }
  }
  async function fetchStats() {
    const version = ++statsVersion
    const session = getAuthSessionVersion()
    statsLoading.value = true
    statsError.value = ''
    try {
      const response = await getAdminUserStats({ errorDisplay: 'inline' })
      if (statsCurrent(version, session)) {
        Object.assign(userStats, response.data)
        statsLoaded.value = true
      }
    } catch (cause) {
      if (statsCurrent(version, session)) statsError.value = errorMessage(cause, '用户统计暂时无法读取。')
    } finally {
      if (statsCurrent(version, session)) statsLoading.value = false
    }
  }
  const handleUserSelectionChange = (selection: AdminUserVO[]) => {
    selectedUsers.value = selection
  }
  const clearUserSelection = () => userTableRef.value?.clearSelection()
  async function runAction(label: string, work: () => Promise<number>) {
    if (actionPending.value) return
    const version = ++actionVersion
    const session = getAuthSessionVersion()
    actionPending.value = true
    actionError.value = ''
    try {
      const count = await work()
      if (!actionCurrent(version, session)) return
      if (count) ElMessage.success(label)
      await Promise.all([fetchUsers(), fetchStats()])
    } catch (cause) {
      if (actionCurrent(version, session)) actionError.value = errorMessage(cause, `${label}失败，请重试。`)
    } finally {
      if (actionCurrent(version, session)) actionPending.value = false
    }
  }
  async function confirmAction(
    message: string,
    title: string,
    work: () => Promise<{ completed: number; failed: number }>,
  ) {
    if (actionPending.value) return
    const version = ++actionVersion
    const session = getAuthSessionVersion()
    actionPending.value = true
    actionError.value = ''
    try {
      await ElMessageBox.confirm(message, title, {
        type: 'warning',
        confirmButtonText: title.includes('删除') ? '删除' : '确认',
        cancelButtonText: '取消',
      })
      if (!actionCurrent(version, session)) return
      const result = await work()
      if (!actionCurrent(version, session)) return
      if (result.failed)
        actionError.value = `已${title.replace('批量', '')} ${result.completed} 位用户，${result.failed} 位处理失败。`
      if (result.completed) ElMessage.success(title)
      await Promise.all([fetchUsers(), fetchStats()])
    } catch (cause) {
      if (actionCurrent(version, session) && cause !== 'cancel' && cause !== 'close')
        actionError.value = errorMessage(cause, `${title}失败，请重试。`)
    } finally {
      if (actionCurrent(version, session)) actionPending.value = false
    }
  }
  const toggleStatus = (user: AdminUserVO) => {
    const newStatus = user.status === 1 ? 0 : 1
    const label = newStatus ? '启用用户' : '禁用用户'
    return runAction(label, async () => {
      await updateUserStatus(user.id, newStatus, { errorDisplay: 'inline' })
      return 1
    })
  }
  const handleBulkStatus = (status: number) => {
    const targets = selectedUsers.value.filter((user) => user.status !== status)
    if (!targets.length) return Promise.resolve()
    const action = status === 1 ? '启用' : '禁用'
    return confirmAction(`确定${action}选中的 ${targets.length} 位用户？`, `批量${action}`, async () => {
      const results = await Promise.allSettled(
        targets.map((user) => updateUserStatus(user.id, status, { errorDisplay: 'inline' })),
      )
      const failed = results.filter((result) => result.status === 'rejected').length
      return { completed: targets.length - failed, failed }
    })
  }
  const handleDelete = (id: number) =>
    confirmAction('确定删除该用户？此操作不可恢复。', '删除用户', async () => {
      await deleteAdminUser(id, { errorDisplay: 'inline' })
      return { completed: 1, failed: 0 }
    })
  const stopSession = onAuthSessionChange(() => {
    listVersion++
    statsVersion++
    actionVersion++
    users.value = []
    selectedUsers.value = []
    total.value = 0
    Object.assign(userStats, { total: 0, active: 0, disabled: 0, admins: 0 })
    loading.value = false
    listError.value = ''
    statsError.value = ''
    statsLoading.value = false
    statsLoaded.value = false
    hasLoaded.value = false
    actionPending.value = false
    actionError.value = ''
  })
  onMounted(() => {
    void fetchUsers()
    void fetchStats()
  })
  onBeforeUnmount(() => {
    alive = false
    listVersion++
    statsVersion++
    actionVersion++
    stopSession()
  })
  return {
    users,
    userTableRef,
    selectedUsers,
    loading,
    listError,
    statsError,
    statsLoading,
    statsLoaded,
    hasLoaded,
    actionPending,
    actionError,
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
