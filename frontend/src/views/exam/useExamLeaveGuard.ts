import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { useConfirmSheet } from '@/composables/useConfirmSheet'

interface ExamLeaveGuardOptions {
  hasQuestions: () => boolean
  submitting: Ref<boolean>
  finished: Ref<boolean>
  onLeave?: () => void
}

export const leaveMessage = '离开后，本页未提交的答案会丢失，考试计时仍会继续。'

/**
 * Protects an active exam from accidental route and browser-window exits. A route
 * exit asks through `leaveSheet`, which the page renders as an LtSheet.
 */
export function useExamLeaveGuard(options: ExamLeaveGuardOptions) {
  const leaveSheet = useConfirmSheet()
  const confirming = ref(false)
  const navigationAllowed = ref(false)
  let pendingConfirmation: Promise<boolean> | undefined

  const shouldProtect = () => options.hasQuestions() && !options.finished.value && !navigationAllowed.value

  function requestLeave() {
    if (!shouldProtect()) return true
    if (options.submitting.value) return false
    if (pendingConfirmation) return pendingConfirmation

    confirming.value = true
    pendingConfirmation = leaveSheet
      .ask()
      .then((ok) => {
        if (ok) options.onLeave?.()
        return ok
      })
      .finally(() => {
        pendingConfirmation = undefined
        confirming.value = false
      })
    return pendingConfirmation
  }

  function allowNavigation() {
    navigationAllowed.value = true
  }

  watch([options.finished, navigationAllowed], ([finished, allowed]) => {
    if ((finished || allowed) && confirming.value) leaveSheet.answer(false)
  })

  const onBeforeUnload = (event: BeforeUnloadEvent) => {
    if (!shouldProtect()) return
    event.preventDefault()
    event.returnValue = ''
  }

  window.addEventListener('beforeunload', onBeforeUnload)
  onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload))

  onBeforeRouteLeave(requestLeave)
  onBeforeRouteUpdate((to, from) => {
    if (to.params.recordId === from.params.recordId) return true
    return requestLeave()
  })

  return { confirming, leaveSheet, requestLeave, allowNavigation }
}
