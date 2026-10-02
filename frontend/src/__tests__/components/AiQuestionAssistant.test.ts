import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
const mocks = vi.hoisted(() => ({ stream: vi.fn(), examStream: vi.fn(), version: 1, listeners: new Set<() => void>() }))
vi.mock('@/api/ai', () => ({ streamQuestionAi: mocks.stream, streamExamLearningAi: mocks.examStream }))
vi.mock('@/utils/auth', () => ({
  getAuthSessionVersion: () => mocks.version,
  onAuthSessionChange: (fn: () => void) => {
    mocks.listeners.add(fn)
    return () => mocks.listeners.delete(fn)
  },
}))
import AIQuestionAssistant from '@/components/AiQuestionAssistant.vue'

type Handlers = { onContent: (text: string) => void; onDone: (source: string) => void }
function deferred() {
  let resolve!: () => void, reject!: (error: Error) => void
  const promise = new Promise<void>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}
const wrappers: Array<{ unmount: () => void }> = []
function setup() {
  const wrapper = mount(AIQuestionAssistant, {
    props: { questionId: 1 },
    global: {
      stubs: {
        'el-button': {
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
          props: ['disabled'],
          emits: ['click'],
        },
        'el-icon': { template: '<i><slot /></i>' },
      },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}
function button(wrapper: ReturnType<typeof setup>, text: string) {
  return wrapper.findAll('button').find((item) => item.text() === text)!
}
beforeEach(() => {
  vi.resetAllMocks()
  mocks.version = 1
  mocks.listeners.clear()
})
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))

describe('AI question assistant', () => {
  it('blocks assistance until the paper question has a first answer', async () => {
    const wrapper = setup()
    await wrapper.setProps({ learningSessionId: 8, disabled: true, disabledReason: '先提交本题答案' })
    expect(wrapper.text()).toContain('先提交本题答案')
    expect(wrapper.findAll('button').every((item) => item.attributes('disabled') !== undefined)).toBe(true)
    await button(wrapper, '补充解析').trigger('click')
    expect(mocks.stream).not.toHaveBeenCalled()
    expect(mocks.examStream).not.toHaveBeenCalled()
  })
  it('keeps partial content on failure and restarts instead of treating it as a complete cache', async () => {
    const pending = deferred()
    mocks.stream.mockReturnValueOnce(pending.promise)
    const wrapper = setup()
    await button(wrapper, '补充解析').trigger('click')
    const handlers = mocks.stream.mock.calls[0][2] as Handlers
    handlers.onContent('已经收到的部分解释')
    pending.reject(new Error('AI 回答中断'))
    await flushPromises()
    expect(wrapper.text()).toContain('已经收到的部分解释')
    expect(wrapper.get('[role="alert"]').text()).toContain('中断')
    mocks.stream.mockImplementationOnce(async (_type, _id, next: Handlers) => {
      next.onContent('完整解释')
      next.onDone('ai')
    })
    await button(wrapper, '重新生成').trigger('click')
    await flushPromises()
    expect(mocks.stream).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('完整解释')
    expect(wrapper.text()).not.toContain('已经收到的部分解释')
    await button(wrapper, '补充解析').trigger('click')
    expect(mocks.stream).toHaveBeenCalledTimes(2)
  })
  it('cancels on stop, keeps honest partial content, and ignores callbacks from the stopped stream', async () => {
    const pending = deferred()
    mocks.stream.mockReturnValueOnce(pending.promise)
    const wrapper = setup()
    await button(wrapper, '补充解析').trigger('click')
    const handlers = mocks.stream.mock.calls[0][2] as Handlers
    const signal = mocks.stream.mock.calls[0][3] as AbortSignal
    handlers.onContent('部分解释')
    await button(wrapper, '停止生成').trigger('click')
    expect(signal.aborted).toBe(true)
    handlers.onContent('迟到内容')
    handlers.onDone('ai')
    pending.resolve()
    await flushPromises()
    expect(wrapper.text()).toContain('已停止')
    expect(wrapper.text()).toContain('部分解释')
    expect(wrapper.text()).not.toContain('迟到内容')
  })
  it.each(['question', 'account', 'unmount'])('invalidates responses at the %s boundary', async (boundary) => {
    const pending = deferred()
    mocks.stream.mockReturnValueOnce(pending.promise)
    const wrapper = setup()
    await button(wrapper, '补充解析').trigger('click')
    const handlers = mocks.stream.mock.calls[0][2] as Handlers
    const signal = mocks.stream.mock.calls[0][3] as AbortSignal
    if (boundary === 'question') await wrapper.setProps({ questionId: 2 })
    else if (boundary === 'account') {
      mocks.version++
      mocks.listeners.forEach((fn) => fn())
    } else wrapper.unmount()
    handlers.onContent('旧会话内容')
    pending.resolve()
    await flushPromises()
    expect(signal.aborted).toBe(true)
    if (boundary !== 'unmount') expect(wrapper.text()).not.toContain('旧会话内容')
  })
  it('uses the authorized paper session route and prevents duplicate in-flight generation', async () => {
    const pending = deferred()
    mocks.examStream.mockReturnValueOnce(pending.promise)
    const wrapper = setup()
    await wrapper.setProps({ learningSessionId: 8 })
    await button(wrapper, '补充解析').trigger('click')
    await button(wrapper, '补充解析').trigger('click')
    expect(mocks.examStream).toHaveBeenCalledTimes(1)
    expect(mocks.examStream.mock.calls[0].slice(0, 3)).toEqual(['explanation', 8, 1])
    expect(mocks.stream).not.toHaveBeenCalled()
    pending.resolve()
    await flushPromises()
  })
})
