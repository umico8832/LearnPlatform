import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, reactive } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  createCourse: vi.fn(),
  createKnowledgePoint: vi.fn(),
  validate: vi.fn(),
  getAdminCoursePage: vi.fn(),
  getKnowledgeTree: vi.fn(),
  getExamPaperList: vi.fn(),
  publishExamPaper: vi.fn(),
  deleteExamPaper: vi.fn(),
}))
const auth = vi.hoisted(() => ({ version: 1, listeners: [] as Array<() => void> }))
const route = reactive({ query: { courseId: '1' } })
const confirm = vi.hoisted(() => vi.fn())

vi.mock('@/api/course', () => ({
  createCourse: (...args: unknown[]) => api.createCourse(...args),
  deleteCourse: vi.fn(),
  getAdminCoursePage: (...args: unknown[]) => api.getAdminCoursePage(...args),
  updateCourse: vi.fn(),
}))
vi.mock('@/api/knowledgePoint', () => ({
  createKnowledgePoint: (...args: unknown[]) => api.createKnowledgePoint(...args),
  deleteKnowledgePoint: vi.fn(),
  getKnowledgeTree: (...args: unknown[]) => api.getKnowledgeTree(...args),
  updateKnowledgePoint: vi.fn(),
}))
vi.mock('@/api/exam', () => ({
  deleteExamPaper: (...args: unknown[]) => api.deleteExamPaper(...args),
  getExamPaperList: (...args: unknown[]) => api.getExamPaperList(...args),
  publishExamPaper: (...args: unknown[]) => api.publishExamPaper(...args),
}))
vi.mock('@/utils/auth', () => ({
  getAuthSessionVersion: () => auth.version,
  onAuthSessionChange: (listener: () => void) => {
    auth.listeners.push(listener)
    return () => undefined
  },
}))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push: vi.fn() }) }))
vi.mock('element-plus', async (original) => ({
  ...(await original<typeof import('element-plus')>()),
  ElMessage: { success: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm },
}))

import CourseManage from '@/admin/views/CourseManage.vue'
import KnowledgePointManage from '@/admin/views/KnowledgePointManage.vue'
import ExamManage from '@/admin/views/ExamManage.vue'

