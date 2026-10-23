import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { getAllCourses } from '@/api/course'
import { submitQuestion } from '@/api/submission'
import QuestionSubmissionEditor from '@/views/practice/QuestionSubmissionEditor.vue'

vi.mock('@/api/course', () => ({ getAllCourses: vi.fn() }))
vi.mock('@/api/submission', () => ({ submitQuestion: vi.fn() }))

const wrappers: VueWrapper[] = []
const sheet = defineComponent({
  name: 'LtSheet',
  props: { open: Boolean },
  emits: ['update:open'],
  template:
    '<section v-if="open"><button type="button" @click="$emit(\'update:open\', false)">关闭 sheet</button><slot /></section>',
})

function mountEditor() {
  const host = mount(
    defineComponent({
      components: { QuestionSubmissionEditor },
      setup() {
        const open = ref(true)
        return { open }
      },
      template: '<QuestionSubmissionEditor v-model="open" />',
    }),
    { attachTo: document.body, global: { stubs: { LtSheet: sheet } } },
  )
  wrappers.push(host)
  return host
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(getAllCourses).mockResolvedValue({ code: 0, message: '', data: [{ id: 1, name: '课程一' }] } as never)
})
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))

describe('QuestionSubmissionEditor', () => {
  it('提交中拒绝 sheet 关闭，成功后由提交逻辑关闭', async () => {
    let resolve!: (value: unknown) => void
    vi.mocked(submitQuestion).mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }) as never,
    )
    const wrapper = mountEditor()
    await flushPromises()
    const selects = wrapper.findAll('select')
    await selects[0]!.setValue('1')
    await selects[1]!.setValue('TRUE_FALSE')
    await wrapper.get('[role="radio"]:nth-of-type(4)').trigger('click')
    await wrapper.get('textarea[placeholder="写下完整的题目"]').setValue('投稿题目')
    await wrapper.get('input[type="radio"][value="TRUE"]').setValue()
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(submitQuestion).toHaveBeenCalledOnce()
    expect(submitQuestion).toHaveBeenCalledWith(
      expect.objectContaining({ difficulty: 4 }),
      { errorDisplay: 'inline' },
      expect.any(String),
    )
    const close = wrapper.get('button')
    ;(close.element as HTMLButtonElement).focus()
    expect(document.activeElement).toBe(close.element)
    await close.trigger('click')
    expect((wrapper.vm as unknown as { open: boolean }).open).toBe(true)

    resolve({ code: 0, message: '', data: { id: 1 } })
    await flushPromises()
    expect((wrapper.vm as unknown as { open: boolean }).open).toBe(false)
  })
})
