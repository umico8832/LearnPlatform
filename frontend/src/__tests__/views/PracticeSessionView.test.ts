import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'

const { mockSubmitAnswer, mockPush, mockReplace } = vi.hoisted(() => ({
  mockSubmitAnswer: vi.fn(),
  mockPush: vi.fn(),
  mockReplace: vi.fn(),
}))

vi.mock('@/api/practice', () => ({
  submitAnswer: (...args: unknown[]) => mockSubmitAnswer(...args),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({ push: mockPush, replace: mockReplace }),
  }
})

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), warning: vi.fn() },
}))

import PracticeSessionView from '@/views/practice/PracticeSessionView.vue'

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

  it('keeps the result intact until the closing transition finishes before moving to the next question', async () => {
    const wrapper = mount(PracticeSessionView, { global: { stubs } })
    await flushPromises()

    const correctOption = wrapper.findAll('input[type="radio"]').find((option) => option.attributes('value') === 'TRUE')
    expect(correctOption).toBeDefined()
    await correctOption!.setValue()
    const submitButton = wrapper.findAll('button').find((button) => button.text().includes('提交答案'))
    await submitButton!.trigger('click')
    await flushPromises()

    const nextButton = wrapper.findAll('button').find((button) => button.text().includes('下一题'))
    await nextButton!.trigger('click')

    expect(wrapper.text()).toContain('第一题')
    expect(wrapper.get('[data-testid="result-dialog"]').text()).toContain('答对了')

    ;(wrapper.findComponent(DialogStub).vm as unknown as { finishClosing: () => void }).finishClosing()
    await flushPromises()

    expect(wrapper.text()).toContain('第二题')
    expect(wrapper.find('[data-testid="result-dialog"]').exists()).toBe(false)
  })

  it('uses native radio controls for true-or-false answers', async () => {
    const wrapper = mount(PracticeSessionView, { global: { stubs } })
    await flushPromises()

    const radios = wrapper.findAll('input[type="radio"]')
    expect(radios).toHaveLength(2)
    await radios[0].setValue()
    expect((radios[0].element as HTMLInputElement).checked).toBe(true)
  })
})
