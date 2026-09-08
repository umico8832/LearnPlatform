import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import ElementPlus from 'element-plus'
import type { PrivateExamDraft } from '@/api/exam'
import PrivateExamDraftReview from '@/components/exam/PrivateExamDraftReview.vue'

const { generate, review, success, error } = vi.hoisted(() => ({
  generate: vi.fn(),
  review: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
}))
vi.mock('@/api/exam', () => ({
  generatePrivateExamDraftAnswer: (...args: unknown[]) => generate(...args),
  reviewPrivateExamDraftQuestion: (...args: unknown[]) => review(...args),
  downloadPrivateExamDraftSourceFile: vi.fn(),
}))
vi.mock('element-plus', async (original) => ({
  ...(await original<typeof import('element-plus')>()),
  ElMessage: { success, error, warning: vi.fn() },
}))

type Question = PrivateExamDraft['questions'][number]
type Response = { code: number; data?: PrivateExamDraft; message?: string }
type ReviewVm = {
  generateDraftAnswer: (id: number) => Promise<void>
  reviewDraftQuestion: (id: number) => Promise<void>
}

function fixture(id = 31): PrivateExamDraft {
  return {
    id,
    title: `草稿${id}`,
    courseId: 10,
    duration: 30,
    status: 'AI_GENERATED',
    confirmedPaperId: null,
    sourceName: 'paper.md',
    sourceFormat: 'MARKDOWN',
    originalFileAvailable: false,
    reviewedQuestionCount: 0,
    questionCount: 2,
    createTime: '',
    questions: [41, 42].map((questionId, index) => ({
      id: questionId,
      sortOrder: index + 1,
      content: `题目${index + 1}`,
      questionType: 'SINGLE_CHOICE',
      score: 1,
      options: [
        { label: 'A', content: '栈' },
        { label: 'B', content: '队列' },
      ],
      originalAnswerLabels: [],
      originalAnalysis: null,
      aiAnswerLabels: ['A'],
      aiAnalysis: `建议${index + 1}`,
      generationStatus: 'GENERATED',
      finalAnswerLabels: [],
      finalAnalysis: null,
      reviewStatus: 'PENDING',
    })),
  }
}

function changed(draft: PrivateExamDraft, id: number, update: Partial<Question>) {
  const questions = draft.questions.map((q) => (q.id === id ? { ...q, ...update } : q))
  const count = questions.filter((q) => q.reviewStatus === 'REVIEWED').length
  return {
    ...draft,
    questions,
    reviewedQuestionCount: count,
    status: (count === questions.length ? 'READY' : count ? 'REVIEWING' : 'AI_GENERATED') as PrivateExamDraft['status'],
  }
}

