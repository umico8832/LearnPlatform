import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockList, mockGrade, mockSuccess } = vi.hoisted(() => ({
  mockList: vi.fn(),
  mockGrade: vi.fn(),
  mockSuccess: vi.fn(),
}))

vi.mock('@/api/exam', () => ({
  getPendingSubjectiveReviews: (...args: unknown[]) => mockList(...args),
  gradeSubjectiveAnswer: (...args: unknown[]) => mockGrade(...args),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: mockSuccess, error: vi.fn() },
}))

import SubjectiveReviewView from '@/views/admin/SubjectiveReviewView.vue'

const stubs = {
  'el-card': { template: '<section><slot /></section>' },
  'el-icon': { template: '<i><slot /></i>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-alert': { template: '<div>{{ title }}</div>', props: ['title'] },
  'el-empty': { template: '<div />' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-button': {
    template: '<button @click="$emit(\'click\')"><slot /></button>',
    emits: ['click'],
  },
  'el-drawer': {
    template: '<aside v-if="modelValue"><slot /></aside>',
    props: ['modelValue'],
  },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<label><slot /></label>' },
  'el-input': { template: '<textarea />' },
  'el-input-number': { template: '<input type="number" />' },
}

describe('SubjectiveReviewView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockList.mockResolvedValue({
      code: 0,
      data: [
        {
          answerId: 9,
          examRecordId: 7,
          userId: 3,
          examTitle: '2026 年 408 真题·数据结构部分',
          displayNumber: '第41题',
          content: '算法题干',
          userAnswer: '考生答案',
          fullScore: 13,
          gradingStatus: 'PENDING',
          submittedAt: '2026-08-15T01:00:00',
          gradingPoints: [
            {
              pointKey: 'idea',
              title: '算法思想',
              description: '维护最小差值',
              referenceAnswer: '中序遍历',
              maxScore: 4,
              sortOrder: 1,
            },
            {
              pointKey: 'code',
              title: '算法实现',
              description: '完整实现',
              referenceAnswer: '安全递归',
              maxScore: 9,
              sortOrder: 2,
            },
          ],
        },
      ],
    })
    mockGrade.mockResolvedValue({ code: 0, data: { answerId: 9 } })
  })

  it('加载待批阅答案并逐评分点提交', async () => {
    const wrapper = mount(SubjectiveReviewView, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()

    expect(mockList).toHaveBeenCalled()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('开始批阅'))!
      .trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('考生答案')
    expect(wrapper.text()).toContain('参考：中序遍历')

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('确认并完成批阅'))!
      .trigger('click')
    await flushPromises()

    expect(mockGrade).toHaveBeenCalledWith(9, {
      points: [
        { pointKey: 'idea', awardedScore: 0, comment: undefined },
        { pointKey: 'code', awardedScore: 0, comment: undefined },
      ],
      reviewComment: undefined,
    })
    expect(mockSuccess).toHaveBeenCalledWith('批阅已保存，考试成绩已重新计算')
  })
})
