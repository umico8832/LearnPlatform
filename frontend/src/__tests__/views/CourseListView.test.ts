import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockGetAllCourses, mockPush, listeners } = vi.hoisted(() => ({
  mockGetAllCourses: vi.fn(),
  mockPush: vi.fn(),
  listeners: new Set<() => void>(),
}))
vi.mock('@/api/course', () => ({ getAllCourses: (...args: unknown[]) => mockGetAllCourses(...args) }))
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
    mockGetAllCourses.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ data: [] })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('暂时无法读取课程库')
    expect(wrapper.text()).not.toContain('没有匹配的课程')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(mockGetAllCourses).toHaveBeenLastCalledWith({ errorDisplay: 'inline' })
    expect(wrapper.text()).toContain('没有匹配的课程')
  })

  it('opens detail with an explicit course-list return source', async () => {
    mockGetAllCourses.mockResolvedValue({
      data: [{ id: 408, name: '408 数据结构', description: '', contentKey: 'cs408', sortOrder: 1 }],
    })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('button').trigger('click')
    expect(mockPush).toHaveBeenCalledWith({ name: 'CourseDetail', params: { id: 408 }, query: { from: 'course-list' } })
  })
})