function deferred() {
  let resolve!: (response: Response) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<Response>((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

const wrappers: ReturnType<typeof mount>[] = []
function setup(draft = fixture()) {
  const current = ref(draft)
  const wrapper = mount(
    defineComponent({
      components: { PrivateExamDraftReview },
      setup: () => ({ current }),
      template: '<PrivateExamDraftReview :draft="current" @updated="current = $event" />',
    }),
    { global: { plugins: [ElementPlus] } },
  )
  wrappers.push(wrapper)
  const child = wrapper.findComponent(PrivateExamDraftReview)
  return {
    wrapper,
    child,
    current,
    vm: child.vm as unknown as ReviewVm,
    row: (index: number) => wrapper.findAll('.draft-question')[index]!,
  }
}

beforeEach(() => vi.clearAllMocks())
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
})

describe('私有试卷逐题复核编辑与请求边界', () => {
  it('同时编辑两题，确认一题后保留另一题未保存的答案和解析', async () => {
    const draft = fixture()
    const { row, current } = setup(draft)
    for (const index of [0, 1]) {
      await row(index).findAll('input[type="checkbox"]')[0]!.setValue(false)
      await row(index).findAll('input[type="checkbox"]')[1]!.setValue(true)
      await row(index)
        .get('textarea')
        .setValue(`人工解析${index + 1}`)
    }
    review.mockResolvedValue({
      code: 0,
      data: changed(draft, 41, {
        reviewStatus: 'REVIEWED',
        finalAnswerLabels: ['B'],
        finalAnalysis: '人工解析1',
      }),
    })
    await row(0).get('button').trigger('click')
    await flushPromises()
    expect(review).toHaveBeenCalledWith(31, 41, { answerLabels: ['B'], analysis: '人工解析1' })
    expect(current.value.questions[0]!.reviewStatus).toBe('REVIEWED')
    expect(row(1).get('textarea').element.value).toBe('人工解析2')
    expect(row(1).findAll('input')[1]!.element.checked).toBe(true)
    expect(row(1).findAll('input')[0]!.element.checked).toBe(false)
  })

  it('AI更新按字段合并：保留编辑过的答案和清空的解析，更新未编辑字段', async () => {
    const { current, row } = setup()
    await row(0).findAll('input')[0]!.setValue(false)
    await row(0).findAll('input')[1]!.setValue(true)
    await row(1).get('textarea').setValue('')
    current.value = changed(changed(current.value, 41, { aiAnalysis: '新解析1' }), 42, {
      aiAnswerLabels: ['B'],
      aiAnalysis: '新解析2',
    })
    await flushPromises()
    expect(row(0).findAll('input')[1]!.element.checked).toBe(true)
    expect(row(0).get('textarea').element.value).toBe('新解析1')
    expect(row(1).get('textarea').element.value).toBe('')
    expect(row(1).findAll('input')[1]!.element.checked).toBe(true)
    expect(review).not.toHaveBeenCalled()
  })

  it('生成中可编辑本题，建议回来后保留输入且仍需显式复核', async () => {
    const pending = changed(fixture(), 41, { generationStatus: 'PENDING', aiAnswerLabels: [], aiAnalysis: null })
    const response = deferred()
    generate.mockReturnValue(response.promise)
    const { row, vm, current } = setup(pending)
    const request = vm.generateDraftAnswer(41)
    await flushPromises()
    await row(0).findAll('input')[1]!.setValue(true)
    await row(0).get('textarea').setValue('我自己的依据')
    response.resolve({ code: 0, data: fixture() })
    await request
    await flushPromises()
    expect(row(0).get('textarea').element.value).toBe('我自己的依据')
    expect(row(0).findAll('input')[1]!.element.checked).toBe(true)
    expect(current.value.questions[0]!.reviewStatus).toBe('PENDING')
    expect(current.value.questions[0]!.finalAnswerLabels).toEqual([])
    expect(review).not.toHaveBeenCalled()
  })

  it('按题去重并顺序发送复核，等待期间锁定提交输入，最后采用服务端READY状态', async () => {
    const first = deferred()
    const second = deferred()
    review.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const { row, vm, current } = setup()
    const one = vm.reviewDraftQuestion(41)
    const duplicate = vm.reviewDraftQuestion(41)
    const two = vm.reviewDraftQuestion(42)
    await flushPromises()
    expect(review).toHaveBeenCalledTimes(1)
    expect(row(0).get('textarea').attributes('disabled')).toBeDefined()
    expect(row(1).get('textarea').attributes('disabled')).toBeDefined()
    expect(row(1).text()).toContain('等待')
    const firstDraft = changed(fixture(), 41, {
      reviewStatus: 'REVIEWED',
      finalAnswerLabels: ['B'],
      finalAnalysis: '服务端解析1',
    })
    first.resolve({ code: 0, data: firstDraft })
    await one
    await duplicate
    await flushPromises()
    expect(review).toHaveBeenCalledTimes(2)
    expect(row(0).get('textarea').element.value).toBe('服务端解析1')
    expect(row(1).text()).not.toContain('等待')
    second.resolve({
      code: 0,
      data: changed(firstDraft, 42, {
        reviewStatus: 'REVIEWED',
        finalAnswerLabels: ['A'],
        finalAnalysis: '服务端解析2',
      }),
    })
    await two
    await flushPromises()
    expect(current.value.status).toBe('READY')
    expect(current.value.reviewedQuestionCount).toBe(2)
    await vm.reviewDraftQuestion(41)
    expect(review).toHaveBeenCalledTimes(2)
  })

  it('失败按题显示并保留输入，不阻塞下一题，支持失败题重试', async () => {
    const failure = deferred()
    review.mockReturnValueOnce(failure.promise).mockResolvedValueOnce({ code: 9, message: '第二题校验失败' })
    const { row, vm } = setup()
    await row(0).get('textarea').setValue('不能丢失的解析')
    const first = vm.reviewDraftQuestion(41)
    const next = vm.reviewDraftQuestion(42)
    await flushPromises()
    failure.reject(new Error('network unavailable'))
    await Promise.all([first, next])
    await flushPromises()
    expect(row(0).text()).toContain('复核失败')
    expect(row(1).text()).toContain('第二题校验失败')
    expect(row(0).get('textarea').element.value).toBe('不能丢失的解析')
    expect(row(0).get('textarea').attributes('disabled')).toBeUndefined()
    review.mockResolvedValueOnce({
      code: 0,
      data: changed(fixture(), 41, {
        reviewStatus: 'REVIEWED',
        finalAnswerLabels: ['A'],
        finalAnalysis: '不能丢失的解析',
      }),
    })
    await vm.reviewDraftQuestion(41)
    await flushPromises()
    expect(review).toHaveBeenLastCalledWith(31, 41, { answerLabels: ['A'], analysis: '不能丢失的解析' })
    expect(row(0).text()).not.toContain('复核失败')
    expect(row(1).text()).toContain('第二题校验失败')
  })

  it('草稿切换后取消旧队列，忽略晚到的成功响应及其状态清理', async () => {
    const old = deferred()
    const latest = deferred()
    review.mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise)
    const { vm, current, row, child } = setup()
    const oldRequest = vm.reviewDraftQuestion(41)
    const oldQueued = vm.reviewDraftQuestion(42)
    await flushPromises()
    current.value = fixture(32)
    await flushPromises()
    const newRequest = vm.reviewDraftQuestion(41)
    await flushPromises()
    latest.resolve({
      code: 0,
      data: changed(fixture(32), 41, {
        reviewStatus: 'REVIEWED',
        finalAnswerLabels: ['B'],
        finalAnalysis: '新草稿答案',
      }),
    })
    await newRequest
    old.resolve({ code: 0, data: changed(fixture(), 41, { reviewStatus: 'REVIEWED', finalAnalysis: '旧草稿答案' }) })
    await Promise.all([oldRequest, oldQueued])
    await flushPromises()
    expect(review.mock.calls.map((args) => args.slice(0, 2))).toEqual([
      [31, 41],
      [32, 41],
    ])
    expect(current.value.id).toBe(32)
    expect(row(0).get('textarea').element.value).toBe('新草稿答案')
    expect(child.emitted('updated')).toHaveLength(1)
    expect(success).toHaveBeenCalledTimes(1)
  })

  it('卸载后晚到的失败不再反馈或发送排队请求', async () => {
    const pending = deferred()
    review.mockReturnValue(pending.promise)
    const { vm, wrapper } = setup()
    const first = vm.reviewDraftQuestion(41)
    const second = vm.reviewDraftQuestion(42)
    await flushPromises()
    wrapper.unmount()
    pending.reject(new Error('late failure'))
    await Promise.all([first, second])
    expect(review).toHaveBeenCalledTimes(1)
    expect(error).not.toHaveBeenCalled()
  })
  it('生成去重并与复核互斥，生成失败保留输入且可重试，不阻塞另一题', async () => {
    const draft = changed(fixture(), 41, { generationStatus: 'PENDING', aiAnswerLabels: [], aiAnalysis: null })
    const pending = deferred()
    generate.mockReturnValueOnce(pending.promise)
    const reviewed = changed(draft, 42, { reviewStatus: 'REVIEWED', finalAnswerLabels: ['A'], finalAnalysis: '建议2' })
    review.mockResolvedValueOnce({ code: 0, data: reviewed })
    const { row, vm, current } = setup(draft)
    await row(0).get('textarea').setValue('生成失败也要保留')
    const first = vm.generateDraftAnswer(41)
    const duplicate = vm.generateDraftAnswer(41)
    await vm.reviewDraftQuestion(41)
    const other = vm.reviewDraftQuestion(42)
    await flushPromises()
    expect(generate).toHaveBeenCalledTimes(1)
    expect(review).not.toHaveBeenCalled()
    expect(row(0).get('textarea').attributes('disabled')).toBeUndefined()
    expect(row(0).text()).toContain('正在生成')
    expect(row(1).text()).toContain('等待')
    pending.reject(new Error('今日AI调用额度已用完'))
    await Promise.all([first, duplicate, other])
    await flushPromises()
    expect(row(0).text()).toContain('今日AI调用额度已用完')
    expect(row(0).get('textarea').element.value).toBe('生成失败也要保留')
    expect(row(1).text()).toContain('已复核')
    generate.mockResolvedValueOnce({
      code: 0,
      data: changed(reviewed, 41, {
        generationStatus: 'GENERATED',
        aiAnswerLabels: ['B'],
        aiAnalysis: 'AI 新建议',
      }),
    })
    await vm.generateDraftAnswer(41)
    await flushPromises()
    expect(row(0).text()).not.toContain('今日AI调用额度已用完')
    expect(row(0).get('textarea').element.value).toBe('生成失败也要保留')
    expect(row(0).findAll('input')[1]!.element.checked).toBe(true)
    expect(current.value.questions[0]!.reviewStatus).toBe('PENDING')
    expect(current.value.reviewedQuestionCount).toBe(1)
  })

  it('排队复核锁定点击时的完整输入，不因前一题响应改变待提交的内容', async () => {
    const pending = deferred()
    review.mockReturnValueOnce(pending.promise).mockResolvedValueOnce({ code: 9, message: '请重试' })
    const { row, vm } = setup()
    const first = vm.reviewDraftQuestion(41)
    const second = vm.reviewDraftQuestion(42)
    await flushPromises()
    pending.resolve({
      code: 0,
      data: changed(
        changed(fixture(), 41, {
          reviewStatus: 'REVIEWED',
          finalAnswerLabels: ['A'],
          finalAnalysis: '服务端确认',
        }),
        42,
        { aiAnswerLabels: ['B'], aiAnalysis: '其他会话的新建议' },
      ),
    })
    await Promise.all([first, second])
    await flushPromises()
    expect(review).toHaveBeenLastCalledWith(31, 42, { answerLabels: ['A'], analysis: '建议2' })
    expect(row(1).get('textarea').element.value).toBe('建议2')
    expect(row(1).findAll('input')[0]!.element.checked).toBe(true)
  })

  it('切走再切回同ID也不会接受旧会话失败或解除新请求忙碌状态', async () => {
    const old = deferred()
    const fresh = deferred()
    review.mockReturnValueOnce(old.promise).mockReturnValueOnce(fresh.promise)
    const { row, current, vm, child } = setup()
    const one = vm.reviewDraftQuestion(41)
    await flushPromises()
    current.value = fixture(32)
    await flushPromises()
    current.value = fixture(31)
    await flushPromises()
    const two = vm.reviewDraftQuestion(41)
    await flushPromises()
    old.reject(new Error('旧会话失败'))
    await one
    await flushPromises()
    expect(row(0).text()).not.toContain('旧会话失败')
    expect(row(0).text()).toContain('正在保存')
    expect(row(0).get('textarea').attributes('disabled')).toBeDefined()
    expect(child.emitted('updated')).toBeUndefined()
    fresh.resolve({
      code: 0,
      data: changed(fixture(), 41, {
        reviewStatus: 'REVIEWED',
        finalAnswerLabels: ['A'],
        finalAnalysis: '最新服务端确认',
      }),
    })
    await two
    await flushPromises()
    expect(row(0).get('textarea').element.value).toBe('最新服务端确认')
  })

  it('已复核表单只显示服务端最终值，不用AI建议填补空的最终值', async () => {
    const { row, current, vm } = setup()
    await row(0).get('textarea').setValue('未提交文本')
    current.value = changed(current.value, 41, { reviewStatus: 'REVIEWED', finalAnswerLabels: [], finalAnalysis: null })
    await flushPromises()
    expect(row(0).get('textarea').element.value).toBe('')
    expect(
      row(0)
        .findAll('input')
        .every((input) => !input.element.checked),
    ).toBe(true)
    expect(row(0).get('textarea').attributes('disabled')).toBeDefined()
    await vm.reviewDraftQuestion(41)
    await vm.generateDraftAnswer(41)
    expect(review).not.toHaveBeenCalled()
    expect(generate).not.toHaveBeenCalled()
  })
})
