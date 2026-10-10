/**
 * Analytic damped-spring core shared by the visualization stage and the
 * redesigned UI (ADR-0011). Framework-agnostic: no Vue imports here.
 *
 * Parameterised like SwiftUI's `spring(response:dampingFraction:)`:
 * `response` is the undamped period in seconds, `damping` the damping fraction
 * (1 = critically damped, < 1 = bouncy). The closed-form solution keeps
 * behaviour identical at any frame rate and preserves velocity when a target
 * changes mid-flight, which is what makes interruptions feel continuous.
 */
export interface SpringConfig {
  response: number
  damping: number
}

/** Named presets; tune a new one only when several surfaces share the need. */
export const springs = {
  /** Layout moves: nodes and panels sliding into new slots, sheets opening. */
  layout: { response: 0.55, damping: 0.86 },
  /** Pointer tips: a little overshoot so retargeting reads as a bend. */
  pointer: { response: 0.5, damping: 0.66 },
  /** Curve control points lag behind the tip to give the rubber-band look. */
  bend: { response: 0.62, damping: 0.74 },
  /** Small UI selections: segmented thumbs, pressed buttons, highlight bars. */
  snappy: { response: 0.34, damping: 0.84 },
  /** Appear, disappear and opacity. */
  fade: { response: 0.32, damping: 1 },
  /** Camera framing on the visualization stage. */
  camera: { response: 0.7, damping: 0.95 },
} as const satisfies Record<string, SpringConfig>

export type SpringPreset = keyof typeof springs

const REST_DELTA = 0.01
const REST_VELOCITY = 0.02
const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)'

let reducedMotionQuery: MediaQueryList | null | undefined

/** Live OS preference; springs jump straight to their target when it is on. */
export function prefersReducedMotion(): boolean {
  if (reducedMotionQuery === undefined) {
    reducedMotionQuery =
      typeof window !== 'undefined' && typeof window.matchMedia === 'function'
        ? window.matchMedia(REDUCED_MOTION_QUERY)
        : null
  }
  return reducedMotionQuery?.matches ?? false
}

/** Advance displacement `x` (relative to target) and velocity `v` by `dt` seconds. */
export function stepSpring(x: number, v: number, config: SpringConfig, dt: number): [number, number] {
  const omega = (2 * Math.PI) / Math.max(config.response, 0.01)
  const zeta = Math.min(Math.max(config.damping, 0), 1)

  if (zeta >= 0.999) {
    const decay = Math.exp(-omega * dt)
    const b = v + omega * x
    const nextX = (x + b * dt) * decay
    const nextV = (b - omega * (x + b * dt)) * decay
    return [nextX, nextV]
  }

  const omegaD = omega * Math.sqrt(1 - zeta * zeta)
  const decay = Math.exp(-zeta * omega * dt)
  const cos = Math.cos(omegaD * dt)
  const sin = Math.sin(omegaD * dt)
  const b = (v + zeta * omega * x) / omegaD
  const nextX = decay * (x * cos + b * sin)
  const nextV = decay * ((b * omegaD - zeta * omega * x) * cos - (x * omegaD + zeta * omega * b) * sin)
  return [nextX, nextV]
}

export class Spring {
  value: number
  velocity = 0
  target: number
  config: SpringConfig

  constructor(value: number, config: SpringConfig = springs.layout) {
    this.value = value
    this.target = value
    this.config = config
  }

  /** Retarget from the current value, keeping velocity. */
  set(target: number): void {
    this.target = target
  }

  /** Teleport without animation. */
  jump(value: number): void {
    this.value = value
    this.target = value
    this.velocity = 0
  }

  /** Add an impulse (units per second), e.g. to make a long move travel in an arc. */
  kick(velocity: number): void {
    this.velocity += velocity
  }

  get settled(): boolean {
    return Math.abs(this.value - this.target) < REST_DELTA && Math.abs(this.velocity) < REST_VELOCITY
  }

  /** Returns true while still moving. */
  step(dt: number): boolean {
    if (prefersReducedMotion()) {
      this.jump(this.target)
      return false
    }
    if (this.settled) {
      this.value = this.target
      this.velocity = 0
      return false
    }
    const [x, v] = stepSpring(this.value - this.target, this.velocity, this.config, dt)
    this.value = this.target + x
    this.velocity = v
    return true
  }
}

export class Spring2 {
  readonly x: Spring
  readonly y: Spring

  constructor(x: number, y: number, config: SpringConfig = springs.layout) {
    this.x = new Spring(x, config)
    this.y = new Spring(y, config)
  }

  set(x: number, y: number): void {
    this.x.set(x)
    this.y.set(y)
  }

  jump(x: number, y: number): void {
    this.x.jump(x)
    this.y.jump(y)
  }

  step(dt: number): boolean {
    const a = this.x.step(dt)
    const b = this.y.step(dt)
    return a || b
  }
}

/**
 * Shared requestAnimationFrame loop. Tick callbacks return true while they
 * still need frames; the loop stops itself when everything is at rest.
 */
export type TickFn = (dt: number) => boolean

const tickers = new Set<TickFn>()
let frameId = 0
let lastTime = 0

function loop(now: number): void {
  const dt = Math.min(Math.max((now - lastTime) / 1000, 0), 1 / 30)
  lastTime = now
  let busy = false
  for (const fn of [...tickers]) {
    if (fn(dt)) busy = true
    else tickers.delete(fn)
  }
  frameId = busy ? requestAnimationFrame(loop) : 0
}

/** Schedule `fn` every frame until it returns false or is unregistered. */
export function wake(fn: TickFn): void {
  tickers.add(fn)
  if (!frameId) {
    lastTime = performance.now()
    frameId = requestAnimationFrame(loop)
  }
}

export function unregister(fn: TickFn): void {
  tickers.delete(fn)
}
