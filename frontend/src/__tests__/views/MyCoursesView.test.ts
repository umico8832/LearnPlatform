import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockGetMyCourses, mockGetCourseOverview, mockPush, auth } = vi.hoisted(() => ({
  mockGetMyCourses: vi.fn(),
  mockGetCourseOverview: vi.fn(),
  mockPush: vi.fn(),
  auth: { version: 1, listeners: new Set<() => void>() },
}))
vi.mock('@/api/course', () => ({
  getMyCourses: (...args: unknown[]) => mockGetMyCourses(...args),
  getCourseOverview: (...args: unknown[]) => mockGetCourseOverview(...args),
}))
vi.mock('@/utils/auth', () => ({
  getAuthSessionVersion: () => auth.version,
  isAuthenticated: () => true,
  onAuthSessionChange: (listener: () => void) => {
    auth.listeners.add(listener)
    return () => auth.listeners.delete(listener)
  },
}))
vi.mock('@/utils/learningTarget', () => ({ openLearningTarget: vi.fn() }))
vi.mock('@/utils/format', () => ({ formatRelativeTime: () => '刚刚' }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mockPush }) }))
import MyCoursesView from '@/views/course/MyCoursesView.vue'
const stubs = {
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-icon': { template: '<i><slot /></i>' },
  LpPageHeader: { template: '<header><slot name="actions" /></header>' },
  LpKicker: { template: '<span><slot /></span>' },
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
  const w = mount(MyCoursesView, { global: { stubs } })
  mounted.push(w)
  return w
}
afterEach(() => {
  while (mounted.length) mounted.pop()?.unmount()
})
beforeEach(() => {
  vi.clearAllMocks()
  auth.version = 1
  auth.listeners.clear()
})
describe('MyCoursesView', () => {
  it('does not present a failed overview as not started and retries that overview inline', async () => {
    mockGetMyCourses.mockResolvedValue({ data: [{ courseId: 1, courseName: 'Java', description: null }] })
    mockGetCourseOverview.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({
      data: { answeredCount: 0, correctCount: 0, dueReviewCount: 0, recommendedTargets: [], lastLearningTime: null },
    })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('学习概况暂时无法读取')
    expect(wrapper.text()).not.toContain('尚未开始学习')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('重新读取'))!
      .trigger('click')
    await flushPromises()
    expect(mockGetCourseOverview).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('尚未开始学习')
  })

  it('drops a late overview from the previous auth session', async () => {
    let finishOld!: (value: {
      data: {
        answeredCount: number
        correctCount: number
        dueReviewCount: number
        recommendedTargets: never[]
        lastLearningTime: null
      }
    }) => void
    mockGetMyCourses
      .mockResolvedValueOnce({ data: [{ courseId: 1, courseName: '旧课程', description: null }] })
      .mockResolvedValueOnce({ data: [{ courseId: 2, courseName: '新课程', description: null }] })
    mockGetCourseOverview
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishOld = resolve
          }),
      )
      .mockResolvedValueOnce({
        data: { answeredCount: 1, correctCount: 1, dueReviewCount: 0, recommendedTargets: [], lastLearningTime: null },
      })
    const wrapper = mountView()
    await flushPromises()

    auth.version += 1
    auth.listeners.forEach((listener) => listener())
    await flushPromises()
    finishOld({
      data: { answeredCount: 9, correctCount: 9, dueReviewCount: 0, recommendedTargets: [], lastLearningTime: null },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('新课程')
    expect(wrapper.text()).not.toContain('旧课程')
  })

  it('does not apply an overview response after unmount', async () => {
    let finish!: (value: {
      data: {
        answeredCount: number
        correctCount: number
        dueReviewCount: number
        recommendedTargets: never[]
        lastLearningTime: null
      }
    }) => void
    mockGetMyCourses.mockResolvedValue({ data: [{ courseId: 1, courseName: 'Java', description: null }] })
    mockGetCourseOverview.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    const wrapper = mountView()
    await flushPromises()
    wrapper.unmount()
    finish({
      data: { answeredCount: 1, correctCount: 1, dueReviewCount: 0, recommendedTargets: [], lastLearningTime: null },
    })
    await flushPromises()

    expect((wrapper.vm as unknown as { courses: unknown[] }).courses).toEqual([])
  })
})
