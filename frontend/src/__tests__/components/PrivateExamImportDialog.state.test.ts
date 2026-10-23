import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, shallowMount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { LtSheet } from '@/components/living'
import type { PrivateExamDraft, PrivateExamImportPreview, PrivateExamImportRequest } from '@/api/exam'
import { deletePrivateExamDraft, getPrivateExamDrafts } from '@/api/exam'
import {
  confirmPrivateExamSource,
  createPrivateExamAnswerDraft,
  previewPrivateExamSource,
} from '@/components/exam/privateExamImportRequests'
import PrivateExamImportDialog from '@/components/exam/PrivateExamImportDialog.vue'
import PrivateExamDraftReview from '@/components/exam/PrivateExamDraftReview.vue'

const { mockConfirmSheet } = vi.hoisted(() => ({ mockConfirmSheet: vi.fn() }))

vi.mock('@/composables/useConfirmSheet', () => ({
  useConfirmSheet: () => ({
    open: ref(false),
    ask: (...args: unknown[]) => mockConfirmSheet(...args),
    answer: vi.fn(),
  }),
}))

vi.mock('@/api/exam', () => ({
  confirmPrivateExamDraft: vi.fn(),
  deletePrivateExamDraft: vi.fn(),
  getPrivateExamDrafts: vi.fn().mockResolvedValue({ code: 0, data: [] }),
  getPrivateExamStorageUsage: vi.fn().mockResolvedValue({ code: 0, data: null }),
}))
vi.mock('@/api/course', () => ({ getAllCourses: vi.fn().mockResolvedValue({ code: 0, data: [] }) }))
vi.mock('@/components/exam/privateExamImportRequests', () => ({
  confirmPrivateExamSource: vi.fn(),
  createPrivateExamAnswerDraft: vi.fn(),
  previewPrivateExamSource: vi.fn(),
}))

type DialogVm = {
  activeDraft: PrivateExamDraft | null
  privateDrafts: PrivateExamDraft[]
  replaceDraft: (draft: PrivateExamDraft) => void
  importForm: PrivateExamImportRequest
  importPreview: PrivateExamImportPreview | null
  previewImport: () => Promise<void>
  createAnswerDraft: () => Promise<void>
  previewLoading: boolean
  confirmLoading: boolean
  loadPrivateDrafts: () => Promise<void>
  deleteDraft: (draft: PrivateExamDraft) => Promise<void>
  validationError: string
  actionError: string
  retryKind: string | null
  retryAction: () => void
  confirmImport: () => Promise<void>
  openDraft: (draft: PrivateExamDraft | null) => void
}
function fixture(id: number): PrivateExamDraft {
  return {
    id,
    title: `草稿${id}`,
    courseId: 10,
    duration: 30,
    status: 'DRAFT',
    confirmedPaperId: null,
    sourceName: null,
    sourceFormat: null,
    originalFileAvailable: false,
    reviewedQuestionCount: 0,
    questionCount: 0,
    questions: [],
    createTime: '',
  }
}
function previewFixture(): PrivateExamImportPreview {
  return {
    title: '草稿',
    courseId: 10,
    duration: 30,
    sourceName: '试卷.md',
    sourceFormat: 'MARKDOWN',
    content: '两道无答案题',
    contentHash: 'hash',
    questionCount: 2,
    totalScore: 2,
    requiresAnswerReview: true,
    questions: [],
  }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((yes) => {
    resolve = yes
  })
  return { promise, resolve }
}

