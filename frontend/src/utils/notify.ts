import { ElMessage } from 'element-plus'

type ErrorNotifier = (message: string) => void

/** 管理端沿用 Element Plus 提示；学员端在入口处换成新基线的提示条。 */
let errorNotifier: ErrorNotifier = (message) => {
  ElMessage.error(message)
}

export function setErrorNotifier(notifier: ErrorNotifier) {
  errorNotifier = notifier
}

export function notifyError(message: string) {
  errorNotifier(message)
}
