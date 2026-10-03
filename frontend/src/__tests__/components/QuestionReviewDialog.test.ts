import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { removeToken } from '@/utils/auth'
const api = vi.hoisted(() => ({
  records: vi.fn(),
  suggestion: vi.fn(),
  submit: vi.fn(),
  message: { success: vi.fn(), warning: vi.fn() },
}))
vi.mock('@/api/question', () => ({
  getReviewRecords: api.records,
  getReviewSuggestion: api.suggestion,
  performReReview: api.submit,
}))
vi.mock('element-plus', () => ({ ElMessage: api.message }))
import QuestionReviewDialog from '@/admin/views/question/QuestionReviewDialog.vue'

const pass = { template: '<div><slot name="title"/><slot/></div>' }
const wrappers: ReturnType<typeof mount>[] = []
const target = (id = 12) => ({
  id,
  content: `题干${id}`,
  questionType: 'SINGLE_CHOICE',
  difficulty: 3,
  sourceType: 'MANUAL',
  reviewRounds: 0,
})
function render() {
  const wrapper = mount(QuestionReviewDialog, {
    global: {
      stubs: {
        'el-dialog': {
          props: ['modelValue'],
          template: '<section v-if="modelValue"><slot/><slot name="footer"/></section>',
        },
        'el-button': {
          props: ['disabled', 'loading'],
          template: '<button :disabled="disabled || loading"><slot/></button>',
        },
        'el-descriptions': pass,
        'el-descriptions-item': pass,
        'el-tag': pass,
        'el-form': pass,
        'el-form-item': pass,
        'el-radio-group': pass,
        'el-radio-button': pass,
        'el-input': {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)"/>',
        },
        'el-rate': true,
        'el-alert': pass,
        'el-timeline': pass,
        'el-timeline-item': pass,
        'el-card': pass,
      },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}
async function open(wrapper: ReturnType<typeof render>, id = 12) {
  await (wrapper.vm as unknown as { open: (question: object) => Promise<void> }).open(target(id))
  await flushPromises()
}
async function click(wrapper: ReturnType<typeof render>, label: string) {
  const button = wrapper.findAll('button').find((item) => item.text().includes(label))
  expect(button).toBeDefined()
  await button!.trigger('click')
  await flushPromises()
}
beforeEach(() => {
  vi.clearAllMocks()
  api.records.mockResolvedValue({ code: 0, data: [] })
  api.suggestion.mockResolvedValue({
    code: 0,
    data: {
      recommendation: 'REVISE',
      confidenceScore: 88,
      summary: '补充边界条件',
      suggestedContent: '修订后的题干',
      suggestedDifficulty: 4,
    },
  })
  api.submit.mockResolvedValue({ code: 0 })
})
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))

describe('QuestionReviewDialog', () => {
  it('loads context, applies an AI suggestion and submits the explicit review', async () => {
    const wrapper = render()
    await open(wrapper)
    await click(wrapper, 'AI 复审建议')
    await click(wrapper, '应用到表单')
    await click(wrapper, '提交复审')
    expect(api.submit).toHaveBeenCalledWith(
      12,
      { action: 'REVISE', newContent: '修订后的题干', newDifficulty: 4, comment: '补充边界条件' },
      { errorDisplay: 'inline' },
    )
    expect(wrapper.emitted('reviewed')).toHaveLength(1)
  })
  it('keeps history failure distinct from no records and retries in place', async () => {
    api.records.mockRejectedValueOnce(new Error('记录读取失败'))
    const wrapper = render()
    await open(wrapper)
    expect(wrapper.text()).toContain('记录读取失败')
    expect(wrapper.text()).not.toContain('暂无复审记录')
    await click(wrapper, '重试读取')
    expect(wrapper.text()).toContain('暂无复审记录')
  })
  it('ignores an old question suggestion after another question is opened', async () => {
    let resolve!: (value: unknown) => void
    api.suggestion.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = render()
    await open(wrapper)
    await click(wrapper, 'AI 复审建议')
    await open(wrapper, 13)
    resolve({ code: 0, data: { recommendation: 'APPROVE', summary: '旧题建议' } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('旧题建议')
  })
  it('locks duplicate submissions and preserves the draft on failure', async () => {
    let reject!: (error: unknown) => void
    api.submit.mockReturnValueOnce(
      new Promise((_, fail) => {
        reject = fail
      }),
    )
    const wrapper = render()
    await open(wrapper)
    await wrapper.get('textarea').setValue('复审意见草稿')
    await click(wrapper, '提交复审')
    await click(wrapper, '提交复审')
    expect(api.submit).toHaveBeenCalledTimes(1)
    reject(new Error('暂时无法保存'))
    await flushPromises()
    expect(wrapper.text()).toContain('暂时无法保存')
    expect(wrapper.get('textarea').element.value).toBe('复审意见草稿')
    await click(wrapper, '提交复审')
    expect(wrapper.emitted('reviewed')).toHaveLength(1)
  })
  it('closes and ignores a late write when the session changes', async () => {
    let resolve!: (value: unknown) => void
    api.submit.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = render()
    await open(wrapper)
    await wrapper.get('textarea').setValue('账号隔离')
    await click(wrapper, '提交复审')
    removeToken()
    resolve({ code: 0 })
    await flushPromises()
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.emitted('reviewed')).toBeUndefined()
  })
})
