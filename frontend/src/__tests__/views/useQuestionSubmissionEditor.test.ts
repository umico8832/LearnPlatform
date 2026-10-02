import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { getAllCourses } from '@/api/course'
import { submitQuestion } from '@/api/submission'
import { setToken } from '@/utils/auth'
import { useQuestionSubmissionEditor } from '@/views/practice/useQuestionSubmissionEditor'
import { emptySubmissionDraft, submissionOptions, submissionPayload } from '@/views/practice/questionSubmissionForm'

vi.mock('@/api/course', () => ({ getAllCourses: vi.fn() }))
vi.mock('@/api/submission', () => ({ submitQuestion: vi.fn() }))
const courseResponse = { code: 0, message: '', data: [{ id: 1, name: '课程一' }] }
const submissionResponse = { code: 0, message: '', data: { id: 8 } }
const wrappers: VueWrapper[] = []
function setup() {
  const open = ref(true)
  const saved = vi.fn()
  let editor!: ReturnType<typeof useQuestionSubmissionEditor>
  wrappers.push(
    mount(
      defineComponent({
        setup() {
          editor = useQuestionSubmissionEditor(open, saved)
          return () => null
        },
      }),
    ),
  )
  return { editor, open, saved }
}
function fill(editor: ReturnType<typeof useQuestionSubmissionEditor>) {
  Object.assign(editor.draft, { courseId: 1, questionType: 'TRUE_FALSE', content: '题目内容', correctAnswer: 'TRUE' })
}
beforeEach(() => {
  vi.resetAllMocks()
  setToken('test-submission-account-a')
  vi.mocked(getAllCourses).mockResolvedValue(courseResponse as never)
  vi.mocked(submitQuestion).mockResolvedValue(submissionResponse as never)
})
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  localStorage.clear()
})

describe('投稿表单与提交状态', () => {
  it('未选课程不能以0通过校验，空选项不改变正确答案归属', () => {
    const draft = { ...emptySubmissionDraft(), courseId: 0, questionType: 'SINGLE_CHOICE', content: '题目' }
    const options = [
      { key: 1, content: '第一项', isCorrect: false },
      { key: 2, content: ' ', isCorrect: false },
      { key: 3, content: '正确项', isCorrect: true },
    ]
    expect(() => submissionPayload(draft, options)).toThrow('请选择所属课程')
    const result = submissionPayload({ ...draft, courseId: 1 }, options)
    expect(JSON.parse(result.optionsJson!)).toEqual([
      { label: 'A', content: '第一项', isCorrect: false },
      { label: 'B', content: '正确项', isCorrect: true },
    ])
    expect(() =>
      submissionPayload(
        { ...draft, courseId: 1 },
        options.map((option) => ({ ...option, isCorrect: true })),
      ),
    ).toThrow('只能标记一个')
  })

  it('课程加载失败可重试，失败时保留输入且不可提交', async () => {
    vi.mocked(getAllCourses).mockRejectedValueOnce(new Error('offline'))
    const { editor } = setup()
    fill(editor)
    await flushPromises()
    expect(editor.coursesError.value).toBe(true)
    await editor.submit()
    expect(submitQuestion).not.toHaveBeenCalled()
    await editor.loadCourses()
    expect(editor.coursesError.value).toBe(false)
    expect(editor.draft.content).toBe('题目内容')
    expect(getAllCourses).toHaveBeenLastCalledWith({ errorDisplay: 'inline' })
  })

  it('提交失败保留内容和打开状态，再次提交成功后清稿并通知刷新', async () => {
    vi.mocked(submitQuestion).mockRejectedValueOnce(new Error('offline'))
    const { editor, open, saved } = setup()
    await flushPromises()
    fill(editor)
    await editor.submit()
    expect(open.value).toBe(true)
    expect(editor.draft.content).toBe('题目内容')
    expect(editor.error.value).toContain('内容已保留')
    expect(saved).not.toHaveBeenCalled()
    await editor.submit()
    expect(open.value).toBe(false)
    expect(saved).toHaveBeenCalledOnce()
    expect(editor.draft.content).toBe('')
  })

  it('快速重复提交只产生一个写请求', async () => {
    let finish!: (value: never) => void
    vi.mocked(submitQuestion).mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve
      }) as never,
    )
    const { editor, saved } = setup()
    await flushPromises()
    fill(editor)
    const first = editor.submit()
    await editor.submit()
    expect(submitQuestion).toHaveBeenCalledOnce()
    expect(editor.submitting.value).toBe(true)
    finish(submissionResponse as never)
    await first
    expect(saved).toHaveBeenCalledOnce()
  })

  it('账号切换清空私有草稿，旧提交完成不能清除新草稿或触发成功', async () => {
    let finish!: (value: never) => void
    vi.mocked(submitQuestion).mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve
      }) as never,
    )
    const { editor, open, saved } = setup()
    await flushPromises()
    fill(editor)
    const first = editor.submit()
    setToken('test-submission-account-b')
    expect(editor.draft.content).toBe('')
    expect(open.value).toBe(false)
    open.value = true
    await flushPromises()
    editor.draft.content = '第二个账号的新题目'
    finish(submissionResponse as never)
    await first
    expect(editor.draft.content).toBe('第二个账号的新题目')
    expect(open.value).toBe(true)
    expect(saved).not.toHaveBeenCalled()
  })

  it('卸载后返回的提交结果不关闭新界面或调用成功回调', async () => {
    let finish!: (value: never) => void
    vi.mocked(submitQuestion).mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve
      }) as never,
    )
    const { editor, open, saved } = setup()
    await flushPromises()
    fill(editor)
    const pending = editor.submit()
    wrappers.at(-1)!.unmount()
    finish(submissionResponse as never)
    await pending
    expect(open.value).toBe(true)
    expect(saved).not.toHaveBeenCalled()
  })

  it('详情对损坏选项安全降级，并保留真实正确标记', () => {
    expect(submissionOptions('{}')).toEqual([])
    expect(submissionOptions('invalid')).toEqual([])
    expect(submissionOptions('[{"content":"答案","isCorrect":1},null]')).toEqual([
      { label: 'A', content: '答案', isCorrect: true },
    ])
  })
})
