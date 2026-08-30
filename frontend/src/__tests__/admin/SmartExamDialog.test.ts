import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { create, error, preview, success } = vi.hoisted(() => ({
  create: vi.fn(),
  error: vi.fn(),
  preview: vi.fn(),
  success: vi.fn(),
}))

vi.mock('@/api/exam', () => ({
  smartExamCreate: (...args: unknown[]) => create(...args),
  smartExamPreview: (...args: unknown[]) => preview(...args),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { error, success } }
})

import SmartExamDialog from '@/admin/views/exam/SmartExamDialog.vue'

const DialogStub = defineComponent({
  props: { modelValue: Boolean },
  template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
})

const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})

const PassThroughStub = defineComponent({
  template: '<div><slot /></div>',
})

const previewResult = {
  title: '自适应练习卷',
  description: '覆盖两个知识点',
  courseId: 1,
  courseName: '数据结构',
  questionCount: 20,
  totalScore: 100,
  duration: 60,
  knowledgePointDistribution: { 树: 10, 图: 10 },
  difficultyDistribution: { '★★★': 20 },
  questionIds: [1, 2],
  recommendation: '难度均衡',
}

function mountDialog() {
  return mount(SmartExamDialog, {
    props: { courses: [{ id: 1, name: '数据结构' }] },
    global: {
      stubs: {
        ElButton: ButtonStub,
        ElDialog: DialogStub,
        ElAlert: PassThroughStub,
        ElCol: PassThroughStub,
        ElDescriptions: PassThroughStub,
        ElDescriptionsItem: PassThroughStub,
        ElEmpty: PassThroughStub,
        ElForm: PassThroughStub,
        ElFormItem: PassThroughStub,
        ElInput: PassThroughStub,
        ElInputNumber: PassThroughStub,
        ElOption: PassThroughStub,
        ElProgress: PassThroughStub,
        ElRadioButton: PassThroughStub,
        ElRadioGroup: PassThroughStub,
        ElRow: PassThroughStub,
        ElSelect: PassThroughStub,
        ElSlider: PassThroughStub,
        ElSwitch: PassThroughStub,
      },
    },
  })
}

describe('SmartExamDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    preview.mockResolvedValue({ code: 0, data: previewResult })
    create.mockResolvedValue({ code: 0, data: previewResult })
  })

  it('打开后使用默认规则生成预览', async () => {
    const wrapper = mountDialog()

    wrapper.vm.open()
    await wrapper.vm.$nextTick()
    const previewButton = wrapper.findAll('button').find((button) => button.text() === '生成预览')
    expect(previewButton).toBeDefined()
    await previewButton!.trigger('click')
    await flushPromises()

    expect(preview).toHaveBeenCalledWith({
      courseId: undefined,
      questionCount: 20,
      difficultyMode: 'ADAPTIVE',
      includeWrongQuestions: true,
      title: '',
      duration: 60,
    })
    expect(wrapper.text()).toContain('自适应练习卷')
    expect(wrapper.find('[title="难度均衡"]').exists()).toBe(true)
  })

  it('确认预览后创建试卷并通知父页面刷新', async () => {
    const wrapper = mountDialog()

    wrapper.vm.open()
    await wrapper.vm.$nextTick()
    const previewButton = wrapper.findAll('button').find((button) => button.text() === '生成预览')
    expect(previewButton).toBeDefined()
    await previewButton!.trigger('click')
    await flushPromises()
    const createButton = wrapper.findAll('button').find((button) => button.text() === '确认创建')
    expect(createButton).toBeDefined()
    await createButton!.trigger('click')
    await flushPromises()

    expect(create).toHaveBeenCalledWith(previewResult)
    expect(success).toHaveBeenCalledWith('智能试卷「自适应练习卷」已创建，共 20 题')
    expect(wrapper.emitted('created')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('自适应练习卷')
  })
})
