import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import type { WrongQuestionVO } from '@/api/wrongQuestion'

const api = vi.hoisted(() => ({
  getWrongQuestions: vi.fn(),
  getWrongQuestionStats: vi.fn(),
  updateMasteryLevel: vi.fn(),
  removeWrongQuestion: vi.fn(),
}))
vi.mock('@/api/wrongQuestion', () => api)
const practice = vi.hoisted(() => ({ getWrongQuestionPractice: vi.fn() }))
vi.mock('@/api/practice', () => practice)
const session = vi.hoisted(() => ({ savePracticeSession: vi.fn(() => true) }))
vi.mock('@/utils/practiceSession', () => session)
const route = reactive({ query: {} as Record<string, string> })
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }))
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()),
  useRoute: () => route,
  useRouter: () => router,
}))

import WrongQuestionsView from '@/views/wrong/WrongQuestionsView.vue'

const reviewed = { kind: 'CURATED', reviewStatus: 'REVIEWED' } as const
function record(overrides: Partial<WrongQuestionVO> = {}): WrongQuestionVO {
  return {
    id: 1,
    questionId: 17,
    questionContent: '入栈顺序为 1,2,3,4,5，哪个出栈序列可能？',
    questionType: 'SINGLE_CHOICE',
    courseId: 3,
    courseName: '408 数据结构',
    difficulty: 2,
    wrongCount: 2,
    masteryLevel: 0,
    lastWrongAnswer: 'B',
    misconception: { title: '以为能越过 2 直接弹出 1', explanation: '栈顶是 2。', source: reviewed },
    nextReviewDate: null,
    createTime: '',
    updateTime: '',
    ...overrides,
  }
}
function page(records: WrongQuestionVO[], total = records.length) {
  return { code: 0, data: { records, total } }
}

const mounted: ReturnType<typeof mount>[] = []
function render() {
  const wrapper = mount(WrongQuestionsView)
  mounted.push(wrapper)
  return wrapper
}
function button(wrapper: ReturnType<typeof mount>, name: string) {
  const found = wrapper.findAll('button').find((item) => item.text() === name || item.attributes('aria-label') === name)
  if (!found) throw new Error(`button ${name} missing`)
  return found
}

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  useUserStore().userInfo = { id: 5 } as never
  route.query = {}
  api.getWrongQuestions.mockResolvedValue(page([record()]))
  api.getWrongQuestionStats.mockResolvedValue({
    code: 0,
    data: { total: 4, unmastered: 2, partial: 1, mastered: 1, courseWrongCount: {} },
  })
  api.updateMasteryLevel.mockResolvedValue({ code: 0, data: null })
  api.removeWrongQuestion.mockResolvedValue({ code: 0, data: null })
  practice.getWrongQuestionPractice.mockResolvedValue({ code: 0, data: [{ id: 17 }] })
})
afterEach(() => mounted.splice(0).forEach((wrapper) => wrapper.unmount()))

