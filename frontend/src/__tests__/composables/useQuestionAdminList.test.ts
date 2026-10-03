import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  clear: vi.fn(),
  remove: vi.fn(),
  list: vi.fn(),
  courses: vi.fn(),
  message: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  confirm: vi.fn(),
}))
const auth = vi.hoisted(() => ({ version: 1, listeners: [] as Array<() => void> }))
vi.mock('@/api/ai', () => ({ clearAssetCache: api.clear }))
vi.mock('@/api/course', () => ({ getAllCourses: api.courses }))
vi.mock('@/api/question', () => ({ deleteQuestion: api.remove, getAdminQuestionPage: api.list }))
vi.mock('@/utils/auth', () => ({
  getAuthSessionVersion: () => auth.version,
  onAuthSessionChange: (listener: () => void) => {
    auth.listeners.push(listener)
    return () => undefined
  },
}))
vi.mock('element-plus', () => ({ ElMessage: api.message, ElMessageBox: { confirm: api.confirm } }))
import { useQuestionAdminList } from '@/admin/views/question/useQuestionAdminList'

const wrappers: ReturnType<typeof mount>[] = []
function mountList() {
  let state!: ReturnType<typeof useQuestionAdminList>
  const wrapper = mount(
    defineComponent({
      setup() {
        state = useQuestionAdminList()
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

describe('useQuestionAdminList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    auth.version = 1
    auth.listeners.length = 0
    api.list.mockResolvedValue({ data: { records: [{ id: 11, content: '题目' }], total: 1 } })
    api.courses.mockResolvedValue({ data: [{ id: 7, name: '数据结构' }] })
    api.remove.mockResolvedValue(undefined)
    api.clear.mockResolvedValue(undefined)
    api.confirm.mockResolvedValue(undefined)
  })
  afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))
  it('uses inline reads and exposes an error instead of a fake empty list', async () => {
    api.list.mockRejectedValueOnce(new Error('offline'))
    const state = mountList()
    await flushPromises()
    expect(state.listError.value).toContain('offline')
    expect(state.hasLoaded.value).toBe(false)
    expect(state.total.value).toBe(0)
    await state.fetchQuestions()
    expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 1 }), { errorDisplay: 'inline' })
    expect(state.hasLoaded.value).toBe(true)
  })
  it('keeps filters, selection, row deletion and cache actions available', async () => {
    const state = mountList()
    await flushPromises()
    expect(state.courseList.value[0]?.id).toBe(7)
    state.filters.keyword = '链表'
    state.filters.courseId = 7
    await state.fetchQuestions()
    expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '链表', courseId: 7 }), {
      errorDisplay: 'inline',
    })
    const question = state.questions.value[0]!
    state.handleQuestionSelectionChange([question])
    expect(state.selectedQuestions.value).toEqual([question])
    await state.handleDelete(question.id)
    expect(api.remove).toHaveBeenCalledWith(question.id, { errorDisplay: 'inline' })
    await state.handleClearAiCache(question.id)
    expect(api.clear).toHaveBeenCalledWith(question.id, { errorDisplay: 'inline' })
  })
  it('locks before confirmation and does not delete after a session switch', async () => {
    const waiting = deferred<void>()
    api.confirm.mockReturnValueOnce(waiting.promise)
    const state = mountList()
    await flushPromises()
    const question = state.questions.value[0]!
    const first = state.handleDelete(question.id)
    const second = state.handleDelete(question.id)
    expect(api.confirm).toHaveBeenCalledTimes(1)
    expect(state.actionPending.value).toBe(true)
    sessionChange()
    waiting.resolve()
    await first
    await second
    await flushPromises()
    expect(api.remove).not.toHaveBeenCalled()
    expect(state.actionPending.value).toBe(false)
  })
  it('reports partial bulk cache failure and unlocks after the refreshed list', async () => {
    const state = mountList()
    await flushPromises()
    state.handleQuestionSelectionChange([{ id: 1 } as never, { id: 2 } as never])
    api.clear.mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error('denied'))
    await state.handleBulkClearAiCache()
    await flushPromises()
    expect(api.clear).toHaveBeenCalledWith(1, { errorDisplay: 'inline' })
    expect(state.actionError.value).toContain('1 项处理失败')
    expect(state.actionPending.value).toBe(false)
  })
  it('does not apply a late partial cache result after the session changes', async () => {
    let reject!: (reason?: unknown) => void
    const pending = new Promise<void>((_, fail) => {
      reject = fail
    })
    const state = mountList()
    await flushPromises()
    state.handleQuestionSelectionChange([{ id: 1 } as never])
    api.clear.mockReturnValueOnce(pending)
    const action = state.handleBulkClearAiCache()
    await flushPromises()
    expect(api.clear).toHaveBeenCalledWith(1, { errorDisplay: 'inline' })
    sessionChange()
    reject(new Error('late failure'))
    await action
    expect(state.actionError.value).toBe('')
  })
})
