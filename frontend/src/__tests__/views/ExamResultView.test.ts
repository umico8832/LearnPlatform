import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockGetExamResult, mockPush, mockError } = vi.hoisted(() => ({
  mockGetExamResult: vi.fn(),
  mockPush: vi.fn(),
  mockError: vi.fn(),
}))

vi.mock('@/api/exam', () => ({
  getExamResult: (...args: unknown[]) => mockGetExamResult(...args),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRoute: () => ({ params: { recordId: '101' } }),
  useRouter: () => ({ push: mockPush }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: mockError },
}))

import ExamResultView from '@/views/exam/ExamResultView.vue'

const stubs = {
  'el-card': { template: '<article><slot /></article>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-icon': { template: '<i><slot /></i>' },
  'el-button': {
    template: '<button @click="$emit(\'click\')"><slot /></button>',
    emits: ['click'],
  },
  'el-empty': { template: '<div><slot /></div>' },
  'el-alert': { template: '<div>{{ title }}</div>', props: ['title'] },
}

describe('ExamResultView authoritative review', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    sessionStorage.setItem('exam_result_101', JSON.stringify({ examTitle: '本地伪造结果', score: 100 }))
    mockGetExamResult.mockResolvedValue({
      code: 0,
      data: {
        id: 101,
        examPaperId: 7,
        examTitle: '服务端权威结果',
        courseId: 408,
        paperType: 'OFFICIAL_EXAM',
        examName: '全国硕士研究生招生考试',
        examYear: 2025,
        sourceReference: '教育主管部门公开文件',
        sourceVerified: true,
        startTime: '2026-08-13T10:00:00',
        endTime: '2026-08-13T10:25:30',
        score: 5,
        totalScore: 10,
        status: 1,
        duration: 30,
        answers: [
          {
            questionId: 22,
            content: '错误题目',
            questionType: 'SINGLE_CHOICE',
            sortOrder: 1,
            fullScore: 5,
            sectionTitle: '第一部分 数据结构',
            displayNumber: '一、1（1）',
            userAnswer: 'B',
            isCorrect: 0,
            score: 0,
            correctAnswer: 'A',
            analysis: '服务端解析',
          },
          {
            questionId: 23,
            content: '正确题目',
            questionType: 'TRUE_FALSE',
            sortOrder: 2,
            fullScore: 5,
            sectionTitle: '第二部分 判断题',
            displayNumber: '二、1',
            userAnswer: 'TRUE',
            isCorrect: 1,
            score: 5,
            correctAnswer: 'TRUE',
            analysis: null,
          },
        ],
      },
    })
  })

  it('始终读取 API 权威结果并展示可信来源、分区和原题号', async () => {
    const wrapper = mount(ExamResultView, {
      global: {
        stubs,
        directives: { loading: () => undefined },
      },
    })
    await flushPromises()

    expect(mockGetExamResult).toHaveBeenCalledWith(101)
    expect(wrapper.text()).toContain('服务端权威结果')
    expect(wrapper.text()).not.toContain('本地伪造结果')
    expect(wrapper.text()).toContain('来源已核验')
    expect(wrapper.text()).toContain('2025 · 全国硕士研究生招生考试')
    expect(wrapper.text()).toContain('来源：教育主管部门公开文件')
    expect(wrapper.text()).toContain('第一部分 数据结构')
    expect(wrapper.text()).toContain('一、1（1）')
  })

  it('在课程上下文中返回课程总览，并把错题精确深链到错题本', async () => {
    const wrapper = mount(ExamResultView, {
      global: {
        stubs,
        directives: { loading: () => undefined },
      },
    })
    await flushPromises()

    const courseButton = wrapper.findAll('button').find((button) => button.text().includes('返回课程总览'))!
    const reviewButton = wrapper.findAll('button').find((button) => button.text().includes('复习此错题'))!
    expect(courseButton).toBeTruthy()
    expect(wrapper.findAll('button').filter((button) => button.text().includes('复习此错题'))).toHaveLength(1)

    await courseButton.trigger('click')
    await reviewButton.trigger('click')

    expect(mockPush).toHaveBeenCalledWith({ name: 'CourseOverview', params: { id: '408' } })
    expect(mockPush).toHaveBeenCalledWith({
      name: 'WrongQuestions',
      query: { courseId: '408', questionId: '22' },
    })
  })

  it('兼容权威结果暂未返回答题数组的响应', async () => {
    mockGetExamResult.mockResolvedValue({
      code: 0,
      data: {
        id: 101,
        examPaperId: 7,
        examTitle: '无答题明细结果',
        courseId: null,
        paperType: 'PRACTICE',
        startTime: '2026-08-13T10:00:00',
        endTime: '2026-08-13T10:01:00',
        score: 0,
        totalScore: 0,
        status: 1,
        duration: 30,
        answers: null,
      },
    })

    const wrapper = mount(ExamResultView, {
      global: {
        stubs,
        directives: { loading: () => undefined },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('0 题 · 0 题需复习')
    expect(wrapper.findAll('.answer-item')).toHaveLength(0)
  })

  it('主观题待批阅时只展示暂定分且不泄露答案与解析', async () => {
    mockGetExamResult.mockResolvedValue({
      code: 0,
      data: {
        id: 101,
        examPaperId: 7,
        examTitle: '含主观题试卷',
        courseId: 408,
        paperType: 'OFFICIAL_EXAM',
        startTime: '2026-08-13T10:00:00',
        endTime: '2026-08-13T10:20:00',
        score: 22,
        totalScore: 45,
        status: 3,
        answers: [
          {
            questionId: 41,
            content: '算法题',
            questionType: 'SHORT_ANSWER',
            fullScore: 13,
            displayNumber: '第41题',
            userAnswer: '我的算法',
            isCorrect: null,
            score: null,
            correctAnswer: null,
            analysis: null,
            gradingStatus: 'PENDING',
          },
        ],
      },
    })

    const wrapper = mount(ExamResultView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('暂定分')
    expect(wrapper.text()).toContain('待人工批阅')
    expect(wrapper.text()).not.toContain('正确答案')
    expect(wrapper.text()).not.toContain('复习此错题')
  })
})
