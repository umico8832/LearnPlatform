import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { removeToken, setToken } from '@/utils/auth'

const api = vi.hoisted(() => ({ load: vi.fn(), setOption: vi.fn(), dispose: vi.fn() }))
vi.mock('@/api/statistics', () => ({ getAdminStatisticsOverview: api.load }))
vi.mock('echarts/core', () => ({
  use: vi.fn(),
  init: () => ({ setOption: api.setOption, dispose: api.dispose, resize: vi.fn() }),
}))
import AdminDashboard from '@/admin/views/AdminDashboard.vue'

const data = {
  totalUsers: 12,
  enabledUsers: 10,
  totalQuestions: 30,
  weeklyNewQuestions: 2,
  totalExamPapers: 5,
  publishedExamPapers: 3,
  draftExamPapers: 2,
  todayActiveUsers: 4,
  totalPracticeRecords: 90,
  questionTypeDistribution: { 单选题: 30 },
  dailyActivity: [{ date: '2026-10-02', practiceCount: 7, activeUsers: 4 }],
}
const pass = { template: '<div><slot name="header"/><slot/></div>' }
const wrappers: ReturnType<typeof mount>[] = []
function render() {
  const wrapper = mount(AdminDashboard, {
    global: {
      stubs: {
        'el-row': pass,
        'el-col': pass,
        'el-card': pass,
        'el-tag': pass,
        'el-icon': pass,
        'el-progress': true,
        'el-skeleton': { props: ['loading'], template: '<div><slot v-if="!loading"/></div>' },
        'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot/></button>' },
      },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}
beforeEach(() => {
  vi.clearAllMocks()
  api.load.mockResolvedValue({ code: 0, data })
})
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.unstubAllGlobals()
})

describe('AdminDashboard', () => {
  it('shows a recoverable error instead of zero metrics when the read fails', async () => {
    api.load.mockRejectedValueOnce(new Error('读取失败'))
    const wrapper = render()
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.find('.metric-value').exists()).toBe(false)
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('12')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(api.load).toHaveBeenLastCalledWith({ errorDisplay: 'inline' })
  })

  it('discards a late overview after the authentication session changes', async () => {
    let resolve!: (value: unknown) => void
    api.load.mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = render()
    removeToken()
    resolve({ code: 0, data })
    await flushPromises()
    expect(wrapper.find('.metric-value').exists()).toBe(false)
    expect(api.setOption).not.toHaveBeenCalled()
  })

  it('loads the new session and ignores an earlier delayed overview', async () => {
    let resolve!: (value: unknown) => void
    api.load.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = render()
    api.load.mockResolvedValueOnce({ code: 0, data: { ...data, totalUsers: 23 } })
    setToken('new-admin-fixture-session')
    await flushPromises()
    expect(wrapper.get('.metric-value').text()).toBe('23')
    resolve({ code: 0, data })
    await flushPromises()
    expect(wrapper.get('.metric-value').text()).toBe('23')
  })

  it('renders accessible table alternatives and respects reduced motion in charts', async () => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })),
    )
    const wrapper = render()
    await flushPromises()
    expect(wrapper.findAll('table')).toHaveLength(2)
    expect(wrapper.text()).toContain('2026-10-02')
    expect(api.setOption).toHaveBeenCalled()
    expect(api.setOption.mock.calls.every(([options]) => options.animation === false)).toBe(true)
  })
})
