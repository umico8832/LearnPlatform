import { beforeEach, describe, expect, it } from 'vitest'
import { clearExamDraft, loadExamDraft, saveExamDraft } from '@/views/exam/examDraft'

describe('examDraft', () => {
  beforeEach(() => sessionStorage.clear())

  it('restores a valid draft only for the same user and active record', () => {
    expect(saveExamDraft({ userId: 7, recordId: 101, answers: { 11: 'A', 12: 'A,C' }, currentQuestionId: 12 })).toBe(
      true,
    )

    expect(loadExamDraft({ userId: 7, recordId: 101, questionIds: [11, 12] })).toEqual({
      answers: { 11: 'A', 12: 'A,C' },
      currentQuestionId: 12,
    })
    expect(loadExamDraft({ userId: 8, recordId: 101, questionIds: [11, 12] })).toBeNull()
    expect(loadExamDraft({ userId: 7, recordId: 102, questionIds: [11, 12] })).toBeNull()
  })

  it('drops stale question IDs and a stale position rather than restoring them into a changed paper', () => {
    expect(saveExamDraft({ userId: 7, recordId: 101, answers: { 11: 'A', 99: 'B' }, currentQuestionId: 99 })).toBe(true)

    expect(loadExamDraft({ userId: 7, recordId: 101, questionIds: [11, 12] })).toEqual({
      answers: { 11: 'A' },
      currentQuestionId: null,
    })
  })

  it('clears the exact scoped draft after an authoritative finish', () => {
    saveExamDraft({ userId: 7, recordId: 101, answers: { 11: 'A' }, currentQuestionId: 11 })
    clearExamDraft(7, 101)
    expect(loadExamDraft({ userId: 7, recordId: 101, questionIds: [11] })).toBeNull()
  })
})
