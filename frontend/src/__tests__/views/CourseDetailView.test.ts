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
    props: ['loading', 'type', 'icon', 'text'],
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

function findButton(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((item) => item.text().includes(text))
  expect(button, `button with text ${text}`).toBeTruthy()
  return button!
}

describe('CourseDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetCourseById.mockResolvedValue({ data: { id: 408, name: '408 数据结构', description: '课程描述' } })
    mockGetKnowledgeTree.mockResolvedValue({
      data: [
        { id: 30, name: '元素数量与数组容量', description: '', contentKey: 'ods-array-size-capacity', children: [] },
        { id: 31, name: 'ArrayStack 按位插入', description: '', contentKey: 'ods-arraystack-insertion', children: [] },
        { id: 32, name: 'ArrayStack 按位删除', description: '', contentKey: 'ods-arraystack-removal', children: [] },
        { id: 33, name: 'ArrayStack 的容量调整', description: '', contentKey: 'ods-arraystack-resize', children: [] },
        { id: 34, name: 'ArrayStack 调整容量的摊还成本', description: '', contentKey: 'ods-arraystack-amortized-resize', children: [] },
        { id: 35, name: 'ArrayStack 的操作复杂度', description: '', contentKey: 'ods-arraystack-performance', children: [] },
        { id: 36, name: 'FastArrayStack 的批量复制优化', description: '', contentKey: 'ods-fastarraystack-block-copy', children: [] },
        { id: 37, name: 'ArrayQueue 的循环数组表示', description: '', contentKey: 'ods-arrayqueue-representation', children: [] },
        { id: 38, name: 'ArrayQueue 的入队', description: '', contentKey: 'ods-arrayqueue-enqueue', children: [] },
        { id: 39, name: 'ArrayQueue 的出队', description: '', contentKey: 'ods-arrayqueue-dequeue', children: [] },
        { id: 40, name: 'ArrayQueue 调整容量时的线性化复制', description: '', contentKey: 'ods-arrayqueue-resize', children: [] },
        { id: 41, name: 'ArrayQueue 的操作复杂度', description: '', contentKey: 'ods-arrayqueue-performance', children: [] },
      ],
    })
    mockAddCourseToLibrary.mockResolvedValue({ data: { courseId: 408 } })
  })

  it('未加入课程库时，引导用户先加入而不是直接开始需要课程库权限的 Tutor', async () => {
    mockGetMyCourses.mockResolvedValue({ data: [] })
    const wrapper = mount(CourseDetailView, { global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('加入课程库后可学习')
    expect(wrapper.text()).not.toContain('开始 AI 教学')
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

  it('已加入课程库时，将主操作切换为进入课程总览并开放 Tutor 入口', async () => {
    mockGetMyCourses.mockResolvedValue({ data: [{ courseId: 408 }] })
    const wrapper = mount(CourseDetailView, { global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('开始 AI 教学')
    await findButton(wrapper, '进入课程总览').trigger('click')

    expect(mockPush).toHaveBeenCalledWith({ name: 'CourseOverview', params: { id: 408 } })
    expect(mockAddCourseToLibrary).not.toHaveBeenCalled()
  })

  it('已加入课程库时，为已迁入的 ArrayQueue 操作复杂度知识提供 Tutor 入口', async () => {
    mockGetMyCourses.mockResolvedValue({ data: [{ courseId: 408 }] })
    const wrapper = mount(CourseDetailView, { global: { stubs } })
    await flushPromises()

    const tutorButtons = wrapper.findAll('button').filter((item) => item.text().includes('开始 AI 教学'))
    expect(tutorButtons).toHaveLength(12)
    await tutorButtons[11].trigger('click')
    expect(mockPush).toHaveBeenCalledWith({
      name: 'TutorSession',
      params: { id: 408 },
      query: { knowledgePointId: '41' },
    })
  })
})
