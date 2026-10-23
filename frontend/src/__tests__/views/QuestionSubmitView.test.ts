import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import QuestionSubmitView from '@/views/practice/QuestionSubmitView.vue'
import { removeToken, setToken } from '@/utils/auth'

const { load } = vi.hoisted(() => ({ load: vi.fn() }))
vi.mock('@/api/submission', () => ({ getMySubmissions: load, submitQuestion: vi.fn() }))
const wrappers: ReturnType<typeof mount>[] = []
function page() {
  const wrapper = mount(QuestionSubmitView, {
    global: { stubs: { LtBackLink: true, QuestionSubmissionEditor: true, QuestionSubmissionDetail: true } },
  })
  wrappers.push(wrapper)
  return wrapper
}
beforeEach(() => {
  vi.clearAllMocks()
  setToken('submission-ui-test')
})
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  removeToken()
})

describe('QuestionSubmitView collection states', () => {
  it('读取完成前不显示默认零总数，成功空结果才展示空态', async () => {
    let resolve!: (value: unknown) => void
    load.mockImplementationOnce(() => new Promise((done) => (resolve = done)))
    const wrapper = page()
    expect(wrapper.get('[role="status"]').text()).toContain('正在加载投稿记录')
    expect(wrapper.text()).not.toContain('共 0 篇投稿')
    expect(wrapper.text()).not.toContain('还没有投稿记录')
    resolve({ code: 0, data: { records: [], total: 0 } })
    await flushPromises()
    expect(wrapper.text()).toContain('共 0 篇投稿')
    expect(wrapper.text()).toContain('还没有投稿记录')
  })

  it('读取失败原位播报错误并可重试，不将失败伪装成空记录', async () => {
    load.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ code: 0, data: { records: [], total: 0 } })
    const wrapper = page()
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('投稿记录暂时无法加载')
    expect(wrapper.text()).not.toContain('还没有投稿记录')
    expect(wrapper.text()).not.toContain('共 0 篇投稿')
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(load).toHaveBeenCalledTimes(2)
    expect(load).toHaveBeenLastCalledWith({ pageNum: 1, pageSize: 10, status: undefined }, { errorDisplay: 'inline' })
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('还没有投稿记录')
  })
})
