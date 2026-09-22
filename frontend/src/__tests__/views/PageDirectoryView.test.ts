import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PageDirectoryView from '@/views/dev/PageDirectoryView.vue'
import { pages } from '@/views/dev/pageCatalog'
import { normalizeAdminBase, resolvePageUrl } from '@/views/dev/pageDirectoryUrl'

const wrappers: ReturnType<typeof mount>[] = []
function renderDirectory() {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    },
  )
  const wrapper = mount(PageDirectoryView)
  wrappers.push(wrapper)
  return wrapper
}

afterEach(() => {
  wrappers.forEach((wrapper) => wrapper.unmount())
  wrappers.length = 0
  vi.unstubAllGlobals()
})

describe('全局页面预览', () => {
  it('初始首页自动预览，选择普通页面会立即替换预览', async () => {
    const wrapper = renderDirectory()
    expect(wrapper.get('iframe').attributes('src')).toBe(`${window.location.origin}/`)
    expect(wrapper.find('.primary-action').exists()).toBe(false)

    await wrapper.get('[data-page="learner:LearningDiagnosis"]').trigger('click')
    expect(wrapper.get('iframe').attributes('src')).toBe(`${window.location.origin}/learning-diagnosis`)
    expect(wrapper.find('#auth-preview-state').exists()).toBe(false)
  })

  it('动态参数改变时卸载旧预览，只在按 Enter 后加载有效地址', async () => {
    const wrapper = renderDirectory()
    await wrapper.get('[data-page="learner:TutorSession"]').trigger('click')
    expect(wrapper.find('iframe').exists()).toBe(false)

    const idInput = wrapper.get('#param-id')
    await idInput.setValue('21')
    expect(wrapper.find('iframe').exists()).toBe(false)
    await idInput.trigger('keydown.enter')
    expect(wrapper.get('iframe').attributes('src')).toBe(`${window.location.origin}/my-courses/21/tutor`)

    await idInput.setValue('0')
    expect(wrapper.find('iframe').exists()).toBe(false)
    await idInput.trigger('keydown.enter')
    expect(wrapper.find('iframe').exists()).toBe(false)
  })

  it('管理端选择后立即预览，编辑地址只在按 Enter 的有效地址后应用', async () => {
    const wrapper = renderDirectory()
    await wrapper.get('[data-page="admin:AdminQuestionManage"]').trigger('click')
    expect(wrapper.get('iframe').attributes('src')).toBe('http://localhost:5174/admin/questions')

    const baseInput = wrapper.get('#admin-base')
    await baseInput.setValue('http://localhost:6274/admin/')
    expect(wrapper.find('iframe').exists()).toBe(false)
    expect(wrapper.get('.secondary-action').attributes('href')).toBe('http://localhost:6274/admin/questions')
    await baseInput.trigger('keydown.enter')
    expect(wrapper.get('iframe').attributes('src')).toBe('http://localhost:6274/admin/questions')
    expect(wrapper.get('.secondary-action').attributes('href')).toBe('http://localhost:6274/admin/questions')

    await baseInput.setValue('javascript:alert(1)')
    expect(wrapper.find('iframe').exists()).toBe(false)
    await baseInput.trigger('keydown.enter')
    expect(wrapper.find('iframe').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })

  it('认证状态选项只在认证页面出现，普通及管理页面不会携带认证预览参数', async () => {
    const wrapper = renderDirectory()
    await wrapper.get('[data-page="learner:Login"]').trigger('click')
    expect(wrapper.get('#auth-preview-state').element).toBeInstanceOf(HTMLSelectElement)
    expect(wrapper.get('iframe').attributes('src')).toBe(`${window.location.origin}/login?auth-preview=default`)

    await wrapper.get('[data-page="admin:AdminLogin"]').trigger('click')
    expect(wrapper.find('#auth-preview-state').exists()).toBe(false)
    expect(wrapper.get('iframe').attributes('src')).toBe('http://localhost:5174/admin/login')
    expect(wrapper.get('iframe').attributes('src')).not.toContain('auth-preview')
  })

  it('真实认证流程会移除状态参数，并在切换页面时恢复该页的默认状态', async () => {
    const wrapper = renderDirectory()
    await wrapper.get('[data-page="learner:Register"]').trigger('click')
    const state = wrapper.get('#auth-preview-state')
    expect(wrapper.get('iframe').attributes('src')).toBe(`${window.location.origin}/register?auth-preview=step-1`)

    await wrapper.get('#preview-width').setValue('390')
    await state.setValue('step-3')
    expect(wrapper.get('iframe').attributes('src')).toBe(`${window.location.origin}/register?auth-preview=step-3`)
    expect(wrapper.get<HTMLSelectElement>('#preview-width').element.value).toBe('390')
    expect(wrapper.get('iframe').attributes('style')).toContain('height: 844px')
    expect(wrapper.get('.secondary-action').attributes('href')).toBe(
      `${window.location.origin}/register?auth-preview=step-3`,
    )

    await state.setValue('')
    expect(wrapper.get('iframe').attributes('src')).toBe(`${window.location.origin}/register`)
    expect(wrapper.get('.secondary-action').attributes('href')).toBe(`${window.location.origin}/register`)

    await wrapper.get('[data-page="learner:Login"]').trigger('click')
    await wrapper.get('[data-page="learner:Register"]').trigger('click')
    expect(wrapper.get<HTMLSelectElement>('#auth-preview-state').element.value).toBe('step-1')
    expect(wrapper.get('iframe').attributes('src')).toBe(`${window.location.origin}/register?auth-preview=step-1`)
  })

  it.each([
    ['Login', 'default', '/login?auth-preview=default'],
    ['Register', 'step-1', '/register?auth-preview=step-1'],
    ['Register', 'step-2', '/register?auth-preview=step-2'],
    ['Register', 'step-3', '/register?auth-preview=step-3'],
    ['ForgotPassword', 'form', '/forgot-password?auth-preview=form'],
    ['ForgotPassword', 'sent', '/forgot-password?auth-preview=sent'],
    ['ForgotPassword', 'resent', '/forgot-password?auth-preview=resent'],
    ['ForgotPassword', 'support', '/forgot-password?auth-preview=support'],
    ['ResetPassword', 'checking', '/reset-password?auth-preview=checking'],
    ['ResetPassword', 'form', '/reset-password?auth-preview=form'],
    ['ResetPassword', 'success', '/reset-password?auth-preview=success'],
    ['ResetPassword', 'error', '/reset-password?auth-preview=error'],
    ['OAuthCallback', 'loading', '/oauth/callback?auth-preview=loading'],
    ['OAuthCallback', 'error', '/oauth/callback?auth-preview=error'],
  ])('认证状态 %s / %s 会立即更新 iframe 与新标签地址', async (pageName, state, expectedPath) => {
    const wrapper = renderDirectory()
    await wrapper.get(`[data-page="learner:${pageName}"]`).trigger('click')
    await wrapper.get('#auth-preview-state').setValue(state)

    const expectedUrl = `${window.location.origin}${expectedPath}`
    expect(wrapper.get('iframe').attributes('src')).toBe(expectedUrl)
    expect(wrapper.get('.secondary-action').attributes('href')).toBe(expectedUrl)
  })

  it('按隐藏页面名称、路由名或路径查找，空结果保留恢复入口', async () => {
    const wrapper = renderDirectory()
    await wrapper.get('#page-search').setValue('learning-diagnosis')
    expect(wrapper.findAll('[data-page]')).toHaveLength(1)
    await wrapper.get('[data-page="learner:LearningDiagnosis"]').trigger('click')
    expect(wrapper.get('.page-badges').text()).toContain('菜单中隐藏')
    await wrapper.get('#page-search').setValue('不存在的页面关键词')
    expect(wrapper.findAll('[data-page]')).toHaveLength(0)
    expect(wrapper.find('.search-empty').exists()).toBe(true)
    await wrapper.get('#page-search').setValue('')
    expect(wrapper.findAll('[data-page]')).toHaveLength(pages.length)
    expect(wrapper.find('a[href="/dev/auth-preview"]').exists()).toBe(false)
  })
})

describe('页面访问参数', () => {
  const exam = pages.find((page) => page.name === 'ExamTake')!
  it.each(['', '0', '-1', '1.5', '1/../../login', '1?role=admin', '9007199254740992'])(
    '拒绝无效记录 ID %s',
    (recordId) => {
      expect(resolvePageUrl(exam, { recordId }, 'http://localhost:5173', '')).toBeUndefined()
    },
  )
  it('保留管理端部署子路径，并拒绝带凭据的地址', () => {
    expect(normalizeAdminBase('http://localhost:5174')).toBe('http://localhost:5174/admin/')
    expect(normalizeAdminBase('http://localhost:5174/tools/admin')).toBe('http://localhost:5174/tools/admin/')
    expect(normalizeAdminBase('https://example.test/admin/?key=example')).toBeUndefined()
    expect(normalizeAdminBase('https://example:placeholder@example.test/admin/')).toBeUndefined()
  })
})
