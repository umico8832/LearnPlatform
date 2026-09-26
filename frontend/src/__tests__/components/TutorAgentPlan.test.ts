import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get, confirm, open } = vi.hoisted(() => ({ get: vi.fn(), confirm: vi.fn(), open: vi.fn() }))

vi.mock('@/api/tutorPlan', () => ({
  getTutorAgentPlan: (...args: unknown[]) => get(...args),
  confirmTutorAgentPlan: (...args: unknown[]) => confirm(...args),
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/utils/learningTarget', () => ({ openLearningTarget: (...args: unknown[]) => open(...args) }))

import TutorAgentPlan from '@/components/course/TutorAgentPlan.vue'
import type { TutorAgentPlanVO } from '@/api/tutorPlan'

const stubs = {
  'el-button': {
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    props: ['disabled', 'loading', 'type'],
    emits: ['click'],
  },
  'el-alert': { template: '<div>{{ title }}</div>', props: ['title', 'type', 'closable', 'showIcon'] },
}

const availablePlan: TutorAgentPlanVO = {
  available: true,
  confirmed: false,
  confirmedAt: null,
  steps: [{ type: 'TUTOR', title: '回看栈顶', reason: '先巩固本节概念', knowledgePointId: 7, questionId: null }],
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

type PlanProps = {
  courseId: number
  sessionKey: string
  runKey: string
  sequence: number
  busy?: boolean
}

function mountPlan(props: PlanProps = { courseId: 1, sessionKey: 'session', runKey: 'run', sequence: 2 }) {
  return mount(TutorAgentPlan, { props, global: { stubs } })
}

describe('TutorAgentPlan', () => {
  beforeEach(() => {
    get.mockReset()
    confirm.mockReset()
    open.mockReset()
  })

  it('loads the saved plan on mount without confirming it', async () => {
    get.mockResolvedValueOnce({ data: availablePlan })
    const wrapper = mountPlan()
    await flushPromises()
    expect(get).toHaveBeenCalledWith(1, 'session', 'run', 2)
    expect(confirm).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('本课程建议安排')
    expect(wrapper.text()).toContain('回看栈顶')
    expect(wrapper.get('[data-testid="plan-confirm"]')).toBeTruthy()
  })

  it('offers an explicit retry after the initial GET fails', async () => {
    get.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ data: availablePlan })
    const wrapper = mountPlan()
    await flushPromises()
    expect(wrapper.text()).toContain('offline')
    await wrapper.get('[data-testid="plan-retry"]').trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('回看栈顶')
  })

  it('confirms explicitly and restores the server confirmation', async () => {
    get.mockResolvedValueOnce({ data: availablePlan })
    confirm.mockResolvedValueOnce({ data: { ...availablePlan, confirmed: true, confirmedAt: '2026-09-26T10:00:00' } })
    const wrapper = mountPlan()
    await flushPromises()
    await wrapper.get('[data-testid="plan-confirm"]').trigger('click')
    await flushPromises()
    expect(confirm).toHaveBeenCalledWith(1, 'session', 'run', 2)
    expect(wrapper.text()).toContain('已确认')
    expect(wrapper.get('[data-testid="plan-open-step"]')).toBeTruthy()
  })

  it('recovers a persisted confirmation after its POST response is lost', async () => {
    get.mockResolvedValueOnce({ data: availablePlan }).mockResolvedValueOnce({
      data: { ...availablePlan, confirmed: true, confirmedAt: '2026-09-26T10:00:00' },
    })
    confirm.mockRejectedValueOnce(new Error('network'))
    const wrapper = mountPlan()
    await flushPromises()
    await wrapper.get('[data-testid="plan-confirm"]').trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('已确认')
  })

  it('requires an explicit successful sync before retrying after POST and recovery both fail', async () => {
    get
      .mockResolvedValueOnce({ data: availablePlan })
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ data: availablePlan })
    confirm.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({
      data: { ...availablePlan, confirmed: true, confirmedAt: '2026-09-26T10:00:00' },
    })
    const wrapper = mountPlan()
    await flushPromises()
    await wrapper.get('[data-testid="plan-confirm"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="plan-sync"]')).toBeTruthy()
    await wrapper.get('[data-testid="plan-confirm"]').trigger('click')
    expect(confirm).toHaveBeenCalledTimes(1)
    await wrapper.get('[data-testid="plan-sync"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="plan-confirm"]').trigger('click')
    await flushPromises()
    expect(confirm).toHaveBeenCalledTimes(2)
  })

  it('deduplicates confirmation clicks and ignores late responses after context changes or unmount', async () => {
    const posting = deferred<{ data: TutorAgentPlanVO }>()
    get.mockResolvedValueOnce({ data: availablePlan }).mockResolvedValueOnce({ data: { ...availablePlan, steps: [] } })
    confirm.mockReturnValueOnce(posting.promise)
    const wrapper = mountPlan()
    await flushPromises()
    await wrapper.get('[data-testid="plan-confirm"]').trigger('click')
    await wrapper.get('[data-testid="plan-confirm"]').trigger('click')
    expect(confirm).toHaveBeenCalledTimes(1)
    await wrapper.setProps({ sessionKey: 'next', sequence: 3 })
    posting.resolve({ data: { ...availablePlan, confirmed: true, confirmedAt: '2026-09-26T10:00:00' } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('已确认')
    wrapper.unmount()
  })

  it('ignores late GET responses after context changes', async () => {
    const oldLoad = deferred<{ data: TutorAgentPlanVO }>()
    get.mockReturnValueOnce(oldLoad.promise).mockResolvedValueOnce({
      data: { ...availablePlan, steps: [{ ...availablePlan.steps[0], title: '新会话建议' }] },
    })
    const wrapper = mountPlan()
    await wrapper.setProps({ sessionKey: 'next', sequence: 3 })
    oldLoad.resolve({ data: availablePlan })
    await flushPromises()
    expect(wrapper.text()).toContain('新会话建议')
    expect(wrapper.text()).not.toContain('回看栈顶')
  })

  it('does not issue recovery GET after unmount during a failed confirmation', async () => {
    const posting = deferred<{ data: TutorAgentPlanVO }>()
    get.mockResolvedValueOnce({ data: availablePlan })
    confirm.mockReturnValueOnce(posting.promise)
    const unmounted = mountPlan({ courseId: 1, sessionKey: 'unmounted', runKey: 'run', sequence: 2 })
    await flushPromises()
    await unmounted.get('[data-testid="plan-confirm"]').trigger('click')
    unmounted.unmount()
    posting.reject(new Error('network'))
    await flushPromises()
    expect(get).toHaveBeenCalledTimes(1)
  })

  it('disables confirmation and navigation for unavailable or busy plans, but opens confirmed available steps explicitly', async () => {
    get.mockResolvedValueOnce({
      data: { ...availablePlan, available: false, confirmed: true, confirmedAt: '2026-09-26' },
    })
    const unavailable = mountPlan()
    await flushPromises()
    expect(unavailable.get('[data-testid="plan-confirm"]').attributes('disabled')).toBeDefined()
    expect(unavailable.get('[data-testid="plan-open-step"]').attributes('disabled')).toBeDefined()
    expect(unavailable.text()).toContain('目标已有变化')
    await unavailable.get('[data-testid="plan-confirm"]').trigger('click')
    await unavailable.get('[data-testid="plan-open-step"]').trigger('click')
    expect(confirm).not.toHaveBeenCalled()
    expect(open).not.toHaveBeenCalled()

    get.mockResolvedValueOnce({ data: availablePlan })
    const busy = mountPlan({ courseId: 1, sessionKey: 'other', runKey: 'run', sequence: 2, busy: true })
    await flushPromises()
    expect(busy.get('[data-testid="plan-confirm"]').attributes('disabled')).toBeDefined()
    await busy.get('[data-testid="plan-confirm"]').trigger('click')
    expect(confirm).not.toHaveBeenCalled()

    get.mockResolvedValueOnce({ data: { ...availablePlan, confirmed: true, confirmedAt: '2026-09-26' } })
    const ready = mountPlan({ courseId: 1, sessionKey: 'ready', runKey: 'run', sequence: 2 })
    await flushPromises()
    await ready.get('[data-testid="plan-open-step"]').trigger('click')
    expect(open).toHaveBeenCalled()
  })
})
