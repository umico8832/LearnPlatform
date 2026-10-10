import { nextTick } from 'vue'
import { prefersReducedMotion } from '@/motion'

const NAME = 'viz-stage'
/** Longest wait for the full page before the transition gives up and just cross-fades. */
const READY_TIMEOUT = 800

let announce: (() => void) | null = null

/** Called by a full-size player once its first frame is on screen. */
export function announceFullPlayerReady(): void {
  announce?.()
  announce = null
}

/**
 * Expand an embedded player into its full page. The card morphs into the full
 * page's card with a native view transition; the scene loads asynchronously,
 * so the new state is captured only after the full player reports ready.
 */
export function expandInto(card: HTMLElement | null, navigate: () => Promise<unknown>): void {
  if (!card || typeof document.startViewTransition !== 'function' || prefersReducedMotion()) {
    void navigate()
    return
  }
  card.style.viewTransitionName = NAME
  const ready = new Promise<void>((resolve) => {
    announce = resolve
    window.setTimeout(resolve, READY_TIMEOUT)
  })
  document.startViewTransition(async () => {
    await navigate()
    await ready
    await nextTick()
  })
}
