import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const {
  mockGetPublishedPapers,
  mockGetMyExamRecords,
  mockGetPaperDetail,
  mockStartExam,
  mockPush,
} = vi.hoisted(() => ({
  mockGetPublishedPapers: vi.fn(),
  mockGetMyExamRecords: vi.fn(),
  mockGetPaperDetail: vi.fn(),
  mockStartExam: vi.fn(),
  mockPush: vi.fn(),
}))

vi.mock('@/api/exam', () => ({
  getPublishedPapers: (...args: unknown[]) => mockGetPublishedPapers(...args),
  getMyExamRecords: (...args: unknown[]) => mockGetMyExamRecords(...args),
  getPaperDetail: (...args: unknown[]) => mockGetPaperDetail(...args),
  startExam: (...args: unknown[]) => mockStartExam(...args),
  startExamLearningSession: vi.fn(),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal<typeof import('vue-router')>(),
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: mockPush }),
}))

import ExamListView from '@/views/exam/ExamListView.vue'

const stubs = {
  'el-tabs': { template: '<div><slot /></div>' },
  'el-tab-pane': { template: '<section><slot /></section>' },
  'el-card': { template: '<article><slot /></article>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-icon': { template: '<i><slot /></i>' },
  'el-button': {
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    props: ['disabled', 'loading', 'type', 'link', 'size'],
    emits: ['click'],
  },
  'el-empty': { template: '<div />' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-pagination': { template: '<div />' },
}

describe('ExamListView paper provenance', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    mockStartExam.mockResolvedValue({ code: 0, data: { id: 101, examPaperId: 1, status: 0 } })
    mockGetMyExamRecords.mockResolvedValue({ code: 0, data: { records: [], total: 0 } })
    mockGetPublishedPapers.mockResolvedValue({
      code: 0,
      data: {
        total: 2,
        records: [
          {
            id: 1,
            title: '来源已核验试卷',
            paperType: 'OFFICIAL_EXAM',
            examName: '全国硕士研究生招生考试',
            examYear: 2025,
            sourceReference: '教育主管部门公开文件',
            sourceVerified: true,
            questionCount: 1,
            duration: 30,
            totalScore: 2,
          },
          {
            id: 2,
            title: '日常练习',
            paperType: 'PRACTICE',
            sourceVerified: false,
            questionCount: 1,
            duration: 10,
            totalScore: 1,
          },
        ],
      },
    })
  })

  it('只把来源已核验的官方试卷标记为官方原题并展示来源', async () => {
    const wrapper = mount(ExamListView, {
      global: {
        stubs,
        directives: { loading: () => undefined },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('官方原题')
    expect(wrapper.text()).toContain('2025 · 全国硕士研究生招生考试')
    expect(wrapper.text()).toContain('来源：教育主管部门公开文件')
    expect(wrapper.text()).toContain('普通练习')
  })

  it('考试模式只创建服务端会话并导航，不预取或缓存试题', async () => {
    const wrapper = mount(ExamListView, {
      global: {
        stubs,
        directives: { loading: () => undefined },
      },
    })
    await flushPromises()

    await wrapper.findAll('button').find(button => button.text().includes('考试模式'))!.trigger('click')
    await flushPromises()

    expect(mockStartExam).toHaveBeenCalledWith(1)
    expect(mockGetPaperDetail).not.toHaveBeenCalled()
    expect(sessionStorage.length).toBe(0)
    expect(mockPush).toHaveBeenCalledWith({ name: 'ExamTake', params: { recordId: '101' } })
  })

  it('分别为进行中、已完成和已超时记录提供正确状态与操作', async () => {
    mockGetPublishedPapers.mockResolvedValue({ code: 0, data: { records: [], total: 0 } })
    mockGetMyExamRecords.mockResolvedValue({
      code: 0,
      data: {
        total: 3,
        records: [
          { id: 11, examTitle: '进行中的试卷', status: 0, startTime: '2026-08-13T10:00:00', totalScore: 100 },
          { id: 12, examTitle: '已完成的试卷', status: 1, startTime: '2026-08-12T10:00:00', score: 80, totalScore: 100 },
          { id: 13, examTitle: '超时的试卷', status: 2, startTime: '2026-08-11T10:00:00', score: 0, totalScore: 100 },
        ],
      },
    })

    const wrapper = mount(ExamListView, {
      global: {
        stubs,
        directives: { loading: () => undefined },
      },
    })
    await flushPromises()

    const cards = wrapper.findAll('.record-mobile-card')
    expect(cards).toHaveLength(3)
    expect(cards[0].text()).toContain('进行中')
    expect(cards[0].text()).toContain('继续考试')
    expect(cards[1].text()).toContain('已完成')
    expect(cards[1].text()).toContain('查看结果')
    expect(cards[2].text()).toContain('已超时')
    expect(cards[2].text()).not.toContain('继续考试')

    await cards[0].find('button').trigger('click')
    await cards[1].find('button').trigger('click')
    expect(mockPush).toHaveBeenCalledWith({ name: 'ExamTake', params: { recordId: '11' } })
    expect(mockPush).toHaveBeenCalledWith({ name: 'ExamResult', params: { recordId: '12' } })
  })
})
