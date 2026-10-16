import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import { reactive } from 'vue'
import type { KnowledgePointVO } from '@/api/knowledgePoint'
import type { QuestionVO } from '@/api/question'

const course = vi.hoisted(() => ({ getCourseOverview: vi.fn() }))
vi.mock('@/api/course', () => course)
const knowledge = vi.hoisted(() => ({ getKnowledgeTree: vi.fn() }))
vi.mock('@/api/knowledgePoint', () => knowledge)
const questions = vi.hoisted(() => ({ getQuestionPage: vi.fn() }))
vi.mock('@/api/question', () => questions)
const route = reactive({ params: { id: '408' } })
const router = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()),
  useRoute: () => route,
  useRouter: () => router,
}))

import CourseHomeView from '@/views/course/home/CourseHomeView.vue'
import { groupByChapter, pickNext, type Chapter } from '@/views/course/home/useCourseHome'

function point(id: number, name: string, sortOrder: number, children: KnowledgePointVO[] = []): KnowledgePointVO {
  return { id, name, sortOrder, children, courseId: 408, parentId: 0, description: '', createTime: '' }
}
function row(id: number, knowledgePointIds: number[], extra: Record<string, unknown> = {}): QuestionVO {
  return { id, content: `第 ${id} 题`, knowledgePointIds, hasUnderstanding: true, ...extra } as unknown as QuestionVO
}

const tree = [point(1, '线性表', 1, [point(11, '单链表', 1)]), point(2, '栈、队列和数组', 2), point(3, '树', 3)]

describe('groupByChapter', () => {
  it('puts each question under the top-level chapter of its earliest knowledge point, in catalogue order', () => {
    const chapters = groupByChapter(tree, [
      row(30, [2]),
      row(21, [11]),
      row(20, [1]),
      row(22, [2, 11]),
      row(99, [3], { hasUnderstanding: false }),
      row(98, [77]),
    ])
    expect(chapters.map((chapter) => [chapter.name, chapter.questions.map((question) => question.id)])).toEqual([
      ['线性表', [20, 21, 22]],
      ['栈、队列和数组', [30]],
    ])
  })
})

describe('pickNext', () => {
  const today = new Date(2026, 9, 16)
  function chapter(items: [number, string, string | null][]): Chapter[] {
    return [
      {
        id: 1,
        name: '线性表',
        questions: items.map(([id, status, redoDueDate]) => ({
          id,
          stem: '',
          status: status as never,
          redoDueDate,
        })),
      },
    ]
  }

  it('prefers a redo that is due, then the first untouched question, then an early redo', () => {
    expect(
      pickNext(
        chapter([
          [1, 'DONE', null],
          [2, 'NEW', null],
          [3, 'TO_REDO', '2026-10-16'],
        ]),
        today,
      )?.question.id,
    ).toBe(3)
    expect(
      pickNext(
        chapter([
          [1, 'TO_REDO', '2026-10-17'],
          [2, 'NEW', null],
        ]),
        today,
      )?.question.id,
    ).toBe(2)
    expect(pickNext(chapter([[1, 'TO_REDO', '2026-10-17']]), today)?.reason).toContain('提前')
    expect(pickNext(chapter([[1, 'DONE', null]]), today)).toBeNull()
  })
})

describe('CourseHomeView', () => {
  const mounted: ReturnType<typeof mount>[] = []
  function render() {
    const wrapper = mount(CourseHomeView, { global: { stubs: { RouterLink: RouterLinkStub } } })
    mounted.push(wrapper)
    return wrapper
  }

  beforeEach(() => {
    course.getCourseOverview.mockResolvedValue({
      data: { courseId: 408, courseName: '408 数据结构', dueReviewCount: 2, unresolvedWrongCount: 3 },
    })
    knowledge.getKnowledgeTree.mockResolvedValue({ data: tree })
    questions.getQuestionPage.mockResolvedValue({
      data: {
        records: [row(20, [1], { learnerStatus: 'DONE' }), row(30, [2], { learnerStatus: 'NEW' })],
        pages: 1,
      },
    })
  })
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    vi.clearAllMocks()
  })

  it('offers the next question and lists chapters that link into the understanding page', async () => {
    const wrapper = render()
    await flushPromises()
    expect(wrapper.find('h1').text()).toBe('408 数据结构')
    expect(wrapper.text()).toContain('栈、队列和数组的下一道')
    const links = wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'))
    expect(links).toEqual(['/questions/20', '/questions/30'])
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('开始这道'))!
      .trigger('click')
    expect(router.push).toHaveBeenCalledWith('/questions/30')
  })

  it('keeps the old course space one step away', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('打开课程空间'))!
      .trigger('click')
    expect(router.push).toHaveBeenCalledWith({ name: 'CourseSpace', params: { id: 408 } })
  })

  it('stays in place with recovery when the course fails to load', async () => {
    course.getCourseOverview.mockRejectedValueOnce(new Error('offline'))
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('课程没有加载出来')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('重试'))!
      .trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('408 数据结构')
  })
})
