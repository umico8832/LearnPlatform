const KEY = 'examNotice'

/** History state that carries a one-off notice from the exam page to the exam list. */
export function examNoticeState(message: string) {
  return { [KEY]: message }
}

/** Reads the notice left for this history entry once, so a reload does not repeat it. */
export function takeExamNotice(): string {
  const state = window.history.state as Record<string, unknown> | null
  const message = state?.[KEY]
  if (typeof message !== 'string' || !message) return ''
  window.history.replaceState({ ...state, [KEY]: null }, '')
  return message
}
