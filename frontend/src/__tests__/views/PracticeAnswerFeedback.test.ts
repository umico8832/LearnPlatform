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
})
