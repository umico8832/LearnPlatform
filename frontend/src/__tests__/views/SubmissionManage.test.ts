import { removeToken, setToken } from '@/utils/auth'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
const m = vi.hoisted(() => ({
  list: vi.fn(),
  stats: vi.fn(),
  review: vi.fn(),
  confirm: vi.fn(),
  message: { success: vi.fn(), warning: vi.fn(), info: vi.fn() },
}))
vi.mock('@/api/submission', () => ({
  getAdminSubmissions: (...a: unknown[]) => m.list(...a),
  getSubmissionStats: (...a: unknown[]) => m.stats(...a),
  reviewSubmission: (...a: unknown[]) => m.review(...a),
  importSubmission: vi.fn(),
}))
vi.mock('element-plus', () => ({ ElMessage: m.message, ElMessageBox: { confirm: m.confirm } }))
import View from '@/admin/views/SubmissionManage.vue'
const stubs: Record<string, unknown> = {
  'el-card': { template: '<section><slot/></section>' },
  'el-table': { template: '<div><slot/><slot name="empty"/></div>' },
  'el-table-column': { template: '<div/>' },
  'el-button': {
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot/></button>',
    props: ['disabled', 'loading'],
    emits: ['click'],
  },
  'el-alert': { template: '<div><slot/></div>' },
  'el-pagination': { template: '<nav/>' },
  'el-input': { template: '<input/>' },
  'el-radio-group': { template: '<div><slot/></div>' },
  'el-radio-button': { template: '<button><slot/></button>' },
  'el-icon': { template: '<i><slot/></i>' },
  'el-dropdown': { template: '<div><slot/></div>' },
  'el-tag': { template: '<span><slot/></span>' },
}
const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))
function render() {
  const w = mount(View, { global: { stubs: stubs as never, directives: { loading: () => undefined } } })
  wrappers.push(w)
  return w
}
describe('SubmissionManage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    m.list.mockResolvedValue({
      code: 0,
      data: {
        records: [
          { id: 1, status: 0 },
          { id: 2, status: 0 },
        ],
        total: 2,
      },
    })
    m.stats.mockResolvedValue({ code: 0, data: { pending: 2, approved: 0, rejected: 0, imported: 0 } })
    m.confirm.mockResolvedValue(true)
    m.review.mockResolvedValueOnce({ code: 0 }).mockRejectedValueOnce(new Error('offline'))
  })
  it('locks before confirmation, releases after cancel, reports partial completion, then permits a later batch', async () => {
    const w = render()
    await flushPromises()
    const vm = w.vm as unknown as {
      handleSubmissionSelectionChange: (items: { id: number; status: number }[]) => void
      handleBulkApprove: () => Promise<void>
      bulkAction: string | null
    }
    vm.handleSubmissionSelectionChange([
      { id: 1, status: 0 },
      { id: 2, status: 0 },
    ])
    const pending = vm.handleBulkApprove()
    expect(vm.bulkAction).toBe('approve')
    await pending
    expect(vm.bulkAction).toBe(null)
    expect(m.review).toHaveBeenCalledTimes(2)
    expect(m.message.warning).toHaveBeenCalledWith('已通过 1 条投稿，1 条处理失败')
    let rejectConfirm!: (reason: unknown) => void
    vm.handleSubmissionSelectionChange([
      { id: 1, status: 0 },
      { id: 2, status: 0 },
    ])
    m.confirm.mockReturnValueOnce(
      new Promise((_, reject) => {
        rejectConfirm = reject
      }),
    )
    const cancelled = vm.handleBulkApprove()
    expect(vm.bulkAction).toBe('approve')
    rejectConfirm('cancel')
    await cancelled
    expect(vm.bulkAction).toBe(null)
    m.review.mockResolvedValue({ code: 0 })
    await vm.handleBulkApprove()
    expect(m.review).toHaveBeenCalledTimes(4)
  })
  it('does not write selected submissions when the session changes during confirmation', async () => {
    let resolve!: (value: unknown) => void
    m.confirm.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const w = render()
    await flushPromises()
    const vm = w.vm as unknown as {
      handleSubmissionSelectionChange: (items: object[]) => void
      handleBulkApprove: () => Promise<void>
    }
    vm.handleSubmissionSelectionChange([{ id: 1, status: 0 }])
    const action = vm.handleBulkApprove()
    removeToken()
    resolve(true)
    await action
    expect(m.review).not.toHaveBeenCalled()
  })
  it('keeps a failed statistics read distinct from a zero and ignores late lists', async () => {
    let resolve!: (value: unknown) => void
    m.list.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    m.stats.mockRejectedValueOnce(new Error('统计读取失败'))
    const w = render()
    await flushPromises()
    expect(w.text()).toContain('统计读取失败')
    expect(w.find('.admin-summary-value').exists()).toBe(false)
    removeToken()
    resolve({ code: 0, data: { records: [{ id: 999 }], total: 999 } })
    await flushPromises()
    expect(w.text()).not.toContain('999')
  })
  it('loads a replacement authenticated session without applying the earlier list', async () => {
    let resolve!: (value: unknown) => void
    m.list.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const w = render()
    m.list.mockResolvedValueOnce({ code: 0, data: { records: [], total: 23 } })
    setToken('replacement-admin-fixture')
    await flushPromises()
    expect(w.text()).toContain('当前筛选 23 条投稿')
    resolve({ code: 0, data: { records: [], total: 999 } })
    await flushPromises()
    expect(w.text()).toContain('当前筛选 23 条投稿')
    expect(w.text()).not.toContain('999')
  })
})
