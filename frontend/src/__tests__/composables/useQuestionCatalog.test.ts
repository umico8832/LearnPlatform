import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const {
  addFavorite,
  auth,
  getAllCourses,
  getFavoriteIds,
  getQuestionPage,
  message,
  removeFavorite,
  replaceRoute,
  route,
  submitQuestionCorrectionReport,
} = vi.hoisted(() => ({
  addFavorite: vi.fn(),
  auth: { authenticated: true, listener: undefined as (() => void) | undefined },
  getAllCourses: vi.fn(),
  getFavoriteIds: vi.fn(),
  getQuestionPage: vi.fn(),
  message: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  removeFavorite: vi.fn(),
  replaceRoute: vi.fn(),
  route: { query: { courseId: '7' } as Record<string, string> },
  submitQuestionCorrectionReport: vi.fn(),
}))

vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ replace: replaceRoute }) }))
vi.mock('@/api/course', () => ({ getAllCourses }))
vi.mock('@/api/favorite', () => ({ addFavorite, getFavoriteIds, removeFavorite }))
vi.mock('@/api/question', () => ({ getQuestionPage, submitQuestionCorrectionReport }))
vi.mock('element-plus', () => ({ ElMessage: message }))
vi.mock('@/utils/auth', () => ({
  getAuthSessionVersion: () => 1,
  isAuthenticated: () => auth.authenticated,
  onAuthSessionChange: (listener: () => void) => {
    auth.listener = listener
    return () => {
      auth.listener = undefined
    }
  },
}))

import { useQuestionCatalog } from '@/views/course/useQuestionCatalog'

