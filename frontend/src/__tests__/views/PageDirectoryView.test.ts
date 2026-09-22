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
  it('选择页面不挂载真实页面，开始预览后切换页面会卸载', async () => {
    const wrapper = renderDirectory()
    expect(wrapper.find('iframe').exists()).toBe(false)
    await wrapper.get('[data-page="learner:TutorSession"]').trigger('click')
    expect(wrapper.find('iframe').exists()).toBe(false)
    expect(wrapper.get('.primary-action').attributes('disabled')).toBeDefined()
    await wrapper.get('#param-id').setValue('21')
    expect(wrapper.find('iframe').exists()).toBe(false)
    await wrapper.get('.primary-action').trigger('click')
    expect(wrapper.get('iframe').attributes('src')).toMatch(/\/my-courses\/21\/tutor$/)
    await wrapper.get('[data-page="learner:Home"]').trigger('click')
    expect(wrapper.find('iframe').exists()).toBe(false)
  })

  it('参数变化停止旧会话预览，无效参数不能打开业务页', async () => {
    const wrapper = renderDirectory()
    await wrapper.get('[data-page="learner:ExamTake"]').trigger('click')
    await wrapper.get('#param-recordId').setValue('7')
    await wrapper.get('.primary-action').trigger('click')
    expect(wrapper.find('iframe').exists()).toBe(true)
    await wrapper.get('#param-recordId').setValue('0')
    expect(wrapper.find('iframe').exists()).toBe(false)
    expect(wrapper.get('.primary-action').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.secondary-action').exists()).toBe(false)
    expect(wrapper.get('.page-actions a').attributes('href')).toMatch(/\/exams$/)
  })

  it('管理端使用独立地址且地址变更不会自动加载页面', async () => {
    const wrapper = renderDirectory()
    await wrapper.get('[data-page="admin:AdminQuestionManage"]').trigger('click')
    expect(wrapper.get('.secondary-action').attributes('href')).toContain(':5174/admin/questions')
    await wrapper.get('#admin-base').setValue('http://localhost:6274/admin/')
    expect(wrapper.get('.secondary-action').attributes('href')).toBe('http://localhost:6274/admin/questions')
    expect(wrapper.find('iframe').exists()).toBe(false)
    await wrapper.get('#admin-base').setValue('javascript:alert(1)')
    expect(wrapper.get('.primary-action').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
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
    expect(wrapper.get('a[href="/dev/auth-preview"]').attributes('target')).toBe('_blank')
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
