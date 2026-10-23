import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import QuestionListView from '@/views/course/QuestionListView.vue'
import { useQuestionCatalog } from '@/views/course/useQuestionCatalog'

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('@/views/course/useQuestionCatalog', async () => {
  const { ref } = await import('vue')
  const state = {
    questions: ref([]),
    loading: ref(false),
    loadError: ref(''),
    pageNum: ref(1),
    pageSize: ref(10),
    total: ref(30),
    filters: ref({ courseId: null, questionType: '', difficulty: null }),
    questionTypes: [],
    difficultyOptions: [],
    courseList: [],
    coursesLoading: false,
    coursesError: '',
    favoriteSet: new Set(),
    favoritePending: new Set(),
    favoritesReady: true,
    favoritesLoading: false,
    favoritesError: '',
    actionError: '',
    notice: '',
    practiceStartingId: null,
    expandedComments: new Set(),
    correctionDialogVisible: ref(false),
    correctionSubmitting: ref(false),
    correctionQuestion: null,
    correctionForm: { reportType: 'CONTENT', description: '' },
    correctionError: '',
    activeFilterCount: 0,
    resultSummary: '30题',
    searchContext: null,
    searchQueryWarning: '',
    fetchQuestions: vi.fn(),
    handleSizeChange: vi.fn(),
    handleFilterChange: vi.fn(),
    resetFilters: vi.fn(),
    questionTypeLabel: vi.fn(() => '单选题'),
    difficultyLabel: vi.fn(() => '简单'),
  }
  return { useQuestionCatalog: () => state }
})
vi.mock('@/views/course/CatalogSearch.vue', () => ({ default: { template: '<div />' } }))

describe('QuestionListView pagination', () => {
  beforeEach(() => {
    const state = useQuestionCatalog()
    state.pageNum.value = 1
    state.questions.value = []
    vi.clearAllMocks()
  })
  it('keeps the current page and page size wired to real query handlers', async () => {
    const state = useQuestionCatalog()
    const w = mount(QuestionListView, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' }, LtSheet: { template: '<div />' } } },
    })
    expect(
      w
        .findAll('button')
        .find((b) => b.text() === '上一页')!
        .attributes('disabled'),
    ).toBeDefined()
    await w
      .findAll('button')
      .find((b) => b.text() === '下一页')!
      .trigger('click')
    await flushPromises()
    expect(state.pageNum.value).toBe(2)
    expect(state.fetchQuestions).toHaveBeenCalledOnce()
    await w.get('[aria-label="每页题数"]').setValue('20')
    expect(state.pageSize.value).toBe(20)
    expect(state.handleSizeChange).toHaveBeenCalledOnce()
    w.unmount()
  })

  it('renders option text with inline Markdown', () => {
    const state = useQuestionCatalog()
    state.questions.value = [
      {
        id: 1,
        content: '题干',
        questionType: 'SINGLE_CHOICE',
        courseId: 1,
        courseName: '课程',
        difficulty: 1,
        analysis: null,
        tags: null,
        score: 1,
        status: 1,
        createTime: '2026-10-23T00:00:00',
        updateTime: '2026-10-23T00:00:00',
        options: [{ id: 11, optionLabel: 'A', content: '使用 `Push` 更新远端分支', isCorrect: 0, sortOrder: 1 }],
        knowledgePointIds: [],
        knowledgePointNames: [],
      },
    ]

    const w = mount(QuestionListView, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' }, LtSheet: { template: '<div />' } } },
    })

    expect(w.get('.question-options li').html()).toContain('<code>Push</code>')
    w.unmount()
  })
})
