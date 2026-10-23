import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockGetCoursePage, mockPush, listeners } = vi.hoisted(() => ({
  mockGetCoursePage: vi.fn(),
  mockPush: vi.fn(),
  listeners: new Set<() => void>(),
}))
vi.mock('@/api/course', () => ({ getCoursePage: (...args: unknown[]) => mockGetCoursePage(...args) }))
vi.mock('@/utils/auth', () => ({
  getAuthSessionVersion: () => 1,
  isAuthenticated: () => true,
  onAuthSessionChange: (listener: () => void) => {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mockPush }) }))
import CourseListView from '@/views/course/CourseListView.vue'

const stubs = {
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-input': { template: '<input />', props: ['modelValue'], emits: ['update:modelValue'] },
  'el-icon': { template: '<i><slot /></i>' },
  LpPageHeader: { template: '<header><slot name="actions" /></header>' },
  LpSectionHeading: { template: '<h2 :id="headingId">{{ title }}</h2>', props: ['title', 'headingId'] },
  LpStatePanel: {
    template:
      '<section><strong>{{ title }}</strong><button @click="$emit(\'retry\')">重试</button><slot name="actions" /></section>',
    props: ['state', 'title'],
    emits: ['retry'],
  },
}
const mounted: ReturnType<typeof mount>[] = []
const mountView = () => {
  const wrapper = mount(CourseListView, { global: { stubs } })
  mounted.push(wrapper)
  return wrapper
}
afterEach(() => {
  while (mounted.length) mounted.pop()?.unmount()
})
beforeEach(() => {
  vi.clearAllMocks()
  listeners.clear()
})

describe('CourseListView', () => {
  it('keeps a failed course request distinct from an empty catalog and retries inline', async () => {
    mockGetCoursePage
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ data: { records: [], total: 0, pages: 0 } })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('暂时无法读取课程库')
    expect(wrapper.text()).not.toContain('没有匹配的课程')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '重试')!
      .trigger('click')
    await flushPromises()
    expect(mockGetCoursePage).toHaveBeenLastCalledWith(
      { pageNum: 1, pageSize: 12, keyword: undefined },
      { errorDisplay: 'inline' },
    )
    expect(wrapper.text()).toContain('没有匹配的课程')
  })

  it('opens detail with an explicit course-list return source', async () => {
    mockGetCoursePage.mockResolvedValue({
      data: {
        records: [{ id: 408, name: '408 数据结构', description: '', contentKey: 'cs408', sortOrder: 1 }],
        total: 1,
        pages: 1,
      },
    })
    const wrapper = mountView()
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '查看课程')!
      .trigger('click')
    expect(mockPush).toHaveBeenCalledWith({ name: 'CourseDetail', params: { id: 408 }, query: { from: 'course-list' } })
  })
  it('searches and paginates through the real server parameters', async () => {
    mockGetCoursePage.mockResolvedValue({ data: { records: [{ id: 1, name: '数学' }], total: 25, pages: 3 } })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('input').setValue('数学')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(mockGetCoursePage).toHaveBeenLastCalledWith(
      { pageNum: 1, pageSize: 12, keyword: '数学' },
      { errorDisplay: 'inline' },
    )
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '下一页')!
      .trigger('click')
    await flushPromises()
    expect(mockGetCoursePage).toHaveBeenLastCalledWith(
      { pageNum: 2, pageSize: 12, keyword: '数学' },
      { errorDisplay: 'inline' },
    )
    await wrapper.get('select').setValue('24')
    await flushPromises()
    expect(mockGetCoursePage).toHaveBeenLastCalledWith(
      { pageNum: 1, pageSize: 24, keyword: '数学' },
      { errorDisplay: 'inline' },
    )
  })
})
