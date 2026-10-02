import { onUnmounted, readonly, ref } from 'vue'

const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)'

/** Keeps a component in sync with the user's OS-level motion preference. */
export function useReducedMotion() {
  const mediaQuery =
    typeof window !== 'undefined' && typeof window.matchMedia === 'function'
      ? window.matchMedia(REDUCED_MOTION_QUERY)
      : undefined
  const reducedMotion = ref(mediaQuery?.matches ?? false)

  const update = () => {
    reducedMotion.value = mediaQuery?.matches ?? false
  }

  mediaQuery?.addEventListener('change', update)

  onUnmounted(() => mediaQuery?.removeEventListener('change', update))

  return { reducedMotion: readonly(reducedMotion) }
}
