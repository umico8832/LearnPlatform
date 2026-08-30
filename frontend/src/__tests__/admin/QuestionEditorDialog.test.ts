import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { create, getKnowledgeTree, success, update, warning } = vi.hoisted(() => ({
  create: vi.fn(),
  getKnowledgeTree: vi.fn(),
  success: vi.fn(),
  update: vi.fn(),
  warning: vi.fn(),
}))

vi.mock('@/api/question', () => ({
  createQuestion: (...args: unknown[]) => create(...args),
  updateQuestion: (...args: unknown[]) => update(...args),
}))

vi.mock('@/api/knowledgePoint', () => ({
  getKnowledgeTree: (...args: unknown[]) => getKnowledgeTree(...args),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { success, warning } }
})

import QuestionEditorDialog from '@/admin/views/question/QuestionEditorDialog.vue'

const DialogStub = defineComponent({
  props: { modelValue: Boolean },
  template: '<section v-if="modelValue"><slot /><slot name="footer" /></section>',
})

const FormStub = defineComponent({
  methods: {
    validate: () => Promise.resolve(true),
  },
  template: '<form><slot /></form>',
})

const SelectStub = defineComponent({
  props: { modelValue: [String, Number], placeholder: String },
  emits: ['update:modelValue', 'change'],
  template:
    '<button type="button" :data-placeholder="placeholder" @click="$emit(\'update:modelValue\', 2); $emit(\'change\', 2)"><slot /></button>',
})

const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
})

const PassThroughStub = defineComponent({
  template: '<div><slot /></div>',
})

const question = {
  id: 7,
  content: '二叉树的最大度是多少？',
  questionType: 'SHORT_ANSWER',
  courseId: 1,
  courseName: '数据结构',
  difficulty: 3,
  analysis: '树中结点的最大孩子数。',
  tags: '树',
  score: 5,
  status: 1,
  createTime: '2026-08-30T10:00:00',
  updateTime: '2026-08-30T10:00:00',
  options: [],
  knowledgePointIds: [11],
  knowledgePointNames: ['树的基本概念'],
}

function mountEditor() {
  return mount(QuestionEditorDialog, {
    props: {
      courses: [
        { id: 1, name: '数据结构' },
        { id: 2, name: '计算机网络' },
      ],
    },
    global: {
      stubs: {
        ElButton: ButtonStub,
        ElCheckbox: PassThroughStub,
        ElCol: PassThroughStub,
        ElDialog: DialogStub,
        ElForm: FormStub,
        ElFormItem: PassThroughStub,
        ElInput: PassThroughStub,
        ElInputNumber: PassThroughStub,
        ElOption: PassThroughStub,
        ElRate: PassThroughStub,
        ElRow: PassThroughStub,
        ElSelect: SelectStub,
        ElTreeSelect: PassThroughStub,
      },
    },
  })
}

describe('QuestionEditorDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getKnowledgeTree.mockResolvedValue({ data: [{ id: 11, name: '树的基本概念', children: [] }] })
    update.mockResolvedValue({ code: 0, data: {} })
    create.mockResolvedValue({ code: 0, data: {} })
  })

  it('编辑题目时加载原课程知识点并保存原有字段', async () => {
    const wrapper = mountEditor()

    wrapper.vm.open(question)
    await wrapper.vm.$nextTick()
    const saveButton = wrapper.findAll('button').find((button) => button.text() === '更新')
    expect(saveButton).toBeDefined()
    await saveButton!.trigger('click')
    await wrapper.vm.$nextTick()

    expect(getKnowledgeTree).toHaveBeenCalledWith(1)
    expect(update).toHaveBeenCalledWith(7, {
      content: question.content,
      questionType: question.questionType,
      courseId: 1,
      difficulty: 3,
      analysis: question.analysis,
      tags: question.tags,
      score: 5,
      options: undefined,
      knowledgePointIds: [11],
    })
    expect(success).toHaveBeenCalledWith('更新成功')
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })

  it('切换课程时加载新课程知识点并清除旧知识点选择', async () => {
    const wrapper = mountEditor()

    wrapper.vm.open(question)
    await wrapper.vm.$nextTick()
    await wrapper.find('[data-placeholder="选择课程"]').trigger('click')
    await wrapper.vm.$nextTick()
    const saveButton = wrapper.findAll('button').find((button) => button.text() === '更新')
    expect(saveButton).toBeDefined()
    await saveButton!.trigger('click')
    await wrapper.vm.$nextTick()

    expect(getKnowledgeTree).toHaveBeenNthCalledWith(1, 1)
    expect(getKnowledgeTree).toHaveBeenNthCalledWith(2, 2)
    expect(update).toHaveBeenCalledWith(7, expect.objectContaining({ courseId: 2, knowledgePointIds: undefined }))
  })
})
