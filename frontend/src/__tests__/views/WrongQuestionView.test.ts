import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'

const {
  mockGetWrongQuestions,
  mockGetWrongQuestionStats,
  mockGetWrongQuestionPractice,
  mockPush,
  mockUpdateMastery,
  mockRemoveWrong,
} = vi.hoisted(() => ({
  mockGetWrongQuestions: vi.fn(),
  mockGetWrongQuestionStats: vi.fn(),
  mockGetWrongQuestionPractice: vi.fn(),
  mockPush: vi.fn(),
  mockUpdateMastery: vi.fn(),
  mockRemoveWrong: vi.fn(),
}))

vi.mock('@/api/wrongQuestion', () => ({
  getWrongQuestions: (...args: unknown[]) => mockGetWrongQuestions(...args),
  getWrongQuestionStats: (...args: unknown[]) => mockGetWrongQuestionStats(...args),
  updateMasteryLevel: (...args: unknown[]) => mockUpdateMastery(...args),
  removeWrongQuestion: (...args: unknown[]) => mockRemoveWrong(...args),
}))
vi.mock('@/api/practice', () => ({
  getWrongQuestionPractice: (...args: unknown[]) => mockGetWrongQuestionPractice(...args),
}))
vi.mock('@/api/statistics', () => ({ getSimilarQuestions: vi.fn() }))
let routeQuery: Record<string, string> = { courseId: '408', questionId: '22' }

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRoute: () => ({ query: routeQuery }),
  useRouter: () => ({ push: mockPush, replace: mockPush }),
}))

import WrongQuestionView from '@/views/practice/WrongQuestionView.vue'
enableAutoUnmount(afterEach)

