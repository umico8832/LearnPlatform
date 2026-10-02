import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  generate: vi.fn(),
  stream: vi.fn(),
  view: vi.fn(),
  complete: vi.fn(),
  version: 1,
  listeners: new Set<() => void>(),
}))
vi.mock('@/api/ai', () => ({
  getQuestionAssets: mocks.get,
  generateAsset: mocks.generate,
  streamAsset: mocks.stream,
  recordAssetView: mocks.view,
  completeVariantTraining: mocks.complete,
}))
vi.mock('@/utils/auth', () => ({
  getAuthSessionVersion: () => mocks.version,
  onAuthSessionChange: (fn: () => void) => {
    mocks.listeners.add(fn)
    return () => mocks.listeners.delete(fn)
  },
}))
import { useLearningAssets } from '@/components/question-learning/useLearningAssets'

type Handlers = { onContent: (text: string) => void; onDone: (source: string) => void }
function deferred<T>() {
  let resolve!: (value: T) => void, reject!: (error: Error) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}
function cached(content: string) {
  return { data: [{ assetType: 'FULL_EXPLANATION', content }] }
}
const wrappers: Array<{ unmount: () => void }> = []
function setup() {
  const questionId = ref(1),
    ready = vi.fn()
  let state!: ReturnType<typeof useLearningAssets>
  const wrapper = mount(
    defineComponent({
      setup() {
        state = useLearningAssets(questionId, ready)
        return () => h('div')
      },
    }),
  )
  wrappers.push(wrapper)
  return { state, questionId, ready, wrapper }
}
beforeEach(() => {
  vi.resetAllMocks()
  mocks.version = 1
  mocks.listeners.clear()
  mocks.get.mockResolvedValue({ data: [] })
})
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))

