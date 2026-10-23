import { onActivated, onBeforeUnmount, onDeactivated, onMounted } from 'vue'

export interface LivingThemeOptions {
  /** Fix the document to the dark appearance (ADR-0014: learner pages from Phase 28 on). */
  dark?: boolean
}

const holders = { lt: 0, dark: 0 }

function take(key: keyof typeof holders, apply: () => void) {
  holders[key] += 1
  if (holders[key] === 1) apply()
}

function drop(key: keyof typeof holders, remove: () => void) {
  holders[key] = Math.max(0, holders[key] - 1)
  if (holders[key] === 0) remove()
}

/**
 * Put the whole document on the new baseline (ADR-0011) while a redesigned
 * page is on screen: canvas, text colour and scrollbars follow the `.lt` tokens.
 * A page kept alive by <KeepAlive> lets go while it is cached and takes the
 * document back when it returns. Embedded components carry their own `.lt`
 * root instead and do not call this.
 */
export function useLivingTheme(options: LivingThemeOptions = {}): void {
  let holding = false

  function hold() {
    if (holding) return
    holding = true
    const root = document.documentElement
    take('lt', () => root.classList.add('lt'))
    if (options.dark) take('dark', () => root.setAttribute('data-appearance', 'dark'))
  }

  function release() {
    if (!holding) return
    holding = false
    const root = document.documentElement
    drop('lt', () => root.classList.remove('lt'))
    if (options.dark) drop('dark', () => root.removeAttribute('data-appearance'))
  }

  onMounted(hold)
  onActivated(hold)
  onDeactivated(release)
  onBeforeUnmount(release)
}