describe('useQuestionCatalog', () => {
  const mountedWrappers: Array<{ unmount: () => void }> = []

  beforeEach(() => {
    vi.clearAllMocks()
    auth.authenticated = true
    auth.listener = undefined
    route.query = { courseId: '7' }
    getQuestionPage.mockResolvedValue({ data: { records: [{ id: 12, questionType: 'SINGLE_CHOICE' }], total: 1 } })
    getAllCourses.mockResolvedValue({ data: [{ id: 7, name: '数据结构' }] })
    getFavoriteIds.mockResolvedValue({ code: 0, data: [12] })
    addFavorite.mockResolvedValue(undefined)
    removeFavorite.mockResolvedValue(undefined)
    submitQuestionCorrectionReport.mockResolvedValue(undefined)
  })

  afterEach(() => {
    mountedWrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  })

  it('hydrates route filters, questions, courses and favorites', async () => {
    let state!: ReturnType<typeof useQuestionCatalog>
    mountedWrappers.push(
      mount(
        defineComponent({
          setup() {
            state = useQuestionCatalog()
            return () => h('div')
          },
        }),
      ),
    )
    await flushPromises()

    expect(getQuestionPage).toHaveBeenCalledWith(expect.objectContaining({ courseId: 7, pageNum: 1, pageSize: 10 }))
    expect(state.questions.value).toHaveLength(1)
    expect(state.courseList.value[0]?.id).toBe(7)
    expect(state.favoriteSet.value.has(12)).toBe(true)
    expect(state.activeFilterCount.value).toBe(1)
    expect(state.resultSummary.value).toBe('显示第 1-1 题，共 1 题。')
  })

  it('owns filter, favorite and correction interactions', async () => {
    let state!: ReturnType<typeof useQuestionCatalog>
    mountedWrappers.push(
      mount(
        defineComponent({
          setup() {
            state = useQuestionCatalog()
            return () => h('div')
          },
        }),
      ),
    )
    await flushPromises()

    state.selectDifficulty(3)
    await flushPromises()
    expect(getQuestionPage).toHaveBeenLastCalledWith(expect.objectContaining({ difficulty: 3, pageNum: 1 }))

    await state.toggleFavorite(12)
    expect(removeFavorite).toHaveBeenCalledWith(12)
    expect(state.favoriteSet.value.has(12)).toBe(false)

    const question = state.questions.value[0]!
    state.openCorrectionDialog(question)
    state.correctionForm.description = '题干存在歧义'
    await state.submitCorrection()
    expect(submitQuestionCorrectionReport).toHaveBeenCalledWith(12, {
      reportType: 'CONTENT',
      description: '题干存在歧义',
    })
    expect(state.correctionDialogVisible.value).toBe(false)
  })

  it('keeps an exact question context until the user explicitly clears it', async () => {
    route.query = { questionId: '12' }
    let state!: ReturnType<typeof useQuestionCatalog>
    const wrapper = mount(
      defineComponent({
        setup() {
          state = useQuestionCatalog()
          return () => h('div')
        },
      }),
    )
    mountedWrappers.push(wrapper)
    await flushPromises()

    expect(getQuestionPage).toHaveBeenCalledWith(expect.objectContaining({ questionId: 12 }))
    expect(state.searchContext.value).toMatchObject({ label: '搜索选中的题目' })
    state.filters.questionType = 'SINGLE_CHOICE'
    state.filters.courseId = 7
    state.selectDifficulty(3)
    await flushPromises()
    expect(getQuestionPage).toHaveBeenLastCalledWith(
      expect.objectContaining({ questionId: 12, questionType: 'SINGLE_CHOICE', courseId: 7, difficulty: 3 }),
    )

    await state.clearSearchContext()
    const clearedQuery = { questionType: 'SINGLE_CHOICE', courseId: '7', difficulty: '3' }
    expect(replaceRoute).toHaveBeenCalledWith({ query: clearedQuery })
    wrapper.unmount()
    mountedWrappers.splice(mountedWrappers.indexOf(wrapper), 1)
    route.query = clearedQuery
    let remounted!: ReturnType<typeof useQuestionCatalog>
    const remountedWrapper = mount(
      defineComponent({
        setup() {
          remounted = useQuestionCatalog()
          return () => h('div')
        },
      }),
    )
    mountedWrappers.push(remountedWrapper)
    await flushPromises()
    expect(remounted.filters).toMatchObject({ questionType: 'SINGLE_CHOICE', courseId: 7, difficulty: 3 })
  })

  it('does not let a late question response overwrite a newer filter', async () => {
    let resolveFirst!: (value: {
      data: { records: Array<{ id: number; questionType: string }>; total: number }
    }) => void
    getQuestionPage.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveFirst = resolve
        }),
    )
    getQuestionPage.mockResolvedValueOnce({ data: { records: [{ id: 99, questionType: 'SINGLE_CHOICE' }], total: 1 } })
    let state!: ReturnType<typeof useQuestionCatalog>
    mountedWrappers.push(
      mount(
        defineComponent({
          setup() {
            state = useQuestionCatalog()
            return () => h('div')
          },
        }),
      ),
    )
    await flushPromises()

    state.selectDifficulty(3)
    await flushPromises()
    resolveFirst({ data: { records: [{ id: 12, questionType: 'SINGLE_CHOICE' }], total: 1 } })
    await flushPromises()

    expect(state.questions.value.map((question) => question.id)).toEqual([99])
  })

  it('reports an invalid search constraint and allows an inline retry after a loading failure', async () => {
    route.query = { questionId: 'not-an-id' }
    getQuestionPage.mockRejectedValueOnce(new Error('network'))
    let state!: ReturnType<typeof useQuestionCatalog>
    mountedWrappers.push(
      mount(
        defineComponent({
          setup() {
            state = useQuestionCatalog()
            return () => h('div')
          },
        }),
      ),
    )
    await flushPromises()

    expect(state.searchQueryWarning.value).toContain('无效')
    expect(state.loadError.value).toBe('题目暂时无法加载，请重试')
    state.retryFetch()
    await flushPromises()
    expect(state.loadError.value).toBe('')
  })

  it('clears favorites and does not request catalog data after logout', async () => {
    let resolveFavorites!: (value: { code: number; data: number[] }) => void
    getFavoriteIds.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveFavorites = resolve
        }),
    )
    let state!: ReturnType<typeof useQuestionCatalog>
    mountedWrappers.push(
      mount(
        defineComponent({
          setup() {
            state = useQuestionCatalog()
            return () => h('div')
          },
        }),
      ),
    )
    await flushPromises()
    const catalogCallsBeforeLogout = getQuestionPage.mock.calls.length
    auth.authenticated = false
    auth.listener?.()
    resolveFavorites({ code: 0, data: [12] })
    await flushPromises()

    expect(getQuestionPage).toHaveBeenCalledTimes(catalogCallsBeforeLogout)
    expect(state.favoriteSet.value.size).toBe(0)
  })
})
