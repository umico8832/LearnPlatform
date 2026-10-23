import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AssessmentHistoryDialog from '@/components/course/AssessmentHistoryDialog.vue'
import AssessmentSetupDialog from '@/components/course/AssessmentSetupDialog.vue'
import StageAssessmentDialog from '@/components/course/StageAssessmentDialog.vue'
import type { CourseStageAssessmentSummaryVO, CourseStageAssessmentVO } from '@/api/course'

const stubs = {
  LtSheet: {
    name: 'LtSheet',
    props: ['open', 'title'],
    emits: ['update:open'],
    template:
      '<section :data-open="open"><h2>{{ title }}</h2><button @click="$emit(\'update:open\', false)">关闭浮层</button><slot /></section>',
  },
  LtButton: {
    props: ['disabled', 'type'],
    template: '<button :type="type || \'button\'" :disabled="disabled"><slot /></button>',
  },
}

const summary: CourseStageAssessmentSummaryVO = {
  id: 9,
  selectionStrategy: 'LEARNING_STATE_PRIORITY',
  targetKnowledgePointId: 7,
  targetKnowledgePointName: '栈',
  questionCount: 5,
  correctCount: 4,
  sourceComposition: { officialExamCount: 2, manualCount: 3, userPrivateCount: 0, aiGeneratedCount: 0 },
  startTime: '2026-10-23T12:00:00',
  completeTime: '2026-10-23T12:10:00',
}

const assessment = {
  id: 9,
  courseId: 408,
  status: 'IN_PROGRESS',
  selectionStrategy: 'LEARNING_STATE_PRIORITY',
  targetKnowledgePointId: null,
  targetKnowledgePointName: null,
  questionCount: 2,
  correctCount: null,
  startTime: '2026-10-23T12:00:00',
  completeTime: null,
  sourceComposition: { officialExamCount: 2, manualCount: 0, userPrivateCount: 0, aiGeneratedCount: 0 },
  questions: [
    {
      id: 1,
      questionId: 101,
      sortOrder: 1,
      questionType: 'SINGLE_CHOICE',
      sourceType: 'OFFICIAL',
      sourceCategory: 'OFFICIAL_EXAM',
      originQuestionId: null,
      content: '栈遵循什么顺序？',
      options: [
        { label: 'A', content: '先进先出' },
        { label: 'B', content: '后进先出' },
      ],
      score: 1,
      userAnswer: null,
      correct: null,
      correctAnswer: null,
      analysis: null,
    },
    {
      id: 2,
      questionId: 102,
      sortOrder: 2,
      questionType: 'MULTIPLE_CHOICE',
      sourceType: 'AI_GENERATED',
      sourceCategory: 'AI_GENERATED',
      originQuestionId: 41,
      content: '选择两个答案',
      options: [
        { label: 'A', content: '选项 A' },
        { label: 'B', content: '选项 B' },
      ],
      score: 1,
      userAnswer: null,
      correct: null,
      correctAnswer: null,
      analysis: null,
    },
  ],
} satisfies CourseStageAssessmentVO

describe('阶段测评弹层', () => {
  it('设置范围后开始测评，并在开始期间拒绝关闭请求', async () => {
    const wrapper = mount(AssessmentSetupDialog, {
      props: { visible: true, starting: false, error: '', knowledgePoints: [{ id: 7, title: '栈' }] },
      global: { stubs },
    })
    await wrapper.find('select').setValue('7')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.emitted('start')).toEqual([[7]])

    await wrapper.setProps({ starting: true })
    await wrapper.findComponent({ name: 'LtSheet' }).vm.$emit('update:open', false)
    expect(wrapper.emitted('update:visible')).toBeUndefined()
  })

  it('历史记录使用原生筛选与分页，并在明细读取中禁用操作', async () => {
    const wrapper = mount(AssessmentHistoryDialog, {
      props: {
        visible: true,
        loading: false,
        failed: false,
        records: [summary],
        page: 2,
        pageSize: 10,
        total: 21,
        filterKnowledgePointId: 0,
        knowledgePointOptions: [{ id: 7, title: '栈' }],
        detailError: '',
        detailLoading: false,
      },
      global: { stubs },
    })
    await wrapper.find('select').setValue('7')
    await wrapper.get('button[type="button"]').trigger('click')
    expect(wrapper.emitted('filter-change')).toEqual([[7]])
    expect(wrapper.emitted('open-detail')).toEqual([[9]])
    const pageButtons = wrapper.findAll('nav button')
    await pageButtons[1].trigger('click')
    expect(wrapper.emitted('load')).toEqual([[3]])

    await wrapper.setProps({ detailLoading: true })
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === '正在打开…')
        ?.attributes('disabled'),
    ).toBeDefined()
  })

  it('原生单选和多选更新答案，提交期间拒绝关闭', async () => {
    const wrapper = mount(StageAssessmentDialog, {
      props: {
        visible: true,
        assessment,
        answers: { 1: [], 2: [] },
        submitting: false,
        submitError: '',
        focusQuestionId: null,
        reviewedKnowledgePointIds: [],
      },
      global: { stubs },
    })
    await wrapper.find('input[type="radio"][value="B"]').setValue()
    await wrapper.find('input[type="checkbox"][value="A"]').setValue()
    expect(wrapper.props('answers')).toEqual({ 1: ['B'], 2: ['A'] })
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '提交测评')!
      .trigger('click')
    expect(wrapper.emitted('submit')).toHaveLength(1)

    await wrapper.setProps({ submitting: true })
    await wrapper.findComponent({ name: 'LtSheet' }).vm.$emit('update:open', false)
    expect(wrapper.emitted('update:visible')).toBeUndefined()
  })
})
