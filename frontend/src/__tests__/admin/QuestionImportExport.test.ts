import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { downloadMarkdown, downloadTemplate, exportQuestions, importExcel, importMarkdown, success } = vi.hoisted(
  () => ({
    downloadMarkdown: vi.fn(),
    downloadTemplate: vi.fn(),
    exportQuestions: vi.fn(),
    importExcel: vi.fn(),
    importMarkdown: vi.fn(),
    success: vi.fn(),
  }),
)

vi.mock('@/api/question', () => ({
  downloadMarkdownTemplate: (...args: unknown[]) => downloadMarkdown(...args),
  downloadTemplate: (...args: unknown[]) => downloadTemplate(...args),
  exportQuestions: (...args: unknown[]) => exportQuestions(...args),
  importQuestions: (...args: unknown[]) => importExcel(...args),
  importQuestionsMarkdown: (...args: unknown[]) => importMarkdown(...args),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { error: vi.fn(), success, warning: vi.fn() } }
})

import QuestionImportExport from '@/admin/views/question/QuestionImportExport.vue'

const DialogStub = defineComponent({
  props: { modelValue: Boolean, title: String },
  template: '<section v-if="modelValue" :data-title="title"><slot /><slot name="footer" /></section>',
})

const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
})

const UploadStub = defineComponent({
  props: { onChange: Function },
  methods: {
    clearFiles() {},
  },
  template:
    '<button type="button" data-upload @click="onChange?.({ raw: { name: \'questions.xlsx\' } })"><slot /></button>',
})

const PassThroughStub = defineComponent({
  template: '<div><slot /><slot name="tip" /></div>',
})

function mountImportExport() {
  return mount(QuestionImportExport, {
    props: {
      filters: { questionType: 'SINGLE_CHOICE', courseId: 3, difficulty: 4 },
    },
    global: {
      stubs: {
        ElButton: ButtonStub,
        ElDescriptions: PassThroughStub,
        ElDescriptionsItem: PassThroughStub,
        ElDialog: DialogStub,
        ElIcon: PassThroughStub,
        ElScrollbar: PassThroughStub,
        ElTabPane: PassThroughStub,
        ElTabs: PassThroughStub,
        ElTag: PassThroughStub,
        ElUpload: UploadStub,
      },
    },
  })
}

describe('QuestionImportExport', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    importExcel.mockResolvedValue({
      data: { totalRows: 2, successCount: 1, failCount: 1, errors: ['第 2 行缺少答案'] },
    })
    importMarkdown.mockResolvedValue({ data: { totalRows: 1, successCount: 1, failCount: 0, errors: [] } })
    exportQuestions.mockResolvedValue({ data: new Uint8Array([1, 2, 3]) })
    downloadTemplate.mockResolvedValue({ data: new Uint8Array([1]) })
    downloadMarkdown.mockResolvedValue({ data: '# template' })
    vi.spyOn(window.URL, 'createObjectURL').mockReturnValue('blob:questions')
    vi.spyOn(window.URL, 'revokeObjectURL').mockImplementation(() => undefined)
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
  })

  it('Excel 导入成功后展示结果并通知父页面刷新', async () => {
    const wrapper = mountImportExport()

    wrapper.vm.openImport()
    await wrapper.vm.$nextTick()
    await wrapper.findAll('[data-upload]')[0].trigger('click')
    const importButton = wrapper.findAll('button').find((button) => button.text() === '开始导入')
    expect(importButton).toBeDefined()
    await importButton!.trigger('click')
    await flushPromises()

    expect(importExcel).toHaveBeenCalledOnce()
    expect(wrapper.emitted('imported')).toHaveLength(1)
    expect(wrapper.find('[data-title="导入结果"]').text()).toContain('第 2 行缺少答案')
  })

  it('导出时传递当前题型、课程和难度筛选', async () => {
    const wrapper = mountImportExport()

    await wrapper.vm.exportQuestions()

    expect(exportQuestions).toHaveBeenCalledWith({ questionType: 'SINGLE_CHOICE', courseId: 3, difficulty: 4 })
    expect(success).toHaveBeenCalledWith('导出成功')
  })
})
