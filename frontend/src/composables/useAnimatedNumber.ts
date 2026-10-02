import { onUnmounted, readonly, ref, watch, type Ref } from 'vue'

export interface AnimatedNumberOptions {
  duration?: number
  reducedMotion?: Readonly<Ref<boolean>>
}

/** A cancellable numeric transition for values returned by the server. */
export function useAnimatedNumber(initialValue = 0, options: AnimatedNumberOptions = {}) {
  const value = ref(initialValue)
  let frameId: number | undefined
  let targetValue = initialValue
  let hasReceivedValue = false

  const stop = () => {
    if (frameId !== undefined) cancelAnimationFrame(frameId)
    frameId = undefined
  }

  const animateTo = (target: number) => {
    stop()
    if (!Number.isFinite(target)) return
    targetValue = target
    const token =
      typeof document === 'undefined'
        ? ''
        : getComputedStyle(document.documentElement).getPropertyValue('--lp-duration-feedback').trim()
    const tokenDuration = Number.parseFloat(token) * (token.endsWith('ms') ? 1 : 1000)
    const duration = options.duration ?? (Number.isFinite(tokenDuration) ? tokenDuration : 260)
    if (!hasReceivedValue || options.reducedMotion?.value || duration <= 0) {
      hasReceivedValue = true
      value.value = target
      return
    }

    const start = value.value
    const difference = target - start
    if (difference === 0) return
    const startedAt = performance.now()

    const tick = (now: number) => {
      const progress = Math.min((now - startedAt) / duration, 1)
      const eased = 1 - (1 - progress) ** 3
      value.value = progress === 1 ? target : Math.round(start + difference * eased)
      if (progress < 1) frameId = requestAnimationFrame(tick)
      else frameId = undefined
    }
    hasReceivedValue = true
    frameId = requestAnimationFrame(tick)
  }

  onUnmounted(stop)
  if (options.reducedMotion) {
    watch(options.reducedMotion, (reduced) => {
      if (reduced) {
        stop()
        value.value = targetValue
      }
    })
  }
  return { value: readonly(value), animateTo, stop }
}
