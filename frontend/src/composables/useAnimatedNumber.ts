import { onUnmounted, readonly, ref, watch, type Ref } from 'vue'

export interface AnimatedNumberOptions {
  duration?: number
  reducedMotion?: Readonly<Ref<boolean>>
}

/** A cancellable numeric transition for values returned by the server. */
export function useAnimatedNumber(initialValue = 0, options: AnimatedNumberOptions = {}) {
  const value = ref(initialValue)
  const duration = options.duration ?? 420
  let frameId: number | undefined
  let targetValue = initialValue

  const stop = () => {
    if (frameId !== undefined) cancelAnimationFrame(frameId)
    frameId = undefined
  }

  const animateTo = (target: number) => {
    stop()
    targetValue = Number.isFinite(target) ? target : 0
    if (!Number.isFinite(target) || options.reducedMotion?.value || duration <= 0) {
      value.value = Number.isFinite(target) ? target : 0
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
