import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockGetMyExamRecords, mockPush } = vi.hoisted(() => ({
  mockGetMyExamRecords: vi.fn(),
  mockPush: vi.fn(),
}))

vi.mock('@/api/exam', () => ({
  getMyExamRecords: (...args: unknown[]) => mockGetMyExamRecords(...args),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRouter: () => ({ push: mockPush }),
}))

import ExamRecordList from '@/components/exam/ExamRecordList.vue'

const stubs = {
  'el-tag': { template: '<span><slot /></span>' },
  'el-button': {
    template: '<button @click="$emit(\'click\')"><slot /></button>',
    emits: ['click'],
  },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-pagination': { template: '<div />' },
}

describe('ExamRecordList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetMyExamRecords.mockResolvedValue({
      code: 0,
      data: {
        total: 5,
        records: [
          { id: 11, examTitle: '进行中', status: 0, startTime: '2026-08-30T10:00:00', totalScore: 100 },
          { id: 12, examTitle: '已完成', status: 1, startTime: '2026-08-29T10:00:00', score: 80, totalScore: 100 },
          { id: 13, examTitle: '已超时', status: 2, startTime: '2026-08-28T10:00:00', score: 0, totalScore: 100 },
          { id: 14, examTitle: '待批阅', status: 3, startTime: '2026-08-27T10:00:00', score: 50, totalScore: 100 },
          {
            id: 15,
            examTitle: '待批阅未出分',
            status: 3,
            startTime: '2026-08-26T10:00:00',
            score: null,
            totalScore: 100,
          },
        ],
      },
    })
  })

  it('加载分页记录并向父页面上报总数', async () => {
    const wrapper = mount(ExamRecordList, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()

    expect(mockGetMyExamRecords).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 }, { errorDisplay: 'inline' })
    expect(wrapper.emitted('totalChange')).toEqual([[5]])
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.text()).toContain('80 / 100')
    expect(wrapper.text()).toContain('50 / 100（暂定）')
    expect(wrapper.text()).toContain('待评分 / 100（暂定）')
    expect(wrapper.text()).toContain('考试已超时，不可继续')
  })

  it('读取失败时不伪造空记录，并原位重试', async () => {
    mockGetMyExamRecords.mockRejectedValueOnce(new Error('network'))
    const wrapper = mount(ExamRecordList, { global: { stubs, directives: { loading: () => undefined } } })
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('考试记录暂时无法读取')
    expect(wrapper.text()).not.toContain('暂无考试记录')
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(mockGetMyExamRecords).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('已完成')
  })

  it('刷新已加载的记录失败时隐藏旧分页', async () => {
    mockGetMyExamRecords.mockResolvedValueOnce({
      code: 0,
      data: { total: 11, records: [{ id: 12, examTitle: '已完成', status: 1, score: 80, totalScore: 100 }] },
    })
    const wrapper = mount(ExamRecordList, { global: { stubs, directives: { loading: () => undefined } } })
    await flushPromises()
    expect(wrapper.find('.pagination-wrapper').exists()).toBe(true)

    mockGetMyExamRecords.mockRejectedValueOnce(new Error('network'))
    await (wrapper.vm as unknown as { reload: () => Promise<void> }).reload()

    expect(wrapper.get('[role="alert"]').text()).toContain('考试记录暂时无法读取')
    expect(wrapper.find('.pagination-wrapper').exists()).toBe(false)
  })

  it('按记录状态进入继续考试或结果页', async () => {
    const wrapper = mount(ExamRecordList, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()

    const cards = wrapper.findAll('.record-mobile-card')
    await cards[0].find('button').trigger('click')
    await cards[1].find('button').trigger('click')

    expect(mockPush).toHaveBeenCalledWith({ name: 'ExamTake', params: { recordId: '11' } })
    expect(mockPush).toHaveBeenCalledWith({ name: 'ExamResult', params: { recordId: '12' } })
  })
})