describe('WrongQuestionsView', () => {
  it('leads each wrong question with why it went wrong and when to redo it, totals last', async () => {
    api.getWrongQuestions.mockResolvedValueOnce(
      page([
        record(),
        record({ id: 2, misconception: null, lastWrongAnswer: 'rear == front', nextReviewDate: '2999-01-01' }),
      ]),
    )
    const wrapper = render()
    expect(wrapper.text()).toContain('正在读取错题')
    await flushPromises()
    const rows = wrapper.findAll('article')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('你选了')
    expect(rows[0].text()).toContain('以为能越过 2 直接弹出 1')
    expect(rows[0].text()).not.toContain('已审核')
    expect(wrapper.text()).toContain('内容由 AI 生成，请注意甄别')
    expect(rows[0].text()).toContain('未排期')
    expect(rows[1].text()).toContain('上次作答：rear == front')
    expect(rows[1].text()).toContain('这个选项还没有单独讲解')
    expect(rows[1].text()).toContain('2999 年 1 月 1 日')
    expect(wrapper.find('.totals').text()).toContain('待处理3')
  })

  it('keeps the deep-link scope in every request and lets the learner drop it', async () => {
    route.query = { courseId: '3', knowledgePointId: '8', knowledgePointName: '出栈序列', questionId: '17' }
    const wrapper = render()
    await flushPromises()
    expect(api.getWrongQuestions).toHaveBeenCalledWith(
      {
        pageNum: 1,
        pageSize: 10,
        courseId: 3,
        questionId: 17,
        knowledgePointId: 8,
        masteryLevel: undefined,
        dueOnly: undefined,
        sortBy: 'nextReviewDate',
      },
      { errorDisplay: 'inline' },
    )
    expect(api.getWrongQuestionStats).toHaveBeenCalledWith(
      { courseId: 3, questionId: 17, knowledgePointId: 8 },
      { errorDisplay: 'inline' },
    )
    expect(wrapper.find('.totals').text()).toContain('这个范围共')
    expect(wrapper.find('.chips').text()).toContain('408 数据结构')
    expect(wrapper.find('.chips').text()).toContain('出栈序列')
    await button(wrapper, '取消“出栈序列”这个范围').trigger('click')
    expect(router.replace).toHaveBeenCalledWith({ query: { courseId: '3', questionId: '17' } })
  })

  it('separates an empty notebook, an empty scope and a failed load', async () => {
    api.getWrongQuestions.mockResolvedValueOnce(page([]))
    const empty = render()
    await flushPromises()
    expect(empty.text()).toContain('还没有错题')
    empty.unmount()
    mounted.splice(mounted.indexOf(empty), 1)

    route.query = { courseId: '3' }
    api.getWrongQuestions.mockResolvedValueOnce(page([]))
    const scoped = render()
    await flushPromises()
    expect(scoped.text()).toContain('这个范围里没有错题')

    api.getWrongQuestions.mockRejectedValueOnce(new Error('断网'))
    const failed = render()
    await flushPromises()
    expect(failed.text()).toContain('错题没有加载出来')
    await button(failed, '重试').trigger('click')
    await flushPromises()
    expect(failed.findAll('article')).toHaveLength(1)
  })

  it('updates mastery in place and reports a failure on its own row', async () => {
    const wrapper = render()
    await flushPromises()
    const row = () => wrapper.find('article')
    const rowOption = (name: string) =>
      row()
        .findAll('[role="radio"]')
        .find((item) => item.text() === name)!
    await rowOption('已掌握').trigger('click')
    await flushPromises()
    expect(api.updateMasteryLevel).toHaveBeenCalledWith(1, 2, { errorDisplay: 'inline' })
    expect(row().find('[role="radio"][aria-checked="true"]').text()).toBe('已掌握')

    api.updateMasteryLevel.mockRejectedValueOnce(new Error('稍后再试'))
    await rowOption('未掌握').trigger('click')
    await flushPromises()
    expect(wrapper.find('article [role="alert"]').text()).toBe('稍后再试')
  })

  it('asks before removing a question', async () => {
    const wrapper = render()
    await flushPromises()
    await button(wrapper, '移出').trigger('click')
    expect(api.removeWrongQuestion).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('从错题本移出这道题？')
    await button(wrapper, '取消').trigger('click')
    await button(wrapper, '移出').trigger('click')
    await wrapper
      .findAll('button')
      .filter((item) => item.text() === '移出')[0]
      .trigger('click')
    await flushPromises()
    expect(api.removeWrongQuestion).toHaveBeenCalledWith(1, { errorDisplay: 'inline' })
    expect(api.getWrongQuestions).toHaveBeenCalledTimes(2)
  })

  it('redoes one question and practises the whole scope in the practice session', async () => {
    route.query = { courseId: '3' }
    const wrapper = render()
    await flushPromises()
    await wrapper.find('article').findAll('button').at(-1)!.trigger('click')
    await flushPromises()
    expect(practice.getWrongQuestionPractice).toHaveBeenCalledWith(
      { count: 1, questionId: 17, courseId: 3 },
      { errorDisplay: 'inline' },
    )
    expect(session.savePracticeSession).toHaveBeenCalledWith(5, [{ id: 17 }], 'wrong_question')
    expect(router.push).toHaveBeenCalledWith({ name: 'PracticeSession' })

    practice.getWrongQuestionPractice.mockResolvedValueOnce({ code: 0, data: [] })
    await button(wrapper, '练习当前范围').trigger('click')
    await flushPromises()
    expect(practice.getWrongQuestionPractice).toHaveBeenLastCalledWith(
      { count: 10, courseId: 3, questionId: undefined, knowledgePointId: undefined, masteryLevel: undefined },
      { errorDisplay: 'inline' },
    )
    expect(wrapper.text()).toContain('这个范围里暂时没有可以重练的题。')
  })

  it('brings a main-line question back to the understanding page instead of a practice session', async () => {
    api.getWrongQuestions.mockResolvedValueOnce(page([record({ hasUnderstanding: true })]))
    const wrapper = render()
    await flushPromises()
    await wrapper.find('article').findAll('button').at(-1)!.trigger('click')
    await flushPromises()
    expect(router.push).toHaveBeenCalledWith({ name: 'QuestionUnderstanding', params: { id: 17 } })
    expect(practice.getWrongQuestionPractice).not.toHaveBeenCalled()
  })

  it('filters to what is due today, counts it, and says so when nothing is due', async () => {
    api.getWrongQuestionStats.mockResolvedValue({
      code: 0,
      data: { total: 4, unmastered: 2, partial: 1, mastered: 1, dueCount: 2, courseWrongCount: {} },
    })
    const wrapper = render()
    await flushPromises()
    const due = wrapper.findAll('[role="radio"]').find((item) => item.text().startsWith('今天该重做'))!
    expect(due.text()).toContain('2')
    api.getWrongQuestions.mockResolvedValueOnce(page([]))
    await due.trigger('click')
    await flushPromises()
    expect(api.getWrongQuestions).toHaveBeenLastCalledWith(expect.objectContaining({ dueOnly: true }), {
      errorDisplay: 'inline',
    })
    expect(wrapper.text()).toContain('今天没有要重做的题')
  })

  it('pages through the list and restarts at page one when the filter changes', async () => {
    api.getWrongQuestions.mockResolvedValue(page([record()], 25))
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('第 1 / 3 页')
    await button(wrapper, '下一页').trigger('click')
    await flushPromises()
    expect(api.getWrongQuestions).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 2 }), {
      errorDisplay: 'inline',
    })
    await wrapper
      .findAll('[role="radio"]')
      .find((item) => item.text() === '已掌握' && !item.element.closest('article'))!
      .trigger('click')
    await flushPromises()
    expect(api.getWrongQuestions).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 1, masteryLevel: 2 }), {
      errorDisplay: 'inline',
    })
  })
})
