import { defineComponent, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

const { close, confirm } = vi.hoisted(() => ({ close: vi.fn(), confirm: vi.fn() }))

vi.mock('element-plus', () => ({ ElMessageBox: { close, confirm } }))

import { useExamLeaveGuard } from '@/views/exam/useExamLeaveGuard'

const ExamHost = defineComponent({
  setup() {
    const hasQuestions = ref(true)
    const submitting = ref(false)
    const finished = ref(false)
    return {
      state: {
        hasQuestions,
        submitting,
        finished,
        ...useExamLeaveGuard({ hasQuestions: () => hasQuestions.value, submitting, finished }),
      },
    }
  },
  template: '<div>考试</div>',
})

async function mountExam() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/exams/take/:recordId', component: ExamHost },
      { path: '/exams', component: { template: '<div>考试列表</div>' } },
    ],
  })
  await router.push('/exams/take/101')
  await router.isReady()
  const wrapper = mount({ template: '<router-view />' }, { global: { plugins: [router] } })
  await flushPromises()
  const state = (wrapper.findComponent(ExamHost).vm as unknown as { state: Record<string, unknown> }).state
  return { router, wrapper, state }
}

describe('useExamLeaveGuard', () => {
  afterEach(() => vi.clearAllMocks())

  it('keeps a loaded exam open when the learner cancels the leave confirmation', async () => {
    confirm.mockRejectedValueOnce('cancel')
    const { router, wrapper } = await mountExam()

    await router.push('/exams')

    expect(router.currentRoute.value.path).toBe('/exams/take/101')
    expect(confirm).toHaveBeenCalledWith('离开后，本页未提交的答案会丢失，考试计时仍会继续。', '离开考试', {
      type: 'warning',
      confirmButtonText: '离开考试',
      cancelButtonText: '继续考试',
      autofocus: false,
    })
    wrapper.unmount()
  })

  it('allows a confirmed leave exactly once', async () => {
    confirm.mockResolvedValueOnce(undefined)
    const { router, wrapper } = await mountExam()

    await router.push('/exams')

    expect(router.currentRoute.value.path).toBe('/exams')
    expect(confirm).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('reuses one pending confirmation for repeated leave requests', async () => {
    let resolve!: () => void
    confirm.mockImplementationOnce(
      () =>
        new Promise<void>((done) => {
          resolve = done
        }),
    )
    const { wrapper, state } = await mountExam()

    const first = state.requestLeave as () => Promise<boolean>
    const second = state.requestLeave as () => Promise<boolean>
    const firstResult = first()
    const secondResult = second()
    expect(confirm).toHaveBeenCalledTimes(1)

    resolve()
    await expect(firstResult).resolves.toBe(true)
    await expect(secondResult).resolves.toBe(true)
    expect(confirm).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('blocks navigation while submission is in flight without opening another confirmation', async () => {
    const { router, wrapper, state } = await mountExam()
    ;(state.submitting as { value: boolean }).value = true

    await router.push('/exams')

    expect(router.currentRoute.value.path).toBe('/exams/take/101')
    expect(confirm).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('allows same-record query updates without treating them as an exam exit', async () => {
    const { router, wrapper } = await mountExam()

    await router.push({ path: '/exams/take/101', query: { source: 'answer-sheet' } })

    expect(router.currentRoute.value.fullPath).toBe('/exams/take/101?source=answer-sheet')
    expect(confirm).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it.each(['真实提交成功', '计时到期'])('allows %s to navigate without a leave confirmation', async () => {
    const { router, wrapper, state } = await mountExam()
    ;(state.finished as { value: boolean }).value = true

    await router.push('/exams')

    expect(router.currentRoute.value.path).toBe('/exams')
    expect(confirm).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('protects browser-window unload only while the valid exam remains unfinished', async () => {
    const { wrapper, state } = await mountExam()
    const beforeFinish = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(beforeFinish)
    expect(beforeFinish.defaultPrevented).toBe(true)

    ;(state.finished as { value: boolean }).value = true
    const afterFinish = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(afterFinish)
    expect(afterFinish.defaultPrevented).toBe(false)
    wrapper.unmount()
  })

  it('closes its pending leave confirmation when the exam finishes and lets the internal expiry redirect proceed', async () => {
    let reject!: () => void
    confirm.mockImplementationOnce(
      () =>
        new Promise<void>((_resolve, done) => {
          reject = done
        }),
    )
    const { wrapper, state } = await mountExam()
    const pending = (state.requestLeave as () => Promise<boolean>)()

    ;(state.finished as { value: boolean }).value = true
    await flushPromises()
    expect(close).toHaveBeenCalledTimes(1)

    reject()
    await expect(pending).resolves.toBe(false)
    wrapper.unmount()
  })
})
