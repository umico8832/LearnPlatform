import { computed, onBeforeUnmount, ref, type Ref } from 'vue'

interface ExamCountdownOptions {
  submitted: Ref<boolean>
  hasQuestions: () => boolean
  onExpired: () => void | Promise<void>
}

export function useExamCountdown(options: ExamCountdownOptions) {
  const remainSeconds = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null
  let deadlineMs = 0
  let serverOffsetMs = 0

  const countdownText = computed(() => {
    const minutes = Math.floor(remainSeconds.value / 60)
    const seconds = remainSeconds.value % 60
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  })

  const stop = () => {
    if (!timer) return
    clearInterval(timer)
    timer = null
  }

  const update = () => {
    remainSeconds.value = Math.max(0, Math.ceil((deadlineMs - (Date.now() + serverOffsetMs)) / 1000))
    if (remainSeconds.value > 0 || options.submitted.value) return
    stop()
    if (options.hasQuestions()) void options.onExpired()
  }

  const configure = (deadline: string, serverTime: string, requestStartedAt: number) => {
    const parsedDeadline = Date.parse(deadline)
    const parsedServerTime = Date.parse(serverTime)
    if (!Number.isFinite(parsedDeadline) || !Number.isFinite(parsedServerTime)) return false
    deadlineMs = parsedDeadline
    // 请求开始时间是服务端时间戳的保守下界，避免把响应延迟补回考试时长。
    serverOffsetMs = parsedServerTime - requestStartedAt
    return true
  }

  const start = () => {
    update()
    if (remainSeconds.value > 0 && !timer) timer = setInterval(update, 1000)
  }

  onBeforeUnmount(stop)

  return { remainSeconds, countdownText, configure, start }
}
