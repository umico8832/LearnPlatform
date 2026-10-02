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
  authListeners,
  authState,
} = vi.hoisted(() => ({
  mockGetPrivateExamSource: vi.fn(),
  mockGetPrivateExamStorageFiles: vi.fn(),
  mockDownloadPrivateExamSourceFile: vi.fn(),
  mockDownloadPrivateExamDraftSourceFile: vi.fn(),
  mockDeletePrivateExamPaper: vi.fn(),
  mockDeletePrivateExamDraft: vi.fn(),
  mockConfirm: vi.fn(),
  authListeners: new Set<() => void>(),
  authState: { version: 0 },
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

vi.mock('@/utils/auth', () => ({
  getAuthSessionVersion: () => authState.version,
  onAuthSessionChange: (listener: () => void) => {
    authListeners.add(listener)
    return () => authListeners.delete(listener)
  },
}))

import PrivateExamSourceManager from '@/components/exam/PrivateExamSourceManager.vue'

const stubs = {
  'el-dialog': { template: '<section><slot /></section>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-button': {
    template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>',
    emits: ['click'],
  },
  'el-pagination': { template: '<nav />' },
  'el-alert': { template: '<aside><slot /><slot name="default" /></aside>' },
}

describe('PrivateExamSourceManager', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authListeners.clear()
    authState.version = 0
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

  it('disables every same-kind storage action while the single storage lock is held', async () => {
    let resolveDownload: (value: { data: Uint8Array; headers: Record<string, string> }) => void = () => undefined
    mockGetPrivateExamStorageFiles.mockResolvedValue({
      code: 0,
      data: {
        total: 2,
        records: [
          {
            id: 41,
            sourceName: 'first.docx',
            sourceFormat: 'DOCX',
            sourceSize: 1,
            createTime: '',
            associationType: 'DRAFT',
            associationId: 31,
            associationTitle: '第一份草稿',
          },
          {
            id: 42,
            sourceName: 'second.docx',
            sourceFormat: 'DOCX',
            sourceSize: 1,
            createTime: '',
            associationType: 'DRAFT',
            associationId: 32,
            associationTitle: '第二份草稿',
          },
        ],
      },
    })
    mockDownloadPrivateExamDraftSourceFile.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveDownload = resolve
      }),
    )
    const wrapper = mount(PrivateExamSourceManager, { global: { stubs, directives: { loading: () => undefined } } })
    const vm = wrapper.vm as unknown as {
      openStorage: () => Promise<void>
      downloadStorageItem: (item: Record<string, unknown>) => Promise<void>
      storageFiles: Record<string, unknown>[]
    }
    await vm.openStorage()
    const downloading = vm.downloadStorageItem(vm.storageFiles[0])
    await flushPromises()
    const downloadButtons = wrapper.findAll('button').filter((button) => button.text() === '下载')
    expect(downloadButtons).toHaveLength(2)
    expect(downloadButtons.every((button) => button.attributes('disabled') !== undefined)).toBe(true)

    resolveDownload({ data: new Uint8Array(), headers: {} })
    await downloading
    wrapper.unmount()
  })

  it('closes source and storage dialogs and clears data on an auth session change', async () => {
    mockGetPrivateExamSource.mockResolvedValue({
      code: 0,
      data: {
        paperId: 51,
        sourceName: 'paper.pdf',
        sourceFormat: 'PDF',
        contentHash: 'hash',
        originalContent: '正文',
        originalFileAvailable: true,
      },
    })
    const wrapper = mount(PrivateExamSourceManager, { global: { stubs, directives: { loading: () => undefined } } })
    const vm = wrapper.vm as unknown as {
      openStorage: () => Promise<void>
      openPaperSource: (id: number) => Promise<void>
      sourceDialogVisible: boolean
      storageDialogVisible: boolean
      privateSource: { paperId: number } | null
      storageFiles: Record<string, unknown>[]
      storageLoaded: boolean
    }
    await vm.openStorage()
    await vm.openPaperSource(51)
    expect(vm.sourceDialogVisible).toBe(true)
    expect(vm.storageDialogVisible).toBe(true)

    authState.version++
    authListeners.forEach((listener) => listener())

    expect(vm.sourceDialogVisible).toBe(false)
    expect(vm.storageDialogVisible).toBe(false)
    expect(vm.privateSource).toBeNull()
    expect(vm.storageFiles).toEqual([])
    expect(vm.storageLoaded).toBe(false)
    wrapper.unmount()
  })

  it('invalidates an in-flight source download immediately when source B replaces source A', async () => {
    let resolveSourceDownload: (value: { data: Uint8Array; headers: Record<string, string> }) => void = () => undefined
    mockGetPrivateExamSource.mockImplementation(async (paperId: number) => ({
      code: 0,
      data: {
        paperId,
        sourceName: `paper-${paperId}.pdf`,
        sourceFormat: 'PDF',
        contentHash: 'hash',
        originalContent: '正文',
        originalFileAvailable: true,
      },
    }))
    mockDownloadPrivateExamSourceFile.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveSourceDownload = resolve
      }),
    )
    const wrapper = mount(PrivateExamSourceManager, { global: { stubs, directives: { loading: () => undefined } } })
    const vm = wrapper.vm as unknown as {
      openPaperSource: (id: number) => Promise<void>
      downloadPaperSource: () => Promise<void>
      privateSource: { paperId: number } | null
      sourceDownloading: boolean
    }
    await vm.openPaperSource(51)
    const downloadA = vm.downloadPaperSource()
    expect(vm.sourceDownloading).toBe(true)
    await vm.openPaperSource(52)
    expect(vm.privateSource?.paperId).toBe(52)
    expect(vm.sourceDownloading).toBe(false)
    resolveSourceDownload({ data: new Uint8Array(), headers: {} })
    await downloadA
    expect(vm.privateSource?.paperId).toBe(52)
    wrapper.unmount()
  })

  it('invalidates an in-flight source download when closing begins, before the dialog close animation completes', async () => {
    let resolveSourceDownload: (value: { data: Uint8Array; headers: Record<string, string> }) => void = () => undefined
    mockGetPrivateExamSource.mockResolvedValue({
      code: 0,
      data: {
        paperId: 51,
        sourceName: 'paper.pdf',
        sourceFormat: 'PDF',
        contentHash: 'hash',
        originalContent: '正文',
        originalFileAvailable: true,
      },
    })
    mockDownloadPrivateExamSourceFile.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveSourceDownload = resolve
      }),
    )
    const createObjectURL = vi.spyOn(URL, 'createObjectURL')
    const wrapper = mount(PrivateExamSourceManager, { global: { stubs, directives: { loading: () => undefined } } })
    const vm = wrapper.vm as unknown as {
      openPaperSource: (id: number) => Promise<void>
      downloadPaperSource: () => Promise<void>
      invalidateSource: () => void
      sourceDownloading: boolean
    }
    await vm.openPaperSource(51)
    const downloading = vm.downloadPaperSource()
    vm.invalidateSource()
    expect(vm.sourceDownloading).toBe(false)
    resolveSourceDownload({ data: new Uint8Array(), headers: {} })
    await downloading
    expect(createObjectURL).not.toHaveBeenCalled()
    createObjectURL.mockRestore()
    wrapper.unmount()
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

    expect(mockGetPrivateExamStorageFiles).toHaveBeenCalledWith(
      { pageNum: 1, pageSize: 10 },
      { errorDisplay: 'inline' },
    )
    expect(wrapper.text()).toContain('paper.docx')
    expect(wrapper.text()).toContain('关联草稿：待复核试卷')

    await vm.deleteStorageItem(vm.storageFiles[0])

    expect(mockDeletePrivateExamDraft).toHaveBeenCalledWith(31, { errorDisplay: 'inline' })
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
    expect(mockDownloadPrivateExamSourceFile).toHaveBeenCalledWith(51, { errorDisplay: 'inline' })
    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:test')
  })

  it('keeps a storage read failure in place and recovers through its retry action', async () => {
    mockGetPrivateExamStorageFiles
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ code: 0, data: { total: 0, records: [] } })
    const wrapper = mount(PrivateExamSourceManager, { global: { stubs, directives: { loading: () => undefined } } })
    const vm = wrapper.vm as unknown as { openStorage: () => Promise<void>; loadStorageFiles: () => Promise<void> }

    await vm.openStorage()
    expect((vm as unknown as { storageError: string }).storageError).toContain('offline')
    expect(wrapper.text()).not.toContain('暂无已保存的 PDF 或 DOCX 原文件')
    await vm.loadStorageFiles()
    expect(wrapper.text()).toContain('暂无已保存的 PDF 或 DOCX 原文件')
  })

  it('keeps loading, empty, and missing storage data mutually exclusive', async () => {
    let resolveStorage: (value: { code: number; data: { total: number; records: unknown[] } }) => void = () => undefined
    mockGetPrivateExamStorageFiles.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveStorage = resolve
      }),
    )
    const wrapper = mount(PrivateExamSourceManager, { global: { stubs, directives: { loading: () => undefined } } })
    const vm = wrapper.vm as unknown as {
      openStorage: () => Promise<void>
      storageFilesLoading: boolean
      storageError: string
    }
    const loading = vm.openStorage()
    expect(vm.storageFilesLoading).toBe(true)
    expect(wrapper.text()).not.toContain('暂无已保存的 PDF 或 DOCX 原文件')
    resolveStorage({ code: 0, data: { total: 0, records: [] } })
    await loading
    expect(wrapper.text()).toContain('暂无已保存的 PDF 或 DOCX 原文件')

    mockGetPrivateExamStorageFiles.mockResolvedValueOnce({ code: 0, data: null })
    await vm.openStorage()
    expect(vm.storageError).toBe('原文件清单暂时无法读取，请重试。')
    expect(wrapper.text()).not.toContain('暂无已保存的 PDF 或 DOCX 原文件')
    wrapper.unmount()
  })

  it('does not delete after its confirmation resolves after unmount', async () => {
    let resolveConfirm: () => void = () => undefined
    mockConfirm.mockReturnValueOnce(
      new Promise<void>((resolve) => {
        resolveConfirm = resolve
      }),
    )
    const wrapper = mount(PrivateExamSourceManager, { global: { stubs, directives: { loading: () => undefined } } })
    const vm = wrapper.vm as unknown as {
      deleteStorageItem: (item: Record<string, unknown>) => Promise<void>
      storageFiles: Record<string, unknown>[]
    }
    await (wrapper.vm as unknown as { openStorage: () => Promise<void> }).openStorage()
    const deleting = vm.deleteStorageItem({
      id: 41,
      sourceName: 'paper.docx',
      sourceFormat: 'DOCX',
      sourceSize: 1,
      createTime: '',
      associationType: 'DRAFT',
      associationId: 31,
      associationTitle: '待复核试卷',
    })
    wrapper.unmount()
    resolveConfirm()
    await deleting
    expect(mockDeletePrivateExamDraft).not.toHaveBeenCalled()
  })

  it('unlocks source and storage downloads independently when both requests finish', async () => {
    let resolveSource: (value: { data: Uint8Array; headers: Record<string, string> }) => void = () => undefined
    let resolveStorage: (value: { data: Uint8Array; headers: Record<string, string> }) => void = () => undefined
    mockGetPrivateExamSource.mockResolvedValue({
      code: 0,
      data: {
        paperId: 51,
        sourceName: 'source.pdf',
        sourceFormat: 'PDF',
        contentHash: 'hash',
        originalContent: '正文',
        originalFileAvailable: true,
      },
    })
    mockDownloadPrivateExamSourceFile.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveSource = resolve
      }),
    )
    mockDownloadPrivateExamDraftSourceFile.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveStorage = resolve
      }),
    )
    const wrapper = mount(PrivateExamSourceManager, { global: { stubs, directives: { loading: () => undefined } } })
    const vm = wrapper.vm as unknown as {
      openPaperSource: (id: number) => Promise<void>
      downloadPaperSource: () => Promise<void>
      downloadStorageItem: (item: Record<string, unknown>) => Promise<void>
      sourceDownloading: boolean
      storageDownloadingId: number | null
    }
    await vm.openPaperSource(51)
    const sourceDownload = vm.downloadPaperSource()
    const storageDownload = vm.downloadStorageItem({
      id: 41,
      sourceName: 'draft.docx',
      sourceFormat: 'DOCX',
      sourceSize: 1,
      createTime: '',
      associationType: 'DRAFT',
      associationId: 31,
      associationTitle: '草稿',
    })
    expect(vm.sourceDownloading).toBe(true)
    expect(vm.storageDownloadingId).toBe(41)
    resolveSource({ data: new Uint8Array(), headers: {} })
    await sourceDownload
    expect(vm.sourceDownloading).toBe(false)
    expect(vm.storageDownloadingId).toBe(41)
    resolveStorage({ data: new Uint8Array(), headers: {} })
    await storageDownload
    expect(vm.storageDownloadingId).toBeNull()
  })

  it('closing the source dialog does not cancel a pending storage download', async () => {
    let resolveStorage: (value: { data: Uint8Array; headers: Record<string, string> }) => void = () => undefined
    mockDownloadPrivateExamDraftSourceFile.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveStorage = resolve
      }),
    )
    const wrapper = mount(PrivateExamSourceManager, { global: { stubs, directives: { loading: () => undefined } } })
    const vm = wrapper.vm as unknown as {
      downloadStorageItem: (item: Record<string, unknown>) => Promise<void>
      invalidateSource: () => void
      storageDownloadingId: number | null
    }
    const download = vm.downloadStorageItem({
      id: 41,
      sourceName: 'draft.docx',
      sourceFormat: 'DOCX',
      sourceSize: 1,
      createTime: '',
      associationType: 'DRAFT',
      associationId: 31,
      associationTitle: '草稿',
    })
    expect(vm.storageDownloadingId).toBe(41)
    vm.invalidateSource()
    resolveStorage({ data: new Uint8Array(), headers: {} })
    await download
    expect(vm.storageDownloadingId).toBeNull()
  })
})
