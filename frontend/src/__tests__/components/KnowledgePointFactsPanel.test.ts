import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import KnowledgePointFactsPanel from '@/components/course/KnowledgePointFactsPanel.vue'

const { mockGetFacts } = vi.hoisted(() => ({ mockGetFacts: vi.fn() }))

vi.mock('@/api/course', () => ({
  getCourseKnowledgePointFacts: (...args: unknown[]) => mockGetFacts(...args),
}))

const stubs = {
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-icon': { template: '<span><slot /></span>' },
  'el-tooltip': {
    props: ['content'],
    template: '<span data-test="facts-help" :data-content="content"><slot /></span>',
  },
  'el-tag': { template: '<span><slot /></span>' },
  'el-pagination': {
    template: '<nav><button @click="$emit(\'current-change\', 2)">下一页</button></nav>',
    emits: ['current-change'],
  },
}

const currentFact = {
  knowledgePointId: 31,
  knowledgePointName: '栈',
  available: true,
  tutorAvailable: true,
  answeredCount: 6,
  correctCount: 4,
  unresolvedWrongCount: 2,
  dueReviewCount: 1,
}

describe('KnowledgePointFactsPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetFacts.mockResolvedValue({ data: { records: [currentFact], total: 11, current: 1, size: 10 } })
  })

  it('分页展示事实计数，并通过已有知识点入口发出导航事件', async () => {
    const wrapper = mount(KnowledgePointFactsPanel, { props: { courseId: 408, refreshKey: 0 }, global: { stubs } })
    await flushPromises()

    expect(mockGetFacts).toHaveBeenCalledWith(408, 1, 10)
    expect(wrapper.text()).toContain('作答次数')
    expect(wrapper.get('[data-test="facts-help"]').attributes('data-content')).toContain('作答与答对按次数统计')
    expect(wrapper.get('[data-test="facts-help"]').attributes('data-content')).toContain('错题与复习按题目统计')
    expect(wrapper.text()).toContain('6')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '复习')!
      .trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '错题')!
      .trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '进入教学')!
      .trigger('click')

    expect(wrapper.emitted('open-review')).toEqual([[31, '栈']])
    expect(wrapper.emitted('open-wrong-questions')).toEqual([[31, '栈']])
    expect(wrapper.emitted('open-tutor')).toEqual([[31]])

    await wrapper.find('nav button').trigger('click')
    expect(mockGetFacts).toHaveBeenLastCalledWith(408, 2, 10)
  })

  it('历史与未关联行不提供伪精确导航，只提供课程题库入口', async () => {
    mockGetFacts.mockResolvedValue({
      data: {
        records: [
          {
            ...currentFact,
            knowledgePointId: 77,
            knowledgePointName: '已删除知识点',
            available: false,
            tutorAvailable: false,
          },
          {
            ...currentFact,
            knowledgePointId: null,
            knowledgePointName: '未关联知识点',
            available: false,
            tutorAvailable: false,
          },
        ],
        total: 2,
        current: 1,
        size: 10,
      },
    })
    const wrapper = mount(KnowledgePointFactsPanel, { props: { courseId: 408, refreshKey: 0 }, global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('历史知识点记录')
    expect(wrapper.text()).toContain('未关联知识点的课程记录')
    expect(wrapper.findAll('button').map((button) => button.text())).not.toContain('进入教学')
    expect(wrapper.findAll('button').map((button) => button.text())).not.toContain('复习')
    expect(wrapper.findAll('button').map((button) => button.text())).not.toContain('错题')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '查看课程题库')!
      .trigger('click')
    expect(wrapper.emitted('open-question-bank')).toHaveLength(1)
  })

  it('失败后可重试', async () => {
    mockGetFacts.mockRejectedValueOnce(new Error('network'))
    const wrapper = mount(KnowledgePointFactsPanel, { props: { courseId: 408, refreshKey: 0 }, global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('暂时无法读取知识点学习事实')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '重新加载')!
      .trigger('click')
    await flushPromises()
    expect(mockGetFacts).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('栈')
  })

  it('刷新键变化时重新读取当前分页', async () => {
    const wrapper = mount(KnowledgePointFactsPanel, { props: { courseId: 408, refreshKey: 0 }, global: { stubs } })
    await flushPromises()

    await wrapper.setProps({ refreshKey: 1 })
    await flushPromises()

    expect(mockGetFacts).toHaveBeenCalledTimes(2)
    expect(mockGetFacts).toHaveBeenLastCalledWith(408, 1, 10)
  })

  it('当前页在数据减少后为空时回到最后一页', async () => {
    const wrapper = mount(KnowledgePointFactsPanel, { props: { courseId: 408, refreshKey: 0 }, global: { stubs } })
    await flushPromises()
    mockGetFacts.mockReset()
    mockGetFacts
      .mockResolvedValueOnce({ data: { records: [], total: 11, current: 3, size: 10 } })
      .mockResolvedValueOnce({
        data: { records: [{ ...currentFact, knowledgePointName: '队列' }], total: 11, current: 2, size: 10 },
      })

    const vm = wrapper.vm as unknown as { loadFacts: (page: number) => Promise<void> }
    await vm.loadFacts(3)

    expect(mockGetFacts).toHaveBeenNthCalledWith(1, 408, 3, 10)
    expect(mockGetFacts).toHaveBeenNthCalledWith(2, 408, 2, 10)
    expect(wrapper.text()).toContain('队列')
  })

  it('课程变更时重置分页，且忽略旧课程的晚到响应', async () => {
    let finishOldRequest: ((value: unknown) => void) | undefined
    const oldRequest = new Promise((resolve) => {
      finishOldRequest = resolve
    })
    mockGetFacts.mockReturnValueOnce(oldRequest).mockResolvedValueOnce({
      data: { records: [{ ...currentFact, knowledgePointName: '队列' }], total: 1, current: 1, size: 10 },
    })
    const wrapper = mount(KnowledgePointFactsPanel, { props: { courseId: 408, refreshKey: 0 }, global: { stubs } })
    await wrapper.setProps({ courseId: 409 })
    await flushPromises()
    finishOldRequest!({ data: { records: [currentFact], total: 1, current: 1, size: 10 } })
    await flushPromises()

    expect(mockGetFacts).toHaveBeenNthCalledWith(1, 408, 1, 10)
    expect(mockGetFacts).toHaveBeenNthCalledWith(2, 409, 1, 10)
    expect(wrapper.text()).toContain('队列')
    expect(wrapper.text()).not.toContain('栈')
  })
})
