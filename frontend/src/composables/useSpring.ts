import { onScopeDispose, readonly, shallowRef, type Ref } from 'vue'
import { Spring, springs, unregister, wake, type SpringConfig } from '@/motion'

export interface SpringHandle {
  /** Animated value; updates every frame while the spring moves. */
  readonly value: Readonly<Ref<number>>
  /** Current velocity in units per second, e.g. to stretch a moving thumb. */
  readonly velocity: Readonly<Ref<number>>
  /** Retarget from wherever the value is now, keeping its velocity. */
  set(target: number): void
  /** Move instantly, e.g. for the first layout measurement. */
  jump(value: number): void
}

/** Vue binding for one spring on the shared frame loop. Stops with the owning scope. */
export function useSpring(initial = 0, config: SpringConfig = springs.snappy): SpringHandle {
  const spring = new Spring(initial, config)
  const value = shallowRef(initial)
  const velocity = shallowRef(0)

  const tick = (dt: number) => {
    const moving = spring.step(dt)
    value.value = spring.value
    velocity.value = moving ? spring.velocity : 0
    return moving
  }

  onScopeDispose(() => unregister(tick))

  return {
    value: readonly(value),
    velocity: readonly(velocity),
    set(target) {
      if (target === spring.target && spring.settled) return
      spring.set(target)
      wake(tick)
    },
    jump(next) {
      spring.jump(next)
      value.value = next
      velocity.value = 0
    },
  }
}
