import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import SimilarQuestionsDialog from '@/components/practice/SimilarQuestionsDialog.vue'

enableAutoUnmount(afterEach)
const { read, question, push } = vi.hoisted(() => ({ read: vi.fn(), question: vi.fn(), push: vi.fn() }))
vi.mock('@/api/statistics', () => ({ getSimilarQuestions: read }))
vi.mock('@/api/question', () => ({ getQuestionById: question }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))

function mountDialog() {
  return mount(SimilarQuestionsDialog, {
    global: {
      stubs: {
        'el-dialog': {
          props: ['modelValue'],
          template: '<section v-if="modelValue"><slot/><slot name="footer"/></section>',
        },
        'el-table': { template: '<div/>' },
        'el-empty': { props: ['description'], template: '<p>{{ description }}</p>' },
        'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot/></button>' },
      },
      directives: { loading: () => undefined },
    },
  })
}
function deferred() {
  let resolve: (value: unknown) => void = () => undefined
  const promise = new Promise((done) => (resolve = done))
  return { promise, resolve }
}
function open(wrapper: ReturnType<typeof mountDialog>, id = 10, content = '原题') {
  return (wrapper.vm as unknown as { open: (id: number, content: string) => Promise<void> }).open(id, content)
}
function start(wrapper: ReturnType<typeof mountDialog>) {
  return wrapper.findAll('button').find((button) => button.text().includes('开始练习相似题'))!
}

describe('similar question recovery', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useUserStore().setLoginInfo('test-token', {
      id: 7,
      username: 'learner',
      nickname: 'Learner',
      avatar: null,
      role: 'USER',
    })
    vi.resetAllMocks()
    sessionStorage.clear()
    read.mockResolvedValue({ data: { sourceQuestionId: 10, similarQuestions: [{ questionId: 11 }] } })
    question.mockResolvedValue({ data: { id: 11, content: '正式题面' } })
  })

  it('keeps a failed read distinct from empty and retries the same source', async () => {
    read.mockRejectedValueOnce(new Error('暂时无法读取'))
    const wrapper = mountDialog()
    await open(wrapper)
    await flushPromises()
    expect(wrapper.text()).toContain('暂时无法读取')
    expect(wrapper.text()).not.toContain('暂无相似题目')
    await wrapper.get('[data-testid="similar-retry"]').trigger('click')
    await flushPromises()
    expect(read).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).not.toContain('暂时无法读取')
  })

  it('ignores the late result of a previously opened source', async () => {
    const pending = deferred()
    read.mockReturnValueOnce(pending.promise)
    const wrapper = mountDialog()
    const first = open(wrapper, 10, '先前原题')
    await open(wrapper, 20, '当前原题')
    pending.resolve({ data: { sourceQuestionId: 10, similarQuestions: [] } })
    await first
    await flushPromises()
    expect(start(wrapper).attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).toContain('当前原题')
  })

  it('locks practice reads and ignores their completion after closing', async () => {
    const pending = deferred()
    question.mockReturnValue(pending.promise)
    const wrapper = mountDialog()
    await open(wrapper)
    await flushPromises()
    await start(wrapper).trigger('click')
    await start(wrapper).trigger('click')
    expect(question).toHaveBeenCalledOnce()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '关闭')!
      .trigger('click')
    pending.resolve({ data: { id: 11, content: '迟到的题面' } })
    await flushPromises()
    expect(push).not.toHaveBeenCalled()
    expect(sessionStorage.getItem('practice_questions')).toBeNull()
  })

  it('keeps failed practice reads in the dialog with the selected source', async () => {
    question.mockRejectedValueOnce(new Error('题面暂时无法读取'))
    const wrapper = mountDialog()
    await open(wrapper)
    await flushPromises()
    await start(wrapper).trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('题面暂时无法读取')
    expect(wrapper.text()).toContain('原题')
    await start(wrapper).trigger('click')
    await flushPromises()
    expect(push).toHaveBeenCalledWith({ path: '/practice/session' })
  })
})
