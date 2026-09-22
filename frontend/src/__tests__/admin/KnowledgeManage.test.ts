import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
const { bundles, chunks, confirm, detail, indexes, review } = vi.hoisted(() => ({
  bundles: vi.fn(),
  chunks: vi.fn(),
  confirm: vi.fn(),
  detail: vi.fn(),
  indexes: vi.fn(),
  review: vi.fn(),
}))
vi.mock('@/api/knowledgeAdmin', () => ({
  getKnowledgeBundles: (...args: unknown[]) => bundles(...args),
  getKnowledgeBundle: (...args: unknown[]) => detail(...args),
  getKnowledgeChunks: (...args: unknown[]) => chunks(...args),
  getKnowledgeIndexes: (...args: unknown[]) => indexes(...args),
  reviewKnowledgeBundle: (...args: unknown[]) => review(...args),
}))
vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessage: { error: vi.fn(), success: vi.fn() },
  ElMessageBox: { confirm },
}))
import KnowledgeManage from '@/admin/views/KnowledgeManage.vue'
const bundle = (bundleId: number, reviewStatus = 'PENDING') => ({
  bundleId,
  courseKey: 'cs408',
  version: `v${bundleId}`,
  manifestHash: 'a'.repeat(64),
  sourceRevision: 'test-revision',
  sourceQualityStatus: 'review_pending',
  reviewStatus,
  chunkCount: 1,
  importedBy: 1,
  importedAt: null,
  reviewedBy: null,
  reviewedAt: null,
  reviewNote: null,
})
const page = (records: unknown[] = []) => ({ data: { records, total: records.length, current: 1, size: 20, pages: 1 } })
const slot = { template: '<div><slot /></div>' }
const stubs = {
  ElAlert: { props: ['title'], template: '<div>{{ title }}</div>' },
  ElButton: { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  ElDescriptions: slot,
  ElDescriptionsItem: slot,
  ElDrawer: { props: ['modelValue'], template: '<section v-if="modelValue"><slot /></section>' },
  ElForm: slot,
  ElFormItem: slot,
  ElInput: {
    name: 'ElInput',
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  ElOption: true,
  ElSelect: slot,
  ElTable: slot,
  ElTableColumn: { template: '<div />' },
  ElPagination: {
    name: 'ElPagination',
    props: ['currentPage'],
    emits: ['current-change', 'update:currentPage'],
    template: '<div />',
  },
  ElEmpty: true,
}
function mountPage() {
  return mount(KnowledgeManage, { global: { stubs, directives: { loading: () => undefined } } })
}
type View = {
  select: (row: ReturnType<typeof bundle>) => Promise<void>
  confirmReview: (decision: 'REVIEWED' | 'WITHDRAWN') => Promise<void>
}
async function enterNote(wrapper: ReturnType<typeof mountPage>) {
  await wrapper.findAll('input').at(-1)!.setValue('已核对原创测试内容与许可')
}
describe('KnowledgeManage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    bundles.mockResolvedValue(page([bundle(7)]))
    detail.mockImplementation((id: number) => Promise.resolve({ data: bundle(id) }))
    chunks.mockResolvedValue(page([]))
    indexes.mockResolvedValue(page([]))
    review.mockResolvedValue({ data: null })
    confirm.mockResolvedValue(undefined)
  })
  it('keeps the current bundle ID when a chunk pagination event supplies a page number', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await (wrapper.vm as unknown as View).select(bundle(7))
    const pagination = wrapper.findAllComponents({ name: 'ElPagination' })[1]!
    pagination.vm.$emit('update:currentPage', 2)
    await nextTick()
    pagination.vm.$emit('current-change', 2)
    await flushPromises()
    expect(chunks).toHaveBeenLastCalledWith(7, expect.objectContaining({ pageNum: 2 }))
    wrapper.unmount()
  })
  it('captures version and note before confirmation without replacing a newly opened version', async () => {
    let resolveConfirm!: () => void
    confirm.mockReturnValue(
      new Promise<void>((resolve) => {
        resolveConfirm = resolve
      }),
    )
    const wrapper = mountPage()
    await flushPromises()
    const vm = wrapper.vm as unknown as View
    await vm.select(bundle(7))
    await enterNote(wrapper)
    const pending = vm.confirmReview('REVIEWED')
    await vm.select(bundle(8))
    resolveConfirm()
    await pending
    expect(review).toHaveBeenCalledWith(7, 'REVIEWED', '已核对原创测试内容与许可')
    expect(wrapper.text()).toContain('v8')
    expect(detail).toHaveBeenLastCalledWith(8)
    wrapper.unmount()
  })
  it('does not write when confirmation is cancelled', async () => {
    confirm.mockRejectedValue('cancel')
    const wrapper = mountPage()
    await flushPromises()
    const vm = wrapper.vm as unknown as View
    await vm.select(bundle(7))
    await enterNote(wrapper)
    await vm.confirmReview('REVIEWED')
    expect(review).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('reloads authoritative status after a failed review request', async () => {
    review.mockRejectedValue(new Error('response lost'))
    const wrapper = mountPage()
    await flushPromises()
    const vm = wrapper.vm as unknown as View
    await vm.select(bundle(7))
    await enterNote(wrapper)
    detail.mockResolvedValue({ data: bundle(7, 'WITHDRAWN') })
    await vm.confirmReview('WITHDRAWN')
    expect(detail).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('此版本已撤回')
    wrapper.unmount()
  })
  it('ignores a late response from a previously opened version', async () => {
    let finishFirst!: (value: unknown) => void
    detail.mockImplementation((id: number) =>
      id === 7
        ? new Promise((resolve) => {
            finishFirst = resolve
          })
        : Promise.resolve({ data: bundle(id) }),
    )
    const wrapper = mountPage()
    await flushPromises()
    const vm = wrapper.vm as unknown as View
    const first = vm.select(bundle(7))
    await vm.select(bundle(8))
    finishFirst({ data: bundle(7) })
    await first
    expect(wrapper.text()).toContain('v8')
    expect(wrapper.text()).not.toContain('v7')
    wrapper.unmount()
  })
})
