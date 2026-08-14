import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockGetCourseOverview, mockStartCourseLearning, mockStartAssessment, mockSubmitAssessment, mockPush } =
  vi.hoisted(() => ({
    mockGetCourseOverview: vi.fn(),
    mockStartCourseLearning: vi.fn(),
    mockStartAssessment: vi.fn(),
    mockSubmitAssessment: vi.fn(),
    mockPush: vi.fn(),
  }))

vi.mock('@/api/course', () => ({
  getCourseOverview: (...args: unknown[]) => mockGetCourseOverview(...args),
  startCourseLearning: (...args: unknown[]) => mockStartCourseLearning(...args),
  startCourseStageAssessment: (...args: unknown[]) => mockStartAssessment(...args),
  submitCourseStageAssessment: (...args: unknown[]) => mockSubmitAssessment(...args),
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
  'el-alert': { template: '<p>{{ title }}</p>', props: ['title'] },
  'el-dialog': { template: '<section><slot /><slot name="footer" /></section>' },
  'el-radio-group': { template: '<div><slot /></div>' },
  'el-radio': { template: '<label><slot /></label>' },
  'el-checkbox-group': { template: '<div><slot /></div>' },
  'el-checkbox': { template: '<label><slot /></label>' },
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

  it('从课程中枢进入当前课程的试卷学习入口', async () => {
    const wrapper = mount(CourseOverviewView, { global: { stubs } })
    await flushPromises()

    await findButton(wrapper, '学习课程试卷').trigger('click')

    expect(mockPush).toHaveBeenCalledWith({
      name: 'ExamList',
      query: { courseId: '408' },
    })
  })

  it('未指定知识点时请求服务端选择下一目标', async () => {
    mockStartCourseLearning.mockResolvedValue({
      data: {
        type: 'TUTOR',
        title: '继续 AI 教学',
        reason: '当前首个未完成教学内容',
        questionId: null,
        knowledgePointId: 31,
      },
    })
    const wrapper = mount(CourseOverviewView, { global: { stubs } })
    await flushPromises()

    const button = wrapper.find('button[aria-label="按统一课程状态开始学习"]')
    expect(button.exists()).toBe(true)
    await button.trigger('click')
    await flushPromises()

    expect(mockStartCourseLearning).toHaveBeenCalledWith(408)
    expect(mockPush).toHaveBeenCalledWith({
      name: 'TutorSession',
      params: { id: 408 },
      query: { knowledgePointId: '31' },
    })
  })

  it.each([
    ['DUE_REVIEW', 'Review', 21],
    ['WRONG_QUESTION', 'WrongQuestions', 22],
  ])('将服务端选择的 %s 目标送到对应学习入口', async (type, routeName, questionId) => {
    mockStartCourseLearning.mockResolvedValue({
      data: {
        type,
        title: '继续课程学习',
        reason: '来自统一课程状态',
        questionId,
        knowledgePointId: null,
      },
    })
    const wrapper = mount(CourseOverviewView, { global: { stubs } })
    await flushPromises()

    await wrapper.find('button[aria-label="按统一课程状态开始学习"]').trigger('click')
    await flushPromises()

    expect(mockPush).toHaveBeenCalledWith({
      name: routeName,
      query: { courseId: '408', questionId: String(questionId) },
    })
  })

  it('主动发起阶段测评并在完整作答后展示服务端结果', async () => {
    const started = {
      id: 51,
      courseId: 408,
      status: 'IN_PROGRESS',
      selectionStrategy: 'COURSE_SEQUENCE_FALLBACK',
      questionCount: 1,
      correctCount: null,
      questions: [
        {
          id: 61,
          questionId: 21,
          sortOrder: 1,
          questionType: 'SINGLE_CHOICE',
          content: '栈的访问顺序是？',
          options: [{ label: 'A', content: 'LIFO' }],
          score: 2,
          userAnswer: null,
          correct: null,
          correctAnswer: null,
          analysis: null,
        },
      ],
    }
    mockStartAssessment.mockResolvedValue({ data: started })
    mockSubmitAssessment.mockResolvedValue({
      data: {
        ...started,
        status: 'COMPLETED',
        correctCount: 1,
        questions: [
          { ...started.questions[0], userAnswer: 'A', correct: true, correctAnswer: 'A', analysis: '栈顶元素先离开' },
        ],
      },
    })
    const wrapper = mount(CourseOverviewView, { global: { stubs } })
    await flushPromises()
    const vm = wrapper.vm as unknown as {
      startAssessment: () => Promise<void>
      submitAssessment: () => Promise<void>
      assessmentAnswers: Record<number, string[]>
    }

    await vm.startAssessment()
    expect(mockStartAssessment).toHaveBeenCalledWith(408, 5)
    expect(wrapper.text()).toContain('确定性课程题序')
    expect(wrapper.text()).not.toContain('栈顶元素先离开')
    vm.assessmentAnswers[61] = ['A']
    await vm.submitAssessment()

    expect(mockSubmitAssessment).toHaveBeenCalledWith(51, [{ assessmentQuestionId: 61, userAnswer: 'A' }])
    expect(wrapper.text()).toContain('答对 1 / 1 题')
    expect(wrapper.text()).toContain('栈顶元素先离开')
  })
})
