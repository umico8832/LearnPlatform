import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import FocusLayout from '@/components/layout/FocusLayout.vue'

let wrapper: VueWrapper | undefined

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  window.history.replaceState({}, '')
})

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/my-courses', component: { template: '<div>课程</div>' } },
      { path: '/my-courses/:id', component: { template: '<div>课程空间</div>' } },
      { path: '/my-courses/:id/tutor', name: 'TutorSession', component: { template: '<div>教学</div>' } },
      { path: '/exams', component: { template: '<div>考试</div>' } },
      { path: '/exams/result/:recordId', name: 'ExamResult', component: { template: '<div>结果</div>' } },
    ],
  })
}

describe('FocusLayout navigation', () => {
  it('returns Tutor sessions to their known course when no internal history entry exists', async () => {
    const router = createTestRouter()
    await router.push('/my-courses/17/tutor')
    await router.isReady()
    wrapper = mount(FocusLayout, { global: { plugins: [createPinia(), router, ElementPlus] } })

    await wrapper.get('.focus-back').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/my-courses/17')
  })

  it('uses an internal Vue history back path before the exam fallback', async () => {
    const router = createTestRouter()
    await router.push('/my-courses')
    await router.push('/exams/result/41')
    await router.isReady()
    window.history.replaceState({ back: '/my-courses' }, '')
    wrapper = mount(FocusLayout, { global: { plugins: [createPinia(), router, ElementPlus] } })

    await wrapper.get('.focus-back').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/my-courses')
  })

  it('moves focus only when the focused route path changes', async () => {
    const router = createTestRouter()
    await router.push('/exams')
    await router.isReady()
    wrapper = mount(FocusLayout, {
      attachTo: document.body,
      global: { plugins: [createPinia(), router, ElementPlus] },
    })
    await router.push('/exams/result/42')
    await flushPromises()
    expect(document.activeElement).toBe(wrapper.get('main').element)

    const outside = document.createElement('button')
    document.body.append(outside)
    outside.focus()
    await router.push({ path: '/exams/result/42', query: { panel: 'answers' } })
    await flushPromises()
    expect(document.activeElement).toBe(outside)
  })
})
