import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { applyKnowledge, assessDifficulty, error, knowledgeTagging, qualityCheck, success, warning } = vi.hoisted(
  () => ({
    applyKnowledge: vi.fn(),
    assessDifficulty: vi.fn(),
    error: vi.fn(),
    knowledgeTagging: vi.fn(),
    qualityCheck: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  }),
)

vi.mock('@/api/submission', () => ({
  applyKnowledgePoints: (...args: unknown[]) => applyKnowledge(...args),
  assessDifficulty: (...args: unknown[]) => assessDifficulty(...args),
  kpTaggingSubmission: (...args: unknown[]) => knowledgeTagging(...args),
  qualityCheckSubmission: (...args: unknown[]) => qualityCheck(...args),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { error, success, warning } }
})

import SubmissionAiTools from '@/admin/views/submission/SubmissionAiTools.vue'

const DialogStub = defineComponent({
  props: { modelValue: Boolean, title: String },
  template: '<section v-if="modelValue" :data-title="title"><slot /><slot name="footer" /></section>',
})

const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})

const PassThroughStub = defineComponent({
  template: '<div><slot /><slot name="header" /></div>',
})

const EmptyStub = defineComponent({
  template: '<div />',
})

const qualityResult = {
  qualityScore: 86,
  summary: '内容完整，可以入库',
  recommendation: 'APPROVE',
  formatCheck: { status: 'PASS', detail: '格式完整' },
  completenessCheck: { status: 'PASS', detail: '信息齐全' },
  answerCheck: { status: 'PASS', detail: '答案正确' },
  analysisCheck: { status: 'WARNING', detail: '解析可补充' },
  knowledgePointCheck: { status: 'PASS', detail: '知识点匹配' },
  riskPoints: [],
  suggestions: ['补充边界说明'],
}

const difficultyResult = {
  suggestedDifficulty: 4,
  originalDifficulty: 3,
  difficultyMatch: false,
  confidence: 'HIGH',
  reason: '需要组合多个概念',
  cognitiveLevel: '分析',
  factors: [{ name: '推理链', description: '步骤较多', impact: 'INCREASE' }],
  summary: '建议标记为较难',
}

function mountTools() {
  return mount(SubmissionAiTools, {
    global: {
      stubs: {
        ElAlert: PassThroughStub,
        ElButton: ButtonStub,
        ElCard: PassThroughStub,
        ElCol: PassThroughStub,
        ElDialog: DialogStub,
        ElEmpty: PassThroughStub,
        ElInput: PassThroughStub,
        ElRate: PassThroughStub,
        ElRow: PassThroughStub,
        ElTable: PassThroughStub,
        ElTableColumn: EmptyStub,
        ElTag: PassThroughStub,
      },
      directives: {
        loading: {},
      },
    },
  })
}

describe('SubmissionAiTools', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    qualityCheck.mockResolvedValue({ code: 0, data: qualityResult })
    knowledgeTagging.mockResolvedValue({
      code: 0,
      data: {
        analysis: '匹配到课程知识点',
        suggestedIds: '12,18',
        recommendations: [{ id: 12, name: '二叉树', courseName: '数据结构', confidence: 'HIGH', reason: '题干相关' }],
      },
    })
    assessDifficulty.mockResolvedValue({ code: 0, data: difficultyResult })
    applyKnowledge.mockResolvedValue({ code: 0, data: {} })
  })

  it('按质检命令加载并展示投稿质量结果', async () => {
    const wrapper = mountTools()

    await wrapper.vm.open('quality', { id: 31 })
    await wrapper.vm.$nextTick()

    expect(qualityCheck).toHaveBeenCalledWith(31)
    expect(wrapper.find('[data-title="AI 质检报告"]').text()).toContain('86 分')
    expect(wrapper.find('[data-title="AI 质检报告"]').text()).toContain('内容完整，可以入库')
  })

  it('按难度命令加载并展示投稿难度结果', async () => {
    const wrapper = mountTools()

    await wrapper.vm.open('difficulty', { id: 32 })
    await wrapper.vm.$nextTick()

    expect(assessDifficulty).toHaveBeenCalledWith(32)
    expect(wrapper.find('[data-title="AI 难度评估报告"]').text()).toContain('需要组合多个概念')
    expect(wrapper.find('[data-title="AI 难度评估报告"]').text()).toContain('建议标记为较难')
  })

  it('将知识点建议应用到当前投稿并通知父页面刷新', async () => {
    const wrapper = mountTools()

    await wrapper.vm.open('tagging', { id: 33 })
    await wrapper.vm.$nextTick()
    const applyButton = wrapper.findAll('button').find((button) => button.text() === '应用到投稿')
    expect(applyButton).toBeDefined()
    await applyButton!.trigger('click')
    await wrapper.vm.$nextTick()

    expect(knowledgeTagging).toHaveBeenCalledWith(33)
    expect(applyKnowledge).toHaveBeenCalledWith(33, '12,18')
    expect(success).toHaveBeenCalledWith('知识点已应用到投稿')
    expect(wrapper.emitted('updated')).toHaveLength(1)
    expect(wrapper.find('[data-title="AI 知识点标注"]').exists()).toBe(false)
  })
})
