import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { LtBackLink, LtField, LtSegmented, LtSheet, LtStepper } from '@/components/living'

const options = [
  { value: 'insert', label: '插入' },
  { value: 'delete', label: '删除', disabled: true },
  { value: 'reverse', label: '逆置' },
]

describe('LtBackLink', () => {
  it('keeps the destination and visible return label on a real router link', () => {
    const wrapper = mount(LtBackLink, {
      props: { to: '/practice', label: '返回练习' },
      global: { stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } } },
    })

    expect(wrapper.attributes('href')).toBe('/practice')
    expect(wrapper.text()).toBe('返回练习')
    expect(wrapper.find('svg').exists()).toBe(true)
  })
})

describe('LtSegmented', () => {
  it('以单选组呈现，只有选中项可被 Tab 聚焦', () => {
    const wrapper = mount(LtSegmented, { props: { options, label: '操作', modelValue: 'reverse' } })
    expect(wrapper.attributes('role')).toBe('radiogroup')
    expect(wrapper.attributes('aria-label')).toBe('操作')
    const radios = wrapper.findAll('[role="radio"]')
    expect(radios.map((radio) => radio.attributes('aria-checked'))).toEqual(['false', 'false', 'true'])
    expect(radios.map((radio) => radio.attributes('tabindex'))).toEqual(['-1', '-1', '0'])
  })

  it('点击更新选中值，禁用项不可选', async () => {
    const wrapper = mount(LtSegmented, { props: { options, label: '操作', modelValue: 'insert' } })
    await wrapper.findAll('[role="radio"]')[2].trigger('click')
    await wrapper.findAll('[role="radio"]')[1].trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([['reverse']])
  })

  it('方向键在可用项之间循环并跳过禁用项', async () => {
    const wrapper = mount(LtSegmented, {
      props: {
        options,
        label: '操作',
        modelValue: 'insert',
        'onUpdate:modelValue': (next: string) => wrapper.setProps({ modelValue: next }),
      },
    })
    const radios = () => wrapper.findAll('[role="radio"]')
    await radios()[0].trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.props('modelValue')).toBe('reverse')
    await radios()[2].trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.props('modelValue')).toBe('insert')
    await radios()[0].trigger('keydown', { key: 'ArrowLeft' })
    expect(wrapper.props('modelValue')).toBe('reverse')
  })
})

describe('LtStepper', () => {
  it('在上下限处停止并禁用对应按钮', async () => {
    const wrapper = mount(LtStepper, {
      props: {
        label: '位置',
        min: 1,
        max: 3,
        modelValue: 2,
        'onUpdate:modelValue': (next: number) => wrapper.setProps({ modelValue: next }),
      },
    })
    const [minus, plus] = wrapper.findAll('button')
    expect(wrapper.attributes('aria-label')).toBe('位置')
    await plus.trigger('click')
    expect(wrapper.find('output').text()).toBe('3')
    expect(plus.attributes('disabled')).toBeDefined()
    await minus.trigger('click')
    await minus.trigger('click')
    expect(wrapper.find('output').text()).toBe('1')
    expect(minus.attributes('disabled')).toBeDefined()
    expect(wrapper.emitted('update:modelValue')).toEqual([[3], [2], [1]])
  })
})

describe('LtField', () => {
  it('回车提交、Esc 取消，错误信息关联到输入框', async () => {
    const wrapper = mount(LtField, {
      props: { label: '数据', modelValue: '3 1 4', error: '需要 2–8 个数', description: '用空格分隔' },
    })
    const input = wrapper.find('input')
    expect(input.attributes('aria-invalid')).toBe('true')
    const described = input.attributes('aria-describedby')!.split(' ')
    expect(described.map((id) => wrapper.find(`[id="${id}"]`).text())).toEqual(['用空格分隔', '需要 2–8 个数'])
    expect(wrapper.find('label').attributes('for')).toBe(input.attributes('id'))

    await input.trigger('keydown', { key: 'Enter' })
    await input.trigger('keydown', { key: 'Escape' })
    expect(wrapper.emitted('commit')).toEqual([['3 1 4']])
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })
})

describe('LtSheet', () => {
  it('Esc 触发的 cancel 交给受控状态关闭，标题命名对话框', async () => {
    const wrapper = mount(LtSheet, {
      props: { title: 'AI 场景描述', open: false },
      slots: { default: '<p>内容</p>' },
      attachTo: document.body,
    })
    const dialog = wrapper.find('dialog')
    const titleId = dialog.attributes('aria-labelledby')
    expect(wrapper.find(`[id="${titleId}"]`).text()).toBe('AI 场景描述')

    await wrapper.setProps({ open: true })
    await nextTick()
    const cancel = new Event('cancel', { cancelable: true })
    dialog.element.dispatchEvent(cancel)
    expect(cancel.defaultPrevented).toBe(true)
    expect(wrapper.emitted('update:open')).toEqual([[false]])
    wrapper.unmount()
  })
})
