import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'

const { mockSubmitAnswer, mockPush, mockReplace, mockError, mockRouteLeave } = vi.hoisted(() => ({
  mockSubmitAnswer: vi.fn(),
  mockPush: vi.fn(),
  mockReplace: vi.fn(),
  mockError: vi.fn(),
  mockRouteLeave: vi.fn(),
}))

vi.mock('@/api/practice', () => ({
  submitAnswer: (...args: unknown[]) => mockSubmitAnswer(...args),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({ push: mockPush, replace: mockReplace }),
    onBeforeRouteLeave: (guard: unknown) => mockRouteLeave(guard),
  }
})

vi.mock('element-plus', () => ({
  ElMessage: { error: mockError, warning: vi.fn() },
}))

import PracticeSessionView from '@/views/practice/PracticeSessionView.vue'
import { useGamificationStore } from '@/stores/gamification'

const DialogStub = defineComponent({
  name: 'ElDialog',
  template: '<section v-if="visible" data-testid="result-dialog">{{ title }}<slot /><slot name="footer" /></section>',
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, default: '' },
  },
  emits: ['closed'],
  data() {
    return { visible: this.modelValue }
  },
  watch: {
    modelValue(value: boolean) {
      if (value) this.visible = true
    },
  },
  methods: {
    finishClosing() {
      this.visible = false
      this.$emit('closed')
    },
  },
})

