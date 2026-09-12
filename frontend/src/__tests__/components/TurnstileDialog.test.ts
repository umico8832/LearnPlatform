import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import TurnstileDialog from '@/components/auth/TurnstileDialog.vue'
import TurnstileWidget from '@/components/auth/TurnstileWidget.vue'

enableAutoUnmount(afterEach)

function mountDialog() {
  return mount(TurnstileDialog, {
    global: {
      stubs: {
        'el-dialog': {
          name: 'ElDialog',
          props: ['modelValue'],
          emits: ['update:modelValue', 'closed'],
          template:
            '<div v-if="modelValue" role="dialog"><button data-close @click="$emit(\'update:modelValue\', false)">关闭</button><slot /><slot name="footer" /></div>',
        },
        'el-button': { template: '<button><slot /></button>' },
        TurnstileWidget: {
          template: '<div class="turnstile-stub" />',
          emits: ['update:modelValue', 'error', 'expired'],
        },
      },
    },
  })
}

describe('TurnstileDialog', () => {
  it('mounts a challenge only on demand and resolves a token once', async () => {
    const w = mountDialog()
    expect(w.find('.turnstile-stub').exists()).toBe(false)
    const result = w.vm.verify()
    expect(w.vm.verify()).toBe(result)
    await flushPromises()
    const widget = w.getComponent(TurnstileWidget)
    widget.vm.$emit('update:modelValue', 'verified-token')
    widget.vm.$emit('update:modelValue', 'duplicate-token')
    await expect(result).resolves.toBe('verified-token')
    await flushPromises()
    expect(w.find('.turnstile-stub').exists()).toBe(false)
  })

  it('cancels through the close control and creates a fresh challenge on retry', async () => {
    const w = mountDialog()
    const cancelled = w.vm.verify()
    await flushPromises()
    await w.get('[data-close]').trigger('click')
    await expect(cancelled).resolves.toBeNull()
    const result = w.vm.verify()
    await flushPromises()
    w.getComponent(TurnstileWidget).vm.$emit('update:modelValue', 'new-token')
    await expect(result).resolves.toBe('new-token')
  })

  it('keeps verification pending on empty tokens, expiration or failure and allows retry', async () => {
    const w = mountDialog()
    const result = w.vm.verify()
    let settled = false
    void result.then(() => (settled = true))
    await flushPromises()
    const widget = w.getComponent(TurnstileWidget)
    widget.vm.$emit('update:modelValue', '')
    widget.vm.$emit('expired')
    widget.vm.$emit('error')
    await flushPromises()
    expect(settled).toBe(false)
    await w
      .findAll('button')
      .find((b) => b.text() === '重新验证')!
      .trigger('click')
    const replacement = w.getComponent(TurnstileWidget)
    expect(replacement.vm).not.toBe(widget.vm)
    replacement.vm.$emit('update:modelValue', 'retry-token')
    await expect(result).resolves.toBe('retry-token')
  })

  it('cancels on unmount so navigation cannot submit an abandoned operation', async () => {
    const w = mountDialog()
    const result = w.vm.verify()
    await flushPromises()
    w.unmount()
    await expect(result).resolves.toBeNull()
  })

  it('cancels through the explicit cancel button', async () => {
    const w = mountDialog()
    const result = w.vm.verify()
    await flushPromises()
    await w
      .findAll('button')
      .find((b) => b.text() === '取消')!
      .trigger('click')
    await expect(result).resolves.toBeNull()
  })
  it('restores keyboard focus to the original trigger after cancellation', async () => {
    const trigger = document.createElement('button')
    document.body.appendChild(trigger)
    const w = mountDialog()
    try {
      const result = w.vm.verify(trigger)
      await flushPromises()
      await w.get('[data-close]').trigger('click')
      await result
      w.getComponent({ name: 'ElDialog' }).vm.$emit('closed')
      await vi.waitFor(() => expect(document.activeElement).toBe(trigger))
    } finally {
      trigger.remove()
    }
  })
})
