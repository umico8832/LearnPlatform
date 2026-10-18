import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import { redoWhen } from '@/utils/redoDate'
import { BusinessError } from '@/utils/errors'

const api = vi.hoisted(() => ({
  getUnderstandingQuestion: vi.fn(),
  submitAttempt: vi.fn(),
  revealSolution: vi.fn(),
  getVariant: vi.fn(),
  submitVariantAttempt: vi.fn(),
}))
vi.mock('@/api/understanding', () => api)
const tutorApi = vi.hoisted(() => ({ getTutorNext: vi.fn(), requestNextHint: vi.fn(), recordVizView: vi.fn() }))
vi.mock('@/api/questionTutor', () => tutorApi)
const previewApi = vi.hoisted(() => ({ previewUnderstandingQuestion: vi.fn() }))
vi.mock('@/api/understandingPreview', () => previewApi)
const route = reactive({ params: { id: '17' }, query: {} as Record<string, string> })
const router = vi.hoisted(() => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }))
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()),
  useRoute: () => route,
  useRouter: () => router,
}))
vi.mock('@/viz', () => ({ VizPlayer: { props: ['title'], template: '<div class="viz-stub">{{ title }}</div>' } }))
vi.mock('@/motion', () => ({
  springAnimate: vi.fn(),
  springs: { pointer: {} },
  blurIn: vi.fn(),
}))

import QuestionUnderstandingView from '@/views/question/QuestionUnderstandingView.vue'

function daysFromNow(days: number) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

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
  review: { nextReviewDate: daysFromNow(3), counted: true },
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
const reviewed = { kind: 'AI_GENERATED', reviewStatus: 'REVIEWED' }
const solution = {
  correctAnswer: 'A',
  explanation: '只能从栈顶取数',
  explanationSource: reviewed,
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
  explanationSource: reviewed,
  viz: null,
  assistance: 'UNAIDED',
}