const stubs = {
  'el-dialog': DialogStub,
  'el-button': {
    template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['disabled', 'loading'],
    emits: ['click'],
  },
  'el-card': { template: '<div><slot /></div>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-progress': { template: '<div />' },
  'el-rate': { template: '<div />' },
  'el-divider': { template: '<hr />' },
  'el-icon': { template: '<i><slot /></i>' },
  'el-checkbox': { template: '<input type="checkbox" />' },
  'el-input': {
    template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue'],
    emits: ['update:modelValue'],
  },
  'ai-question-assistant': { template: '<div />' },
  'question-learning-asset': { template: '<div />' },
}

const questions = [
  {
    id: 1,
    content: '第一题',
    questionType: 'TRUE_FALSE',
    courseId: 1,
    courseName: 'Java',
    difficulty: 1,
    score: 5,
    tags: '',
    options: [],
    knowledgePointIds: [],
    knowledgePointNames: [],
  },
  {
    id: 2,
    content: '第二题',
    questionType: 'TRUE_FALSE',
    courseId: 1,
    courseName: 'Java',
    difficulty: 1,
    score: 5,
    tags: '',
    options: [],
    knowledgePointIds: [],
    knowledgePointNames: [],
  },
]

describe('PracticeSessionView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    useUserStore().setLoginInfo('test-token', {
      id: 7,
      username: 'learner',
      nickname: 'Learner',
      avatar: null,
      role: 'USER',
    })
    sessionStorage.clear()
    sessionStorage.setItem('practice_user_id', '7')
    sessionStorage.setItem('practice_questions', JSON.stringify(questions))
    mockSubmitAnswer.mockResolvedValue({
      code: 0,
      data: {
        recordId: 1,
        questionId: 1,
        userAnswer: 'TRUE',
        correct: true,
        correctAnswer: 'TRUE',
        analysis: '解析',
        score: 5,
      },
    })
  })

  it('waits for the authenticated user to restore before reading a refresh cache', async () => {
    const store = useUserStore()
    const user = store.userInfo
    store.userInfo = null
    vi.spyOn(store, 'fetchUserInfo').mockImplementation(async () => {
      store.userInfo = user
    })
    const wrapper = mount(PracticeSessionView, { global: { stubs } })
    await flushPromises()
    expect(store.fetchUserInfo).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('第一题')
    expect(mockReplace).not.toHaveBeenCalled()
  })

  it.each(['other account', 'legacy cache'])('does not render private questions from %s', async (kind) => {
    if (kind === 'other account') sessionStorage.setItem('practice_user_id', '8')
    else sessionStorage.removeItem('practice_user_id')
    const wrapper = mount(PracticeSessionView, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('第一题')
    expect(mockReplace).toHaveBeenCalledWith({ name: 'Practice' })
    expect(sessionStorage.getItem('practice_questions')).toBeNull()
  })

  it('clears the refresh cache only when leaving the session route', async () => {
    const wrapper = mount(PracticeSessionView, { global: { stubs } })
    await flushPromises()
    const leave = mockRouteLeave.mock.calls.at(-1)?.[0] as () => boolean
    expect(leave()).toBe(true)
    expect(sessionStorage.getItem('practice_questions')).toBeNull()
    wrapper.unmount()
  })

  it('returns to practice after authentication changes so another account never sees a blank old session', async () => {
    const wrapper = mount(PracticeSessionView, { global: { stubs } })
    await flushPromises()
    useUserStore().setLoginInfo('other-token', {
      id: 8,
      username: 'other',
      nickname: 'Other',
      avatar: null,
      role: 'USER',
    })
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith({ name: 'Practice' })
    expect(wrapper.text()).not.toContain('第一题')
  })

  it('moves focus to the inline next action, then to the next question heading', async () => {
    const wrapper = mount(PracticeSessionView, { attachTo: document.body, global: { stubs } })
    await flushPromises()

    const correctOption = wrapper.findAll('input[type="radio"]').find((option) => option.attributes('value') === 'TRUE')
    expect(correctOption).toBeDefined()
    await correctOption!.setValue()
    const submitButton = wrapper.findAll('button').find((button) => button.text().includes('提交答案'))
    await submitButton!.trigger('click')
    await flushPromises()

    const nextButton = wrapper.findAll('button').find((button) => button.text().includes('下一题'))
    expect(document.activeElement).toBe(nextButton!.element)
    await nextButton!.trigger('click')

    expect(wrapper.text()).toContain('第二题')
    expect(wrapper.find('[data-testid="practice-feedback"]').exists()).toBe(false)
    expect(document.activeElement).toBe(wrapper.get('.question-content').element)
    wrapper.unmount()
  })

  it('uses native radio controls for true-or-false answers', async () => {
    const wrapper = mount(PracticeSessionView, { global: { stubs } })
    await flushPromises()

    const radios = wrapper.findAll('input[type="radio"]')
    expect(radios).toHaveLength(2)
    await radios[0].setValue()
    expect((radios[0].element as HTMLInputElement).checked).toBe(true)
  })

  it('keeps an ungraded answer out of correct and wrong counts', async () => {
    mockSubmitAnswer.mockResolvedValueOnce({
      code: 0,
      data: {
        recordId: 1,
        questionId: 1,
        userAnswer: '解释',
        correct: null,
        correctAnswer: '',
        analysis: '',
        score: 0,
      },
    })
    const wrapper = mount(PracticeSessionView, { global: { stubs } })
    await flushPromises()
    await wrapper.findAll('input[type="radio"]')[0]!.setValue()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="practice-feedback"]').text()).toContain('等待判分')
    expect(wrapper.text()).toContain('已判分：0 对，0 错')
  })

  it('summarizes only graded answers while retaining a pending answer count', async () => {
    mockSubmitAnswer
      .mockResolvedValueOnce({
        code: 0,
        data: {
          recordId: 1,
          questionId: 1,
          userAnswer: 'TRUE',
          correct: true,
          correctAnswer: 'TRUE',
          analysis: '解析',
          score: 5,
        },
      })
      .mockResolvedValueOnce({
        code: 0,
        data: {
          recordId: 2,
          questionId: 2,
          userAnswer: 'FALSE',
          correct: null,
          correctAnswer: '',
          analysis: '',
          score: 0,
        },
      })
    const wrapper = mount(PracticeSessionView, { global: { stubs } })
    await flushPromises()

    await wrapper.findAll('input[type="radio"]')[0]!.setValue()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('下一题'))!
      .trigger('click')

    await wrapper.findAll('input[type="radio"]')[1]!.setValue()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('查看结果'))!
      .trigger('click')

    expect(wrapper.text()).toContain('2 题已记录')
    expect(wrapper.text()).toContain('待判分1 题')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('100')
  })

  it('submits one answer once while the first request is still pending', async () => {
    let resolve!: (value: {
      code: number
      data: (typeof questions)[number] & {
        recordId: number
        userAnswer: string
        correct: boolean
        correctAnswer: string
        analysis: string
        score: number
      }
    }) => void
    mockSubmitAnswer.mockImplementationOnce(
      () =>
        new Promise((next) => {
          resolve = next
        }),
    )
    const wrapper = mount(PracticeSessionView, { global: { stubs } })
    await flushPromises()
    const correctOption = wrapper
      .findAll('input[type="radio"]')
      .find((option) => option.attributes('value') === 'TRUE')!
    await correctOption.setValue()
    const submitButton = wrapper.findAll('button').find((button) => button.text().includes('提交答案'))!
    const first = submitButton.trigger('click')
    const second = submitButton.trigger('click')
    expect(mockSubmitAnswer).toHaveBeenCalledOnce()
    resolve({
      code: 0,
      data: {
        ...questions[0],
        recordId: 1,
        userAnswer: 'TRUE',
        correct: true,
        correctAnswer: 'TRUE',
        analysis: '解析',
        score: 5,
      },
    })
    await Promise.all([first, second])
    await flushPromises()
    expect(wrapper.get('[data-testid="practice-feedback"]').text()).toContain('回答正确')
  })

  it('keeps a failed submission beside the answer with a retry, without a global error toast', async () => {
    mockSubmitAnswer.mockRejectedValueOnce(new Error('network'))
    const acceptReward = vi.spyOn(useGamificationStore(), 'acceptReward')
    const wrapper = mount(PracticeSessionView, { global: { stubs } })
    await flushPromises()
    const correctOption = wrapper
      .findAll('input[type="radio"]')
      .find((option) => option.attributes('value') === 'TRUE')!
    await correctOption.setValue()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    await flushPromises()
    expect(acceptReward).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="result-dialog"]').exists()).toBe(false)
    expect(wrapper.get('[role="alert"]').text()).toContain('提交答案失败')
    expect(wrapper.get('[role="alert"] button').text()).toBe('重试')
    expect(mockError).not.toHaveBeenCalled()
  })

  it('ignores a late submission from the account that started it', async () => {
    let resolve!: (value: {
      code: number
      data: (typeof questions)[number] & {
        recordId: number
        userAnswer: string
        correct: boolean
        correctAnswer: string
        analysis: string
        score: number
      }
    }) => void
    mockSubmitAnswer.mockImplementationOnce(
      () =>
        new Promise((next) => {
          resolve = next
        }),
    )
    const acceptReward = vi.spyOn(useGamificationStore(), 'acceptReward')
    const wrapper = mount(PracticeSessionView, { global: { stubs } })
    await flushPromises()
    const correctOption = wrapper
      .findAll('input[type="radio"]')
      .find((option) => option.attributes('value') === 'TRUE')!
    await correctOption.setValue()
    const pending = wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    useUserStore().setLoginInfo('other-account-token', {
      id: 8,
      username: 'other',
      nickname: 'Other',
      avatar: null,
      role: 'USER',
    })
    resolve({
      code: 0,
      data: {
        ...questions[0],
        recordId: 1,
        userAnswer: 'TRUE',
        correct: true,
        correctAnswer: 'TRUE',
        analysis: '解析',
        score: 5,
      },
    })
    await pending
    await flushPromises()
    expect(acceptReward).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('答对了')
  })

  it('does not place a late failed submission on the next account session', async () => {
    let reject!: (error: Error) => void
    mockSubmitAnswer.mockImplementationOnce(
      () =>
        new Promise((_resolve, next) => {
          reject = next
        }),
    )
    const wrapper = mount(PracticeSessionView, { global: { stubs } })
    await flushPromises()
    await wrapper.findAll('input[type="radio"]')[0]!.setValue()
    const pending = wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交答案'))!
      .trigger('click')
    useUserStore().setLoginInfo('other-account-token', {
      id: 8,
      username: 'other',
      nickname: 'Other',
      avatar: null,
      role: 'USER',
    })
    reject(new Error('offline'))
    await pending
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })
})
