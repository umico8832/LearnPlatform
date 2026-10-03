import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { auth, mockList, mockReview, mockConfirm, mockPrompt, mockSuccess } = vi.hoisted(() => ({
  auth: { version: 1, listener: undefined as undefined | (() => void) },
  mockList: vi.fn(),
  mockReview: vi.fn(),
  mockConfirm: vi.fn(),
  mockPrompt: vi.fn(),
  mockSuccess: vi.fn(),
}))

vi.mock('@/utils/auth', () => ({
  getAuthSessionVersion: () => auth.version,
  onAuthSessionChange: (listener: () => void) => {
    auth.listener = listener
    return () => undefined
  },
}))
vi.mock('@/api/aiVariantReview', () => ({
  getAiVariantReviews: (...args: unknown[]) => mockList(...args),
  reviewAiVariant: (...args: unknown[]) => mockReview(...args),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessageBox: { confirm: mockConfirm, prompt: mockPrompt }, ElMessage: { success: mockSuccess } }
})

import AiVariantReviewView from '@/admin/views/AiVariantReviewView.vue'

const stubs = {
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-card': { template: '<section><slot /></section>' },
  'el-pagination': { template: '<nav />' },
  'el-empty': { template: '<p>暂无待审查变式题</p>' },
}

describe('AiVariantReviewView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockList.mockResolvedValue({
      data: {
        records: [
          {
            id: 12,
            motherQuestionId: 21,
            motherQuestionContent: '栈的特点是什么？',
            courseName: '408 数据结构',
            questionContent: '下列关于栈的说法正确的是？',
            questionType: 'SINGLE_CHOICE',
            options: [{ label: 'A', content: '后进先出' }],
            correctAnswer: 'A',
            analysis: '栈遵循后进先出。',
            difficulty: 2,
            reviewStatus: 'PENDING',
          },
        ],
        total: 1,
        current: 1,
        size: 10,
      },
    })
    mockConfirm.mockResolvedValue('confirm')
    mockPrompt.mockResolvedValue({ value: '原拒绝说明' })
    mockReview.mockResolvedValue({ data: { reviewStatus: 'APPROVED' } })
  })

  it('同时展示母题、变式答案与解析并可批准发布', async () => {
    const wrapper = mount(AiVariantReviewView, { global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('母题 #21')
    expect(wrapper.text()).toContain('下列关于栈的说法正确的是？')
    expect(wrapper.text()).toContain('正确答案：A')
    const vm = wrapper.vm as unknown as { approve: (id: number) => Promise<void> }
    await vm.approve(12)

    expect(mockReview).toHaveBeenCalledWith(12, 'APPROVE', '管理员核验题干、选项、答案与解析后通过', {
      errorDisplay: 'inline',
    })
    expect(mockList).toHaveBeenCalledTimes(2)
  })

  it('确认期间会话变化不发送审核写请求', async () => {
    let resolve!: () => void
    mockConfirm.mockReturnValueOnce(
      new Promise<void>((done) => {
        resolve = done
      }),
    )
    const wrapper = mount(AiVariantReviewView, { global: { stubs } })
    await flushPromises()
    const vm = wrapper.vm as unknown as { approve: (id: number) => Promise<void> }
    const pending = vm.approve(12)
    auth.version++
    auth.listener?.()
    resolve()
    await pending
    expect(mockReview).not.toHaveBeenCalled()
  })

  it('驳回失败后重试保留 REJECT 和原说明', async () => {
    mockReview.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ data: {} })
    const wrapper = mount(AiVariantReviewView, { global: { stubs } })
    await flushPromises()
    const vm = wrapper.vm as unknown as { reject: (id: number) => Promise<void>; retry: (item: { id: number }) => void }
    await vm.reject(12)
    vm.retry({ id: 12 })
    await flushPromises()
    expect(mockReview).toHaveBeenLastCalledWith(12, 'REJECT', '原拒绝说明', { errorDisplay: 'inline' })
    expect(mockSuccess).toHaveBeenLastCalledWith('已拒绝该变式题')
  })
})
