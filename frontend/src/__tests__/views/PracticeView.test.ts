import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'

const {
  mockGetPracticeQuestions,
  mockGetAdaptiveQuestions,
  mockGetPracticeStats,
  mockGetAdaptiveSummary,
  mockGetCoursePage,
  mockGetKnowledgeTree,
  mockSavePracticeSession,
  mockPush,
} = vi.hoisted(() => ({
  mockGetPracticeQuestions: vi.fn(),
  mockGetAdaptiveQuestions: vi.fn(),
  mockGetPracticeStats: vi.fn(),
  mockGetAdaptiveSummary: vi.fn(),
  mockGetCoursePage: vi.fn(),
  mockGetKnowledgeTree: vi.fn(),
  mockSavePracticeSession: vi.fn(),
  mockPush: vi.fn(),
}))

vi.mock('@/api/practice', () => ({
  getPracticeQuestions: (...args: unknown[]) => mockGetPracticeQuestions(...args),
  getAdaptiveQuestions: (...args: unknown[]) => mockGetAdaptiveQuestions(...args),
  getPracticeStats: (...args: unknown[]) => mockGetPracticeStats(...args),
  getAdaptiveSummary: (...args: unknown[]) => mockGetAdaptiveSummary(...args),
}))

vi.mock('@/api/course', () => ({
  getCoursePage: (...args: unknown[]) => mockGetCoursePage(...args),
}))

vi.mock('@/api/knowledgePoint', () => ({
  getKnowledgeTree: (...args: unknown[]) => mockGetKnowledgeTree(...args),
}))

vi.mock('@/utils/practiceSession', () => ({
  clearPracticeSession: vi.fn(),
  savePracticeSession: (...args: unknown[]) => mockSavePracticeSession(...args),
}))

let routeQuery: Record<string, string> = { courseId: '7', knowledgePointId: '31' }
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routeQuery }),
  useRouter: () => ({ push: mockPush }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), error: vi.fn() },
}))

import PracticeView from '@/views/practice/PracticeView.vue'

