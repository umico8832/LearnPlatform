import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import QuestionErrorAnalysisDialog from '@/components/statistics/QuestionErrorAnalysisDialog.vue'

const data = {
  questionId: 1,
  questionContent: '待判分题目',
  questionType: 'SHORT_ANSWER',
  difficulty: null,
  courseName: '数据结构',
  knowledgePointName: '线性表',
  totalAttempts: 3,
  correctCount: 1,
  wrongCount: 1,
  correctRate: 50,
  currentMasteryLevel: null,
  masteryTrend: 'STAGNANT' as const,
  trendDescription: '等待批阅后更新',
  errorPattern: '暂无稳定结论',
  attempts: [
    { recordId: 1, userAnswer: 'A', isCorrect: 1, answerTime: 10, createTime: '2026-10-02T10:00:00' },
    { recordId: 2, userAnswer: 'B', isCorrect: 0, answerTime: 12, createTime: '2026-10-02T11:00:00' },
    { recordId: 3, userAnswer: '说明', isCorrect: null, answerTime: null, createTime: '2026-10-02T12:00:00' },
  ],
}

describe('QuestionErrorAnalysisDialog', () => {
  it('keeps an ungraded attempt distinct from correct and wrong attempts', () => {
    const wrapper = mount(QuestionErrorAnalysisDialog, {
      props: { modelValue: true, loading: false, data },
      global: { stubs: { 'el-dialog': { template: '<section><slot /><slot name="footer" /></section>' } } },
    })

    expect(wrapper.text()).toContain('答对')
    expect(wrapper.text()).toContain('答错')
    expect(wrapper.text()).toContain('待判分')
    expect(wrapper.findAll('.attempt-row')).toHaveLength(3)
  })
})
