import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const {
  mockGetPrivateExamSource,
  mockGetPrivateExamStorageFiles,
  mockDownloadPrivateExamSourceFile,
  mockDownloadPrivateExamDraftSourceFile,
  mockDeletePrivateExamPaper,
  mockDeletePrivateExamDraft,
  mockConfirm,
} = vi.hoisted(() => ({
  mockGetPrivateExamSource: vi.fn(),
  mockGetPrivateExamStorageFiles: vi.fn(),
  mockDownloadPrivateExamSourceFile: vi.fn(),
  mockDownloadPrivateExamDraftSourceFile: vi.fn(),
  mockDeletePrivateExamPaper: vi.fn(),
  mockDeletePrivateExamDraft: vi.fn(),
  mockConfirm: vi.fn(),
}))

vi.mock('@/api/exam', () => ({
  getPrivateExamSource: (...args: unknown[]) => mockGetPrivateExamSource(...args),
  getPrivateExamStorageFiles: (...args: unknown[]) => mockGetPrivateExamStorageFiles(...args),
  downloadPrivateExamSourceFile: (...args: unknown[]) => mockDownloadPrivateExamSourceFile(...args),
  downloadPrivateExamDraftSourceFile: (...args: unknown[]) => mockDownloadPrivateExamDraftSourceFile(...args),
  deletePrivateExamPaper: (...args: unknown[]) => mockDeletePrivateExamPaper(...args),
  deletePrivateExamDraft: (...args: unknown[]) => mockDeletePrivateExamDraft(...args),
}))

vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessageBox: { confirm: (...args: unknown[]) => mockConfirm(...args) },
}))

import PrivateExamSourceManager from '@/components/exam/PrivateExamSourceManager.vue'

const stubs = {
  'el-dialog': { template: '<section><slot /></section>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-button': {
    template: '<button @click="$emit(\'click\')"><slot /></button>',
    emits: ['click'],
  },
  'el-pagination': { template: '<nav />' },
}

describe('PrivateExamSourceManager', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockConfirm.mockResolvedValue(undefined)
    mockDeletePrivateExamDraft.mockResolvedValue({ code: 0, data: null })
    mockGetPrivateExamStorageFiles.mockResolvedValue({
      code: 0,
      data: {
        total: 1,
        records: [
          {
            id: 41,
            sourceName: 'paper.docx',
            sourceFormat: 'DOCX',
            sourceSize: 2048,
            createTime: '2026-08-30T10:00:00',
            associationType: 'DRAFT',
            associationId: 31,
            associationTitle: '待复核试卷',
          },
        ],
      },
    })
  })

  it('分页加载原文件并在删除关联草稿后刷新和通知父页面', async () => {
    const wrapper = mount(PrivateExamSourceManager, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    const vm = wrapper.vm as unknown as {
      openStorage: () => Promise<void>
      deleteStorageItem: (item: Record<string, unknown>) => Promise<void>
      storageFiles: Record<string, unknown>[]
    }

    await vm.openStorage()

    expect(mockGetPrivateExamStorageFiles).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
    expect(wrapper.text()).toContain('paper.docx')
    expect(wrapper.text()).toContain('关联草稿：待复核试卷')

    await vm.deleteStorageItem(vm.storageFiles[0])

    expect(mockDeletePrivateExamDraft).toHaveBeenCalledWith(31)
    expect(mockGetPrivateExamStorageFiles).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('contentDeleted')).toHaveLength(1)
  })

  it('读取已确认试卷来源并按响应媒体类型下载原文件', async () => {
    mockGetPrivateExamSource.mockResolvedValue({
      code: 0,
      data: {
        paperId: 51,
        sourceName: 'paper.pdf',
        sourceFormat: 'PDF',
        contentHash: 'a'.repeat(64),
        originalContent: '原始试卷正文',
        originalFileAvailable: true,
      },
    })
    mockDownloadPrivateExamSourceFile.mockResolvedValue({
      data: new Uint8Array([1, 2, 3]),
      headers: { 'content-type': 'application/pdf' },
    })
    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:test')
    const revokeObjectURL = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    const wrapper = mount(PrivateExamSourceManager, {
      global: { stubs, directives: { loading: () => undefined } },
    })
    const vm = wrapper.vm as unknown as { openPaperSource: (paperId: number) => Promise<void> }

    await vm.openPaperSource(51)
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('下载原文件'))!
      .trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('原始试卷正文')
    expect(mockDownloadPrivateExamSourceFile).toHaveBeenCalledWith(51)
    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:test')
  })
})
