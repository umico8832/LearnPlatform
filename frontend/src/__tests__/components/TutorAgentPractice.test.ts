import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { TutorAgentPracticeVO } from '@/api/tutor'
const { get, submit } = vi.hoisted(() => ({ get: vi.fn(), submit: vi.fn() }))
vi.mock('@/api/tutor', () => ({
  getTutorAgentPractice: (...args: unknown[]) => get(...args),
  submitTutorAgentPractice: (...args: unknown[]) => submit(...args),
}))
import TutorAgentPractice from '@/components/course/TutorAgentPractice.vue'
const stubs = {
  'el-button': {
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    props: ['disabled'],
    emits: ['click'],
  },
  'el-radio-group': {
    template: "<button @click=\"$emit('update:modelValue', 'A')\"><slot /></button>",
    emits: ['update:modelValue'],
  },
  'el-radio': { template: '<span><slot /></span>', props: ['value'] },
  'el-alert': { template: '<div>{{title}}</div>', props: ['title'] },
}
const practice: TutorAgentPracticeVO = {
  question: {
    id: 7,
    content: '题目',
    questionType: 'SINGLE_CHOICE' as const,
    options: [{ label: 'A', content: '选项' }],
  },
  result: null,
}
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}
type PracticeProps = {
  courseId: number
  sessionKey: string
  runKey: string
  sequence: number
  busy?: boolean
  allowFollowUp?: boolean
}
function mountPractice(props: PracticeProps = { courseId: 1, sessionKey: 's', runKey: 'r', sequence: 2 }) {
  return mount(TutorAgentPractice, { props, global: { stubs } })
}
describe('TutorAgentPractice', () => {
  beforeEach(() => {
    get.mockReset()
    submit.mockReset()
  })
  it('loads only after an explicit click, submits, and restores the server result', async () => {
    get.mockResolvedValueOnce({ data: practice })
    submit.mockResolvedValueOnce({
      data: {
        ...practice,
        result: {
          recordId: 1,
          questionId: 7,
          userAnswer: 'A',
          correct: true,
          correctAnswer: 'A',
          analysis: '解析',
          score: 1,
        },
      },
    })
    const wrapper = mount(TutorAgentPractice, {
      props: { courseId: 1, sessionKey: 's', runKey: 'r', sequence: 2 },
      global: { stubs },
    })
    expect(get).not.toHaveBeenCalled()
    await wrapper.get('[data-testid="practice-open"]').trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledWith(1, 's', 'r', 2)
    await wrapper.findAll('button')[0].trigger('click')
    await wrapper.get('[data-testid="practice-submit"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('回答正确')
    expect(wrapper.get('[data-testid="practice-follow-up"]')).toBeTruthy()
  })

  it('keeps loading explicit after GET fails, and retries only when clicked', async () => {
    get.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ data: practice })
    const wrapper = mountPractice()
    await wrapper.get('[data-testid="practice-open"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('offline')
    expect(get).toHaveBeenCalledTimes(1)
    await wrapper.get('[data-testid="practice-open"]').trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('题目')
  })

  it('recovers the persisted server answer after a POST network failure', async () => {
    get.mockResolvedValueOnce({ data: practice }).mockResolvedValueOnce({
      data: {
        ...practice,
        result: {
          recordId: 2,
          questionId: 7,
          userAnswer: 'B',
          correct: false,
          correctAnswer: 'A',
          analysis: '解析',
          score: 1,
        },
      },
    })
    submit.mockRejectedValueOnce(new Error('network'))
    const wrapper = mountPractice()
    await wrapper.get('[data-testid="practice-open"]').trigger('click')
    await flushPromises()
    await wrapper.findAll('button')[0].trigger('click')
    await wrapper.get('[data-testid="practice-submit"]').trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('回答不正确')
    expect(wrapper.text()).toContain('你的选择：B')
  })

  it('does not let a late GET response pollute switched session props', async () => {
    const firstGet = deferred<{ data: typeof practice }>()
    get
      .mockReturnValueOnce(firstGet.promise)
      .mockResolvedValueOnce({ data: { ...practice, question: { ...practice.question, content: '新题' } } })
    const wrapper = mountPractice()
    await wrapper.get('[data-testid="practice-open"]').trigger('click')
    await wrapper.setProps({ sessionKey: 'new', sequence: 3 })
    firstGet.resolve({ data: practice })
    await flushPromises()
    expect(wrapper.find('[data-testid="practice-open"]').exists()).toBe(true)
    await wrapper.get('[data-testid="practice-open"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('新题')
  })

  it('does not let a late POST response pollute switched session props', async () => {
    const posting = deferred<{ data: TutorAgentPracticeVO }>()
    get.mockResolvedValueOnce({ data: practice })
    submit.mockReturnValueOnce(posting.promise)
    const wrapper = mountPractice()
    await wrapper.get('[data-testid="practice-open"]').trigger('click')
    await flushPromises()
    await wrapper.findAll('button')[0].trigger('click')
    await wrapper.get('[data-testid="practice-submit"]').trigger('click')
    await wrapper.setProps({ sessionKey: 'new', sequence: 3 })
    posting.resolve({
      data: {
        ...practice,
        result: {
          recordId: 3,
          questionId: 7,
          userAnswer: 'A',
          correct: true,
          correctAnswer: 'A',
          analysis: '解析',
          score: 1,
        },
      },
    })
    await flushPromises()
    expect(wrapper.find('[data-testid="practice-open"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('回答正确')
  })

  it('ignores a late recovery GET after a POST failure when props change', async () => {
    const recovery = deferred<{ data: TutorAgentPracticeVO }>()
    get.mockResolvedValueOnce({ data: practice }).mockReturnValueOnce(recovery.promise)
    submit.mockRejectedValueOnce(new Error('network'))
    const wrapper = mountPractice()
    await wrapper.get('[data-testid="practice-open"]').trigger('click')
    await flushPromises()
    await wrapper.findAll('button')[0].trigger('click')
    await wrapper.get('[data-testid="practice-submit"]').trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledTimes(2)
    await wrapper.setProps({ sessionKey: 'new', sequence: 3 })
    recovery.resolve({
      data: {
        ...practice,
        result: {
          recordId: 4,
          questionId: 7,
          userAnswer: 'B',
          correct: false,
          correctAnswer: 'A',
          analysis: '解析',
          score: 1,
        },
      },
    })
    await flushPromises()
    expect(wrapper.find('[data-testid="practice-open"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('你的选择：B')
  })

  it('deduplicates repeated load and submit clicks', async () => {
    const loading = deferred<{ data: typeof practice }>()
    get.mockReturnValueOnce(loading.promise)
    const wrapper = mountPractice()
    await wrapper.get('[data-testid="practice-open"]').trigger('click')
    await wrapper.get('[data-testid="practice-open"]').trigger('click')
    expect(get).toHaveBeenCalledTimes(1)
    loading.resolve({ data: practice })
    await flushPromises()
    const posting = deferred<{ data: typeof practice }>()
    submit.mockReturnValueOnce(posting.promise)
    await wrapper.findAll('button')[0].trigger('click')
    await wrapper.get('[data-testid="practice-submit"]').trigger('click')
    await wrapper.get('[data-testid="practice-submit"]').trigger('click')
    expect(submit).toHaveBeenCalledTimes(1)
  })

  it('requires an explicit successful sync before retrying a failed POST', async () => {
    get
      .mockResolvedValueOnce({ data: practice })
      .mockRejectedValueOnce(new Error('sync offline'))
      .mockResolvedValueOnce({ data: practice })
    submit.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({
      data: {
        ...practice,
        result: {
          recordId: 5,
          questionId: 7,
          userAnswer: 'A',
          correct: true,
          correctAnswer: 'A',
          analysis: '解析',
          score: 1,
        },
      },
    })
    const wrapper = mountPractice()
    await wrapper.get('[data-testid="practice-open"]').trigger('click')
    await flushPromises()
    await wrapper.findAll('button')[0].trigger('click')
    await wrapper.get('[data-testid="practice-submit"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="practice-sync"]')).toBeTruthy()
    await wrapper.get('[data-testid="practice-submit"]').trigger('click')
    expect(submit).toHaveBeenCalledTimes(1)
    await wrapper.get('[data-testid="practice-sync"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="practice-sync"]').exists()).toBe(false)
    await wrapper.get('[data-testid="practice-submit"]').trigger('click')
    await flushPromises()
    expect(submit).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('回答正确')
  })

  it('does not issue recovery GET work after unmount during a failed POST', async () => {
    const posting = deferred<{ data: typeof practice }>()
    get.mockResolvedValueOnce({ data: practice })
    submit.mockReturnValueOnce(posting.promise)
    const wrapper = mountPractice()
    await wrapper.get('[data-testid="practice-open"]').trigger('click')
    await flushPromises()
    await wrapper.findAll('button')[0].trigger('click')
    await wrapper.get('[data-testid="practice-submit"]').trigger('click')
    wrapper.unmount()
    posting.reject(new Error('network'))
    await flushPromises()
    expect(get).toHaveBeenCalledTimes(1)
  })

  it('does not emit follow-up while busy', async () => {
    get.mockResolvedValueOnce({
      data: {
        ...practice,
        result: {
          recordId: 6,
          questionId: 7,
          userAnswer: 'A',
          correct: true,
          correctAnswer: 'A',
          analysis: '解析',
          score: 1,
        },
      },
    })
    const wrapper = mountPractice({ courseId: 1, sessionKey: 's', runKey: 'r', sequence: 2, busy: true })
    await wrapper.get('[data-testid="practice-open"]').trigger('click')
    await flushPromises()
    const followUp = wrapper.get('[data-testid="practice-follow-up"]')
    expect(followUp.attributes('disabled')).toBeDefined()
    await followUp.trigger('click')
    expect(wrapper.emitted('continue-practice')).toBeUndefined()
    await wrapper.setProps({ busy: false })
    await wrapper.get('[data-testid="practice-follow-up"]').trigger('click')
    expect(wrapper.emitted('continue-practice')).toEqual([[]])
  })
})
