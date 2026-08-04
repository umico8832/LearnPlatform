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
    template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
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
        { id: 31, name: 'ArrayStack 按位插入', description: '', contentKey: 'ods-arraystack-insertion', children: [] },
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
})
