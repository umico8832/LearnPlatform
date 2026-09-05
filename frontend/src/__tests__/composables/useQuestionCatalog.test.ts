import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const {
  addFavorite,
  getAllCourses,
  getFavoriteIds,
  getQuestionPage,
  message,
  removeFavorite,
  submitQuestionCorrectionReport,
} = vi.hoisted(() => ({
  addFavorite: vi.fn(),
  getAllCourses: vi.fn(),
  getFavoriteIds: vi.fn(),
  getQuestionPage: vi.fn(),
  message: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  removeFavorite: vi.fn(),
  submitQuestionCorrectionReport: vi.fn(),
}))

vi.mock('vue-router', () => ({ useRoute: () => ({ query: { courseId: '7' } }) }))
vi.mock('@/api/course', () => ({ getAllCourses }))
vi.mock('@/api/favorite', () => ({ addFavorite, getFavoriteIds, removeFavorite }))
vi.mock('@/api/question', () => ({ getQuestionPage, submitQuestionCorrectionReport }))
vi.mock('element-plus', () => ({ ElMessage: message }))

import { useQuestionCatalog } from '@/views/course/useQuestionCatalog'

describe('useQuestionCatalog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getQuestionPage.mockResolvedValue({ data: { records: [{ id: 12, questionType: 'SINGLE_CHOICE' }], total: 1 } })
    getAllCourses.mockResolvedValue({ data: [{ id: 7, name: '数据结构' }] })
    getFavoriteIds.mockResolvedValue({ code: 0, data: [12] })
    addFavorite.mockResolvedValue(undefined)
    removeFavorite.mockResolvedValue(undefined)
    submitQuestionCorrectionReport.mockResolvedValue(undefined)
  })

  it('hydrates route filters, questions, courses and favorites', async () => {
    let state!: ReturnType<typeof useQuestionCatalog>
    mount(
      defineComponent({
        setup() {
          state = useQuestionCatalog()
          return () => h('div')
        },
      }),
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
    mount(
      defineComponent({
        setup() {
          state = useQuestionCatalog()
          return () => h('div')
        },
      }),
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
})
