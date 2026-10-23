import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { AskError, getAskStatus, stopAsk, streamAsk, type AskRecord, type AskStatus } from '@/api/questionAsk'
import { getAuthSessionVersion } from '@/utils/auth'

export const ASK_MAX_LENGTH = 1000

/** What the learner reads when an ask fails; the server's own message is the fallback. */
export function askErrorText(error: unknown): string {
  if (error instanceof AskError) {
    switch (error.code) {
      case 1001:
        return `问题要在 1 到 ${ASK_MAX_LENGTH} 个字之间。`
      case 1006:
        return '今天的 AI 次数用完了，明天再来。也可以继续按提示往下走。'
      case 1007:
        return error.message
      case 1010:
        return '追问暂时不可用。'
      case 1011:
        return '这道题的追问次数用完了，可以继续按提示往下走。'
      case 1012:
        return '这次没有得到回答，已计入次数。可以换个说法再问。'
      case 1013:
        return '回答超时了，已计入次数。可以再问一次。'
      case 1014:
        return '已停止，这次已计入次数。'
      default:
        return error.message
    }
  }
  return '没能连上服务，请检查网络后重试。'
}

interface Running {
  requestId: string
  message: string
  content: string
}

/**
 * Free-form questions about the current attempt. The entry stays hidden unless the server
 * says asking is available, and the page never depends on this request succeeding. Every
 * response is dropped if the attempt or the signed-in user changed meanwhile.
 */
export function useQuestionAsk(attemptId: () => number | null) {
  const status = ref<'idle' | 'loading' | 'ready' | 'hidden'>('idle')
  const info = shallowRef<AskStatus | null>(null)
  const records = shallowRef<AskRecord[]>([])
  const running = ref<Running | null>(null)
  const error = ref('')
  let controller: AbortController | null = null
  let generation = 0
  let alive = true

  function ticket() {
    const mine = generation
    const session = getAuthSessionVersion()
    return () => alive && mine === generation && session === getAuthSessionVersion()
  }

  async function load() {
    const id = attemptId()
    generation += 1
    controller?.abort()
    running.value = null
    error.value = ''
    if (!id) {
      status.value = 'idle'
      return
    }
    const current = ticket()
    status.value = 'loading'
    try {
      const response = await getAskStatus(id)
      if (!current()) return
      info.value = response.data
      records.value = response.data.records ?? []
      status.value = response.data.available ? 'ready' : 'hidden'
    } catch {
      if (current()) status.value = 'hidden'
    }
  }

  async function send(message: string) {
    const id = attemptId()
    const text = message.trim()
    if (!id || running.value || !text) return false
    const current = ticket()
    error.value = ''
    const requestId = crypto.randomUUID()
    running.value = { requestId, message: text, content: '' }
    controller = new AbortController()
    try {
      const done = await streamAsk(
        id,
        { requestId, message: text },
        { onContent: (chunk) => current() && running.value && (running.value.content += chunk) },
        controller.signal,
      )
      if (!current() || !running.value) return true
      const record: AskRecord = {
        requestId,
        attemptId: id,
        message: text,
        content: running.value.content,
        status: 'SUCCEEDED',
        errorCode: null,
        createdAt: new Date().toISOString(),
        suggestion: done.suggestion,
      }
      records.value = [...records.value, record]
      if (info.value) {
        info.value = { ...info.value, questionRemaining: done.questionRemaining, dailyRemaining: done.dailyRemaining }
      }
      running.value = null
      return true
    } catch (cause) {
      if (!current()) return false
      const stopped = cause instanceof DOMException && cause.name === 'AbortError'
      error.value = stopped ? askErrorText(new AskError(1014, '')) : askErrorText(cause)
      running.value = null
      // The server keeps the failed or stopped record and the count; read them back.
      await refresh()
      return false
    } finally {
      controller = null
    }
  }

  async function stop() {
    const id = attemptId()
    const requestId = running.value?.requestId
    controller?.abort()
    if (id && requestId) await stopAsk(id, requestId).catch(() => undefined)
  }

  async function refresh() {
    const id = attemptId()
    if (!id) return
    const current = ticket()
    try {
      const response = await getAskStatus(id)
      if (!current()) return
      info.value = response.data
      records.value = response.data.records ?? []
    } catch {
      /* Keep what is on screen; the error line already says what happened. */
    }
  }

  watch(attemptId, () => void load(), { immediate: true })
  onBeforeUnmount(() => {
    alive = false
    generation += 1
    controller?.abort()
  })

  const used = computed(() => records.value.length > 0 || running.value !== null)
  const exhausted = computed(() => (info.value?.questionRemaining ?? 0) <= 0 || (info.value?.dailyRemaining ?? 1) <= 0)

  return { status, info, records, running, error, used, exhausted, send, stop, load }
}
