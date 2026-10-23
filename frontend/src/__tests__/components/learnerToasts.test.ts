import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import LearnerToastHost from '@/components/layout/LearnerToastHost.vue'
import { dismissToast, showErrorToast, toasts } from '@/components/layout/learnerToasts'

describe('learner toasts', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => {
    ;[...toasts].forEach((toast) => dismissToast(toast.id))
    vi.useRealTimers()
  })

  it('同一句提示不叠加，并在四秒后消失', () => {
    showErrorToast('登录已过期，请重新登录')
    vi.advanceTimersByTime(3000)
    showErrorToast('登录已过期，请重新登录')
    expect(toasts).toHaveLength(1)
    vi.advanceTimersByTime(3000)
    expect(toasts).toHaveLength(1)
    vi.advanceTimersByTime(1000)
    expect(toasts).toHaveLength(0)
  })

  it('最多同时保留三条，最早的先让位', () => {
    ;['一', '二', '三', '四'].forEach(showErrorToast)
    expect(toasts.map((toast) => toast.message)).toEqual(['二', '三', '四'])
  })

  it('浮层可读出提示并能手动关闭', async () => {
    const wrapper = mount(LearnerToastHost)
    showErrorToast('网络异常，请检查网络连接')
    await nextTick()
    expect(wrapper.get('[role="alert"]').text()).toContain('网络异常，请检查网络连接')
    await wrapper.get('button[aria-label="关闭提示"]').trigger('click')
    expect(toasts).toHaveLength(0)
    wrapper.unmount()
  })
})
