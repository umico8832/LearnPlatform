import { ref, type Ref } from 'vue'
import {
  getTutorSession,
  startTutorSession,
  submitTutorCheck,
  type TutorCheckResultVO,
  type TutorSessionVO,
} from '@/api/course'
import { useGamificationStore } from '@/stores/gamification'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
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
  let alive = true

  function clearStoredSessions() {
    try {
      for (let index = sessionStorage.length - 1; index >= 0; index--) {
        const key = sessionStorage.key(index)
        if (key?.startsWith('lp:tutor-session:')) sessionStorage.removeItem(key)
      }
    } catch {
      // Private browsing storage can be unavailable; server-side ownership remains authoritative.
    }
  }

  function clearForNewAccount() {
    generation++
    clearStoredSessions()
    loading.value = false
    failed.value = false
    submitting.value = false
    session.value = undefined
    optionId.value = ''
    result.value = undefined
    checkFailure.value = ''
    if (isAuthenticated()) void load()
  }
  const unsubscribeAuth = onAuthSessionChange(clearForNewAccount)

  function isCurrent(current: number, authSession: number, sessionKey?: string) {
    return (
      alive &&
      current === generation &&
      authSession === getAuthSessionVersion() &&
      (sessionKey === undefined || session.value?.sessionKey === sessionKey)
    )
  }

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
    const authSession = getAuthSessionVersion()
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
        const restored = (await getTutorSession(requestedCourseId, storedSessionKey, { errorDisplay: 'inline' })).data
        if (!isCurrent(current, authSession)) return
        applySession(restored)
        return
      }
      const created = (await startTutorSession(requestedCourseId, requestedPointId, { errorDisplay: 'inline' })).data
      if (!isCurrent(current, authSession)) return
      applySession(created)
      sessionStorage.setItem(key, created.sessionKey)
    } catch {
      if (!isCurrent(current, authSession)) return
      failed.value = true
    } finally {
      if (isCurrent(current, authSession)) loading.value = false
    }
  }

  async function submit() {
    if (!session.value || !optionId.value || result.value || submitting.value) return
    const current = generation
    const authSession = getAuthSessionVersion()
    const sessionKey = session.value.sessionKey
    const selectedOption = optionId.value
    submitting.value = true
    checkFailure.value = ''
    try {
      const response = await submitTutorCheck(courseId.value, sessionKey, selectedOption, { errorDisplay: 'inline' })
      if (!isCurrent(current, authSession, sessionKey)) return
      if (response.data.reward) useGamificationStore().acceptReward(response.data.reward, authSession)
      result.value = response.data
      try {
        const refreshed = await getTutorSession(courseId.value, sessionKey, { errorDisplay: 'inline' })
        if (!isCurrent(current, authSession, sessionKey)) return
        applySession(refreshed.data)
      } catch {
        if (!isCurrent(current, authSession, sessionKey)) return
        checkFailure.value = '理解检查已保存，暂时无法同步最新会话；可刷新后恢复。'
      }
    } catch (error) {
      if (!isCurrent(current, authSession, sessionKey)) return
      checkFailure.value = errorMessage(error, '暂时无法提交理解检查，请重试')
    } finally {
      if (isCurrent(current, authSession, sessionKey)) submitting.value = false
    }
  }

  function dispose() {
    alive = false
    generation++
    unsubscribeAuth()
  }

  return { loading, failed, submitting, session, optionId, result, checkFailure, load, submit, dispose }
}
