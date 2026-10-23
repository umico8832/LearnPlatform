type ErrorNotifier = (message: string) => void

/** 各入口注册自己的提示：管理端用 Element Plus 消息，学员端用新基线的提示条。未注册时只写控制台。 */
let errorNotifier: ErrorNotifier = (message) => {
  console.error(message)
}

export function setErrorNotifier(notifier: ErrorNotifier) {
  errorNotifier = notifier
}

export function notifyError(message: string) {
  errorNotifier(message)
}
