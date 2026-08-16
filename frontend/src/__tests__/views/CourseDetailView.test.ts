import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockAddCourseToLibrary, mockGetCourseById, mockGetKnowledgeTree, mockGetMyCourses, mockPush, mockSuccess } =
  vi.hoisted(() => ({
    mockAddCourseToLibrary: vi.fn(),
    mockGetCourseById: vi.fn(),
    mockGetKnowledgeTree: vi.fn(),
    mockGetMyCourses: vi.fn(),
    mockPush: vi.fn(),
    mockSuccess: vi.fn(),
  }))

vi.mock('@/api/course', () => ({
  addCourseToLibrary: (...args: unknown[]) => mockAddCourseToLibrary(...args),
  getCourseById: (...args: unknown[]) => mockGetCourseById(...args),
  getMyCourses: (...args: unknown[]) => mockGetMyCourses(...args),
}))

vi.mock('@/api/knowledgePoint', () => ({
  getKnowledgeTree: (...args: unknown[]) => mockGetKnowledgeTree(...args),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '408' } }),
  useRouter: () => ({ back: vi.fn(), push: mockPush }),
}))

vi.mock('element-plus', () => ({ ElMessage: { success: mockSuccess } }))

import CourseDetailView from '@/views/course/CourseDetailView.vue'

const stubs = {
  'el-button': {
    template: '<button :disabled="loading" @click="$emit(\'click\', $event)"><slot /></button>',
    props: ['loading', 'type', 'icon', 'text', 'plain'],
    emits: ['click'],
  },
  'el-icon': { template: '<i><slot /></i>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-tree': {
    template: '<div><template v-for="item in data" :key="item.id"><slot :data="item" /></template></div>',
    props: ['data'],
  },
  'el-empty': { template: '<div><slot /><slot name="description" /></div>' },
}

const reviewed = (id: number, name: string, contentKey: string) => ({
  id,
  name,
  description: '',
  contentKey,
  contentReviewStatus: 'REVIEWED',
  children: [],
})

const reviewedNodes = [
  reviewed(30, '元素数量与数组容量', 'ods-array-size-capacity'),
  reviewed(31, 'ArrayStack 按位插入', 'ods-arraystack-insertion'),
  reviewed(32, 'ArrayStack 按位删除', 'ods-arraystack-removal'),
  reviewed(33, 'ArrayStack 的容量调整', 'ods-arraystack-resize'),
  reviewed(34, 'ArrayStack 调整容量的摊还成本', 'ods-arraystack-amortized-resize'),
  reviewed(35, 'ArrayStack 的操作复杂度', 'ods-arraystack-performance'),
  reviewed(36, 'FastArrayStack 的批量复制优化', 'ods-fastarraystack-block-copy'),
  reviewed(37, 'ArrayQueue 的循环数组表示', 'ods-arrayqueue-representation'),
  reviewed(38, 'ArrayQueue 的入队', 'ods-arrayqueue-enqueue'),
  reviewed(39, 'ArrayQueue 的出队', 'ods-arrayqueue-dequeue'),
  reviewed(40, 'ArrayQueue 调整容量时的线性化复制', 'ods-arrayqueue-resize'),
  reviewed(41, 'ArrayQueue 的操作复杂度', 'ods-arrayqueue-performance'),
]

function findButton(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((item) => item.text().includes(text))
  expect(button, `button with text ${text}`).toBeTruthy()
  return button!
}

describe('CourseDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetCourseById.mockResolvedValue({ data: { id: 408, name: '408 数据结构', description: '课程描述' } })
    mockGetKnowledgeTree.mockResolvedValue({ data: reviewedNodes })
    mockAddCourseToLibrary.mockResolvedValue({ data: { courseId: 408 } })
  })

  it('未加入课程库时，引导用户先加入而不是直接开始需要课程库权限的 Tutor', async () => {
    mockGetMyCourses.mockResolvedValue({ data: [] })
    const wrapper = mount(CourseDetailView, { global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('加入课程库后可学习')
    const tutorButtons = wrapper.findAll('button').filter((item) => item.text().trim() === '开始学习')
    expect(tutorButtons).toHaveLength(0)
    expect(findButton(wrapper, '加入课程库').exists()).toBe(true)
  })

  it('加入课程库后直接进入该课程的学习总览', async () => {
    mockGetMyCourses.mockResolvedValue({ data: [] })
    const wrapper = mount(CourseDetailView, { global: { stubs } })
    await flushPromises()

    await findButton(wrapper, '加入课程库').trigger('click')
    await flushPromises()

    expect(mockAddCourseToLibrary).toHaveBeenCalledWith(408)
    expect(mockPush).toHaveBeenCalledWith({ name: 'CourseOverview', params: { id: 408 } })
  })

  it('已加入课程库时，将主操作切换为进入课程空间并开放 Tutor 入口', async () => {
    mockGetMyCourses.mockResolvedValue({ data: [{ courseId: 408 }] })
    const wrapper = mount(CourseDetailView, { global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('开始学习')
    await findButton(wrapper, '进入课程空间').trigger('click')

    expect(mockPush).toHaveBeenCalledWith({ name: 'CourseOverview', params: { id: 408 } })
    expect(mockAddCourseToLibrary).not.toHaveBeenCalled()
  })

  it('已加入课程库时，为每个已审查内容知识提供 Tutor 入口', async () => {
    mockGetMyCourses.mockResolvedValue({ data: [{ courseId: 408 }] })
    const wrapper = mount(CourseDetailView, { global: { stubs } })
    await flushPromises()

    const tutorButtons = wrapper.findAll('button').filter((item) => item.text().includes('开始学习'))
    expect(tutorButtons).toHaveLength(12)
    await tutorButtons[11].trigger('click')
    expect(mockPush).toHaveBeenCalledWith({
      name: 'TutorSession',
      params: { id: 408 },
      query: { knowledgePointId: '41' },
    })
  })

  it('未审查内容不提供学习入口', async () => {
    mockGetMyCourses.mockResolvedValue({ data: [{ courseId: 408 }] })
    mockGetKnowledgeTree.mockResolvedValue({
      data: [{ id: 99, name: '未审查内容', description: '', contentKey: 'cs408-pending', children: [] }],
    })
    const wrapper = mount(CourseDetailView, { global: { stubs } })
    await flushPromises()

    const tutorButtons = wrapper.findAll('button').filter((item) => item.text().trim() === '开始学习')
    expect(tutorButtons).toHaveLength(0)
    expect(wrapper.text()).not.toContain('加入课程库后可学习')
  })
})
