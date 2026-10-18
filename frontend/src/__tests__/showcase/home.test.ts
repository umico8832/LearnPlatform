import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent } from 'vue'
import ShowcaseHome from '@/views/home/showcase/ShowcaseHome.vue'
import { menus, links } from '@/views/home/showcase/showcaseContent'
import { learnerRoutes } from '@/router/routes'

const { authenticated } = vi.hoisted(() => ({ authenticated: vi.fn(() => false) }))
vi.mock('@/utils/auth', () => ({ isAuthenticated: authenticated }))
vi.mock('@/motion', async (original) => ({ ...(await original<object>()), springAnimate: vi.fn() }))
const Link = defineComponent({
  props: ['to', 'href'],
  template: '<a :href="to || href"><slot /></a>',
})
let wrapper: VueWrapper
function render() {
  wrapper = mount(ShowcaseHome, {
    global: {
      stubs: {
        RouterLink: Link,
        LtLinkButton: Link,
        HeroCard: true,
        SeeItSection: true,
        LabSection: true,
        RememberSection: true,
      },
    },
  })
  return wrapper
}

beforeEach(() => authenticated.mockReturnValue(false))
afterEach(() => wrapper?.unmount())

describe('showcase homepage entry', () => {
  it('loads the showcase at the public root', async () => {
    const route = learnerRoutes.find((record) => record.name === 'Home')!
    expect(route.path).toBe('/')
    expect(route.meta?.requiresAuth).toBe(false)
    const loaded = await (route.component as () => Promise<{ default: unknown }>)()
    expect(loaded.default).toBe(ShowcaseHome)
  })

  it('keeps signup and all navigation/footer destinations reachable', () => {
    const page = render()
    expect(page.findAll('a[href="/register"]').length).toBe(3)
    expect(page.find('a[href="/login"]').text()).toBe('Sign in')
    const destinations = [
      ...menus.flatMap((menu) => menu.items.map((item) => item.to)),
      ...links.map((link) => link.to),
    ]
    for (const to of destinations) {
      expect(page.find(`footer a[href="${to}"]`).exists()).toBe(true)
      expect(
        learnerRoutes.some((route) => route.path === to || route.children?.some((child) => `/${child.path}` === to)) ||
          to.startsWith('/viz/'),
      ).toBe(true)
    }
  })

  it('sends signed-in learners to their courses', () => {
    authenticated.mockReturnValue(true)
    const page = render()
    expect(page.findAll('a[href="/my-courses"]').length).toBe(3)
    expect(page.find('a[href="/login"]').exists()).toBe(false)
    expect(page.find('a[href="/register"]').exists()).toBe(false)
  })
})
