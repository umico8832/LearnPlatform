import { defineComponent, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'

import { useExamLeaveGuard } from '@/views/exam/useExamLeaveGuard'
import type { ConfirmSheet } from '@/composables/useConfirmSheet'

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
  const sheet = state.leaveSheet as ConfirmSheet
  return { router, wrapper, state, sheet }
}

describe('useExamLeaveGuard', () => {
  it('keeps a loaded exam open when the learner cancels the leave confirmation', async () => {
    const { router, wrapper, sheet } = await mountExam()

    const leaving = router.push('/exams')
    await flushPromises()
    expect(sheet.open.value).toBe(true)
    sheet.answer(false)
    await leaving

    expect(router.currentRoute.value.path).toBe('/exams/take/101')
    expect(sheet.open.value).toBe(false)
    wrapper.unmount()
  })

  it('treats closing the sheet without an answer as staying in the exam', async () => {
    const { router, wrapper, sheet } = await mountExam()

    const leaving = router.push('/exams')
    await flushPromises()
    sheet.open.value = false
    await leaving

    expect(router.currentRoute.value.path).toBe('/exams/take/101')
    wrapper.unmount()
  })

  it('allows a confirmed leave exactly once', async () => {
    const { router, wrapper, sheet } = await mountExam()

    const leaving = router.push('/exams')
    await flushPromises()
    sheet.answer(true)
    await leaving

    expect(router.currentRoute.value.path).toBe('/exams')
    expect(sheet.open.value).toBe(false)
    wrapper.unmount()
  })

  it('reuses one pending confirmation for repeated leave requests', async () => {
    const { wrapper, state, sheet } = await mountExam()

    const requestLeave = state.requestLeave as () => Promise<boolean>
    const firstResult = requestLeave()
    const secondResult = requestLeave()
    expect(firstResult).toBe(secondResult)
    expect(sheet.open.value).toBe(true)

    sheet.answer(true)
    await expect(firstResult).resolves.toBe(true)
    await expect(secondResult).resolves.toBe(true)
    wrapper.unmount()
  })

  it('blocks navigation while submission is in flight without opening another confirmation', async () => {
    const { router, wrapper, state, sheet } = await mountExam()
    ;(state.submitting as { value: boolean }).value = true

    await router.push('/exams')

    expect(router.currentRoute.value.path).toBe('/exams/take/101')
    expect(sheet.open.value).toBe(false)
    wrapper.unmount()
  })

  it('allows same-record query updates without treating them as an exam exit', async () => {
    const { router, wrapper, sheet } = await mountExam()

    await router.push({ path: '/exams/take/101', query: { source: 'answer-sheet' } })

    expect(router.currentRoute.value.fullPath).toBe('/exams/take/101?source=answer-sheet')
    expect(sheet.open.value).toBe(false)
    wrapper.unmount()
  })

  it.each(['真实提交成功', '计时到期'])('allows %s to navigate without a leave confirmation', async () => {
    const { router, wrapper, state, sheet } = await mountExam()
    ;(state.finished as { value: boolean }).value = true

    await router.push('/exams')

    expect(router.currentRoute.value.path).toBe('/exams')
    expect(sheet.open.value).toBe(false)
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
    const { wrapper, state, sheet } = await mountExam()
    const pending = (state.requestLeave as () => Promise<boolean>)()
    expect(sheet.open.value).toBe(true)

    ;(state.finished as { value: boolean }).value = true
    await flushPromises()

    expect(sheet.open.value).toBe(false)
    await expect(pending).resolves.toBe(false)
    wrapper.unmount()
  })
})