const stubs = {
  'el-button': {
    template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['disabled', 'loading'],
    emits: ['click'],
  },
  'el-card': { template: '<section><slot /></section>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-radio-group': {
    template:
      '<div><button data-testid="adaptive-mode" @click="$emit(\'update:modelValue\', \'adaptive\')">智能推荐</button><button data-testid="custom-mode" @click="$emit(\'update:modelValue\', \'custom\')">自选条件</button><slot /></div>',
    props: ['modelValue'],
    emits: ['update:modelValue'],
  },
  'el-radio-button': { template: '<span><slot /></span>' },
  'el-select': { template: '<select><slot /></select>', props: ['modelValue'] },
  'el-option': { template: '<option>{{ label }}</option>', props: ['label', 'value'] },
  'el-input-number': { template: '<input type="number" />', props: ['modelValue'] },
  'el-rate': { template: '<span />', props: ['modelValue'] },
  LpPageHeader: { template: '<header />' },
  LpSectionHeading: { template: '<h2>{{ title }}</h2>', props: ['title'] },
  LpSkeleton: { template: '<div data-testid="skeleton" />' },
  LpStat: { template: '<div data-testid="stat">{{ label }} {{ value }}</div>', props: ['label', 'value'] },
  LpStatePanel: {
    template:
      '<section data-testid="state-panel"><strong>{{ title }}</strong><button @click="$emit(\'retry\')">重新加载</button></section>',
    props: ['title', 'description', 'state'],
    emits: ['retry'],
  },
  LpEmptyState: {
    template: '<section>{{ title }}<slot /><slot name="actions" /></section>',
    props: ['title', 'description'],
  },
}

const question = {
  id: 11,
  content: '题目',
  questionType: 'TRUE_FALSE',
  courseId: 7,
  courseName: 'Java',
  difficulty: 2,
  score: 5,
  tags: null,
  options: [],
  knowledgePointIds: [],
  knowledgePointNames: [],
}

const mountedViews: ReturnType<typeof mount>[] = []
function mountView() {
  const wrapper = mount(PracticeView, { global: { stubs, directives: { loading: () => undefined } } })
  mountedViews.push(wrapper)
  return wrapper
}

afterEach(() => {
  while (mountedViews.length) mountedViews.pop()?.unmount()
})

function startButton(wrapper: ReturnType<typeof mount>) {
  const button = wrapper.findAll('button').find((item) => item.text().includes('开始刷题'))
  expect(button).toBeTruthy()
  return button!
}

describe('PracticeView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeQuery = { courseId: '7', knowledgePointId: '31' }
    setActivePinia(createPinia())
    useUserStore().setLoginInfo('token', {
      id: 3,
      username: 'learner',
      nickname: 'Learner',
      avatar: null,
      role: 'USER',
    })
    mockGetPracticeStats.mockResolvedValue({
      code: 0,
      data: { totalAnswered: 2, correctCount: 1, wrongCount: 1, correctRate: 50 },
    })
    mockGetAdaptiveSummary.mockResolvedValue({ code: 0, data: { totalAnswered: 0 } })
    mockGetCoursePage.mockResolvedValue({ code: 0, data: { records: [{ id: 7, name: 'Java' }] } })
    mockGetKnowledgeTree.mockResolvedValue({
      code: 0,
      data: [{ id: 31, name: '集合', children: [{ id: 32, name: '列表', children: [] }] }],
    })
    mockGetAdaptiveQuestions.mockResolvedValue({ code: 0, data: [question] })
    mockGetPracticeQuestions.mockResolvedValue({ code: 0, data: [question] })
    mockSavePracticeSession.mockReturnValue(true)
  })

  it('uses the course preset and one primary action to start intelligent recommendation', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.findAll('button').filter((item) => item.text().includes('开始刷题'))).toHaveLength(1)
    await startButton(wrapper).trigger('click')

    expect(mockGetAdaptiveQuestions).toHaveBeenCalledWith({ count: 10, courseId: 7, knowledgePointId: 31 })
    expect(mockGetPracticeQuestions).not.toHaveBeenCalled()
    expect(mockSavePracticeSession).toHaveBeenCalledWith(3, [question], 'adaptive')
    expect(mockPush).toHaveBeenCalledWith({ name: 'PracticeSession' })
  })

  it('switches the single start action to self-selected conditions without dropping the course preset', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="custom-mode"]').trigger('click')
    await flushPromises()
    await startButton(wrapper).trigger('click')
    await flushPromises()

    expect(mockGetPracticeQuestions).toHaveBeenCalledWith({ count: 10, courseId: 7, knowledgePointId: 31 })
    expect(mockGetAdaptiveQuestions).not.toHaveBeenCalled()
    expect(mockSavePracticeSession).toHaveBeenCalledWith(3, [question], 'custom')
  })

  it('drops a late practice response after the same user receives a new auth session', async () => {
    let resolve!: (value: { code: number; data: (typeof question)[] }) => void
    mockGetAdaptiveQuestions.mockImplementationOnce(
      () =>
        new Promise((next) => {
          resolve = next
        }),
    )
    const wrapper = mountView()
    await flushPromises()

    const start = startButton(wrapper).trigger('click')
    useUserStore().setLoginInfo('rotated-token', {
      id: 3,
      username: 'learner',
      nickname: 'Learner',
      avatar: null,
      role: 'USER',
    })
    resolve({ code: 0, data: [question] })
    await start
    await flushPromises()

    expect(mockSavePracticeSession).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('shows failed statistics as a retryable state instead of zero values', async () => {
    mockGetPracticeStats.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mountView()
    await flushPromises()

    const panel = wrapper
      .findAll('[data-testid="state-panel"]')
      .find((item) => item.text().includes('练习统计暂时无法加载'))
    expect(panel).toBeTruthy()
    expect(wrapper.findAll('[data-testid="stat"]')).toHaveLength(0)
    await panel!.get('button').trigger('click')
    await flushPromises()

    expect(mockGetPracticeStats).toHaveBeenCalledTimes(2)
    expect(wrapper.findAll('[data-testid="stat"]')).toHaveLength(4)
  })

  it('keeps a failed recommendation distinct from an empty successful recommendation and retries it', async () => {
    mockGetAdaptiveSummary.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mountView()
    await flushPromises()

    const panel = wrapper
      .findAll('[data-testid="state-panel"]')
      .find((item) => item.text().includes('推荐概览暂时无法加载'))
    expect(panel).toBeTruthy()
    await panel!.get('button').trigger('click')
    await flushPromises()

    expect(mockGetAdaptiveSummary).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('暂无答题记录')
  })
})
