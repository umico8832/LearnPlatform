import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
const api = vi.hoisted(() => ({
  remove: vi.fn(),
  list: vi.fn(),
  stats: vi.fn(),
  message: { success: vi.fn(), warning: vi.fn(), info: vi.fn() },
  status: vi.fn(),
  confirm: vi.fn(),
}))
const auth = vi.hoisted(() => ({ version: 1, listeners: [] as Array<() => void> }))
vi.mock('@/api/adminUser', () => ({
  deleteAdminUser: api.remove,
  getAdminUserList: api.list,
  getAdminUserStats: api.stats,
  updateUserStatus: api.status,
}))
vi.mock('@/utils/auth', () => ({
  getAuthSessionVersion: () => auth.version,
  onAuthSessionChange: (listener: () => void) => {
    auth.listeners.push(listener)
    return () => undefined
  },
}))
vi.mock('element-plus', () => ({ ElMessage: api.message, ElMessageBox: { confirm: api.confirm } }))
import { useAdminUserList } from '@/admin/views/user/useAdminUserList'
const wrappers: ReturnType<typeof mount>[] = []
function mountList() {
  let state!: ReturnType<typeof useAdminUserList>
  const wrapper = mount(
    defineComponent({
      setup() {
        state = useAdminUserList()
        return () => h('div')
      },
    }),
  )
  wrappers.push(wrapper)
  return state
}
function sessionChange() {
  auth.version++
  auth.listeners.forEach((listener) => listener())
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
describe('useAdminUserList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    auth.version = 1
    auth.listeners.length = 0
    api.list.mockResolvedValue({
      data: { records: [{ id: 7, username: 'learner', status: 1, role: 'USER' }], total: 1 },
    })
    api.stats.mockResolvedValue({ data: { total: 4, active: 3, disabled: 1, admins: 1 } })
    api.status.mockResolvedValue(undefined)
    api.remove.mockResolvedValue(undefined)
    api.confirm.mockResolvedValue(undefined)
  })
  afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))
  it('keeps a stats failure explicit while list data stays usable', async () => {
    api.stats.mockRejectedValueOnce(new Error('stats offline'))
    const state = mountList()
    await flushPromises()
    expect(state.hasLoaded.value).toBe(true)
    expect(state.statsError.value).toContain('stats offline')
    expect(api.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }), { errorDisplay: 'inline' })
  })
  it('keeps filters, activation rate and row actions available', async () => {
    const state = mountList()
    await flushPromises()
    expect(state.activationRate.value).toBe(75)
    state.keyword.value = 'admin'
    state.filterRole.value = 'ADMIN'
    await state.fetchUsers()
    expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'admin', role: 'ADMIN' }), {
      errorDisplay: 'inline',
    })
    const user = state.users.value[0]!
    await state.toggleStatus(user)
    expect(api.status).toHaveBeenCalledWith(7, 0, { errorDisplay: 'inline' })
    await state.handleDelete(7)
    expect(api.remove).toHaveBeenCalledWith(7, { errorDisplay: 'inline' })
  })
  it('locks bulk confirmation and never changes users after a session switch', async () => {
    const waiting = deferred<void>()
    api.confirm.mockReturnValueOnce(waiting.promise)
    const state = mountList()
    await flushPromises()
    state.handleUserSelectionChange([state.users.value[0]!])
    const first = state.handleBulkStatus(0)
    const second = state.handleBulkStatus(0)
    expect(api.confirm).toHaveBeenCalledTimes(1)
    sessionChange()
    waiting.resolve()
    await first
    await second
    expect(api.status).not.toHaveBeenCalled()
    expect(state.actionPending.value).toBe(false)
  })
  it('reports partial bulk status failure without leaving the action locked', async () => {
    const state = mountList()
    await flushPromises()
    state.handleUserSelectionChange([{ id: 1, status: 0 } as never, { id: 2, status: 0 } as never])
    api.status.mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error('denied'))
    await state.handleBulkStatus(1)
    await flushPromises()
    expect(api.status).toHaveBeenCalledWith(1, 1, { errorDisplay: 'inline' })
    expect(state.actionError.value).toContain('1 位处理失败')
    expect(state.actionPending.value).toBe(false)
  })
  it('does not apply a late partial status result after the session changes', async () => {
    let reject!: (reason?: unknown) => void
    const pending = new Promise<void>((_, fail) => {
      reject = fail
    })
    const state = mountList()
    await flushPromises()
    state.handleUserSelectionChange([{ id: 1, status: 0 } as never])
    api.status.mockReturnValueOnce(pending)
    const action = state.handleBulkStatus(1)
    await flushPromises()
    expect(api.status).toHaveBeenCalledWith(1, 1, { errorDisplay: 'inline' })
    sessionChange()
    reject(new Error('late failure'))
    await action
    expect(state.actionError.value).toBe('')
  })
})
