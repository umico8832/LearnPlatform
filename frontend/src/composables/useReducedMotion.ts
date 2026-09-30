import { onMounted, onUnmounted, readonly, ref } from 'vue'

const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)'

/** Keeps a component in sync with the user's OS-level motion preference. */
export function useReducedMotion() {
  const reducedMotion = ref(false)
  let mediaQuery: MediaQueryList | undefined

  const update = () => {
    reducedMotion.value = mediaQuery?.matches ?? false
  }

  onMounted(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return
    mediaQuery = window.matchMedia(REDUCED_MOTION_QUERY)
    update()
    mediaQuery.addEventListener('change', update)
  })

  onUnmounted(() => mediaQuery?.removeEventListener('change', update))

  return { reducedMotion: readonly(reducedMotion) }
}
