import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getQuestionById, getSimilarQuestions, message, routerPush } = vi.hoisted(() => ({
  getQuestionById: vi.fn(),
  getSimilarQuestions: vi.fn(),
  message: { error: vi.fn() },
  routerPush: vi.fn(),
}))

vi.mock('@/api/question', () => ({ getQuestionById }))
vi.mock('@/api/statistics', () => ({ getSimilarQuestions }))
vi.mock('element-plus', () => ({ ElMessage: message }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: routerPush }) }))

import SimilarQuestionsDialog from '@/components/practice/SimilarQuestionsDialog.vue'

describe('SimilarQuestionsDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    getSimilarQuestions.mockResolvedValue({
      data: {
        sourceQuestionId: 10,
        sourceQuestionContent: '原题',
        similarQuestions: [
          {
            questionId: 11,
            questionContent: '相似题',
            questionType: 'SINGLE_CHOICE',
            difficulty: 2,
            courseName: '数据结构',
            knowledgePointName: '栈',
            similarityScore: 88,
            reason: '同知识点',
            alreadyAttempted: false,
          },
        ],
      },
    })
    getQuestionById.mockResolvedValue({ data: { id: 11, content: '完整相似题' } })
    routerPush.mockResolvedValue(undefined)
  })

  it('loads recommendations and creates a complete practice session before navigation', async () => {
    const wrapper = mount(SimilarQuestionsDialog, {
      global: {
        stubs: {
          'el-dialog': {
            props: ['modelValue'],
            template: '<section v-if="modelValue"><slot /><slot name="footer" /></section>',
          },
          'el-table': { template: '<div><slot /></div>' },
          'el-table-column': { template: '<div><slot :row="{}" /></div>' },
          'el-progress': true,
          'el-tag': { template: '<span><slot /></span>' },
          'el-empty': true,
          'el-button': {
            emits: ['click'],
            template: '<button @click="$emit(\'click\')"><slot /></button>',
          },
        },
        directives: { loading: () => undefined },
      },
    })

    await (wrapper.vm as unknown as { open: (questionId: number, content: string) => Promise<void> }).open(10, '原题')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('开始练习相似题'))!
      .trigger('click')
    await flushPromises()

    expect(getSimilarQuestions).toHaveBeenCalledWith(10, 8)
    expect(getQuestionById).toHaveBeenCalledWith(11)
    expect(JSON.parse(sessionStorage.getItem('practice_questions') || '[]')).toEqual([
      { id: 11, content: '完整相似题' },
    ])
    expect(sessionStorage.getItem('practice_mode')).toBe('similar')
    expect(routerPush).toHaveBeenCalledWith({ path: '/practice/session' })
  })
})
