import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockGetMyCourses, mockGetCourseOverview, mockGetAllCourses, mockAddCourse, mockOpenTarget, mockPush, auth } =
  vi.hoisted(() => ({
    mockGetMyCourses: vi.fn(),
    mockGetCourseOverview: vi.fn(),
    mockGetAllCourses: vi.fn(),
    mockAddCourse: vi.fn(),
    mockOpenTarget: vi.fn(),
    mockPush: vi.fn(),
    auth: { version: 1, listeners: new Set<() => void>() },
  }))
vi.mock('@/api/course', () => ({
  getMyCourses: (...args: unknown[]) => mockGetMyCourses(...args),
  getCourseOverview: (...args: unknown[]) => mockGetCourseOverview(...args),
  getAllCourses: (...args: unknown[]) => mockGetAllCourses(...args),
  addCourseToLibrary: (...args: unknown[]) => mockAddCourse(...args),
}))
vi.mock('@/utils/auth', () => ({
  getAuthSessionVersion: () => auth.version,
  isAuthenticated: () => true,
  onAuthSessionChange: (listener: () => void) => {
    auth.listeners.add(listener)
    return () => auth.listeners.delete(listener)
  },
}))
vi.mock('@/utils/learningTarget', () => ({ openLearningTarget: (...args: unknown[]) => mockOpenTarget(...args) }))
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
      '<section><strong>{{ title }}</strong><p>{{ description }}</p><button @click="$emit(\'retry\')">重试</button><slot name="actions" /></section>',
    props: ['state', 'title', 'description'],
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
  it('lets a new learner join the published 408 course and open its first server target', async () => {
    const target = {
      type: 'TUTOR',
      title: '线性表的定义',
      reason: '从第一节开始',
      knowledgePointId: 12,
      questionId: null,
    }
    mockGetMyCourses.mockResolvedValue({ data: [] })
    mockGetAllCourses.mockResolvedValue({
      data: [
        { id: 7, name: '数据结构与算法', contentKey: null },
        { id: 42, name: '408 数据结构', contentKey: 'cs408-data-structures' },
      ],
    })
    mockAddCourse.mockResolvedValue({ data: { courseId: 42 } })
    mockGetCourseOverview.mockResolvedValue({ data: { recommendedTargets: [target] } })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('408 数据结构')
    expect(wrapper.text()).not.toContain('全部课程')
    expect(wrapper.findAll('button').filter((button) => button.text().includes('浏览课程库'))).toHaveLength(0)
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('加入并开始学习'))!
      .trigger('click')
    await flushPromises()

    expect(mockAddCourse).toHaveBeenCalledWith(42, { errorDisplay: 'inline' })
    expect(mockGetCourseOverview).toHaveBeenCalledWith(42, { errorDisplay: 'inline' })
    expect(mockOpenTarget).toHaveBeenCalledWith(expect.anything(), 42, target)
  })

  it('offers the course catalog when the recommended course is not published', async () => {
    mockGetMyCourses.mockResolvedValue({ data: [] })
    mockGetAllCourses.mockResolvedValue({ data: [{ id: 7, name: '数据结构与算法', contentKey: null }] })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).not.toContain('加入并开始学习')
    expect(wrapper.findAll('button').filter((button) => button.text().includes('浏览课程库'))).toHaveLength(1)
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('浏览课程库'))!
      .trigger('click')
    expect(mockPush).toHaveBeenCalledWith({ name: 'CourseList' })
  })

  it('keeps the recommendation and supports retry when joining fails', async () => {
    mockGetMyCourses.mockResolvedValue({ data: [] })
    mockGetAllCourses.mockResolvedValue({
      data: [{ id: 42, name: '408 数据结构', contentKey: 'cs408-data-structures' }],
    })
    mockAddCourse.mockRejectedValueOnce(new Error('暂时无法加入')).mockResolvedValueOnce({ data: { courseId: 42 } })
    mockGetCourseOverview.mockResolvedValue({ data: { recommendedTargets: [] } })
    const wrapper = mountView()
    await flushPromises()

    const button = wrapper.findAll('button').find((item) => item.text().includes('加入并开始学习'))!
    await button.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('暂时无法加入')
    expect(mockPush).not.toHaveBeenCalled()

    await button.trigger('click')
    await flushPromises()
    expect(mockAddCourse).toHaveBeenCalledTimes(2)
    expect(mockPush).toHaveBeenCalledWith({ name: 'CourseOverview', params: { id: 42 } })
  })

  it('does not open the first learner course after the authenticated account changes', async () => {
    let finishJoin!: (value: unknown) => void
    mockGetMyCourses.mockResolvedValue({ data: [] })
    mockGetAllCourses.mockResolvedValue({
      data: [{ id: 42, name: '408 数据结构', contentKey: 'cs408-data-structures' }],
    })
    mockAddCourse.mockImplementation(() => new Promise((resolve) => (finishJoin = resolve)))
    const wrapper = mountView()
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('加入并开始学习'))!
      .trigger('click')

    auth.version += 1
    auth.listeners.forEach((listener) => listener())
    finishJoin({ data: { courseId: 42 } })
    await flushPromises()

    expect(mockGetCourseOverview).not.toHaveBeenCalled()
    expect(mockOpenTarget).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })

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
  it('searches, filters real learning facts and resets pagination when the scope changes', async () => {
    mockGetMyCourses.mockResolvedValue({
      data: Array.from({ length: 13 }, (_, index) => ({
        courseId: index + 1,
        courseName: `课程 ${index + 1}`,
        description: '',
      })),
    })
    mockGetCourseOverview.mockImplementation((id: number) =>
      Promise.resolve({
        data: {
          answeredCount: 0,
          correctCount: 0,
          dueReviewCount: id === 13 ? 1 : 0,
          recommendedTargets: [],
          lastLearningTime: null,
        },
      }),
    )
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.findAll('.course-card')).toHaveLength(12)
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '下一页')!
      .trigger('click')
    expect(wrapper.findAll('.course-card')).toHaveLength(1)
    await wrapper.get('select').setValue('due')
    expect(wrapper.get('.course-card').text()).toContain('课程 13')
    expect(wrapper.text()).not.toContain('上一页')
    await wrapper.get('input').setValue('不存在')
    expect(wrapper.text()).toContain('没有匹配的课程')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '清空筛选')!
      .trigger('click')
    expect(wrapper.findAll('.course-card')).toHaveLength(12)
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '下一页')!
      .trigger('click')
    mockGetMyCourses.mockResolvedValueOnce({ data: [{ courseId: 99, courseName: '新账号课程', description: '' }] })
    auth.version += 1
    auth.listeners.forEach((listener) => listener())
    await flushPromises()
    expect(wrapper.get('.course-card').text()).toContain('新账号课程')
    expect(wrapper.text()).not.toContain('没有匹配的课程')
  })
})
