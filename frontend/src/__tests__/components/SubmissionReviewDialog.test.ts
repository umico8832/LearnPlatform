import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { QuestionSubmissionVO } from '@/api/submission'

const { auth, generateReviewComment, message, reviewSubmission } = vi.hoisted(() => ({
  auth: { listener: undefined as undefined | (() => void), version: 1 },
  generateReviewComment: vi.fn(),
  message: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
  reviewSubmission: vi.fn(),
}))

vi.mock('@/utils/auth', () => ({
  getAuthSessionVersion: () => auth.version,
  onAuthSessionChange: (fn: () => void) => {
    auth.listener = fn
    return () => undefined
  },
}))
vi.mock('@/api/submission', () => ({ generateReviewComment, reviewSubmission }))
vi.mock('element-plus', () => ({ ElMessage: message }))

import SubmissionReviewDialog from '@/admin/views/submission/SubmissionReviewDialog.vue'

const submission = { id: 12, content: '待审核题目' } as QuestionSubmissionVO

describe('SubmissionReviewDialog', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    auth.version = 1
    auth.listener = undefined
    generateReviewComment.mockResolvedValue({ code: 0, data: '答案缺少推导说明' })
    reviewSubmission.mockResolvedValue({ code: 0 })
  })

  function mountDialog() {
    return mount(SubmissionReviewDialog, {
      global: {
        stubs: {
          'el-dialog': {
            name: 'ElDialog',
            props: ['modelValue', 'closeOnClickModal', 'closeOnPressEscape', 'showClose'],
            template: '<section v-if="modelValue"><slot /><slot name="footer" /></section>',
          },
          'el-form': { template: '<form><slot /></form>' },
          'el-form-item': { template: '<div><slot /></div>' },
          'el-input': {
            props: ['modelValue'],
            emits: ['update:modelValue'],
            template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
          },
          'el-button': {
            emits: ['click'],
            template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
          },
        },
      },
    })
  }

  it('requires a reason when rejecting a submission', async () => {
    const wrapper = mountDialog()
    ;(wrapper.vm as unknown as { open: (value: QuestionSubmissionVO, action: number) => void }).open(submission, 2)
    await wrapper.vm.$nextTick()

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('确认拒绝'))!
      .trigger('click')

    expect(message.warning).toHaveBeenCalledWith('拒绝时请填写审核意见')
    expect(reviewSubmission).not.toHaveBeenCalled()
  })

  it('applies an AI comment and emits a refresh fact after review', async () => {
    const wrapper = mountDialog()
    ;(wrapper.vm as unknown as { open: (value: QuestionSubmissionVO, action: number) => void }).open(submission, 2)
    await wrapper.vm.$nextTick()

    await (wrapper.vm as unknown as { generateComment: () => Promise<void> }).generateComment()
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('确认拒绝'))!
      .trigger('click')
    await flushPromises()

    expect(generateReviewComment).toHaveBeenCalledWith(12, { errorDisplay: 'inline' })
    expect(reviewSubmission).toHaveBeenCalledWith(
      12,
      { status: 2, reviewComment: '答案缺少推导说明' },
      { errorDisplay: 'inline' },
    )
    expect(wrapper.emitted('reviewed')).toHaveLength(1)
  })
  it('does not submit twice while the first review is pending', async () => {
    let resolve!: (value: { code: number }) => void
    reviewSubmission.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = mountDialog()
    ;(
      wrapper.vm as unknown as {
        open: (value: QuestionSubmissionVO, action: number) => void
        submit: () => Promise<void>
      }
    ).open(submission, 1)
    const vm = wrapper.vm as unknown as { submit: () => Promise<void> }
    const first = vm.submit()
    const second = vm.submit()
    expect(reviewSubmission).toHaveBeenCalledTimes(1)
    resolve({ code: 0 })
    await Promise.all([first, second])
  })
  it('ignores a late generated comment after the dialog changes target', async () => {
    let resolve!: (value: { code: number; data: string }) => void
    generateReviewComment.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = mountDialog()
    const vm = wrapper.vm as unknown as {
      open: (value: QuestionSubmissionVO, action: number) => void
      generateComment: () => Promise<void>
    }
    vm.open(submission, 1)
    const pending = vm.generateComment()
    vm.open({ ...submission, id: 13 }, 1)
    resolve({ code: 0, data: '旧意见' })
    await pending
    expect(wrapper.find('textarea').element.value).toBe('')
  })
  it('does not emit or close a newly opened target after session changes during review', async () => {
    let resolve!: (value: { code: number }) => void
    reviewSubmission.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = mountDialog()
    const vm = wrapper.vm as unknown as {
      open: (value: QuestionSubmissionVO, action: number) => void
      submit: () => Promise<void>
    }
    vm.open(submission, 1)
    const pending = vm.submit()
    auth.version++
    auth.listener?.()
    vm.open({ ...submission, id: 14 }, 1)
    resolve({ code: 0 })
    await pending
    expect(wrapper.emitted('reviewed')).toBeUndefined()
    expect(wrapper.find('textarea').exists()).toBe(true)
  })
  it('allows idle closing and blocks it only while generating or saving', async () => {
    let resolve!: (value: { code: number; data: string }) => void
    generateReviewComment.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = mountDialog()
    const vm = wrapper.vm as unknown as {
      open: (value: QuestionSubmissionVO, action: number) => void
      generateComment: () => Promise<void>
    }
    vm.open(submission, 1)
    await wrapper.vm.$nextTick()
    const dialog = wrapper.findComponent({ name: 'ElDialog' })
    expect(dialog.props('showClose')).toBe(true)
    expect(dialog.props('closeOnClickModal')).toBe(true)
    expect(dialog.props('closeOnPressEscape')).toBe(true)
    const pending = vm.generateComment()
    await wrapper.vm.$nextTick()
    expect(dialog.props('showClose')).toBe(false)
    expect(dialog.props('closeOnClickModal')).toBe(false)
    expect(dialog.props('closeOnPressEscape')).toBe(false)
    resolve({ code: 0, data: '意见' })
    await pending
    await wrapper.vm.$nextTick()
    expect(dialog.props('showClose')).toBe(true)
    wrapper.unmount()
  })
  it('retries failed AI generation without submitting and preserves a failed review draft', async () => {
    generateReviewComment.mockRejectedValueOnce(new Error('network'))
    const wrapper = mountDialog()
    const vm = wrapper.vm as unknown as {
      open: (value: QuestionSubmissionVO, action: number) => void
      generateComment: () => Promise<void>
      submit: () => Promise<void>
    }
    vm.open(submission, 2)
    await vm.generateComment()
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[role="alert"]').text()).toContain('生成审核意见失败')
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(generateReviewComment).toHaveBeenCalledTimes(2)
    expect(reviewSubmission).not.toHaveBeenCalled()
    expect(wrapper.get('textarea').element.value).toBe('答案缺少推导说明')
    reviewSubmission.mockRejectedValueOnce(new Error('network'))
    await vm.submit()
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[role="alert"]').text()).toContain('审核失败')
    expect(wrapper.get('textarea').element.value).toBe('答案缺少推导说明')
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(generateReviewComment).toHaveBeenCalledTimes(2)
    expect(reviewSubmission).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('reviewed')).toHaveLength(1)
    wrapper.unmount()
  })
})
