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
        total: 4,
        records: [
          { id: 11, examTitle: '进行中', status: 0, startTime: '2026-08-30T10:00:00', totalScore: 100 },
          { id: 12, examTitle: '已完成', status: 1, startTime: '2026-08-29T10:00:00', score: 80, totalScore: 100 },
          { id: 13, examTitle: '已超时', status: 2, startTime: '2026-08-28T10:00:00', score: 0, totalScore: 100 },
          { id: 14, examTitle: '待批阅', status: 3, startTime: '2026-08-27T10:00:00', score: 50, totalScore: 100 },
        ],
      },
    })
  })

  it('加载分页记录并向父页面上报总数', async () => {
    const wrapper = mount(ExamRecordList, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    await flushPromises()

    expect(mockGetMyExamRecords).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
    expect(wrapper.emitted('totalChange')).toEqual([[4]])
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.text()).toContain('80 / 100')
    expect(wrapper.text()).toContain('50 / 100（暂定）')
    expect(wrapper.text()).toContain('考试已超时，不可继续')
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