describe('WrongQuestionView course target', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useUserStore().setLoginInfo('test-token', {
      id: 7,
      username: 'learner',
      nickname: 'Learner',
      avatar: null,
      role: 'USER',
    })
    vi.clearAllMocks()
    routeQuery = { courseId: '408', questionId: '22' }
    mockGetWrongQuestionStats.mockResolvedValue({
      code: 0,
      data: { total: 1, unmastered: 1, partial: 0, mastered: 0, courseWrongCount: {} },
    })
    mockGetWrongQuestions.mockResolvedValue({
      code: 0,
      data: { records: [], total: 0 },
    })
  })

  it('刷新列表不应让在途掌握更新丢失结果或永久忙碌', async () => {
    let finish!: (value: unknown) => void
    mockUpdateMastery.mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve
      }),
    )
    const wrapper = mount(WrongQuestionView, {
      global: { stubs: { AiQuestionAssistant: true, SimilarQuestionsDialog: true } },
    })
    await flushPromises()
    const vm = wrapper.vm as unknown as {
      handleMasteryChange: (id: number, level: number) => Promise<void>
      loadRecords: () => Promise<void>
      updatingIds: Set<number>
      masteryErrors: Record<number, { message: string }>
    }
    const updating = vm.handleMasteryChange(81, 1)
    await vm.loadRecords()
    finish({ code: 1, message: '本次更新未完成' })
    await updating
    expect(vm.updatingIds.has(81)).toBe(false)
    expect(vm.masteryErrors[81]?.message).toBe('本次更新未完成')
  })

  it('筛选刷新不能取消独立统计读取的完成状态', async () => {
    let finish!: (value: unknown) => void
    mockGetWrongQuestionStats.mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve
      }),
    )
    const wrapper = mount(WrongQuestionView, {
      global: { stubs: { AiQuestionAssistant: true, SimilarQuestionsDialog: true } },
    })
    await flushPromises()
    const vm = wrapper.vm as unknown as {
      loadRecords: () => Promise<void>
      statsLoading: boolean
      stats: { total: number } | null
    }
    await vm.loadRecords()
    finish({ code: 0, data: { total: 4, unmastered: 4, partial: 0, mastered: 0, courseWrongCount: {} } })
    await flushPromises()
    expect(vm.statsLoading).toBe(false)
    expect(vm.stats?.total).toBe(4)
  })

  it('从课程总览进入时在服务端分页前限定课程和目标题目', async () => {
    mount(WrongQuestionView, {
      global: {
        stubs: { AiQuestionAssistant: true, QuestionLearningAsset: true },
        directives: { loading: () => undefined },
      },
    })
    await flushPromises()

    expect(mockGetWrongQuestions).toHaveBeenCalledWith(
      {
        pageNum: 1,
        pageSize: 10,
        courseId: 408,
        questionId: 22,
      },
      { errorDisplay: 'inline' },
    )
  })

  it('启动错题练习时把列表的课程、题目和知识点范围传给服务端', async () => {
    routeQuery = { courseId: '408', questionId: '22', knowledgePointId: '31', knowledgePointName: '栈' }
    mockGetWrongQuestionPractice.mockResolvedValue({ code: 0, data: [{ id: 22 }] })
    const wrapper = mount(WrongQuestionView, {
      global: {
        stubs: {
          AiQuestionAssistant: true,
          SimilarQuestionsDialog: true,
          'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
        },
        directives: { loading: () => undefined },
      },
    })
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('练习当前范围'))!
      .trigger('click')
    await flushPromises()

    expect(mockGetWrongQuestionPractice).toHaveBeenCalledWith(
      {
        count: 10,
        courseId: 408,
        questionId: 22,
        knowledgePointId: 31,
      },
      { errorDisplay: 'inline' },
    )
  })

  it('从测评复盘进入时按知识点筛选并展示可清除的筛选标记', async () => {
    routeQuery = { courseId: '408', knowledgePointId: '31', knowledgePointName: '栈' }
    const wrapper = mount(WrongQuestionView, {
      global: {
        stubs: { AiQuestionAssistant: true, QuestionLearningAsset: true },
        directives: { loading: () => undefined },
      },
    })
    await flushPromises()

    expect(mockGetWrongQuestions).toHaveBeenCalledWith(
      {
        pageNum: 1,
        pageSize: 10,
        courseId: 408,
        knowledgePointId: 31,
      },
      { errorDisplay: 'inline' },
    )
    expect(wrapper.text()).toContain('知识点：栈')
  })

  it('keeps a mastery update failure on its record with a retry instead of a toast', async () => {
    mockGetWrongQuestions.mockResolvedValueOnce({
      code: 0,
      data: {
        records: [
          {
            id: 81,
            questionId: 22,
            questionContent: '失败时仍显示当前错题',
            questionType: 'SINGLE_CHOICE',
            courseId: 408,
            courseName: '数据结构',
            difficulty: 2,
            wrongCount: 1,
            masteryLevel: 0,
            lastWrongAnswer: 'A',
            createTime: '',
            updateTime: '',
          },
        ],
        total: 1,
      },
    })
    mockUpdateMastery.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mount(WrongQuestionView, {
      global: { stubs: { AiQuestionAssistant: true, SimilarQuestionsDialog: true } },
    })
    await flushPromises()
    const vm = wrapper.vm as unknown as { handleMasteryChange: (id: number, level: number) => Promise<void> }

    await vm.handleMasteryChange(81, 1)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('更新掌握程度失败')
    expect(wrapper.get('[role="alert"] button').text()).toBe('重试')
  })

  it('keeps a failed removal beside its record and can retry it', async () => {
    mockGetWrongQuestions.mockResolvedValueOnce({
      code: 0,
      data: {
        records: [
          {
            id: 82,
            questionId: 22,
            questionContent: '移出失败后仍应保留的错题',
            questionType: 'SINGLE_CHOICE',
            courseId: 408,
            courseName: '数据结构',
            difficulty: 2,
            wrongCount: 1,
            masteryLevel: 0,
            lastWrongAnswer: 'A',
            createTime: '',
            updateTime: '',
          },
        ],
        total: 1,
      },
    })
    mockRemoveWrong.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ code: 0, data: null })
    const wrapper = mount(WrongQuestionView, {
      global: { stubs: { AiQuestionAssistant: true, SimilarQuestionsDialog: true } },
    })
    await flushPromises()
    const vm = wrapper.vm as unknown as { handleRemove: (id: number) => Promise<void> }

    await vm.handleRemove(82)
    await flushPromises()
    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toContain('移出错题失败')
    await alert.get('button').trigger('click')
    await flushPromises()

    expect(mockRemoveWrong).toHaveBeenCalledTimes(2)
  })

  it('shows a failed range practice request beside its starting action and retries with the same scope', async () => {
    mockGetWrongQuestionPractice.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({
      code: 0,
      data: [{ id: 22 }],
    })
    const wrapper = mount(WrongQuestionView, {
      global: { stubs: { AiQuestionAssistant: true, SimilarQuestionsDialog: true } },
    })
    await flushPromises()
    const vm = wrapper.vm as unknown as { handleStartWrongPractice: () => Promise<void> }

    await vm.handleStartWrongPractice()
    await flushPromises()
    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toContain('获取错题重练题目失败')
    await alert.get('button').trigger('click')
    await flushPromises()

    expect(mockGetWrongQuestionPractice).toHaveBeenCalledTimes(2)
  })
})
