import { prefersReducedMotion, springs, stepSpring, type SpringConfig } from './spring'

export interface SpringEasing {
  /** CSS `linear()` easing that traces the spring from 0 to 1. */
  easing: string
  /** Milliseconds until the spring settles. */
  duration: number
}

const SAMPLE_DT = 1 / 120
const MAX_SECONDS = 1.6
const MAX_POINTS = 48

const cache = new Map<string, SpringEasing>()

/**
 * Convert a spring into a CSS `linear()` easing so CSS transitions and WAAPI
 * animations share the same physics as the stage. Overshoot is kept, so a
 * bouncy spring yields values above 1.
 */
export function springEasing(config: SpringConfig = springs.snappy): SpringEasing {
  const key = `${config.response}:${config.damping}`
  const cached = cache.get(key)
  if (cached) return cached

  let x = 1
  let v = 0
  let t = 0
  const values: number[] = [0]
  while (t < MAX_SECONDS) {
    ;[x, v] = stepSpring(x, v, config, SAMPLE_DT)
    t += SAMPLE_DT
    values.push(1 - x)
    if (Math.abs(x) < 0.001 && Math.abs(v) < 0.01) break
  }
  values.push(1)
  const stride = Math.max(1, Math.floor(values.length / MAX_POINTS))
  const points = values
    .filter((_, i) => i % stride === 0 || i === values.length - 1)
    .map((n) => String(Number(n.toFixed(4))))
  const result = { easing: `linear(${points.join(', ')})`, duration: Math.round(t * 1000) }
  cache.set(key, result)
  return result
}

/** Animate with spring physics; returns null (no animation) under reduced motion. */
export function springAnimate(
  target: Element,
  keyframes: Keyframe[],
  config: SpringConfig = springs.snappy,
  options: KeyframeAnimationOptions = {},
): Animation | null {
  if (prefersReducedMotion() || typeof target.animate !== 'function') return null
  const { easing, duration } = springEasing(config)
  return target.animate(keyframes, { duration, easing, ...options })
}
