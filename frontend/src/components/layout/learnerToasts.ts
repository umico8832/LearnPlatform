import { reactive } from 'vue'

export interface LearnerToast {
  id: number
  message: string
}

const VISIBLE_MS = 4000
const MAX_TOASTS = 3

export const toasts = reactive<LearnerToast[]>([])
const timers = new Map<number, ReturnType<typeof setTimeout>>()
let nextId = 1

export function dismissToast(id: number) {
  const index = toasts.findIndex((toast) => toast.id === id)
  if (index >= 0) toasts.splice(index, 1)
  clearTimeout(timers.get(id))
  timers.delete(id)
}

function schedule(id: number) {
  clearTimeout(timers.get(id))
  timers.set(
    id,
    setTimeout(() => dismissToast(id), VISIBLE_MS),
  )
}

/** 同一句提示正在显示时只重新计时，不叠第二条；最多同时显示三条。 */
export function showErrorToast(message: string) {
  const existing = toasts.find((toast) => toast.message === message)
  if (existing) {
    schedule(existing.id)
    return
  }
  const id = nextId++
  toasts.push({ id, message })
  while (toasts.length > MAX_TOASTS) dismissToast(toasts[0]!.id)
  schedule(id)
}
