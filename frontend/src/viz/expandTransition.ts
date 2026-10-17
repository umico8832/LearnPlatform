import { nextTick } from 'vue'

const NAME = 'viz-stage'
/** Longest wait for the full page before the transition gives up and just cross-fades. */
const READY_TIMEOUT = 800

let announce: (() => void) | null = null
/** The embedded card the full page was expanded from; it stays in the DOM while its page is kept alive. */
let origin: HTMLElement | null = null

function canTransition(): boolean {
  return typeof document.startViewTransition === 'function'
}

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
  origin = card
  if (!card || !canTransition()) {
    void navigate()
    return
  }
  card.style.viewTransitionName = NAME
  const ready = new Promise<void>((resolve) => {
    announce = resolve
    window.setTimeout(resolve, READY_TIMEOUT)
  })
  const transition = document.startViewTransition(async () => {
    await navigate()
    await ready
    await nextTick()
  })
  void transition.finished.finally(() => {
    card.style.viewTransitionName = ''
  })
}

/**
 * Go back from the full page into the embedded player it was expanded from.
 * The full card morphs back into that player when its page is still kept
 * alive; otherwise this is a plain navigation.
 */
export function collapseInto(navigate: () => Promise<unknown>): void {
  const target = origin
  origin = null
  if (!target || !canTransition()) {
    void navigate()
    return
  }
  const transition = document.startViewTransition(async () => {
    await navigate()
    await nextTick()
    // Let the router restore the page's scroll position before the new state is captured.
    await new Promise((resolve) => window.setTimeout(resolve, 0))
    if (target.isConnected) target.style.viewTransitionName = NAME
  })
  void transition.finished.finally(() => {
    target.style.viewTransitionName = ''
  })
}
