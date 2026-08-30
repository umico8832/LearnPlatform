import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import type { PrivateExamDraft } from '@/api/exam'

const { mockGenerateDraftAnswer, mockReviewDraftQuestion, mockDownloadDraftSource } = vi.hoisted(() => ({
  mockGenerateDraftAnswer: vi.fn(),
  mockReviewDraftQuestion: vi.fn(),
  mockDownloadDraftSource: vi.fn(),
}))

vi.mock('@/api/exam', () => ({
  generatePrivateExamDraftAnswer: (...args: unknown[]) => mockGenerateDraftAnswer(...args),
  reviewPrivateExamDraftQuestion: (...args: unknown[]) => mockReviewDraftQuestion(...args),
  downloadPrivateExamDraftSourceFile: (...args: unknown[]) => mockDownloadDraftSource(...args),
}))

import PrivateExamDraftReview from '@/components/exam/PrivateExamDraftReview.vue'

const stubs = {
  'el-alert': { template: '<p />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-button': {
    template: '<button @click="$emit(\'click\')"><slot /></button>',
    emits: ['click'],
  },
  'el-form-item': { template: '<label><slot /></label>' },
  'el-checkbox-group': { template: '<div><slot /></div>' },
  'el-checkbox': { template: '<span><slot /></span>' },
  'el-input': { template: '<textarea />' },
}

function draftFixture(overrides: Partial<PrivateExamDraft['questions'][number]> = {}): PrivateExamDraft {
  return {
    id: 31,
    title: 'AI 补全卷',
    courseId: 10,
    duration: 30,
    status: 'AI_GENERATED',
    confirmedPaperId: null,
    sourceName: 'paper.docx',
    sourceFormat: 'DOCX',
    originalFileAvailable: true,
    reviewedQuestionCount: 0,
    questionCount: 1,
    createTime: '2026-08-30T10:00:00',
    questions: [
      {
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
        aiAnswerLabels: ['A'],
        aiAnalysis: '栈遵循后进先出。',
        generationStatus: 'GENERATED',
        finalAnswerLabels: [],
        finalAnalysis: null,
        reviewStatus: 'PENDING',
        ...overrides,
      },
    ],
  }
}

describe('PrivateExamDraftReview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('生成 AI 建议后只向父组件返回更新草稿', async () => {
    const pending = draftFixture({
      aiAnswerLabels: [],
      aiAnalysis: null,
      generationStatus: 'PENDING',
    })
    const generated = draftFixture()
    mockGenerateDraftAnswer.mockResolvedValue({ code: 0, data: generated })
    const wrapper = mount(PrivateExamDraftReview, {
      props: { draft: pending },
      global: { stubs },
    })
    const vm = wrapper.vm as unknown as { generateDraftAnswer: (questionId: number) => Promise<void> }

    await vm.generateDraftAnswer(41)

    expect(mockGenerateDraftAnswer).toHaveBeenCalledWith(31, 41)
    expect(wrapper.emitted('updated')).toEqual([[generated]])
    expect(mockReviewDraftQuestion).not.toHaveBeenCalled()
  })

  it('使用 AI 建议初始化人工表单并显式提交复核', async () => {
    const draft = draftFixture()
    const reviewed = draftFixture({
      finalAnswerLabels: ['A'],
      finalAnalysis: '人工确认：栈遵循后进先出。',
      reviewStatus: 'REVIEWED',
    })
    mockReviewDraftQuestion.mockResolvedValue({ code: 0, data: reviewed })
    const wrapper = mount(PrivateExamDraftReview, {
      props: { draft },
      global: { stubs },
    })
    const vm = wrapper.vm as unknown as { reviewDraftQuestion: (questionId: number) => Promise<void> }

    await vm.reviewDraftQuestion(41)

    expect(mockReviewDraftQuestion).toHaveBeenCalledWith(31, 41, {
      answerLabels: ['A'],
      analysis: '栈遵循后进先出。',
    })
    expect(wrapper.emitted('updated')).toEqual([[reviewed]])
  })
})