function setup() {
  const wrapper = shallowMount(PrivateExamImportDialog, {
    props: { modelValue: true },
    global: {
      stubs: {
        LtSheet: { template: '<div><slot /></div>' },
        LtButton: { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
      },
    },
  })
  return { wrapper, vm: wrapper.vm as unknown as DialogVm }
}

beforeEach(() => {
  vi.clearAllMocks()
  mockConfirmSheet.mockResolvedValue(true)
})

describe('私有试卷导入弹窗的草稿更新隔离', () => {
  it('原生时长输入继续限制为 1 至 600 分钟，越界时不请求解析', async () => {
    const { wrapper, vm } = setup()
    vm.importForm = { ...previewFixture(), duration: 601 }
    await vm.previewImport()
    expect(previewPrivateExamSource).not.toHaveBeenCalled()
    expect(vm.validationError).toContain('1 至 600')
    wrapper.unmount()
  })

  it('预览失败保留输入并可以原位重试', async () => {
    vi.mocked(previewPrivateExamSource)
      .mockRejectedValueOnce(new Error('网络暂时不可用'))
      .mockResolvedValueOnce({ code: 0, message: '', data: previewFixture() })
    const { wrapper, vm } = setup()
    vm.importForm = previewFixture()
    await vm.previewImport()
    expect(vm.actionError).toBe('网络暂时不可用')
    expect(vm.importForm.content).toBe('两道无答案题')
    expect(vm.importPreview).toBeNull()
    vm.retryAction()
    await flushPromises()
    expect(vm.actionError).toBe('')
    expect(vm.importPreview?.title).toBe('草稿')
    wrapper.unmount()
  })

  it('确认失败保留预览，返回修改时清除旧确认重试', async () => {
    vi.mocked(confirmPrivateExamSource).mockRejectedValueOnce(new Error('确认未完成'))
    const { wrapper, vm } = setup()
    vm.importForm = previewFixture()
    vm.importPreview = { ...previewFixture(), requiresAnswerReview: false }
    await vm.confirmImport()
    expect(vm.actionError).toBe('确认未完成')
    expect(vm.importPreview?.title).toBe('草稿')
    expect(vm.retryKind).toBe('confirm')
    vm.openDraft(null)
    expect(vm.importForm.content).toBe('两道无答案题')
    expect(vm.actionError).toBe('')
    expect(vm.retryKind).toBeNull()
    wrapper.unmount()
  })

  it('删除确认期间阻止重复弹窗，关闭导入后不执行迟到的确认', async () => {
    const pending = deferred<boolean>()
    mockConfirmSheet.mockReturnValueOnce(pending.promise)
    const { wrapper, vm } = setup()
    const first = vm.deleteDraft(fixture(31))
    await vm.deleteDraft(fixture(31))
    expect(mockConfirmSheet).toHaveBeenCalledTimes(1)
    await wrapper.setProps({ modelValue: false })
    pending.resolve(true)
    await first
    expect(deletePrivateExamDraft).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('只更新正在复核的同一份草稿和对应列表项', async () => {
    const { wrapper, vm } = setup()
    vm.activeDraft = fixture(32)
    vm.privateDrafts = [fixture(31), fixture(32)]
    await flushPromises()
    const updated = { ...fixture(32), title: '当前服务端草稿' }
    wrapper.findComponent(PrivateExamDraftReview).vm.$emit('updated', updated)
    await flushPromises()
    expect(vm.activeDraft).toEqual(updated)
    expect(vm.privateDrafts.map((draft) => draft.title)).toEqual(['草稿31', '当前服务端草稿'])
    vm.replaceDraft({ ...fixture(31), title: '旧响应' })
    expect(vm.activeDraft?.id).toBe(32)
    expect(vm.privateDrafts[0]!.title).toBe('草稿31')
    wrapper.unmount()
  })

  it('返回导入后旧复核事件不能重新打开草稿', async () => {
    const { wrapper, vm } = setup()
    vm.activeDraft = null
    vm.replaceDraft(fixture(31))
    await flushPromises()
    expect(vm.activeDraft).toBeNull()
    expect(wrapper.findComponent(PrivateExamDraftReview).exists()).toBe(false)
    wrapper.unmount()
  })

  it('关闭弹窗立即卸载复核组件，不等待关闭动画结束', async () => {
    const { wrapper, vm } = setup()
    vm.activeDraft = fixture(31)
    await flushPromises()
    expect(wrapper.findComponent(PrivateExamDraftReview).exists()).toBe(true)
    await wrapper.setProps({ modelValue: false })
    expect(wrapper.findComponent(PrivateExamDraftReview).exists()).toBe(false)
    vm.replaceDraft({ ...fixture(31), title: '关闭后的响应' })
    expect(vm.activeDraft?.title).not.toBe('关闭后的响应')
    wrapper.unmount()
  })
  it('关闭并重新打开后忽略上一会话的晚预览，不解除新预览的忙碌状态', async () => {
    const old = deferred<Awaited<ReturnType<typeof previewPrivateExamSource>>>()
    const fresh = deferred<Awaited<ReturnType<typeof previewPrivateExamSource>>>()
    vi.mocked(previewPrivateExamSource).mockReturnValueOnce(old.promise).mockReturnValueOnce(fresh.promise)
    const { wrapper, vm } = setup()
    vm.importForm = previewFixture()
    const first = vm.previewImport()
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    vm.importForm = { ...previewFixture(), title: '新预览' }
    const second = vm.previewImport()
    old.resolve({ code: 0, message: '', data: previewFixture() })
    await first
    expect(vm.importPreview).toBeNull()
    expect(vm.previewLoading).toBe(true)
    fresh.resolve({ code: 0, message: '', data: { ...previewFixture(), title: '新预览' } })
    await second
    expect(vm.importPreview?.title).toBe('新预览')
    wrapper.unmount()
  })

  it('创建草稿晚响应不能打开已经关闭或重新进入的弹窗', async () => {
    const pending = deferred<Awaited<ReturnType<typeof createPrivateExamAnswerDraft>>>()
    vi.mocked(createPrivateExamAnswerDraft).mockReturnValueOnce(pending.promise)
    const { wrapper, vm } = setup()
    vm.importPreview = previewFixture()
    const request = vm.createAnswerDraft()
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    pending.resolve({ code: 0, message: '', data: fixture(31) })
    await request
    expect(vm.activeDraft).toBeNull()
    expect(vm.importPreview).toBeNull()
    wrapper.unmount()
  })

  it('慢返回的草稿列表不会覆盖刚创建的草稿', async () => {
    const old = deferred<Awaited<ReturnType<typeof getPrivateExamDrafts>>>()
    vi.mocked(getPrivateExamDrafts).mockReturnValueOnce(old.promise)
    vi.mocked(createPrivateExamAnswerDraft).mockResolvedValueOnce({ code: 0, message: '', data: fixture(31) })
    const { wrapper, vm } = setup()
    const loading = vm.loadPrivateDrafts()
    vm.importPreview = previewFixture()
    await vm.createAnswerDraft()
    old.resolve({ code: 0, message: '', data: [] })
    await loading
    expect(vm.privateDrafts.map((draft) => draft.id)).toContain(31)
    wrapper.unmount()
  })
  it('删除草稿后不接受删除前的列表响应', async () => {
    const old = deferred<Awaited<ReturnType<typeof getPrivateExamDrafts>>>()
    vi.mocked(getPrivateExamDrafts).mockReturnValueOnce(old.promise)
    vi.mocked(deletePrivateExamDraft).mockResolvedValueOnce({ code: 0, message: '', data: null })
    mockConfirmSheet.mockResolvedValueOnce(true)
    const { wrapper, vm } = setup()
    vm.privateDrafts = [fixture(31)]
    const loading = vm.loadPrivateDrafts()
    await vm.deleteDraft(fixture(31))
    old.resolve({ code: 0, message: '', data: [fixture(31)] })
    await loading
    expect(vm.privateDrafts).toEqual([])
    wrapper.unmount()
  })
})

describe('LtSheet 删除确认交互', () => {
  it('取消和确认前不会请求删除，确认后才请求一次', async () => {
    const { useConfirmSheet } = await vi.importActual<typeof import('@/composables/useConfirmSheet')>(
      '@/composables/useConfirmSheet',
    )
    const remove = vi.fn()
    const Harness = defineComponent({
      components: { LtSheet },
      setup() {
        const confirm = useConfirmSheet()
        const open = confirm.open
        async function deleteDraft() {
          if (await confirm.ask()) remove()
        }
        return { confirm, deleteDraft, open }
      },
      template: `<button type="button" @click="deleteDraft">删除草稿</button><LtSheet v-model:open="open" title="删除私有试卷草稿"><button type="button" @click="confirm.answer(false)">取消</button><button type="button" @click="confirm.answer(true)">确认删除</button></LtSheet>`,
    })
    const wrapper = mount(Harness, { attachTo: document.body })
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(remove).not.toHaveBeenCalled()
    const dialog = document.querySelector('dialog')!
    const buttons = Array.from(dialog.querySelectorAll('button'))
    await (buttons.find((button) => button.textContent === '取消') as HTMLButtonElement).click()
    await flushPromises()
    expect(remove).not.toHaveBeenCalled()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    const confirmButton = Array.from(document.querySelector('dialog')!.querySelectorAll('button')).find(
      (button) => button.textContent === '确认删除',
    ) as HTMLButtonElement
    confirmButton.click()
    await flushPromises()
    expect(remove).toHaveBeenCalledOnce()
    wrapper.unmount()
  })
})
