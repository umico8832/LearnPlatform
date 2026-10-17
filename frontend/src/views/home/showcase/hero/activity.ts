import { inject, onMounted, onScopeDispose, watch, type InjectionKey, type Ref } from 'vue'

export const heroActive: InjectionKey<Ref<boolean>> = Symbol('heroActive')

/** Keep a scene's continuous frame work inside the visible hero. */
export function useHeroFrames(tick: (now: number) => void): void {
  const active = inject(heroActive)
  let frame = 0
  let mounted = false
  const run = (now: number) => {
    frame = 0
    if (!mounted || active?.value === false) return
    tick(now)
    frame = requestAnimationFrame(run)
  }
  const sync = () => {
    if (active?.value === false) {
      cancelAnimationFrame(frame)
      frame = 0
    } else if (mounted && !frame) frame = requestAnimationFrame(run)
  }
  if (active) watch(active, sync, { flush: 'sync' })
  onMounted(() => {
    mounted = true
    sync()
  })
  onScopeDispose(() => {
    mounted = false
    cancelAnimationFrame(frame)
  })
}
