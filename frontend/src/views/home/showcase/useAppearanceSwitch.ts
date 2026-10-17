import { onBeforeUnmount, onMounted, ref } from 'vue'
import { springEasing, springs } from '@/motion'

const STORAGE_KEY = 'lp-home-appearance'
type Appearance = 'light' | 'dark'

function stored(): Appearance | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null
  }
}

function remember(value: Appearance) {
  try {
    localStorage.setItem(STORAGE_KEY, value)
  } catch {
    /* A private window keeps the choice for this visit only. */
  }
}

/**
 * Light/dark switch for the public homepage. It opens in `fallback` (dark) unless the
 * visitor chose otherwise before, and repaints from the button outwards with a view
 * transition where the browser has one. Leaving the page hands the document back.
 */
export function useAppearanceSwitch(fallback: Appearance = 'dark') {
  const dark = ref(fallback === 'dark')
  const root = () => document.documentElement

  function set(next: Appearance) {
    root().dataset.appearance = next
    dark.value = next === 'dark'
  }

  async function toggle(event: MouseEvent) {
    const next: Appearance = dark.value ? 'light' : 'dark'
    remember(next)
    if (typeof document.startViewTransition !== 'function') {
      set(next)
      return
    }
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
    const x = rect.left + rect.width / 2
    const y = rect.top + rect.height / 2
    const radius = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y))
    root().classList.add('lt-appearance-switch')
    const transition = document.startViewTransition(() => set(next))
    try {
      await transition.ready
      const { easing, duration } = springEasing(springs.camera)
      root().animate(
        { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`] },
        { duration, easing, pseudoElement: '::view-transition-new(root)' },
      )
      await transition.finished
    } finally {
      root().classList.remove('lt-appearance-switch')
    }
  }

  onMounted(() => set(stored() ?? fallback))
  onBeforeUnmount(() => {
    delete root().dataset.appearance
  })

  return { dark, toggle }
}
