import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import LearningDiagnosisRecommendations from '@/components/statistics/LearningDiagnosisRecommendations.vue'
import LearningDiagnosisSummary from '@/components/statistics/LearningDiagnosisSummary.vue'
import type { LearningDiagnosis } from '@/api/statistics'

const baseDiagnosis: LearningDiagnosis = {
  totalPractice: 1,
  overallCorrectRate: 100,
  activeDaysLast30: 1,
  streakDays: 1,
  weakPoints: [
    {
      knowledgePointId: 4,
      knowledgePointName: '递归',
      courseId: 6,
      courseName: '数据结构',
      correctRate: 100,
      totalAttempts: 1,
      wrongCount: 3,
      masteryStatus: 'INSUFFICIENT_DATA',
      priorityScore: 1,
      diagnosis: '仅有一次已判分记录，暂不判断掌握程度。',
    },
    {
      knowledgePointId: 5,
      knowledgePointName: '图',
      courseId: 6,
      courseName: '数据结构',
      correctRate: -1,
      totalAttempts: 0,
      wrongCount: 0,
      masteryStatus: 'NOT_STARTED',
      priorityScore: 1,
      diagnosis: '尚未练习。',
    },
  ],
  courseMasteries: [],
  errorPatterns: {
    topErrorCourses: [],
    masteryDistribution: {},
    repeatedErrorCount: 0,
    recentNewWrongCount: 0,
    questionTypeDistribution: {},
    difficultyDistribution: {},
    knowledgePointErrors: [],
    repeatedErrors: [],
    weeklyErrorTrend: [],
  },
  learningHabit: {
    avgDailyPractice: 1,
    preferredQuestionType: '单选题',
    preferredCourse: '数据结构',
    weeklyTrend: [],
    frequencyLevel: 'INSUFFICIENT_DATA',
    frequencyDescription: '近30天有1天留下练习记录，样本不足，继续积累记录后再评估学习节奏。',
  },
  dailyRecommendations: [],
  dailyAdvice: '从一题开始。',
}

const noRecordDiagnosis: LearningDiagnosis = {
  ...baseDiagnosis,
  weakPoints: [baseDiagnosis.weakPoints[1]],
}

const stubs = {
  'el-card': { template: '<section><header><slot name="header" /></header><slot /></section>' },
  'el-table': { props: ['data'], template: '<div><slot /></div>' },
  'el-table-column': {
    props: ['label'],
    template: '<div><span>{{ label }}</span><slot :row="$parent.data?.[0]" /></div>',
  },
  'el-tag': { props: ['type'], template: '<span :data-type="type"><slot /></span>' },
  'el-progress': { props: ['percentage'], template: '<span>进度 {{ percentage }}%</span>' },
  'el-button': { template: '<button><slot /></button>' },
  'el-alert': { template: '<aside />' },
  'el-row': { template: '<div><slot /></div>' },
  'el-col': { template: '<div><slot /></div>' },
  'el-descriptions': { template: '<dl><slot /></dl>' },
  'el-descriptions-item': { props: ['label'], template: '<div><dt>{{ label }}</dt><dd><slot /></dd></div>' },
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
  MarkdownRenderer: { template: '<div />' },
}

describe('Learning diagnosis trustworthy display', () => {
  it('offers a course entry instead of starting an empty recommendation session', () => {
    const wrapper = mount(LearningDiagnosisRecommendations, {
      props: { courseMasteries: [], recommendations: [], startingPractice: false, practiceError: '' },
      global: { stubs },
    })

    expect(wrapper.text()).toContain('暂时没有推荐题目')
    expect(wrapper.text()).not.toContain('开始练习')
    expect(wrapper.get('a').attributes('href')).toBe('/courses')
    expect(wrapper.emitted('start-recommend-practice')).toBeUndefined()
  })

  it('labels a single graded attempt as insufficient data instead of weak mastery', () => {
    const wrapper = mount(LearningDiagnosisSummary, {
      props: {
        data: baseDiagnosis,
        aiAdviceLoading: false,
        aiAdviceStreaming: false,
        aiAdviceContent: '',
        aiAdviceError: '',
      },
      global: { stubs },
    })

    expect(wrapper.text()).toContain('下一步关注的知识点')
    expect(wrapper.text()).toContain('诊断基于平台已判分的练习记录；记录较少时仅展示事实，不判断掌握程度。')
    expect(wrapper.text()).toContain('记录较少')
    expect(wrapper.text()).toContain('累计错次')
    expect(wrapper.text()).toContain('近30天有1天留下练习记录，样本不足，继续积累记录后再评估学习节奏。')
    expect(wrapper.find('.frequency-description').exists()).toBe(true)
    expect(wrapper.findAll('[data-type="info"]')).toHaveLength(3)
    expect(wrapper.text()).toContain('已判分作答')
    expect(wrapper.text()).toContain('作答正确率')
    expect(wrapper.text()).toContain('作答次数')
    expect(wrapper.text()).toContain('日均作答')
    expect(wrapper.text()).toContain('1 次')
  })

  it('shows no-record text instead of a zero-percent progress bar for unpracticed rows', () => {
    const wrapper = mount(LearningDiagnosisSummary, {
      props: {
        data: noRecordDiagnosis,
        aiAdviceLoading: false,
        aiAdviceStreaming: false,
        aiAdviceContent: '',
        aiAdviceError: '',
      },
      global: { stubs },
    })

    expect(wrapper.text()).toContain('暂无记录')
    expect(wrapper.text()).not.toContain('进度 0%')
  })

  it('does not portray a course with zero attempts as a zero-percent result', () => {
    const wrapper = mount(LearningDiagnosisRecommendations, {
      props: {
        courseMasteries: [
          {
            courseId: 6,
            courseName: '数据结构',
            correctRate: 0,
            totalAttempts: 0,
            wrongCount: 0,
            knowledgePointCount: 8,
            weakPointCount: 0,
          },
        ],
        recommendations: [],
        startingPractice: false,
        practiceError: '',
      },
      global: { stubs },
    })

    expect(wrapper.text()).toContain('暂无记录')
    expect(wrapper.text()).not.toContain('进度 0%')
  })
})
