import { computed, defineComponent, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { removeToken, setToken } from '@/utils/auth'

const { acknowledgeAiUsageAlert, getAiUsageAlerts } = vi.hoisted(() => ({
  acknowledgeAiUsageAlert: vi.fn(),
  getAiUsageAlerts: vi.fn(),
}))

vi.mock('@/api/aiUsage', () => ({ acknowledgeAiUsageAlert, getAiUsageAlerts }))

import { useOpsAlerts } from '@/components/layout/useOpsAlerts'

const Host = defineComponent({
  setup() {
    const admin = ref(true)
    return { admin, ...useOpsAlerts(computed(() => admin.value)) }
  },
  template: '<div />',
})

describe('useOpsAlerts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getAiUsageAlerts.mockResolvedValue({ data: [{ id: 7, level: 'WARNING', type: 'FAILURE', message: '失败率上升' }] })
    acknowledgeAiUsageAlert.mockResolvedValue({ data: { id: 7 } })
  })

  it('keeps an alert failure distinct from an empty successful response and retries in place', async () => {
    getAiUsageAlerts.mockRejectedValueOnce(new Error('network'))
    const wrapper = mount(Host)
    await flushPromises()
    expect(wrapper.vm.error).toBe('运营提醒暂时无法加载')
    expect(wrapper.vm.alerts).toHaveLength(0)

    await wrapper.vm.refresh()
    expect(wrapper.vm.error).toBe('')
    expect(wrapper.vm.alerts).toHaveLength(1)
    wrapper.unmount()
  })

  it('drops a late response after the administrator scope ends', async () => {
    let resolve!: (value: { data: { id: number; level: string; type: string; message: string }[] }) => void
    getAiUsageAlerts.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const wrapper = mount(Host)
    wrapper.vm.admin = false
    resolve({ data: [{ id: 7, level: 'WARNING', type: 'FAILURE', message: '失败率上升' }] })
    await flushPromises()

    expect(wrapper.vm.alerts).toHaveLength(0)
    wrapper.unmount()
  })

  it('does not submit the same acknowledgement twice', async () => {
    let resolve!: () => void
    acknowledgeAiUsageAlert.mockImplementationOnce(
      () =>
        new Promise<void>((done) => {
          resolve = done
        }),
    )
    const wrapper = mount(Host)
    await flushPromises()

    void wrapper.vm.acknowledge(7)
    void wrapper.vm.acknowledge(7)
    expect(acknowledgeAiUsageAlert).toHaveBeenCalledTimes(1)
    resolve()
    await flushPromises()
    expect(wrapper.vm.alerts).toHaveLength(0)
    wrapper.unmount()
  })

  it('keeps a new administrator acknowledgement locked when a prior account responds late', async () => {
    let resolveFirst!: () => void
    let resolveSecond!: () => void
    acknowledgeAiUsageAlert
      .mockImplementationOnce(
        () =>
          new Promise<void>((done) => {
            resolveFirst = done
          }),
      )
      .mockImplementationOnce(
        () =>
          new Promise<void>((done) => {
            resolveSecond = done
          }),
      )
    setToken('ops-account-a')
    const wrapper = mount(Host)
    await flushPromises()

    void wrapper.vm.acknowledge(7)
    expect(wrapper.vm.isAcknowledging(7)).toBe(true)
    setToken('ops-account-b')
    await flushPromises()
    expect(getAiUsageAlerts).toHaveBeenCalledTimes(2)
    expect(wrapper.vm.alerts).toHaveLength(1)

    void wrapper.vm.acknowledge(7)
    expect(wrapper.vm.isAcknowledging(7)).toBe(true)
    resolveFirst()
    await flushPromises()
    expect(wrapper.vm.isAcknowledging(7)).toBe(true)
    expect(wrapper.vm.alerts).toHaveLength(1)

    resolveSecond()
    await flushPromises()
    expect(wrapper.vm.isAcknowledging(7)).toBe(false)
    expect(wrapper.vm.alerts).toHaveLength(0)
    wrapper.unmount()
    removeToken()
  })

  it('clears administrator alerts without refetching when the token is removed', async () => {
    setToken('ops-authenticated-session')
    const wrapper = mount(Host)
    await flushPromises()
    expect(getAiUsageAlerts).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.alerts).toHaveLength(1)

    removeToken()
    await flushPromises()

    expect(getAiUsageAlerts).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.alerts).toHaveLength(0)
    wrapper.unmount()
  })
})
