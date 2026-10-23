import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { getPracticeRecords, push } = vi.hoisted(() => ({ getPracticeRecords: vi.fn(), push: vi.fn() }))
vi.mock('@/api/practice', () => ({ getPracticeRecords }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
}))

import PracticeRecordView from '@/views/practice/PracticeRecordView.vue'

const stubs = {
  'el-button': { template: '<button><slot /></button>' },
  'el-option': { template: '<option><slot /></option>' },
  'el-select': { template: '<select><slot /></select>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-pagination': { template: '<nav />' },
}

const record = (id: number, isCorrect: number | null) => ({
  id,
  questionId: id,
  questionContent: `题目 ${id}`,
  questionType: 'SINGLE_CHOICE',
  courseName: '数据结构',
  difficulty: 2,
  userAnswer: 'A',
  isCorrect,
  answerTime: 12,
  createTime: '2026-10-02T10:00:00',
})

describe('PracticeRecordView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('keeps pending records out of the wrong count and grades the rate from graded records only', async () => {
    getPracticeRecords.mockResolvedValue({
      code: 0,
      data: { records: [record(1, 1), record(2, 0), record(3, null)], total: 3 },
    })
    const wrapper = mount(PracticeRecordView, { global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('正确 1 · 需复习 1 · 待判分 1')
    expect(wrapper.text()).toContain('已判分正确率 50%')
    expect(wrapper.text()).toContain('待判分')
    expect(wrapper.text()).not.toContain('需复习 2')
    wrapper.unmount()
  })

  it('sends real filters and pagination to the records API', async () => {
    getPracticeRecords.mockResolvedValue({ code: 0, data: { records: [record(1, 1)], total: 50 } })
    const wrapper = mount(PracticeRecordView, { global: { stubs } })
    await flushPromises()
    await wrapper.findAll('select')[0]!.setValue('SINGLE_CHOICE')
    await flushPromises()
    await wrapper.findAll('select')[1]!.setValue('0')
    await flushPromises()
    expect(getPracticeRecords).toHaveBeenLastCalledWith(
      { pageNum: 1, pageSize: 10, questionType: 'SINGLE_CHOICE', isCorrect: 0 },
      { errorDisplay: 'inline' },
    )
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '下一页')!
      .trigger('click')
    await flushPromises()
    expect(getPracticeRecords).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 2 }), {
      errorDisplay: 'inline',
    })
    wrapper.unmount()
  })

  it('shows retryable failure instead of an empty history after a request error', async () => {
    getPracticeRecords.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mount(PracticeRecordView, { global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('练习记录暂时无法加载')
    expect(wrapper.text()).not.toContain('还没有练习记录')
    wrapper.unmount()
  })
})