const Button = defineComponent({
  props: { disabled: Boolean, loading: Boolean },
  emits: ['click'],
  setup(props, { emit, slots }) {
    return () =>
      h('button', { disabled: props.disabled || props.loading, onClick: () => emit('click') }, slots.default?.())
  },
})
const Form = defineComponent({
  props: { disabled: Boolean },
  setup(_, { slots, expose }) {
    expose({ validate: () => api.validate() })
    return () => h('form', slots.default?.())
  },
})
const Dialog = defineComponent({
  props: { modelValue: Boolean },
  emits: ['update:modelValue'],
  setup(props, { slots }) {
    return () => (props.modelValue ? h('section', { 'data-dialog': '' }, [slots.default?.(), slots.footer?.()]) : null)
  },
})
const Input = defineComponent({
  props: { modelValue: [String, Number] },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('input', {
        value: props.modelValue,
        onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
      })
  },
})
const StatePanel = defineComponent({
  props: { state: String, title: String },
  emits: ['retry'],
  setup(props, { emit }) {
    return () =>
      h('div', { 'data-state': props.state }, [props.title, h('button', { onClick: () => emit('retry') }, '重试')])
  },
})
const Pass = defineComponent({
  setup(_, { slots }) {
    return () => h('div', slots.default?.())
  },
})
const Column = defineComponent({
  setup() {
    return () => h('div')
  },
})
const stubs = {
  ElButton: Button,
  ElCard: Pass,
  ElDialog: Dialog,
  ElEmpty: Pass,
  ElForm: Form,
  ElFormItem: Pass,
  ElInput: Input,
  ElInputNumber: Input,
  ElPagination: Pass,
  ElPopconfirm: Pass,
  ElTable: Pass,
  ElTableColumn: Column,
  ElTag: Pass,
  ElTree: Pass,
  ElTreeSelect: Pass,
  LpEmptyState: Pass,
  LpStatePanel: StatePanel,
  ExamPaperEditorDialog: Pass,
  SmartExamDialog: Pass,
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
function sessionChange() {
  auth.version += 1
  auth.listeners.forEach((listener) => listener())
}

const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))
describe('admin content state boundaries', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.validate.mockResolvedValue(true)
    auth.version = 1
    auth.listeners.length = 0
    route.query.courseId = '1'
    api.getAdminCoursePage.mockResolvedValue({ code: 0, data: { records: [], total: 0 } })
    api.getKnowledgeTree.mockResolvedValue({ code: 0, data: [] })
    api.getExamPaperList.mockResolvedValue({ code: 0, data: { records: [], total: 0 } })
  })

  it('keeps course read failures out of zero state and retries in place', async () => {
    api.getAdminCoursePage
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ code: 0, data: { records: [{ id: 1, name: '恢复课程' }], total: 1 } })
    const wrapper = mount(CourseManage, { global: { stubs } })
    wrappers.push(wrapper)
    await flushPromises()
    expect(wrapper.find('[data-state="error"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('共 0 门课程')
    await wrapper.get('[data-state="error"] button').trigger('click')
    await flushPromises()
    expect(api.getAdminCoursePage).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('共 1 门课程')
  })

  it('locks before course validation, preserves a failed draft, and releases after a list refresh', async () => {
    const pending = deferred<{ code: number; data: null }>()
    api.createCourse.mockReturnValueOnce(pending.promise).mockResolvedValueOnce({ code: 0, data: {} })
    const wrapper = mount(CourseManage, { global: { stubs } })
    wrappers.push(wrapper)
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('新增课程'))!
      .trigger('click')
    const inputs = wrapper.get('[data-dialog]').findAll('input')
    await inputs[0].setValue('草稿课程')
    await inputs[1].setValue('保留输入')
    const save = wrapper.findAll('button').find((button) => button.text().includes('创建'))!
    await save.trigger('click')
    await save.trigger('click')
    expect(api.createCourse).toHaveBeenCalledTimes(1)
    pending.resolve({ code: 500, data: null })
    await flushPromises()
    expect((wrapper.get('[data-dialog]').findAll('input')[0].element as HTMLInputElement).value).toBe('草稿课程')
    await save.trigger('click')
    await flushPromises()
    expect(api.createCourse).toHaveBeenCalledTimes(2)
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('新增课程'))!
      .trigger('click')
    expect(wrapper.find('[data-dialog]').exists()).toBe(true)
  })

  it('keeps knowledge-point statistics and table counts out of an initial read failure', async () => {
    api.getKnowledgeTree.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mount(KnowledgePointManage, { global: { stubs } })
    wrappers.push(wrapper)
    await flushPromises()
    expect(wrapper.find('[data-state="error"]').exists()).toBe(true)
    expect(wrapper.find('.admin-summary-grid').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('当前显示 0 / 0 个知识点')
  })

  it('does not let a late knowledge tree for the prior course overwrite a new route course', async () => {
    const first = deferred<{ code: number; data: unknown[] }>()
    api.getKnowledgeTree.mockReturnValueOnce(first.promise).mockResolvedValueOnce({ code: 0, data: [] })
    const wrapper = mount(KnowledgePointManage, { global: { stubs } })
    wrappers.push(wrapper)
    await nextTick()
    route.query.courseId = '2'
    await flushPromises()
    expect(api.getKnowledgeTree).toHaveBeenCalledTimes(2)
    first.resolve({ code: 0, data: [{ id: 9, name: '迟到树', children: [] }] })
    await flushPromises()
    expect((wrapper.vm as unknown as { treeData: unknown[] }).treeData).toEqual([])
  })

  it('invalidates pending knowledge-point validation when the route course changes', async () => {
    const validation = deferred<boolean>()
    api.validate.mockReturnValueOnce(validation.promise)
    const wrapper = mount(KnowledgePointManage, { global: { stubs } })
    wrappers.push(wrapper)
    await flushPromises()
    const vm = wrapper.vm as unknown as {
      openDialog: () => void
      handleSubmit: () => Promise<void>
      dialogVisible: boolean
    }
    vm.openDialog()
    await nextTick()
    const pending = vm.handleSubmit()
    route.query.courseId = '2'
    await nextTick()
    validation.resolve(true)
    await pending
    expect(api.createKnowledgePoint).not.toHaveBeenCalled()
    expect(vm.dialogVisible).toBe(false)
  })

  it('keeps exam statistics out of an initial read failure', async () => {
    api.getExamPaperList.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mount(ExamManage, { global: { stubs } })
    wrappers.push(wrapper)
    await flushPromises()
    expect(wrapper.find('[data-state="error"]').exists()).toBe(true)
    expect(wrapper.find('.admin-summary-grid').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('当前筛选 0 份试卷')
  })

  it('locks an exam before confirmation, releases on cancel, and never writes after session change', async () => {
    const confirmPending = deferred<void>()
    confirm.mockReturnValueOnce(confirmPending.promise).mockRejectedValueOnce(new Error('cancel'))
    const wrapper = mount(ExamManage, { global: { stubs } })
    wrappers.push(wrapper)
    await flushPromises()
    const vm = wrapper.vm as unknown as { handlePublish: (paper: { id: number; title: string }) => Promise<void> }
    const first = vm.handlePublish({ id: 7, title: '隔离试卷' })
    const second = vm.handlePublish({ id: 7, title: '隔离试卷' })
    expect(confirm).toHaveBeenCalledTimes(1)
    sessionChange()
    confirmPending.resolve()
    await first
    await second
    await flushPromises()
    expect(api.publishExamPaper).not.toHaveBeenCalled()
    await vm.handlePublish({ id: 7, title: '隔离试卷' })
    await flushPromises()
    expect(confirm).toHaveBeenCalledTimes(2)
  })
})
