import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { clearAssetCache, deleteQuestion, getAdminQuestionPage, getAllCourses, message } = vi.hoisted(() => ({
  clearAssetCache: vi.fn(),
  deleteQuestion: vi.fn(),
  getAdminQuestionPage: vi.fn(),
  getAllCourses: vi.fn(),
  message: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}))

vi.mock('@/api/ai', () => ({ clearAssetCache }))
vi.mock('@/api/course', () => ({ getAllCourses }))
vi.mock('@/api/question', () => ({ deleteQuestion, getAdminQuestionPage }))
vi.mock('element-plus', () => ({
  ElMessage: message,
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(undefined) },
}))

import { useQuestionAdminList } from '@/admin/views/question/useQuestionAdminList'

describe('useQuestionAdminList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getAdminQuestionPage.mockResolvedValue({ data: { records: [{ id: 11, content: '题目' }], total: 1 } })
    getAllCourses.mockResolvedValue({ data: [{ id: 7, name: '数据结构' }] })
    deleteQuestion.mockResolvedValue(undefined)
    clearAssetCache.mockResolvedValue(undefined)
  })

  it('loads the first page and keeps query state in the composable', async () => {
    let state!: ReturnType<typeof useQuestionAdminList>
    mount(
      defineComponent({
        setup() {
          state = useQuestionAdminList()
          return () => h('div')
        },
      }),
    )
    await flushPromises()

    expect(getAdminQuestionPage).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 10,
      keyword: undefined,
      questionType: undefined,
      courseId: undefined,
      difficulty: undefined,
      sourceType: undefined,
    })
    expect(state.questions.value).toHaveLength(1)
    expect(state.total.value).toBe(1)
    expect(state.courseList.value[0]?.id).toBe(7)

    state.filters.keyword = '链表'
    state.filters.courseId = 7
    await state.fetchQuestions()

    expect(getAdminQuestionPage).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '链表', courseId: 7 }))
  })

  it('owns selection, deletion and cache commands', async () => {
    let state!: ReturnType<typeof useQuestionAdminList>
    mount(
      defineComponent({
        setup() {
          state = useQuestionAdminList()
          return () => h('div')
        },
      }),
    )
    await flushPromises()

    const question = state.questions.value[0]!
    state.handleQuestionSelectionChange([question])
    expect(state.selectedQuestions.value).toEqual([question])

    await state.handleDelete(question.id)
    expect(deleteQuestion).toHaveBeenCalledWith(question.id)
    expect(message.success).toHaveBeenCalledWith('删除成功')

    await state.handleClearAiCache(question.id)
    expect(clearAssetCache).toHaveBeenCalledWith(question.id)
    expect(message.success).toHaveBeenCalledWith('AI 学习资产缓存已清除')
  })
})
