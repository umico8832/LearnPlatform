import { mount } from '@vue/test-utils'
import { defineComponent, inject, provide } from 'vue'
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
  props: { beforeClose: Function, modelValue: Boolean },
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
  props: { disabled: Boolean, loading: Boolean },
  emits: ['click'],
  template: '<button type="button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
})

const PassThroughStub = defineComponent({
  template: '<div><slot /></div>',
})
const AlertStub = defineComponent({
  props: { title: String },
  template: '<p role="alert">{{ title }}</p>',
})

const radioGroupUpdate = Symbol('radioGroupUpdate')
const RadioGroupStub = defineComponent({
  props: { disabled: Boolean, modelValue: Number },
  emits: ['update:modelValue'],
  setup(_, { emit }) {
    provide(radioGroupUpdate, (value: number) => emit('update:modelValue', value))
  },
  template: '<div role="radiogroup"><slot /></div>',
})
const RadioStub = defineComponent({
  props: { disabled: Boolean, value: Number },
  setup() {
    return { update: inject<(value: number) => void>(radioGroupUpdate) }
  },
  template:
    '<button type="button" role="radio" :disabled="disabled" @click="update?.(value)" @keydown.right.prevent="update?.(value + 1)"><slot /></button>',
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

const invalidSingleChoice = {
  ...question,
  questionType: 'SINGLE_CHOICE',
  options: [
    { id: 1, content: '选项 A', optionLabel: 'A', isCorrect: 1, sortOrder: 0 },
    { id: 2, content: '选项 B', optionLabel: 'B', isCorrect: 1, sortOrder: 1 },
  ],
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
        ElAlert: AlertStub,
        ElCheckbox: PassThroughStub,
        ElCol: PassThroughStub,
        ElDialog: DialogStub,
        ElForm: FormStub,
        ElFormItem: PassThroughStub,
        ElInput: PassThroughStub,
        ElInputNumber: PassThroughStub,
        ElOption: PassThroughStub,
        ElRate: PassThroughStub,
        ElRadio: RadioStub,
        ElRadioGroup: RadioGroupStub,
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

  it('拒绝保存历史双正确单选题，直到管理员明确修正为一个正确答案', async () => {
    const wrapper = mountEditor()
    wrapper.vm.open(invalidSingleChoice)
    await wrapper.vm.$nextTick()

    const saveButton = wrapper.findAll('button').find((button) => button.text() === '更新')
    await saveButton!.trigger('click')
    await wrapper.vm.$nextTick()

    expect(update).not.toHaveBeenCalled()
    expect(warning).toHaveBeenCalledWith('单选题必须且只能有 1 个正确答案')
  })

  it('让管理员通过单选正确答案控件修复历史双正确配置后再保存', async () => {
    const wrapper = mountEditor()
    wrapper.vm.open(invalidSingleChoice)
    await wrapper.vm.$nextTick()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '正确答案')!
      .trigger('click')
    const saveButton = wrapper.findAll('button').find((button) => button.text() === '更新')
    await saveButton!.trigger('click')
    await wrapper.vm.$nextTick()

    expect(update).toHaveBeenCalledWith(
      7,
      expect.objectContaining({
        options: [
          expect.objectContaining({ optionLabel: 'A', isCorrect: 1 }),
          expect.objectContaining({ optionLabel: 'B', isCorrect: 0 }),
        ],
      }),
    )
  })

  it('uses one named radio group and lets keyboard selection choose the next exclusive answer', async () => {
    const wrapper = mountEditor()
    wrapper.vm.open(invalidSingleChoice)
    await wrapper.vm.$nextTick()

    const group = wrapper.get('[role="radiogroup"]')
    expect(wrapper.findAll('[role="radiogroup"]')).toHaveLength(1)
    const answers = wrapper.findAll('[role="radio"]')
    expect(answers).toHaveLength(2)
    await answers[0].trigger('keydown.right')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '更新')!
      .trigger('click')
    await wrapper.vm.$nextTick()

    expect(group.attributes('aria-label')).toBe('正确答案')
    expect(update).toHaveBeenCalledWith(
      7,
      expect.objectContaining({
        options: [expect.objectContaining({ isCorrect: 0 }), expect.objectContaining({ isCorrect: 1 })],
      }),
    )
  })

  it('将历史无效判断题显示为固定的正确和错误，并要求管理员选择一个合法答案', async () => {
    const wrapper = mountEditor()
    wrapper.vm.open({
      ...question,
      questionType: 'TRUE_FALSE',
      options: [
        { id: 1, content: 'MAYBE', optionLabel: 'A', isCorrect: 1, sortOrder: 0 },
        { id: 2, content: '错误', optionLabel: 'B', isCorrect: 0, sortOrder: 1 },
      ],
    })
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('判断题答案只能是正确/错误')
    expect(wrapper.text()).toContain('正确')
    expect(wrapper.text()).toContain('错误')
    const answerButtons = wrapper.findAll('button').filter((button) => button.text() === '正确答案')
    await answerButtons[1].trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '更新')!
      .trigger('click')
    await wrapper.vm.$nextTick()

    expect(update).toHaveBeenCalledWith(
      7,
      expect.objectContaining({
        options: [
          expect.objectContaining({ content: '正确', isCorrect: 0 }),
          expect.objectContaining({ content: '错误', isCorrect: 1 }),
        ],
      }),
    )
  })

  it('keeps a recognized legacy true/false alias as the selected fixed answer', async () => {
    const wrapper = mountEditor()
    wrapper.vm.open({
      ...question,
      questionType: 'TRUE_FALSE',
      options: [
        { id: 1, content: '对', optionLabel: 'A', isCorrect: 1, sortOrder: 0 },
        { id: 2, content: '错', optionLabel: 'B', isCorrect: 0, sortOrder: 1 },
      ],
    })
    await wrapper.vm.$nextTick()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '更新')!
      .trigger('click')
    await wrapper.vm.$nextTick()

    expect(update).toHaveBeenCalledWith(
      7,
      expect.objectContaining({
        options: [
          expect.objectContaining({ content: '正确', isCorrect: 1 }),
          expect.objectContaining({ content: '错误', isCorrect: 0 }),
        ],
      }),
    )
  })

  it('keeps the form open with the same values after a save failure so the administrator can retry', async () => {
    update.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({ code: 0, data: {} })
    const wrapper = mountEditor()
    wrapper.vm.open(question)
    await wrapper.vm.$nextTick()
    const saveButton = () => wrapper.findAll('button').find((button) => button.text() === '更新')!

    await saveButton().trigger('click')
    await wrapper.vm.$nextTick()
    expect(saveButton().exists()).toBe(true)
    expect(wrapper.emitted('saved')).toBeUndefined()
    expect(update).toHaveBeenLastCalledWith(7, expect.objectContaining({ content: question.content, score: 5 }))

    await saveButton().trigger('click')
    await wrapper.vm.$nextTick()
    expect(update).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })

  it('locks a valid form before asynchronous validation so rapid save clicks send one request', async () => {
    let resolveUpdate!: (value: { code: number; data: object }) => void
    update.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveUpdate = resolve
        }),
    )
    const wrapper = mountEditor()
    wrapper.vm.open(question)
    await wrapper.vm.$nextTick()
    const saveButton = wrapper.findAll('button').find((button) => button.text() === '更新')

    await Promise.all([saveButton!.trigger('click'), saveButton!.trigger('click')])
    expect(update).toHaveBeenCalledTimes(1)
    expect(saveButton!.attributes('disabled')).toBeDefined()
    const close = vi.fn()
    const beforeClose = wrapper.findComponent(DialogStub).props('beforeClose') as (done: () => void) => void
    beforeClose(close)
    expect(close).not.toHaveBeenCalled()
    wrapper.vm.open({ ...question, id: 8, content: '另一道题' })
    expect(update).toHaveBeenLastCalledWith(7, expect.objectContaining({ content: question.content }))
    resolveUpdate({ code: 0, data: {} })
    await wrapper.vm.$nextTick()
  })
})
