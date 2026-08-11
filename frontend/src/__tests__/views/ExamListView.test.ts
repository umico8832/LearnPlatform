import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { mockGetPublishedPapers, mockGetMyExamRecords } = vi.hoisted(() => ({
  mockGetPublishedPapers: vi.fn(),
  mockGetMyExamRecords: vi.fn(),
}))

vi.mock('@/api/exam', () => ({
  getPublishedPapers: (...args: unknown[]) => mockGetPublishedPapers(...args),
  getMyExamRecords: (...args: unknown[]) => mockGetMyExamRecords(...args),
  getPaperDetail: vi.fn(),
  startExam: vi.fn(),
  startExamLearningSession: vi.fn(),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal<typeof import('vue-router')>(),
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn() }),
}))

import ExamListView from '@/views/exam/ExamListView.vue'

const stubs = {
  'el-tabs': { template: '<div><slot /></div>' },
  'el-tab-pane': { template: '<section><slot /></section>' },
  'el-card': { template: '<article><slot /></article>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-icon': { template: '<i><slot /></i>' },
  'el-button': { template: '<button><slot /></button>' },
  'el-empty': { template: '<div />' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-pagination': { template: '<div />' },
}

describe('ExamListView paper provenance', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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
})
