import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockGetCourseOverview, mockPush } = vi.hoisted(() => ({
  mockGetCourseOverview: vi.fn(),
  mockPush: vi.fn(),
}))

vi.mock('@/api/course', () => ({
  getCourseOverview: (...args: unknown[]) => mockGetCourseOverview(...args),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '408' } }),
  useRouter: () => ({ push: mockPush }),
}))

import CourseOverviewView from '@/views/course/CourseOverviewView.vue'

const stubs = {
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-tag': { template: '<span><slot /></span>' },
  'el-result': { template: '<section><slot /><slot name="extra" /></section>' },
}

function findButton(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((item) => item.text().includes(text))
  expect(button, `button with text ${text}`).toBeTruthy()
  return button!
}

describe('CourseOverviewView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetCourseOverview.mockResolvedValue({
      data: {
        courseId: 408,
        courseName: '408 数据结构',
        answeredCount: 0,
        correctCount: 0,
        dueReviewCount: 0,
        unresolvedWrongCount: 0,
        lastLearningTime: null,
        recommendedTargets: [],
        tutorProgress: [
          { knowledgePointId: 31, title: 'ArrayStack 的按位插入', status: 'COMPLETED' },
          { knowledgePointId: 32, title: 'ArrayStack 的容量调整', status: 'IN_PROGRESS' },
          { knowledgePointId: 33, title: 'ArrayStack 的按位删除', status: 'NOT_STARTED' },
        ],
      },
    })
  })

  it('显示已迁入 Tutor 内容的服务端学习状态并允许继续学习', async () => {
    const wrapper = mount(CourseOverviewView, { global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('已完成理解检查')
    expect(wrapper.text()).toContain('已尝试')
    expect(wrapper.text()).toContain('未开始')

    await findButton(wrapper, '继续学习').trigger('click')
    expect(mockPush).toHaveBeenCalledWith({
      name: 'TutorSession',
      params: { id: 408 },
      query: { knowledgePointId: '32' },
    })
  })
})
