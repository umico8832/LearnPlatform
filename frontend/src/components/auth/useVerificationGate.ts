import { onBeforeUnmount, ref } from 'vue'

/**
 * One pending human-verification request at a time: `verify()` opens the challenge and
 * resolves with a token, or null once cancelled. Shared by the old dialog and the
 * new-baseline sheet so both keep the same contract.
 */
export function useVerificationGate() {
  const visible = ref(false)
  const failed = ref(false)
  const attempt = ref(0)
  let pending: Promise<string | null> | undefined
  let resolveVerification: ((token: string | null) => void) | undefined
  let disposed = false
  let returnFocus: HTMLElement | undefined

  function verify(trigger?: HTMLElement): Promise<string | null> {
    if (disposed) return Promise.resolve(null)
    if (pending) return pending
    returnFocus = trigger
    pending = new Promise((resolve) => {
      resolveVerification = resolve
    })
    failed.value = false
    attempt.value++
    visible.value = true
    return pending
  }

  function finish(token: string | null) {
    visible.value = false
    const resolve = resolveVerification
    resolveVerification = undefined
    pending = undefined
    resolve?.(token)
  }

  function onVerified(token: string) {
    if (token && visible.value) finish(token)
  }

  function retry() {
    failed.value = false
    attempt.value++
  }

  /** The element that asked for verification, for the caller that restores focus itself. */
  function takeReturnFocus() {
    const target = returnFocus
    returnFocus = undefined
    return target
  }

  function restoreFocus() {
    requestAnimationFrame(() => {
      if (visible.value || disposed) return
      const target = takeReturnFocus()
      if (target?.isConnected) target.focus({ preventScroll: true })
    })
  }

  onBeforeUnmount(() => {
    disposed = true
    finish(null)
  })

  return { visible, failed, attempt, verify, finish, onVerified, retry, restoreFocus, takeReturnFocus }
}
