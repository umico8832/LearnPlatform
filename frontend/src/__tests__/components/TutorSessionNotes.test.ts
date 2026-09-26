import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TutorSessionNotes from '@/components/course/TutorSessionNotes.vue'

const { getCurrent, getNotes, saveNote, deleteNote } = vi.hoisted(() => ({
  getCurrent: vi.fn(),
  getNotes: vi.fn(),
  saveNote: vi.fn(),
  deleteNote: vi.fn(),
}))
vi.mock('@/api/tutorNotes', () => ({
  getTutorSessionNote: (...args: unknown[]) => getCurrent(...args),
  getTutorSessionNotes: (...args: unknown[]) => getNotes(...args),
  saveTutorSessionNote: (...args: unknown[]) => saveNote(...args),
  deleteTutorSessionNote: (...args: unknown[]) => deleteNote(...args),
}))

const source = {
  available: true,
  knowledgePointId: 31,
  title: '栈的基本操作',
  sessionStartedAt: '2026-09-26T10:00:00Z',
  checkStatus: 'UNANSWERED' as const,
  checkAnsweredAt: '2026-09-26T10:05:00Z',
}
const empty = { sessionKey: 'session-a', revision: 0, note: null, updatedAt: null, source }
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
  'el-pagination': { template: '<nav />', props: ['currentPage', 'pageSize', 'total'] },
}
function create() {
  return mount(TutorSessionNotes, { props: { courseId: 10, sessionKey: 'session-a' }, global: { stubs } })
}
async function open(wrapper: ReturnType<typeof create>) {
  wrapper.get('details').element.open = true
  await wrapper.get('details').trigger('toggle')
  await flushPromises()
}

