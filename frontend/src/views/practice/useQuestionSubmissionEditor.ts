import { computed, onScopeDispose, reactive, ref, watch, type Ref } from 'vue'
import { getAllCourses, type CourseVO } from '@/api/course'
import { submitQuestion, type SubmissionForm } from '@/api/submission'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import { emptySubmissionDraft, submissionPayload, type SubmissionOption } from './questionSubmissionForm'

export function useQuestionSubmissionEditor(open: Ref<boolean>, onSubmitted: () => void) {
  const draft = reactive(emptySubmissionDraft())
  let optionKey = 0
  const newOptions = () => Array.from({ length: 4 }, () => ({ key: optionKey++, content: '', isCorrect: false }))
  const options = ref<SubmissionOption[]>(newOptions())
  const courses = ref<CourseVO[]>([])
  const coursesLoading = ref(false)
  const coursesError = ref(false)
  const coursesLoaded = ref(false)
  const submitting = ref(false)
  const error = ref('')
  let alive = true
  let courseRequest = 0
  const showOptions = computed(() => ['SINGLE_CHOICE', 'MULTIPLE_CHOICE'].includes(draft.questionType))
  function capture() {
    const session = getAuthSessionVersion()
    return () => alive && session === getAuthSessionVersion()
  }
  async function loadCourses() {
    if (coursesLoading.value) return
    const currentSession = capture()
    const request = ++courseRequest
    const current = () => currentSession() && request === courseRequest
    coursesLoading.value = true
    coursesError.value = false
    try {
      const response = await getAllCourses({ errorDisplay: 'inline' })
      if (!current()) return
      if (response.code !== 0 || !response.data) throw new Error('Missing courses')
      courses.value = response.data
      coursesLoaded.value = true
    } catch {
      if (current()) coursesError.value = true
    } finally {
      if (current()) coursesLoading.value = false
    }
  }
  function typeChanged() {
    draft.correctAnswer = ''
    options.value.forEach((option) => {
      option.isCorrect = false
    })
    error.value = ''
  }
  function setCorrect(index: number, checked: boolean) {
    if (draft.questionType === 'SINGLE_CHOICE' && checked)
      options.value.forEach((option) => {
        option.isCorrect = false
      })
    const option = options.value[index]
    if (option) option.isCorrect = checked
  }
  function addOption() {
    if (options.value.length < 8) options.value.push({ key: optionKey++, content: '', isCorrect: false })
  }
  function removeOption(index: number) {
    if (options.value.length > 2) options.value.splice(index, 1)
  }
  async function submit() {
    if (submitting.value || coursesLoading.value || coursesError.value || !coursesLoaded.value) return
    error.value = ''
    let payload: SubmissionForm
    try {
      payload = submissionPayload(draft, options.value)
      if (!courses.value.some((course) => course.id === payload.courseId)) throw new Error('请选择当前可用的课程。')
    } catch (failure) {
      error.value = failure instanceof Error ? failure.message : '请检查题目内容。'
      return
    }
    const current = capture()
    submitting.value = true
    try {
      const response = await submitQuestion(payload, { errorDisplay: 'inline' })
      if (!current()) return
      if (response.code !== 0) throw new Error('Submission failed')
      open.value = false
      Object.assign(draft, emptySubmissionDraft())
      options.value = newOptions()
      onSubmitted()
    } catch {
      if (current()) error.value = '未能确认提交结果。内容已保留，可先关闭表单查看投稿记录，再决定是否重试。'
    } finally {
      if (current()) submitting.value = false
    }
  }
  watch(
    open,
    (value) => {
      if (value && !coursesLoaded.value) void loadCourses()
    },
    { immediate: true },
  )
  onScopeDispose(
    onAuthSessionChange(() => {
      courseRequest++
      Object.assign(draft, emptySubmissionDraft())
      options.value = newOptions()
      courses.value = []
      coursesLoading.value = false
      coursesLoaded.value = false
      coursesError.value = false
      submitting.value = false
      error.value = ''
      open.value = false
    }),
  )
  onScopeDispose(() => {
    alive = false
    courseRequest++
  })
  return {
    draft,
    options,
    courses,
    coursesLoading,
    coursesError,
    coursesLoaded,
    submitting,
    error,
    showOptions,
    loadCourses,
    typeChanged,
    setCorrect,
    addOption,
    removeOption,
    submit,
  }
}
