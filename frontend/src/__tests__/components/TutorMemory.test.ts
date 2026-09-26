import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TutorMemory from '@/components/course/TutorMemory.vue'

const { getMemory, saveMemory, deleteMemory } = vi.hoisted(() => ({
  getMemory: vi.fn(),
  saveMemory: vi.fn(),
  deleteMemory: vi.fn(),
}))
vi.mock('@/api/tutorMemory', () => ({
  getTutorMemory: (...args: unknown[]) => getMemory(...args),
  saveTutorMemory: (...args: unknown[]) => saveMemory(...args),
  deleteTutorMemory: (...args: unknown[]) => deleteMemory(...args),
}))
const empty = { revision: 0, explanationStyle: null, goal: null }
const stubs = {
  'el-button': {
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    props: ['disabled', 'loading', 'type'],
    emits: ['click'],
  },
  'el-input': {
    template:
      '<textarea :value="modelValue" :disabled="disabled" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'disabled'],
    emits: ['update:modelValue'],
  },
  'el-alert': { template: '<p>{{ title }}</p>', props: ['title'] },
}
function create() {
  return mount(TutorMemory, { props: { courseId: 10 }, global: { stubs } })
}
async function open(wrapper: ReturnType<typeof create>) {
  wrapper.get('details').element.open = true
  await wrapper.get('details').trigger('toggle')
  await flushPromises()
}
describe('TutorMemory', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getMemory.mockResolvedValue({ data: empty })
  })

  it('loads on opening, saves explicit preferences, and deletes saved settings using the current revision', async () => {
    const wrapper = create()
    expect(getMemory).not.toHaveBeenCalled()
    await open(wrapper)
    expect(getMemory).toHaveBeenCalledWith(10)
    expect(saveMemory).not.toHaveBeenCalled()
    await wrapper.get('[data-testid="memory-style"]').setValue('EXAMPLES')
    await wrapper.get('[data-testid="memory-goal"]').setValue('  理解栈  ')
    saveMemory.mockResolvedValueOnce({ data: { revision: 1, explanationStyle: 'EXAMPLES', goal: '理解栈' } })
    await wrapper.get('[data-testid="memory-save"]').trigger('click')
    await flushPromises()
    expect(saveMemory).toHaveBeenCalledWith(10, { revision: 0, explanationStyle: 'EXAMPLES', goal: '理解栈' })
    expect(wrapper.text()).toContain('记忆已保存')
    deleteMemory.mockResolvedValueOnce({ data: { ...empty, revision: 2 } })
    await wrapper.get('[data-testid="memory-delete"]').trigger('click')
    await flushPromises()
    expect(deleteMemory).toHaveBeenCalledWith(10, 1)
    expect(wrapper.get('textarea').element.value).toBe('')
    expect(wrapper.text()).toContain('记忆已删除')
    expect(wrapper.get('[data-testid="memory-delete"]').attributes('disabled')).toBeDefined()
  })

  it('requires a fresh read after an uncertain save before any further mutation', async () => {
    const wrapper = create()
    await open(wrapper)
    await wrapper.get('textarea').setValue('待确认的目标')
    saveMemory.mockRejectedValueOnce(new Error('响应丢失'))
    await wrapper.get('[data-testid="memory-save"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('textarea').element.value).toBe('待确认的目标')
    expect(wrapper.get('[data-testid="memory-save"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="memory-save"]').trigger('click')
    expect(saveMemory).toHaveBeenCalledTimes(1)
    getMemory.mockResolvedValueOnce({ data: { revision: 1, explanationStyle: null, goal: '已保存的目标' } })
    await wrapper.get('[data-testid="memory-reload"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('textarea').element.value).toBe('已保存的目标')
    await wrapper.get('textarea').setValue('修正目标')
    saveMemory.mockResolvedValueOnce({ data: { revision: 2, explanationStyle: null, goal: '修正目标' } })
    await wrapper.get('[data-testid="memory-save"]').trigger('click')
    await flushPromises()
    expect(saveMemory).toHaveBeenLastCalledWith(10, { revision: 1, explanationStyle: null, goal: '修正目标' })
  })

  it('recovers an initial read failure only through explicit retry', async () => {
    getMemory.mockRejectedValueOnce(new Error('读取失败'))
    const wrapper = create()
    await open(wrapper)
    expect(wrapper.find('textarea').exists()).toBe(false)
    await wrapper.get('[data-testid="memory-reload"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('textarea').exists()).toBe(true)
    expect(saveMemory).not.toHaveBeenCalled()
  })

  it('ignores a previous course read that arrives after switching courses', async () => {
    let finish!: (value: unknown) => void
    getMemory.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    const wrapper = create()
    await open(wrapper)
    getMemory.mockResolvedValueOnce({ data: { revision: 2, explanationStyle: null, goal: '新课程' } })
    await wrapper.setProps({ courseId: 11 })
    await flushPromises()
    finish({ data: { revision: 9, explanationStyle: 'CONCISE', goal: '旧课程' } })
    await flushPromises()
    expect(wrapper.get('textarea').element.value).toBe('新课程')
  })

  it.each(['success', 'failure'])('ignores late save %s after a course switch', async (outcome) => {
    let resolve!: (value: unknown) => void
    let reject!: (error: Error) => void
    saveMemory.mockImplementationOnce(
      () =>
        new Promise((yes, no) => {
          resolve = yes
          reject = no
        }),
    )
    const wrapper = create()
    await open(wrapper)
    await wrapper.get('textarea').setValue('旧课程目标')
    await wrapper.get('[data-testid="memory-save"]').trigger('click')
    getMemory.mockResolvedValueOnce({ data: { revision: 3, explanationStyle: null, goal: '新课程目标' } })
    await wrapper.setProps({ courseId: 11 })
    await flushPromises()
    if (outcome === 'success') resolve({ data: { revision: 1, explanationStyle: null, goal: '旧课程目标' } })
    else reject(new Error('旧请求失败'))
    await flushPromises()
    expect(wrapper.get('textarea').element.value).toBe('新课程目标')
    expect(wrapper.find('[data-testid="memory-reload"]').exists()).toBe(false)
  })

  it('blocks busy, duplicate and empty saves, including direct component events', async () => {
    const wrapper = create()
    await open(wrapper)
    await wrapper.get('textarea').setValue('   ')
    wrapper.findAllComponents(stubs['el-button'])[0].vm.$emit('click')
    expect(saveMemory).not.toHaveBeenCalled()
    await wrapper.get('textarea').setValue('目标')
    await wrapper.setProps({ busy: true })
    wrapper.findAllComponents(stubs['el-button'])[0].vm.$emit('click')
    expect(saveMemory).not.toHaveBeenCalled()
    await wrapper.setProps({ busy: false })
    let finish!: (value: unknown) => void
    saveMemory.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    await wrapper.get('[data-testid="memory-save"]').trigger('click')
    wrapper.findAllComponents(stubs['el-button'])[0].vm.$emit('click')
    expect(saveMemory).toHaveBeenCalledTimes(1)
    wrapper.unmount()
    finish({ data: { revision: 1, explanationStyle: null, goal: '目标' } })
    await flushPromises()
    expect(getMemory).toHaveBeenCalledTimes(1)
  })
})
