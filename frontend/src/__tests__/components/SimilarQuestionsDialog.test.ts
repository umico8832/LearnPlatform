import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'
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
    setActivePinia(createPinia())
    useUserStore().setLoginInfo('test-token', {
      id: 7,
      username: 'learner',
      nickname: 'Learner',
      avatar: null,
      role: 'USER',
    })
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

  it.each([false, true])(
    'binds the practice response to the requesting account (switch account: %s)',
    async (switchAccount) => {
      let resolveQuestion: ((value: { data: { id: number; content: string } }) => void) | undefined
      if (switchAccount)
        getQuestionById.mockImplementation(
          () =>
            new Promise((resolve) => {
              resolveQuestion = resolve
            }),
        )
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

      if (switchAccount) {
        useUserStore().clearLoginInfo()
        useUserStore().setLoginInfo('other-test-token', {
          id: 8,
          username: 'other',
          nickname: 'Other',
          avatar: null,
          role: 'USER',
        })
        resolveQuestion!({ data: { id: 11, content: 'private question from previous user' } })
        await flushPromises()
        expect(sessionStorage.getItem('practice_questions')).toBeNull()
        expect(routerPush).not.toHaveBeenCalled()
        return
      }

      expect(sessionStorage.getItem('practice_user_id')).toBe('7')
      expect(getSimilarQuestions).toHaveBeenCalledWith(10, 8)
      expect(getQuestionById).toHaveBeenCalledWith(11)
      expect(JSON.parse(sessionStorage.getItem('practice_questions') || '[]')).toEqual([
        { id: 11, content: '完整相似题' },
      ])
      expect(sessionStorage.getItem('practice_mode')).toBe('similar')
      expect(routerPush).toHaveBeenCalledWith({ path: '/practice/session' })
    },
  )
})
