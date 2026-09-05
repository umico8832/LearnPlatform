import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { deleteAdminUser, getAdminUserList, getAdminUserStats, message, updateUserStatus } = vi.hoisted(() => ({
  deleteAdminUser: vi.fn(),
  getAdminUserList: vi.fn(),
  getAdminUserStats: vi.fn(),
  message: { success: vi.fn(), warning: vi.fn(), info: vi.fn() },
  updateUserStatus: vi.fn(),
}))

vi.mock('@/api/adminUser', () => ({ deleteAdminUser, getAdminUserList, getAdminUserStats, updateUserStatus }))
vi.mock('element-plus', () => ({
  ElMessage: message,
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(undefined) },
}))

import { useAdminUserList } from '@/admin/views/user/useAdminUserList'

describe('useAdminUserList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getAdminUserList.mockResolvedValue({
      data: { records: [{ id: 7, username: 'learner', status: 1, role: 'USER' }], total: 1 },
    })
    getAdminUserStats.mockResolvedValue({ data: { total: 4, active: 3, disabled: 1, admins: 1 } })
    updateUserStatus.mockResolvedValue(undefined)
    deleteAdminUser.mockResolvedValue(undefined)
  })

  function mountList() {
    let state!: ReturnType<typeof useAdminUserList>
    mount(
      defineComponent({
        setup() {
          state = useAdminUserList()
          return () => h('div')
        },
      }),
    )
    return state
  }

  it('loads users and statistics with list-owned filters', async () => {
    const state = mountList()
    await flushPromises()

    expect(getAdminUserList).toHaveBeenCalledWith({
      page: 1,
      size: 10,
      keyword: undefined,
      role: undefined,
      status: undefined,
    })
    expect(state.users.value[0]?.username).toBe('learner')
    expect(state.activationRate.value).toBe(75)

    state.keyword.value = 'admin'
    state.filterRole.value = 'ADMIN'
    await state.fetchUsers()
    expect(getAdminUserList).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'admin', role: 'ADMIN' }))
  })

  it('owns selection and row-level status and deletion commands', async () => {
    const state = mountList()
    await flushPromises()
    const user = state.users.value[0]!

    state.handleUserSelectionChange([user])
    expect(state.selectedUsers.value).toEqual([user])

    await state.toggleStatus(user)
    expect(updateUserStatus).toHaveBeenCalledWith(7, 0)
    expect(message.success).toHaveBeenCalledWith('已禁用')

    await state.handleDelete(7)
    expect(deleteAdminUser).toHaveBeenCalledWith(7)
    expect(message.success).toHaveBeenCalledWith('删除成功')
  })
})
