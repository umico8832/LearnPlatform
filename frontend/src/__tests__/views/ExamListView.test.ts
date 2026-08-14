import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const {
  mockGetPublishedPapers,
  mockGetMyExamRecords,
  mockGetPaperDetail,
  mockStartExam,
  mockPreviewPrivateExamImport,
  mockConfirmPrivateExamImport,
  mockGetPrivateExamSource,
  mockCreatePrivateExamDraft,
  mockGeneratePrivateExamDraftAnswer,
  mockReviewPrivateExamDraftQuestion,
  mockConfirmPrivateExamDraft,
  mockGetPrivateExamDrafts,
  mockPush,
} = vi.hoisted(() => ({
  mockGetPublishedPapers: vi.fn(),
  mockGetMyExamRecords: vi.fn(),
  mockGetPaperDetail: vi.fn(),
  mockStartExam: vi.fn(),
  mockPreviewPrivateExamImport: vi.fn(),
  mockConfirmPrivateExamImport: vi.fn(),
  mockGetPrivateExamSource: vi.fn(),
  mockCreatePrivateExamDraft: vi.fn(),
  mockGeneratePrivateExamDraftAnswer: vi.fn(),
  mockReviewPrivateExamDraftQuestion: vi.fn(),
  mockConfirmPrivateExamDraft: vi.fn(),
  mockGetPrivateExamDrafts: vi.fn(),
  mockPush: vi.fn(),
}))

vi.mock('@/api/exam', () => ({
  getPublishedPapers: (...args: unknown[]) => mockGetPublishedPapers(...args),
  getMyExamRecords: (...args: unknown[]) => mockGetMyExamRecords(...args),
  getPaperDetail: (...args: unknown[]) => mockGetPaperDetail(...args),
  startExam: (...args: unknown[]) => mockStartExam(...args),
  startExamLearningSession: vi.fn(),
  previewPrivateExamImport: (...args: unknown[]) => mockPreviewPrivateExamImport(...args),
  confirmPrivateExamImport: (...args: unknown[]) => mockConfirmPrivateExamImport(...args),
  getPrivateExamSource: (...args: unknown[]) => mockGetPrivateExamSource(...args),
  createPrivateExamDraft: (...args: unknown[]) => mockCreatePrivateExamDraft(...args),
  generatePrivateExamDraftAnswer: (...args: unknown[]) => mockGeneratePrivateExamDraftAnswer(...args),
  reviewPrivateExamDraftQuestion: (...args: unknown[]) => mockReviewPrivateExamDraftQuestion(...args),
  confirmPrivateExamDraft: (...args: unknown[]) => mockConfirmPrivateExamDraft(...args),
  getPrivateExamDrafts: (...args: unknown[]) => mockGetPrivateExamDrafts(...args),
}))

vi.mock('@/api/course', () => ({
  getAllCourses: vi.fn().mockResolvedValue({ code: 0, data: [{ id: 10, name: '数据结构' }] }),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: mockPush }),
}))

import ExamListView from '@/views/exam/ExamListView.vue'