describe('learning asset lifecycle', () => {
  it('distinguishes a failed cache read from missing content and retries before generation', async () => {
    mocks.get.mockRejectedValueOnce(new Error('offline'))
    const { state } = setup()
    await state.loadExistingAssets()
    expect(state.existingError.value).not.toBe('')
    await state.generateTab('FULL_EXPLANATION')
    expect(mocks.stream).not.toHaveBeenCalled()
    mocks.get.mockResolvedValueOnce(cached('已存讲解'))
    await state.loadExistingAssets()
    expect(state.existingError.value).toBe('')
    expect(state.tabContent.FULL_EXPLANATION).toBe('已存讲解')
  })
  it('discards a cache response after switching questions', async () => {
    const pending = deferred<ReturnType<typeof cached>>()
    mocks.get.mockReturnValueOnce(pending.promise)
    const { state, questionId } = setup()
    const first = state.loadExistingAssets()
    questionId.value = 2
    mocks.get.mockResolvedValueOnce(cached('新题讲解'))
    await state.loadExistingAssets()
    pending.resolve(cached('旧题讲解'))
    await first
    expect(state.tabContent.FULL_EXPLANATION).toBe('新题讲解')
    expect(state.existingLoading.value).toBe(false)
  })
  it('retains partial output on failure and makes it available only after a done event on retry', async () => {
    const pending = deferred<void>()
    mocks.stream.mockReturnValueOnce(pending.promise)
    const { state, ready } = setup()
    const first = state.generateTab('FULL_EXPLANATION')
    const handlers = mocks.stream.mock.calls[0][2] as Handlers
    handlers.onContent('部分内容')
    pending.reject(new Error('中断'))
    await first
    expect(state.partialContent.value).toBe('部分内容')
    expect(state.tabContent.FULL_EXPLANATION).toBe('')
    expect(ready).not.toHaveBeenCalled()
    mocks.stream.mockImplementationOnce(async (_id, _type, next: Handlers) => {
      next.onContent('完整讲解')
      next.onDone('ai')
    })
    await state.generateTab('FULL_EXPLANATION')
    expect(state.tabContent.FULL_EXPLANATION).toBe('完整讲解')
    expect(state.partialContent.value).toBe('')
    expect(ready).toHaveBeenCalledOnce()
  })
  it('cancels on type change and ignores late callbacks from the previous explanation', async () => {
    const pending = deferred<void>()
    mocks.stream.mockReturnValueOnce(pending.promise)
    const { state, ready } = setup()
    const first = state.generateTab('FULL_EXPLANATION')
    const handlers = mocks.stream.mock.calls[0][2] as Handlers
    const signal = mocks.stream.mock.calls[0][3] as AbortSignal
    state.activeTab.value = 'STEP_BY_STEP'
    state.onTabChange()
    ready.mockClear()
    handlers.onContent('旧讲解')
    handlers.onDone('ai')
    pending.resolve()
    await first
    expect(signal.aborted).toBe(true)
    expect(state.tabContent.FULL_EXPLANATION).toBe('')
    expect(state.partialContent.value).toBe('')
    expect(ready).not.toHaveBeenCalled()
  })
  it.each(['question', 'account', 'unmount'])(
    'does not accept a late structured variant after %s',
    async (boundary) => {
      const pending = deferred<{ data: { content: string } }>()
      mocks.generate.mockReturnValueOnce(pending.promise)
      const { state, questionId, wrapper, ready } = setup()
      const first = state.generateTab('VARIANT')
      await state.generateTab('VARIANT')
      expect(mocks.generate).toHaveBeenCalledOnce()
      if (boundary === 'question') questionId.value = 2
      else if (boundary === 'account') {
        mocks.version++
        mocks.listeners.forEach((fn) => fn())
      } else wrapper.unmount()
      pending.resolve({ data: { content: '旧变式' } })
      await first
      expect(state.tabContent.VARIANT).toBe('')
      expect(ready).not.toHaveBeenCalled()
    },
  )
  it('does not send completion for a new account after the old view request returns', async () => {
    const pending = deferred<{ data: null }>()
    mocks.view.mockReturnValueOnce(pending.promise)
    const { state } = setup()
    const first = state.handleVariantTrainingComplete()
    mocks.version++
    mocks.listeners.forEach((fn) => fn())
    pending.resolve({ data: null })
    await first
    expect(mocks.complete).not.toHaveBeenCalled()
    expect(state.variantTraining.completed).toBe(false)
    expect(state.variantTrainingSubmitting.value).toBe(false)
  })

  it('keeps saved training while collapsed and cancels pending cache work without blocking reopen', async () => {
    const pending = deferred<ReturnType<typeof cached>>()
    mocks.get.mockReturnValueOnce(pending.promise)
    const { state, questionId } = setup()
    state.applyVariantTraining({
      questionId: 1,
      assetId: 2,
      status: 'COMPLETED',
      completed: true,
      answered: true,
      correct: false,
      userAnswer: 'B',
      correctAnswer: 'A',
      analysis: '真实解析',
      startedTime: '',
      completedTime: '',
    })
    const first = state.loadExistingAssets()
    state.suspend()
    expect(state.variantTraining.userAnswer).toBe('B')
    expect(state.variantTraining.answered).toBe(true)
    expect(state.existingLoading.value).toBe(false)
    await state.loadExistingAssets()
    expect(mocks.get).toHaveBeenCalledTimes(2)
    pending.resolve(cached('被收起的旧请求'))
    await first
    expect(state.tabContent.FULL_EXPLANATION).toBe('')
    questionId.value = 2
    expect(state.variantTraining.answered).toBe(false)
    expect(state.variantTraining.userAnswer).toBe('')
  })

  it('lets an explicitly submitted completion finish while the panel is collapsed', async () => {
    const pending = deferred<{ data: { questionId: number; assetId: number; status: string; completed: boolean } }>()
    mocks.complete.mockReturnValueOnce(pending.promise)
    const { state } = setup()
    state.applyVariantTraining({
      questionId: 1,
      assetId: 2,
      status: 'STARTED',
      completed: false,
      startedTime: '',
      completedTime: null,
    })
    const completion = state.handleVariantTrainingComplete()
    state.suspend()
    pending.resolve({ data: { questionId: 1, assetId: 2, status: 'COMPLETED', completed: true } })
    await completion
    expect(state.variantTraining.completed).toBe(true)
    expect(state.variantTrainingSubmitting.value).toBe(false)
  })
})
