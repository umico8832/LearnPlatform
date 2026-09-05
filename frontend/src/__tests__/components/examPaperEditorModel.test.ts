import { describe, expect, it } from 'vitest'
import type { ExamPaperVO } from '@/api/exam'
import {
  createEmptyExamPaperForm,
  createExamPaperForm,
  createExamPaperRequest,
  validateExamPaperForm,
} from '@/admin/views/exam/examPaperEditorModel'

const officialPaper: ExamPaperVO = {
  id: 8,
  title: '2025 年示例考试',
  description: '说明',
  courseId: 3,
  courseName: '数据结构',
  totalScore: 10,
  duration: 90,
  questionCount: 1,
  status: 1,
  createBy: 1,
  ownerUserId: null,
  visibility: 'PUBLIC',
  paperType: 'OFFICIAL_EXAM',
  examName: '示例考试',
  examYear: 2025,
  sourceReference: '公开试题册第 10 页',
  sourceVerified: true,
  importStatus: null,
  createTime: '2025-01-01T00:00:00',
  questions: [
    {
      questionId: 12,
      sortOrder: 0,
      score: 10,
      content: '示例题干',
      questionType: 'SHORT_ANSWER',
      sectionTitle: '第一部分',
      majorQuestionNumber: '一',
      minorQuestionNumber: '1',
      subquestionNumber: null,
      displayNumber: '一、1',
      options: [],
    },
  ],
}

describe('examPaperEditorModel', () => {
  it('maps persisted detail into an editable form and back into a request', () => {
    const form = createExamPaperForm(officialPaper)

    expect(form.questions[0]).toEqual(
      expect.objectContaining({ questionId: 12, displayNumber: '一、1', subquestionNumber: '' }),
    )
    expect(createExamPaperRequest(form)).toEqual(
      expect.objectContaining({
        title: officialPaper.title,
        paperType: 'OFFICIAL_EXAM',
        questions: [expect.objectContaining({ questionId: 12, displayNumber: '一、1', subquestionNumber: undefined })],
      }),
    )
  })

  it('enforces provenance and display numbers only when publishing an official paper', () => {
    const form = createEmptyExamPaperForm()
    form.title = '官方试卷'
    form.paperType = 'OFFICIAL_EXAM'
    form.status = 1

    expect(validateExamPaperForm(form)).toBe('发布官方试卷前请填写考试名称、年份和来源')

    form.examName = '示例考试'
    form.examYear = 2025
    form.sourceReference = '公开来源'
    expect(validateExamPaperForm(form)).toBe('发布官方试卷前必须完成人工来源核验')

    form.sourceVerified = true
    form.questions.push({
      questionId: 1,
      sortOrder: 0,
      score: 5,
      sectionTitle: '',
      majorQuestionNumber: '',
      minorQuestionNumber: '',
      subquestionNumber: '',
      displayNumber: '',
    })
    expect(validateExamPaperForm(form)).toBe('官方试卷每道题都必须填写展示题号')

    form.questions[0]!.displayNumber = '1'
    expect(validateExamPaperForm(form)).toBeNull()
  })
})
