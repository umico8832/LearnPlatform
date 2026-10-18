import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import type { AskStatus } from '@/api/questionAsk'

enableAutoUnmount(afterEach)

const api = vi.hoisted(() => ({ getAskStatus: vi.fn(), streamAsk: vi.fn(), stopAsk: vi.fn() }))
vi.mock('@/api/questionAsk', async (original) => ({
  ...(await original<typeof import('@/api/questionAsk')>()),
  ...api,
}))

import { AskError } from '@/api/questionAsk'
import AskPanel from '@/views/question/AskPanel.vue'
import { useQuestionAsk } from '@/views/question/useQuestionAsk'

function status(extra: Partial<AskStatus> = {}): { data: AskStatus } {
  return {
    data: { available: true, reason: null, questionRemaining: 5, dailyRemaining: 50, records: [], ...extra },
  }
}

const attemptId = ref<number | null>(7)
function mountPanel() {
  let ask!: ReturnType<typeof useQuestionAsk>
  const Host = defineComponent({
    setup(_props, { attrs }) {
      ask = useQuestionAsk(() => attemptId.value)
      return () => (ask.status.value === 'ready' ? h(AskPanel, { ask, solved: false, ...attrs }) : h('p', 'hidden'))
    },
  })
  const onHint = vi.fn()
  const wrapper = mount(Host, { attrs: { onHint } })
  return { wrapper, onHint, ask: () => ask }
}

describe('AskPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    attemptId.value = 7
    api.getAskStatus.mockResolvedValue(status())
  })

  it('stays hidden when the server says asking is not available', async () => {
    api.getAskStatus.mockResolvedValue(status({ available: false, reason: 'UNCONFIGURED' }))
    const { wrapper } = mountPanel()
    await flushPromises()
    expect(wrapper.text()).toBe('hidden')
  })

  it('streams an answer, keeps it in the thread and lets the learner follow the suggested hint', async () => {
    api.streamAsk.mockImplementation(async (_id, _body, handlers) => {
      handlers.onContent('先看中序里 b 的位置。')
      return {
        requestId: 'r',
        questionRemaining: 4,
        dailyRemaining: 49,
        suggestion: {
          action: { type: 'HINT', level: 1 },
          reason: '先领一级提示',
          reasonSource: { kind: 'CURATED', label: '' },
        },
      }
    })
    const { wrapper, onHint } = mountPanel()
    await flushPromises()
    expect(wrapper.text()).toContain('这道题还能问 5 次')
    await wrapper.get('textarea').setValue('为什么 c 不能是 b 的左孩子？')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const [id, body] = api.streamAsk.mock.calls[0]
    expect(id).toBe(7)
    expect(body.message).toBe('为什么 c 不能是 b 的左孩子？')
    expect(body.requestId).toMatch(/^[0-9a-f-]{36}$/)
    expect(wrapper.text()).toContain('先看中序里 b 的位置。')
    expect(wrapper.text()).not.toContain('审核')
    expect(wrapper.text()).toContain('这道题还能问 4 次')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('看第 1 级提示'))!
      .trigger('click')
    expect(onHint).toHaveBeenCalledOnce()
  })

  it('explains a used-up question, keeps the draft and reads the record back', async () => {
    api.streamAsk.mockRejectedValue(new AskError(1011, 'limit'))
    const { wrapper } = mountPanel()
    await flushPromises()
    await wrapper.get('textarea').setValue('再讲一遍')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('这道题的追问次数用完了')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('再讲一遍')
    expect(api.getAskStatus).toHaveBeenCalledTimes(2)
  })

  it('disables asking once the question has no asks left', async () => {
    api.getAskStatus.mockResolvedValue(status({ questionRemaining: 0 }))
    const { wrapper } = mountPanel()
    await flushPromises()
    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('这道题的追问次数用完了')
  })
})