describe('TutorSessionNotes', () => {
  beforeEach(() => {
    getCurrent.mockReset()
    getNotes.mockReset()
    saveNote.mockReset()
    deleteNote.mockReset()
    getCurrent.mockResolvedValue({ data: empty })
    getNotes.mockResolvedValue({ data: { records: [], total: 0, current: 1, size: 5 } })
  })

  it('reads the current session and saved summaries only after opening, then saves an explicit review', async () => {
    const wrapper = create()
    expect(getCurrent).not.toHaveBeenCalled()
    await open(wrapper)
    expect(getCurrent).toHaveBeenCalledWith(10, 'session-a')
    expect(getNotes).toHaveBeenCalledWith(10, 1)
    await wrapper.get('[data-testid="session-note-input"]').setValue('  我能解释入栈和出栈的顺序。  ')
    saveNote.mockResolvedValueOnce({ data: { ...empty, revision: 1, note: '我能解释入栈和出栈的顺序。' } })
    await wrapper.get('[data-testid="session-note-save"]').trigger('click')
    await flushPromises()
    expect(saveNote).toHaveBeenCalledWith(10, 'session-a', { revision: 0, note: '我能解释入栈和出栈的顺序。' })
  })

  it('selects a paged saved review for correction and keeps the real check result visible', async () => {
    const existing = {
      ...empty,
      sessionKey: 'session-b',
      revision: 4,
      note: '旧复盘',
      source: { ...source, checkStatus: 'INCORRECT' as const },
    }
    getNotes
      .mockResolvedValueOnce({ data: { records: [existing], total: 6, current: 1, size: 5 } })
      .mockResolvedValueOnce({ data: { records: [existing], total: 6, current: 2, size: 5 } })
    const wrapper = create()
    await open(wrapper)
    expect(wrapper.text()).toContain('已作答，待复习')
    const pagination = wrapper.findComponent(stubs['el-pagination'])
    pagination.vm.$emit('current-change', 2)
    await flushPromises()
    expect(getNotes).toHaveBeenLastCalledWith(10, 2)
    getCurrent.mockResolvedValueOnce({ data: existing })
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '编辑此复盘')!
      .trigger('click')
    await flushPromises()
    expect(wrapper.get('textarea').element.value).toBe('旧复盘')
    await wrapper.get('textarea').setValue('修正复盘')
    saveNote.mockResolvedValueOnce({ data: { ...existing, revision: 5, note: '修正复盘' } })
    await wrapper.get('[data-testid="session-note-save"]').trigger('click')
    await flushPromises()
    expect(saveNote).toHaveBeenCalledWith(10, 'session-b', { revision: 4, note: '修正复盘' })
  })

  it('keeps the draft and requires a fresh read when a write result is uncertain', async () => {
    const wrapper = create()
    await open(wrapper)
    await wrapper.get('textarea').setValue('待确认复盘')
    saveNote.mockRejectedValueOnce(new Error('响应丢失'))
    await wrapper.get('[data-testid="session-note-save"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('textarea').element.value).toBe('待确认复盘')
    expect(wrapper.get('[data-testid="session-note-save"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="session-note-save"]').trigger('click')
    expect(saveNote).toHaveBeenCalledTimes(1)
    getCurrent.mockResolvedValueOnce({ data: { ...empty, revision: 1, note: '已保存复盘' } })
    await wrapper.get('[data-testid="session-note-reload"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('textarea').element.value).toBe('已保存复盘')
  })

  it('refreshes only the current session source after a check result while preserving an unsaved draft', async () => {
    getNotes.mockResolvedValueOnce({
      data: { records: [{ ...empty, note: '列表复盘' }], total: 1, current: 1, size: 5 },
    })
    const wrapper = create()
    await open(wrapper)
    await wrapper.get('textarea').setValue('尚未保存的复盘')
    getCurrent.mockResolvedValueOnce({
      data: { ...empty, revision: 8, note: '服务端旧复盘', source: { ...source, checkStatus: 'CORRECT' as const } },
    })
    await wrapper.setProps({ checkResult: { correct: true } })
    await flushPromises()
    expect(getCurrent).toHaveBeenLastCalledWith(10, 'session-a')
    expect(wrapper.get('textarea').element.value).toBe('尚未保存的复盘')
    expect(wrapper.text()).toContain('已答对')
    expect(wrapper.text()).not.toContain('尚未作答')
  })

  it('reloads the selected historical session after a failed write and can explicitly return to this session', async () => {
    const existing = { ...empty, sessionKey: 'session-b', revision: 4, note: '历史复盘' }
    getNotes.mockResolvedValueOnce({ data: { records: [existing], total: 1, current: 1, size: 5 } })
    const wrapper = create()
    await open(wrapper)
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '编辑此复盘')!
      .trigger('click')
    await wrapper.get('textarea').setValue('待确认修正')
    saveNote.mockRejectedValueOnce(new Error('响应丢失'))
    await wrapper.get('[data-testid="session-note-save"]').trigger('click')
    await flushPromises()
    getCurrent.mockResolvedValueOnce({ data: { ...existing, revision: 5, note: '服务端复盘' } })
    await wrapper.get('[data-testid="session-note-reload"]').trigger('click')
    await flushPromises()
    expect(getCurrent).toHaveBeenLastCalledWith(10, 'session-b')
    getCurrent.mockResolvedValueOnce({ data: { ...empty, note: '本次会话复盘' } })
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '返回本次会话')!
      .trigger('click')
    await flushPromises()
    expect(getCurrent).toHaveBeenLastCalledWith(10, 'session-a')
    expect(wrapper.get('textarea').element.value).toBe('本次会话复盘')
  })

  it('reads a selected historical review afresh and rejects an older current-session response', async () => {
    const existing = { ...empty, sessionKey: 'session-b', revision: 4, note: '历史服务端复盘' }
    let finishOld!: (value: unknown) => void
    getCurrent.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finishOld = resolve
        }),
    )
    getNotes.mockResolvedValueOnce({ data: { records: [existing], total: 1, current: 1, size: 5 } })
    const wrapper = create()
    await open(wrapper)
    getCurrent.mockResolvedValueOnce({ data: existing })
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '编辑此复盘')!
      .trigger('click')
    await flushPromises()
    expect(getCurrent).toHaveBeenLastCalledWith(10, 'session-b')
    finishOld({ data: { ...empty, note: '旧会话响应' } })
    await flushPromises()
    expect(wrapper.get('textarea').element.value).toBe('历史服务端复盘')
  })

  it('refreshes the list after saving even when an older list request is still pending', async () => {
    let finishOldList!: (value: unknown) => void
    getNotes.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finishOldList = resolve
        }),
    )
    const wrapper = create()
    await open(wrapper)
    await wrapper.get('textarea').setValue('新复盘')
    const saved = { ...empty, revision: 1, note: '新复盘' }
    saveNote.mockResolvedValueOnce({ data: saved })
    getNotes.mockResolvedValueOnce({ data: { records: [saved], total: 1, current: 1, size: 5 } })
    await wrapper.get('[data-testid="session-note-save"]').trigger('click')
    await flushPromises()
    expect(getNotes).toHaveBeenCalledTimes(2)
    finishOldList({ data: { records: [], total: 0, current: 1, size: 5 } })
    await flushPromises()
    expect(wrapper.text()).toContain('新复盘')
  })

  it('returns to the last valid list page after deleting the sole saved note on the final page', async () => {
    const currentSaved = { ...empty, revision: 2, note: '待删除复盘' }
    const remaining = { ...empty, sessionKey: 'session-c', revision: 1, note: '仍可查看的复盘' }
    getCurrent.mockResolvedValue({ data: currentSaved })
    getNotes
      .mockResolvedValueOnce({ data: { records: [currentSaved], total: 6, current: 2, size: 5 } })
      .mockResolvedValueOnce({ data: { records: [], total: 5, current: 2, size: 5 } })
      .mockResolvedValueOnce({ data: { records: [remaining], total: 5, current: 1, size: 5 } })
    deleteNote.mockResolvedValueOnce({ data: { ...currentSaved, revision: 3, note: null } })
    const wrapper = create()
    await open(wrapper)
    await wrapper.get('[data-testid="session-note-delete"]').trigger('click')
    await flushPromises()
    expect(getNotes).toHaveBeenLastCalledWith(10, 1)
    expect(wrapper.text()).toContain('仍可查看的复盘')
  })

  it('allows only deletion for an unavailable source and preserves the actual check boundary', async () => {
    getCurrent.mockResolvedValue({
      data: {
        ...empty,
        revision: 2,
        note: '历史复盘',
        source: { ...source, available: false, title: null, checkStatus: null },
      },
    })
    const wrapper = create()
    await open(wrapper)
    expect(wrapper.text()).toContain('原课节已不可用')
    expect(wrapper.text()).not.toContain('理解检查：')
    expect(wrapper.get('[data-testid="session-note-save"]').attributes('disabled')).toBeDefined()
    deleteNote.mockResolvedValueOnce({
      data: { ...empty, revision: 3, source: { ...source, available: false, title: null, checkStatus: null } },
    })
    await wrapper.get('[data-testid="session-note-delete"]').trigger('click')
    await flushPromises()
    expect(deleteNote).toHaveBeenCalledWith(10, 'session-a', 2)
  })

  it('ignores late current and list responses after session changes and after unmount', async () => {
    let finishCurrent!: (value: unknown) => void
    let finishList!: (value: unknown) => void
    getCurrent.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finishCurrent = resolve
        }),
    )
    getNotes.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finishList = resolve
        }),
    )
    const wrapper = create()
    await open(wrapper)
    getCurrent.mockResolvedValueOnce({ data: { ...empty, sessionKey: 'session-b', note: '新会话' } })
    getNotes.mockResolvedValueOnce({ data: { records: [], total: 0, current: 1, size: 5 } })
    await wrapper.setProps({ sessionKey: 'session-b' })
    await flushPromises()
    finishCurrent({ data: { ...empty, note: '旧会话' } })
    finishList({ data: { records: [{ ...empty, note: '旧列表' }], total: 1, current: 1, size: 5 } })
    await flushPromises()
    expect(wrapper.get('textarea').element.value).toBe('新会话')
    wrapper.unmount()
  })
})
