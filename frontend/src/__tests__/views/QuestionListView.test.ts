import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import QuestionListView from '@/views/course/QuestionListView.vue'
import { useQuestionCatalog } from '@/views/course/useQuestionCatalog'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
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
  }
  return { useQuestionCatalog: () => state }
})
vi.mock('@/views/course/CatalogSearch.vue', () => ({ default: { template: '<div />' } }))

describe('QuestionListView pagination', () => {
  beforeEach(() => {
    const state = useQuestionCatalog()
    state.pageNum.value = 1
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
})