/** A tutor that follows the contract's order: hints → misconception player → solution → variant → done. */
function fakeTutor({ ladder }: { ladder: boolean }) {
  let hintsUsed = 0
  let revealed = false
  let variantDone = false
  const state = () => ({
    nextHintLevel: ladder && !revealed && hintsUsed < 3 ? hintsUsed + 1 : null,
    hintsUsed,
    misconceptionVizViewed: false,
    solutionVizViewed: false,
    solutionRevealed: revealed,
    variantAttempted: variantDone,
  })
  const action = () => {
    const now = state()
    if (now.nextHintLevel) return { type: 'HINT', level: now.nextHintLevel }
    if (!revealed) return { type: 'REVEAL_SOLUTION' }
    return variantDone ? { type: 'DONE' } : { type: 'TRY_VARIANT', variantId: 91 }
  }
  tutorApi.getTutorNext.mockImplementation(async () => ({
    data: {
      action: action(),
      reason: `建议理由 ${hintsUsed}`,
      reasonSource: { kind: 'CURATED', reviewStatus: 'REVIEWED' },
      state: state(),
    },
  }))
  tutorApi.requestNextHint.mockImplementation(async () => {
    hintsUsed += 1
    return { data: { level: hintsUsed, text: `第 ${hintsUsed} 级提示文本`, source: reviewed, state: state() } }
  })
  api.revealSolution.mockImplementation(async () => {
    revealed = true
    return { data: solution }
  })
  api.submitVariantAttempt.mockImplementation(async () => {
    variantDone = true
    return { data: { ...variantResult, assistance: hintsUsed > 0 ? 'AIDED' : 'UNAIDED' } }
  })
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
  setActivePinia(createPinia())
  route.query = {}
  api.getUnderstandingQuestion.mockResolvedValue({ data: question })
  api.submitAttempt.mockResolvedValue({ data: wrongAttempt })
  api.revealSolution.mockResolvedValue({ data: solution })
  api.getVariant.mockResolvedValue({ data: variant })
  api.submitVariantAttempt.mockResolvedValue({ data: variantResult })
  fakeTutor({ ladder: false })
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

  it('climbs the hint ladder before the player and the solution, and marks the variant as aided', async () => {
    fakeTutor({ ladder: true })
    const wrapper = render()
    await flushPromises()
    await wrapper.find('input[value="B"]').setValue()
    await button(wrapper, '提交').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('越过栈顶')
    expect(wrapper.text()).toContain('建议理由 0')
    expect(wrapper.text()).not.toContain('用你选的序列演示')
    expect(wrapper.findAll('button').some((item) => item.text().includes('看正解'))).toBe(false)

    for (const [name, level] of [
      ['给我一点提示', 1],
      ['再具体一点', 2],
      ['看一个相似的例子', 3],
    ] as const) {
      await button(wrapper, name).trigger('click')
      await flushPromises()
      expect(wrapper.text()).toContain(`第 ${level} 级提示文本`)
      expect(wrapper.find('.meter').attributes('aria-label')).toBe(`已用 ${level} 级提示，共 3 级`)
    }
    expect(tutorApi.requestNextHint).toHaveBeenCalledTimes(3)
    expect(wrapper.text()).not.toContain('已审核')
    expect(wrapper.text()).toContain('内容由 AI 生成，请注意甄别')
    expect(wrapper.text()).toContain('用你选的序列演示')

    await button(wrapper, '看正解').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('只能从栈顶取数')
    expect(wrapper.text()).toContain('你在原题用过提示')
    const variantStep = wrapper.findAll('article').find((item) => item.text().includes('哪个不可能？'))!
    await variantStep.find('input[value="A"]').setValue()
    await variantStep.find('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('原题用过提示，这次变式不计入复习安排。')
  })

  it('offers a retry when the tutor suggestion fails to load', async () => {
    tutorApi.getTutorNext.mockRejectedValueOnce(new Error('导师暂不可用'))
    const wrapper = render()
    await flushPromises()
    await wrapper.find('input[value="B"]').setValue()
    await button(wrapper, '提交').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('导师暂不可用')
    await button(wrapper, '重试').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('看正解')
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
    expect(wrapper.text()).toContain(`这次独立作答已计入复习安排。这道题${redoWhen(daysFromNow(3)).text}再做一次。`)
  })

  it('lets an admin preview pending content through the preview endpoint only', async () => {
    useUserStore().userInfo = { id: 1, role: 'ADMIN' } as never
    api.getUnderstandingQuestion.mockRejectedValueOnce(new BusinessError('没有内容', 1004))
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('这道题还没有理解内容')
    await button(wrapper, '预览待审核内容').trigger('click')
    expect(router.replace).toHaveBeenCalledWith({ query: { preview: '1' } })

    const pending = { kind: 'AI_GENERATED', reviewStatus: 'PENDING' }
    const progressSeen: unknown[] = []
    previewApi.previewUnderstandingQuestion.mockImplementation(
      async (_id: number, progress: Record<string, unknown>) => {
        progressSeen.push({ ...progress })
        const level = (progress.hintLevel as number | undefined) ?? 0
        const answered = typeof progress.answer === 'string'
        return {
          data: {
            preview: true,
            question,
            attempt: answered
              ? { correct: false, answer: 'B', misconception: wrongAttempt.misconception, viz: null }
              : null,
            hints: Array.from({ length: level }, (_, i) => ({
              level: i + 1,
              text: `待审提示 ${i + 1}`,
              source: pending,
            })),
            solution: null,
            variant: null,
            variantResult: null,
            next: answered
              ? {
                  action: level < 3 ? { type: 'HINT', level: level + 1 } : { type: 'REVEAL_SOLUTION' },
                  reason: '预览理由',
                  reasonSource: { kind: 'CURATED', reviewStatus: 'REVIEWED' },
                  state: { nextHintLevel: level < 3 ? level + 1 : null, hintsUsed: level },
                }
              : null,
          },
        }
      },
    )
    route.query = { preview: '1' }
    await flushPromises()
    expect(wrapper.text()).toContain('预览 · 含待审核内容')
    await wrapper.find('input[value="B"]').setValue()
    await button(wrapper, '提交').trigger('click')
    await flushPromises()
    await button(wrapper, '给我一点提示').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('待审提示 1')
    expect(progressSeen.at(-1)).toEqual({ answer: 'B', hintLevel: 1 })
    expect(api.submitAttempt).not.toHaveBeenCalled()
    expect(tutorApi.getTutorNext).not.toHaveBeenCalled()
    expect(tutorApi.requestNextHint).not.toHaveBeenCalled()
  })
})