const stubs = {
  'el-tabs': { template: '<div><slot /></div>' },
  'el-tab-pane': { template: '<section><slot /></section>' },
  'el-card': { template: '<article><slot /></article>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-icon': { template: '<i><slot /></i>' },
  'el-button': {
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    props: ['disabled', 'loading', 'type', 'link', 'size'],
    emits: ['click'],
  },
  'el-empty': { template: '<div />' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-pagination': { template: '<div />' },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-alert': { template: '<div />' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<label><slot /></label>' },
  'el-input': { template: '<textarea />' },
  'el-input-number': { template: '<input />' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option />' },
  'el-checkbox-group': { template: '<div><slot /></div>' },
  'el-checkbox': { template: '<label><slot /></label>' },
}

describe('ExamListView paper provenance', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    mockStartExam.mockResolvedValue({ code: 0, data: { id: 101, examPaperId: 1, status: 0 } })
    mockGetMyExamRecords.mockResolvedValue({ code: 0, data: { records: [], total: 0 } })
    mockGetPrivateExamDrafts.mockResolvedValue({ code: 0, data: [] })
    mockGetPublishedPapers.mockResolvedValue({
      code: 0,
      data: {
        total: 2,
        records: [
          {
            id: 1,
            title: '来源已核验试卷',
            paperType: 'OFFICIAL_EXAM',
            examName: '全国硕士研究生招生考试',
            examYear: 2025,
            sourceReference: '教育主管部门公开文件',
            sourceVerified: true,
            questionCount: 1,
            duration: 30,
            totalScore: 2,
          },
          {
            id: 2,
            title: '日常练习',
            paperType: 'PRACTICE',
            sourceVerified: false,
            questionCount: 1,
            duration: 10,
            totalScore: 1,
          },
        ],
      },
    })
  })

  it('解析预览后显式确认才创建私有试卷', async () => {
    mockPreviewPrivateExamImport.mockResolvedValue({
      code: 0,
      data: {
        title: '我的练习卷',
        courseId: 10,
        duration: 30,
        sourceName: 'notes.txt',
        sourceFormat: 'TEXT',
        contentHash: 'a'.repeat(64),
        questionCount: 1,
        totalScore: 2,
        questions: [
          {
            content: '栈遵循哪种顺序？',
            questionType: 'SINGLE_CHOICE',
            answer: 'B',
            score: 2,
            options: [
              { label: 'A', content: 'FIFO', correct: false },
              { label: 'B', content: 'LIFO', correct: true },
            ],
          },
        ],
      },
    })
    mockConfirmPrivateExamImport.mockResolvedValue({ code: 0, data: { id: 9 } })
    const wrapper = mount(ExamListView, { global: { stubs, directives: { loading: () => undefined } } })
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      importForm: {
        title: string
        courseId: number
        duration: number
        sourceName: string
        sourceFormat: 'TEXT'
        content: string
      }
      previewImport: () => Promise<void>
      confirmImport: () => Promise<void>
    }
    vm.importForm = {
      title: '我的练习卷',
      courseId: 10,
      duration: 30,
      sourceName: 'notes.txt',
      sourceFormat: 'TEXT',
      content: '题目内容',
    }
    await vm.previewImport()
    expect(mockConfirmPrivateExamImport).not.toHaveBeenCalled()
    await vm.confirmImport()
    expect(mockConfirmPrivateExamImport).toHaveBeenCalledWith(
      expect.objectContaining({
        expectedContentHash: 'a'.repeat(64),
        confirmed: true,
      }),
    )
  })

  it('无答案题目必须经过AI建议和逐题人工复核后才启用', async () => {
    const draftQuestion = {
      id: 41,
      sortOrder: 1,
      content: '先进后出的数据结构是？',
      questionType: 'SINGLE_CHOICE',
      score: 1,
      options: [
        { label: 'A', content: '栈' },
        { label: 'B', content: '队列' },
      ],
      originalAnswerLabels: [],
      originalAnalysis: null,
      aiAnswerLabels: [],
      aiAnalysis: null,
      generationStatus: 'PENDING',
      finalAnswerLabels: [],
      finalAnalysis: null,
      reviewStatus: 'PENDING',
    }
    const draft = {
      id: 31,
      title: 'AI 补全卷',
      courseId: 10,
      duration: 30,
      status: 'DRAFT',
      confirmedPaperId: null,
      reviewedQuestionCount: 0,
      questionCount: 1,
      createTime: '2026-08-15T10:00:00',
      questions: [draftQuestion],
    }
    mockPreviewPrivateExamImport.mockResolvedValue({
      code: 0,
      data: {
        title: 'AI 补全卷',
        courseId: 10,
        duration: 30,
        sourceName: 'answerless.txt',
        sourceFormat: 'TEXT',
        contentHash: 'b'.repeat(64),
        questionCount: 1,
        totalScore: 1,
        requiresAnswerReview: true,
        questions: [
          {
            content: draftQuestion.content,
            questionType: draftQuestion.questionType,
            answer: null,
            analysis: null,
            score: 1,
            answerComplete: false,
            options: draftQuestion.options.map((option) => ({ ...option, correct: false })),
          },
        ],
      },
    })
    mockCreatePrivateExamDraft.mockResolvedValue({ code: 0, data: draft })
    const generated = {
      ...draft,
      status: 'AI_GENERATED',
      questions: [
        {
          ...draftQuestion,
          generationStatus: 'GENERATED',
          aiAnswerLabels: ['A'],
          aiAnalysis: '栈遵循后进先出。',
        },
      ],
    }
    mockGeneratePrivateExamDraftAnswer.mockResolvedValue({ code: 0, data: generated })
    mockReviewPrivateExamDraftQuestion.mockResolvedValue({
      code: 0,
      data: {
        ...generated,
        status: 'READY',
        reviewedQuestionCount: 1,
        questions: [{ ...generated.questions[0], reviewStatus: 'REVIEWED' }],
      },
    })
    mockConfirmPrivateExamDraft.mockResolvedValue({ code: 0, data: { id: 51 } })
    const wrapper = mount(ExamListView, { global: { stubs, directives: { loading: () => undefined } } })
    await flushPromises()
    const vm = wrapper.vm as unknown as {
      importForm: Record<string, unknown>
      previewImport: () => Promise<void>
      createAnswerDraft: () => Promise<void>
      generateDraftAnswer: (questionId: number) => Promise<void>
      reviewDraftQuestion: (questionId: number) => Promise<void>
      confirmDraft: () => Promise<void>
    }
    vm.importForm = {
      title: 'AI 补全卷',
      courseId: 10,
      duration: 30,
      sourceName: 'answerless.txt',
      sourceFormat: 'TEXT',
      content: '无答案结构化题目',
    }

    await vm.previewImport()
    await vm.createAnswerDraft()
    expect(mockConfirmPrivateExamDraft).not.toHaveBeenCalled()
    await vm.generateDraftAnswer(41)
    await vm.reviewDraftQuestion(41)
    expect(mockReviewPrivateExamDraftQuestion).toHaveBeenCalledWith(31, 41, {
      answerLabels: ['A'],
      analysis: '栈遵循后进先出。',
    })
    await vm.confirmDraft()
    expect(mockConfirmPrivateExamDraft).toHaveBeenCalledWith(31)
  })

  it('只把来源已核验的官方试卷标记为官方原题并展示来源', async () => {
    const wrapper = mount(ExamListView, {
      global: {
        stubs,
        directives: { loading: () => undefined },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('官方原题')
    expect(wrapper.text()).toContain('2025 · 全国硕士研究生招生考试')
    expect(wrapper.text()).toContain('来源：教育主管部门公开文件')
    expect(wrapper.text()).toContain('普通练习')
  })

  it('考试模式只创建服务端会话并导航，不预取或缓存试题', async () => {
    const wrapper = mount(ExamListView, {
      global: {
        stubs,
        directives: { loading: () => undefined },
      },
    })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('考试模式'))!
      .trigger('click')
    await flushPromises()

    expect(mockStartExam).toHaveBeenCalledWith(1)
    expect(mockGetPaperDetail).not.toHaveBeenCalled()
    expect(sessionStorage.length).toBe(0)
    expect(mockPush).toHaveBeenCalledWith({ name: 'ExamTake', params: { recordId: '101' } })
  })

  it('分别为进行中、已完成和已超时记录提供正确状态与操作', async () => {
    mockGetPublishedPapers.mockResolvedValue({ code: 0, data: { records: [], total: 0 } })
    mockGetMyExamRecords.mockResolvedValue({
      code: 0,
      data: {
        total: 3,
        records: [
          { id: 11, examTitle: '进行中的试卷', status: 0, startTime: '2026-08-13T10:00:00', totalScore: 100 },
          {
            id: 12,
            examTitle: '已完成的试卷',
            status: 1,
            startTime: '2026-08-12T10:00:00',
            score: 80,
            totalScore: 100,
          },
          { id: 13, examTitle: '超时的试卷', status: 2, startTime: '2026-08-11T10:00:00', score: 0, totalScore: 100 },
        ],
      },
    })

    const wrapper = mount(ExamListView, {
      global: {
        stubs,
        directives: { loading: () => undefined },
      },
    })
    await flushPromises()

    const cards = wrapper.findAll('.record-mobile-card')
    expect(cards).toHaveLength(3)
    expect(cards[0].text()).toContain('进行中')
    expect(cards[0].text()).toContain('继续考试')
    expect(cards[1].text()).toContain('已完成')
    expect(cards[1].text()).toContain('查看结果')
    expect(cards[2].text()).toContain('已超时')
    expect(cards[2].text()).not.toContain('继续考试')

    await cards[0].find('button').trigger('click')
    await cards[1].find('button').trigger('click')
    expect(mockPush).toHaveBeenCalledWith({ name: 'ExamTake', params: { recordId: '11' } })
    expect(mockPush).toHaveBeenCalledWith({ name: 'ExamResult', params: { recordId: '12' } })
  })
})
