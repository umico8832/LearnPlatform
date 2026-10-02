import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import NotFoundView from '@/views/NotFoundView.vue'

describe('NotFoundView', () => {
  it('renders 404 heading', () => {
    const wrapper = mount(NotFoundView, {
      global: {
        stubs: {
          'el-button': {
            template: '<button @click="$emit(\'click\')"><slot /></button>',
          },
        },
      },
    })
    expect(wrapper.find('h1').text()).toBe('404')
  })

  it('renders error message', () => {
    const wrapper = mount(NotFoundView, {
      global: {
        stubs: {
          'el-button': {
            template: '<button><slot /></button>',
          },
        },
      },
    })
    expect(wrapper.find('p').text()).toBe('这个地址不存在，或内容已移动。')
  })

  it('renders a back-to-home button', () => {
    const wrapper = mount(NotFoundView, {
      global: {
        stubs: {
          'el-button': {
            template: '<button><slot /></button>',
          },
        },
      },
    })
    expect(wrapper.find('button').text()).toBe('返回首页')
  })
})
