import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PracticeAnswerFeedback from '@/views/practice/PracticeAnswerFeedback.vue'

describe('PracticeAnswerFeedback', () => {
  it('renders server analysis through the safe markdown renderer', () => {
    const wrapper = mount(PracticeAnswerFeedback, {
      props: {
        result: {
          recordId: 1,
          questionId: 1,
          userAnswer: 'A',
          correct: false,
          correctAnswer: 'B',
          analysis: '使用 `extends`，而不是 `implements`。',
          score: 0,
        },
      },
    })

    expect(wrapper.find('.practice-answer-feedback__analysis code').text()).toBe('extends')
    expect(wrapper.text()).toContain('implements')
  })

  it('announces only the short grading status instead of the complete feedback card', () => {
    const wrapper = mount(PracticeAnswerFeedback, {
      props: {
        result: {
          recordId: 1,
          questionId: 1,
          userAnswer: 'A',
          correct: false,
          correctAnswer: 'B',
          analysis: '这是较长的解析内容。',
          score: 0,
        },
      },
    })

    expect(wrapper.get('[data-testid="practice-feedback"]').attributes('aria-live')).toBeUndefined()
    const status = wrapper.get('[data-testid="practice-feedback-status"]')
    expect(status.attributes('role')).toBe('status')
    expect(status.attributes('aria-atomic')).toBe('true')
    expect(status.text()).toBe('本题回答错误。')
  })
})
