import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const {
  mockGetCourseOverview,
  mockStartCourseLearning,
  mockStartAssessment,
  mockSubmitAssessment,
  mockGetAssessmentHistory,
  mockGetAssessmentDetail,
  mockPush,
} = vi.hoisted(() => ({
  mockGetCourseOverview: vi.fn(),
  mockStartCourseLearning: vi.fn(),
  mockStartAssessment: vi.fn(),
  mockSubmitAssessment: vi.fn(),
  mockGetAssessmentHistory: vi.fn(),
  mockGetAssessmentDetail: vi.fn(),
  mockPush: vi.fn(),
}))

vi.mock('@/api/course', () => ({
  getCourseOverview: (...args: unknown[]) => mockGetCourseOverview(...args),
  startCourseLearning: (...args: unknown[]) => mockStartCourseLearning(...args),
  startCourseStageAssessment: (...args: unknown[]) => mockStartAssessment(...args),
  submitCourseStageAssessment: (...args: unknown[]) => mockSubmitAssessment(...args),
  getCourseStageAssessmentHistory: (...args: unknown[]) => mockGetAssessmentHistory(...args),
  getCourseStageAssessmentDetail: (...args: unknown[]) => mockGetAssessmentDetail(...args),
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
  'el-pagination': { template: '<nav />' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option>{{ label }}</option>', props: ['label'] },
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
        latestStageAssessment: null,
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
          sourceType: 'AI_GENERATED',
          originQuestionId: 20,
          content: '栈的访问顺序是？',
          options: [{ label: 'A', content: 'LIFO' }],
          score: 2,
          userAnswer: null,
          correct: null,
          correctAnswer: null,
          analysis: null,
          knowledgePoints: [{ id: 31, name: '栈' }],
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
      openAssessmentSetup: () => void
      startAssessment: () => Promise<void>
      submitAssessment: () => Promise<void>
      assessmentAnswers: Record<number, string[]>
    }

    vm.openAssessmentSetup()
    expect(wrapper.text()).toContain('课程整体测评')
    await vm.startAssessment()
    expect(mockStartAssessment).toHaveBeenCalledWith(408, 5, null)
    expect(wrapper.text()).toContain('范围：课程整体')
    expect(wrapper.text()).toContain('确定性课程题序')
    expect(wrapper.text()).toContain('知识点：栈')
    expect(wrapper.text()).toContain('AI 审查生成题 · 母题 #20')
    expect(wrapper.text()).not.toContain('栈顶元素先离开')
    vm.assessmentAnswers[61] = ['A']
    await vm.submitAssessment()

    expect(mockSubmitAssessment).toHaveBeenCalledWith(51, [{ assessmentQuestionId: 61, userAnswer: 'A' }])
    expect(wrapper.text()).toContain('答对 1 / 1 题')
    expect(wrapper.text()).toContain('栈顶元素先离开')
  })

  it('限定已审查知识点开始测评并随会话展示固化范围', async () => {
    mockStartAssessment.mockResolvedValue({
      data: {
        id: 52,
        courseId: 408,
        status: 'IN_PROGRESS',
        selectionStrategy: 'COURSE_SEQUENCE_FALLBACK',
        targetKnowledgePointId: 32,
        targetKnowledgePointName: 'ArrayStack 的容量调整',
        questionCount: 1,
        correctCount: null,
        questions: [],
      },
    })
    const wrapper = mount(CourseOverviewView, { global: { stubs } })
    await flushPromises()
    const vm = wrapper.vm as unknown as {
      assessmentKnowledgePointId: number | null
      startAssessment: () => Promise<void>
    }

    vm.assessmentKnowledgePointId = 32
    await vm.startAssessment()

    expect(mockStartAssessment).toHaveBeenCalledWith(408, 5, 32)
    expect(wrapper.text()).toContain('范围：ArrayStack 的容量调整')
  })

  it('复盘按知识点标注并提供错题复习与已审查教学内容入口', async () => {
    const detail = {
      id: 53,
      courseId: 408,
      status: 'COMPLETED',
      selectionStrategy: 'COURSE_SEQUENCE_FALLBACK',
      questionCount: 2,
      correctCount: 1,
      questions: [
        {
          id: 71,
          questionId: 21,
          sortOrder: 1,
          questionType: 'SINGLE_CHOICE',
          sourceType: 'AI_GENERATED',
          originQuestionId: 20,
          content: '栈的访问顺序是？',
          options: [{ label: 'A', content: 'LIFO' }],
          score: 2,
          userAnswer: 'B',
          correct: false,
          correctAnswer: 'A',
          analysis: '栈顶元素先离开',
          knowledgePoints: [{ id: 31, name: '栈' }],
        },
        {
          id: 72,
          questionId: 22,
          sortOrder: 2,
          questionType: 'SINGLE_CHOICE',
          sourceType: 'MANUAL',
          originQuestionId: null,
          content: '队列的访问顺序是？',
          options: [{ label: 'A', content: 'FIFO' }],
          score: 2,
          userAnswer: 'A',
          correct: true,
          correctAnswer: 'A',
          analysis: '先进先出',
          knowledgePoints: [{ id: 999, name: '未审查目录节点' }],
        },
      ],
    }
    mockGetAssessmentDetail.mockResolvedValue({
      data: {
        ...detail,
        knowledgePointSummary: [
          { id: 31, name: '栈', questionCount: 1, correctCount: 0 },
          { id: 999, name: '未审查目录节点', questionCount: 1, correctCount: 1 },
        ],
      },
    })
    mockGetAssessmentHistory.mockResolvedValue({
      data: {
        records: [
          {
            id: 53,
            selectionStrategy: 'COURSE_SEQUENCE_FALLBACK',
            questionCount: 2,
            correctCount: 1,
            sourceComposition: { officialExamCount: 0, manualCount: 2, userPrivateCount: 0, aiGeneratedCount: 0 },
            completeTime: '2026-08-15T10:05:00',
          },
        ],
        total: 1,
        current: 1,
        size: 10,
      },
    })
    const wrapper = mount(CourseOverviewView, { global: { stubs } })
    await flushPromises()
    const vm = wrapper.vm as unknown as { openAssessmentHistory: () => Promise<void> }

    await vm.openAssessmentHistory()
    await findButton(wrapper, '查看复盘').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('知识点：栈')
    expect(wrapper.text()).toContain('知识点：未审查目录节点')
    expect(wrapper.text()).toContain('按知识点统计')
    expect(wrapper.text()).toContain('答对 0 / 1 题')
    await findButton(wrapper, '进入错题复习').trigger('click')
    expect(mockPush).toHaveBeenCalledWith({
      name: 'WrongQuestions',
      query: { courseId: '408', questionId: '21' },
    })
    await findButton(wrapper, '知识点：栈').trigger('click')
    expect(mockPush).toHaveBeenCalledWith({
      name: 'TutorSession',
      params: { id: 408 },
      query: { knowledgePointId: '31' },
    })
    await findButton(wrapper, '进入教学').trigger('click')
    expect(mockPush).toHaveBeenCalledWith({
      name: 'TutorSession',
      params: { id: 408 },
      query: { knowledgePointId: '31' },
    })
  })

  it('展示最近测评事实并可从本人历史打开逐题复盘', async () => {
    mockGetCourseOverview.mockResolvedValue({
      data: {
        courseId: 408,
        courseName: '408 数据结构',
        answeredCount: 5,
        correctCount: 3,
        dueReviewCount: 1,
        unresolvedWrongCount: 2,
        lastLearningTime: '2026-08-15T10:05:00',
        latestStageAssessment: {
          id: 51,
          selectionStrategy: 'LEARNING_STATE_PRIORITY',
          questionCount: 5,
          correctCount: 3,
          sourceComposition: {
            officialExamCount: 3,
            manualCount: 0,
            userPrivateCount: 0,
            aiGeneratedCount: 2,
          },
          startTime: '2026-08-15T10:00:00',
          completeTime: '2026-08-15T10:05:00',
        },
        recommendedTargets: [],
        tutorProgress: [],
      },
    })
    mockGetAssessmentHistory.mockResolvedValue({
      data: {
        records: [
          {
            id: 51,
            selectionStrategy: 'LEARNING_STATE_PRIORITY',
            questionCount: 5,
            correctCount: 3,
            sourceComposition: {
              officialExamCount: 3,
              manualCount: 0,
              userPrivateCount: 0,
              aiGeneratedCount: 2,
            },
            completeTime: '2026-08-15T10:05:00',
          },
        ],
        total: 1,
        current: 1,
        size: 10,
      },
    })
    mockGetAssessmentDetail.mockResolvedValue({
      data: {
        id: 51,
        courseId: 408,
        status: 'COMPLETED',
        selectionStrategy: 'LEARNING_STATE_PRIORITY',
        questionCount: 1,
        correctCount: 1,
        sourceComposition: {
          officialExamCount: 0,
          manualCount: 0,
          userPrivateCount: 0,
          aiGeneratedCount: 1,
        },
        questions: [
          {
            id: 61,
            sortOrder: 1,
            questionType: 'SINGLE_CHOICE',
            content: '栈的访问顺序是？',
            options: [{ label: 'A', content: 'LIFO' }],
            userAnswer: 'A',
            correct: true,
            correctAnswer: 'A',
            analysis: '栈顶元素先离开',
          },
        ],
      },
    })
    const wrapper = mount(CourseOverviewView, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('最近阶段测评')
    expect(wrapper.text()).toContain('答对 3 / 5 题')
    expect(wrapper.text()).toContain('官方原题 3 · AI 生成题 2')
    const vm = wrapper.vm as unknown as {
      openAssessmentHistory: () => Promise<void>
      openAssessmentDetail: (id: number) => Promise<void>
    }

    await vm.openAssessmentHistory()
    expect(mockGetAssessmentHistory).toHaveBeenCalledWith(408, 1, 10)
    expect(wrapper.text()).toContain('按当前学习事实优先选题')
    expect(wrapper.text()).toContain('官方原题 3 · AI 生成题 2')
    await vm.openAssessmentDetail(51)
    expect(mockGetAssessmentDetail).toHaveBeenCalledWith(51)
    expect(wrapper.text()).toContain('栈顶元素先离开')
  })
})
