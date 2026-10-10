import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { BusinessError } from '@/utils/errors'

const api = vi.hoisted(() => ({
  getUnderstandingQuestion: vi.fn(),
  submitAttempt: vi.fn(),
  revealSolution: vi.fn(),
  getVariant: vi.fn(),
  submitVariantAttempt: vi.fn(),
}))
vi.mock('@/api/understanding', () => api)
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()),
  useRoute: () => ({ params: { id: '17' } }),
  useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
}))
vi.mock('@/viz', () => ({ VizPlayer: { props: ['title'], template: '<div class="viz-stub">{{ title }}</div>' } }))
vi.mock('@/motion', () => ({
  springAnimate: vi.fn(),
  springs: { pointer: {} },
  blurIn: vi.fn(),
  prefersReducedMotion: () => true,
}))

import QuestionUnderstandingView from '@/views/question/QuestionUnderstandingView.vue'

const source = { kind: 'AI_GENERATED', title: '种子', url: null, verification: 'REVIEWED' }
const question = {
  questionId: 17,
  stem: '哪个出栈序列可行？',
  questionType: 'SINGLE_CHOICE',
  options: [
    { label: 'A', content: '3,2,1' },
    { label: 'B', content: '3,1,2' },
  ],
  position: {
    courseId: 1,
    courseName: '数据结构',
    chapterName: '栈',
    knowledgePointId: 2,
    knowledgePointName: '出栈序列',
  },
  source,
}
const misconceptionViz = {
  title: '用你选的序列演示',
  spec: { scene: 'stack', op: 'check', seq: [3, 1, 2], pauseAt: ['stack-buried'] },
}
const wrongAttempt = {
  attemptId: 81,
  correct: false,
  answer: 'B',
  misconception: {
    title: '越过栈顶',
    explanation: '不能越过栈顶',
    source: { kind: 'CURATED', reviewStatus: 'REVIEWED' },
  },
  viz: misconceptionViz,
}
const solution = {
  correctAnswer: 'A',
  explanation: '只能从栈顶取数',
  viz: { title: '正解演示', spec: { scene: 'stack', op: 'check', seq: [3, 2, 1], pauseAt: [] } },
}
const variant = {
  variantId: 91,
  stem: '哪个不可能？',
  options: question.options,
  changedCondition: '问法变化',
  source,
}
const variantResult = {
  variantAttemptId: 92,
  correct: false,
  correctAnswer: 'B',
  explanation: '仍要看栈顶',
  viz: null,
  assistance: 'UNAIDED',
}

const mounted: ReturnType<typeof mount>[] = []
function render() {
  const wrapper = mount(QuestionUnderstandingView, {
    global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
  })
  mounted.push(wrapper)
  return wrapper
}
function button(wrapper: ReturnType<typeof mount>, name: string) {
  const found = wrapper.findAll('button').find((item) => item.text().includes(name))
  if (!found) throw new Error(`button ${name} missing`)
  return found
}

beforeEach(() => {
  vi.clearAllMocks()
  api.getUnderstandingQuestion.mockResolvedValue({ data: question })
  api.submitAttempt.mockResolvedValue({ data: wrongAttempt })
  api.revealSolution.mockResolvedValue({ data: solution })
  api.getVariant.mockResolvedValue({ data: variant })
  api.submitVariantAttempt.mockResolvedValue({ data: variantResult })
})
afterEach(() => mounted.splice(0).forEach((wrapper) => wrapper.unmount()))

describe('QuestionUnderstandingView', () => {
  it('shows loading, missing content and retryable failure states', async () => {
    let finish!: (value: unknown) => void
    api.getUnderstandingQuestion.mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve
      }),
    )
    const loading = render()
    expect(loading.text()).toContain('正在加载题目')
    finish({ data: question })
    await flushPromises()
    expect(loading.text()).toContain('哪个出栈序列可行？')

    api.getUnderstandingQuestion.mockRejectedValueOnce(new BusinessError('没有内容', 1004))
    const missing = render()
    await flushPromises()
    expect(missing.text()).toContain('这道题还没有理解内容')

    api.getUnderstandingQuestion.mockRejectedValueOnce(new Error('连接失败'))
    const error = render()
    await flushPromises()
    expect(error.text()).toContain('题目没有加载出来')
    expect(error.text()).toContain('连接失败')
    await button(error, '重试').trigger('click')
    await flushPromises()
    expect(error.text()).toContain('哪个出栈序列可行？')
  })

  it('renders the wrong-answer path through misconception, demo, solution, variant and result', async () => {
    const wrapper = render()
    await flushPromises()
    expect(button(wrapper, '提交').attributes('disabled')).toBeDefined()
    await wrapper.find('input[value="B"]').setValue()
    expect(button(wrapper, '提交').attributes('disabled')).toBeUndefined()
    await button(wrapper, '提交').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('越过栈顶')
    expect(wrapper.text()).toContain('用你选的序列演示')
    expect(wrapper.text()).not.toContain('正确答案')
    await button(wrapper, '看正解').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('正确答案')
    expect(wrapper.text()).toContain('只能从栈顶取数')
    expect(wrapper.text()).toContain('换一个条件，再做一次')
    expect(wrapper.text()).toContain('哪个不可能？')
    expect(wrapper.findAll('.viz-stub')).toHaveLength(2)
    const variantStep = wrapper.findAll('article').find((item) => item.text().includes('哪个不可能？'))!
    expect(variantStep.find('button').attributes('disabled')).toBeDefined()
    await variantStep.find('input[value="A"]').setValue()
    await variantStep.find('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('这次还不对，正确答案是 B')
    expect(wrapper.text()).toContain('这道变式的思路')
  })
})
