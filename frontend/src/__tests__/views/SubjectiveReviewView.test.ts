import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const auth = vi.hoisted(() => ({ version: 1, listeners: [] as Array<() => void> }))
const { mockList, mockGrade, mockSuccess } = vi.hoisted(() => ({
  mockList: vi.fn(),
  mockGrade: vi.fn(),
  mockSuccess: vi.fn(),
}))

vi.mock('@/api/exam', () => ({
  getPendingSubjectiveReviews: (...args: unknown[]) => mockList(...args),
  gradeSubjectiveAnswer: (...args: unknown[]) => mockGrade(...args),
}))

vi.mock('@/utils/auth', () => ({
  getAuthSessionVersion: () => auth.version,
  onAuthSessionChange: (listener: () => void) => {
    auth.listeners.push(listener)
    return () => undefined
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: mockSuccess, error: vi.fn() },
}))

import SubjectiveReviewView from '@/admin/views/SubjectiveReviewView.vue'

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
    auth.version = 1
    auth.listeners.length = 0
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

    expect(mockGrade).toHaveBeenCalledWith(
      9,
      {
        points: [
          { pointKey: 'idea', awardedScore: 0, comment: undefined },
          { pointKey: 'code', awardedScore: 0, comment: undefined },
        ],
        reviewComment: undefined,
      },
      { errorDisplay: 'inline' },
    )
    expect(mockSuccess).toHaveBeenCalledWith('批阅已保存，考试成绩已重新计算')
  })

  it('keeps loading and failed queue reads out of empty and zero summary states', async () => {
    let reject!: (reason?: unknown) => void
    mockList.mockReturnValueOnce(
      new Promise((_, fail) => {
        reject = fail
      }),
    )
    const wrapper = mount(SubjectiveReviewView, { global: { stubs, directives: { loading: () => undefined } } })
    await flushPromises()
    expect(wrapper.find('.admin-summary-grid').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('当前没有待批阅答案')

    reject(new Error('offline'))
    await flushPromises()
    expect(wrapper.text()).toContain('待批阅队列暂时无法读取')
    expect(wrapper.find('.admin-summary-grid').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('当前没有待批阅答案')
  })

  it('discards a late pending queue response after an account session changes', async () => {
    let resolve!: (value: unknown) => void
    mockList.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = mount(SubjectiveReviewView, { global: { stubs, directives: { loading: () => undefined } } })
    auth.version += 1
    auth.listeners.forEach((listener) => listener())
    resolve({ code: 0, data: [{ answerId: 99, examTitle: '旧账号队列', gradingPoints: [] }] })
    await flushPromises()
    expect(wrapper.text()).not.toContain('旧账号队列')
    expect(wrapper.text()).not.toContain('当前没有待批阅答案')
  })

  it('does not close or announce a late grading response after the session changes', async () => {
    let resolve!: (value: { code: number; data: unknown }) => void
    mockGrade.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = mount(SubjectiveReviewView, { global: { stubs, directives: { loading: () => undefined } } })
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('开始批阅'))!
      .trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('确认并完成批阅'))!
      .trigger('click')
    expect(mockGrade).toHaveBeenCalledTimes(1)
    auth.version += 1
    auth.listeners.forEach((listener) => listener())
    resolve({ code: 0, data: { answerId: 9 } })
    await flushPromises()
    expect(mockSuccess).not.toHaveBeenCalled()
    expect(wrapper.find('aside').exists()).toBe(false)
  })
})
