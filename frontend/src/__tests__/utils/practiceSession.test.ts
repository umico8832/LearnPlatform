import { beforeEach, describe, expect, it } from 'vitest'
import { loadPracticeSession, savePracticeSession } from '@/utils/practiceSession'
import { removeToken, setToken } from '@/utils/auth'
import type { PracticeQuestionVO } from '@/api/practice'

const questions: PracticeQuestionVO[] = [
  {
    id: 10,
    content: 'private question',
    questionType: 'FILL_BLANK',
    courseId: 1,
    courseName: 'course',
    difficulty: 1,
    score: 1,
    tags: '',
    options: [],
    knowledgePointIds: [],
    knowledgePointNames: [],
  },
]

describe('practice session ownership', () => {
  beforeEach(() => sessionStorage.clear())

  it('restores the same user after refresh and rejects another user', () => {
    savePracticeSession(7, questions, 'favorite')
    expect(loadPracticeSession(7)).toEqual({ questions, mode: 'favorite' })
    expect(loadPracticeSession(8)).toBeNull()
    expect(sessionStorage.getItem('practice_questions')).toBeNull()
  })

  it('clears malformed caches and does not create anonymous sessions', () => {
    expect(savePracticeSession(undefined, questions)).toBe(false)
    sessionStorage.setItem('practice_user_id', '7')
    sessionStorage.setItem('practice_questions', '{broken')
    expect(loadPracticeSession(7)).toBeNull()
  })

  it('clears private questions on login expiry and on replacement login', () => {
    setToken('test-only-a')
    savePracticeSession(7, questions)
    removeToken()
    expect(loadPracticeSession(7)).toBeNull()
    savePracticeSession(7, questions)
    setToken('test-only-b')
    expect(loadPracticeSession(7)).toBeNull()
  })
})
