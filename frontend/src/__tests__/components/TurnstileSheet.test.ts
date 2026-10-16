import { afterEach, describe, expect, it } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import TurnstileSheet from '@/components/auth/TurnstileSheet.vue'
import TurnstileWidget from '@/components/auth/TurnstileWidget.vue'

enableAutoUnmount(afterEach)

function mountSheet() {
  return mount(TurnstileSheet, {
    global: {
      stubs: {
        LtSheet: {
          name: 'LtSheet',
          props: ['open', 'origin'],
          emits: ['update:open'],
          template:
            '<div v-if="open" role="dialog"><button data-close @click="$emit(\'update:open\', false)">关闭</button><slot /></div>',
        },
        TurnstileWidget: {
          template: '<div class="turnstile-stub" />',
          emits: ['update:modelValue', 'error', 'expired'],
        },
      },
    },
  })
}

describe('TurnstileSheet', () => {
  it('mounts a challenge only on demand and resolves a token once', async () => {
    const w = mountSheet()
    expect(w.find('.turnstile-stub').exists()).toBe(false)
    const result = w.vm.verify()
    const duplicate = w.vm.verify()
    await flushPromises()
    const widget = w.getComponent(TurnstileWidget)
    widget.vm.$emit('update:modelValue', 'verified-token')
    widget.vm.$emit('update:modelValue', 'duplicate-token')
    await expect(result).resolves.toBe('verified-token')
    await expect(duplicate).resolves.toBe('verified-token')
    await flushPromises()
    expect(w.find('.turnstile-stub').exists()).toBe(false)
  })

  it('cancels through the close control and creates a fresh challenge on retry', async () => {
    const w = mountSheet()
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
    const w = mountSheet()
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
    const w = mountSheet()
    const result = w.vm.verify()
    await flushPromises()
    w.unmount()
    await expect(result).resolves.toBeNull()
  })

  it('cancels through the explicit cancel button', async () => {
    const w = mountSheet()
    const result = w.vm.verify()
    await flushPromises()
    await w
      .findAll('button')
      .find((b) => b.text() === '取消')!
      .trigger('click')
    await expect(result).resolves.toBeNull()
  })
  it('passes the trigger to the shared sheet for focus restoration', async () => {
    const trigger = document.createElement('button')
    const w = mountSheet()
    const result = w.vm.verify(trigger)
    await flushPromises()
    expect(w.getComponent({ name: 'LtSheet' }).props('origin')).toBe(trigger)
    await w.get('[data-close]').trigger('click')
    await expect(result).resolves.toBeNull()
  })
})
