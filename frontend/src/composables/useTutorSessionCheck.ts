import { ref, type Ref } from 'vue'
import {
  getTutorSession,
  startTutorSession,
  submitTutorCheck,
  type TutorCheckResultVO,
  type TutorSessionVO,
} from '@/api/course'
import { errorMessage } from '@/utils/errors'

export function useTutorSessionCheck(courseId: Ref<number>, pointId: Ref<number>) {
  const loading = ref(false)
  const failed = ref(false)
  const submitting = ref(false)
  const session = ref<TutorSessionVO>()
  const optionId = ref('')
  const result = ref<TutorCheckResultVO>()
  const checkFailure = ref('')
  let generation = 0

  function storageKey(id = courseId.value, point = pointId.value) {
    return `lp:tutor-session:${id}:${point}`
  }

  function applySession(value: TutorSessionVO) {
    session.value = value
    optionId.value = value.checkAnswer ?? ''
    result.value = value.checkResult ?? undefined
  }

  async function load(restart = false) {
    const current = ++generation
    const requestedCourseId = courseId.value
    const requestedPointId = pointId.value
    const key = storageKey(requestedCourseId, requestedPointId)
    loading.value = true
    failed.value = false
    submitting.value = false
    session.value = undefined
    optionId.value = ''
    result.value = undefined
    checkFailure.value = ''
    try {
      const storedSessionKey = restart ? null : sessionStorage.getItem(key)
      if (storedSessionKey) {
        const restored = (await getTutorSession(requestedCourseId, storedSessionKey)).data
        if (current !== generation) return
        applySession(restored)
        return
      }
      const created = (await startTutorSession(requestedCourseId, requestedPointId)).data
      if (current !== generation) return
      applySession(created)
      sessionStorage.setItem(key, created.sessionKey)
    } catch {
      if (current !== generation) return
      failed.value = true
    } finally {
      if (current === generation) loading.value = false
    }
  }

  async function submit() {
    if (!session.value || !optionId.value || result.value || submitting.value) return
    const current = generation
    const sessionKey = session.value.sessionKey
    const selectedOption = optionId.value
    submitting.value = true
    checkFailure.value = ''
    try {
      const response = await submitTutorCheck(courseId.value, sessionKey, selectedOption)
      if (current !== generation || session.value?.sessionKey !== sessionKey) return
      result.value = response.data
      try {
        const refreshed = await getTutorSession(courseId.value, sessionKey)
        if (current !== generation || session.value?.sessionKey !== sessionKey) return
        applySession(refreshed.data)
      } catch {
        if (current !== generation || session.value?.sessionKey !== sessionKey) return
        checkFailure.value = '理解检查已保存，暂时无法同步最新会话；可刷新后恢复。'
      }
    } catch (error) {
      if (current !== generation || session.value?.sessionKey !== sessionKey) return
      checkFailure.value = errorMessage(error, '暂时无法提交理解检查，请重试')
    } finally {
      if (current === generation) submitting.value = false
    }
  }

  function dispose() {
    generation++
  }

  return { loading, failed, submitting, session, optionId, result, checkFailure, load, submit, dispose }
}
