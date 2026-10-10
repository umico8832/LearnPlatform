import { onActivated, onBeforeUnmount, onDeactivated, onMounted } from 'vue'

const ROOT_CLASS = 'lt'
let holders = 0

/**
 * Put the whole document on the new baseline (ADR-0011) while a redesigned
 * page is on screen: canvas, text colour and scrollbars follow the `.lt` tokens.
 * A page kept alive by <KeepAlive> lets go while it is cached and takes the
 * document back when it returns. Embedded components carry their own `.lt`
 * root instead and do not call this.
 */
export function useLivingTheme(): void {
  let holding = false

  function hold() {
    if (holding) return
    holding = true
    holders += 1
    if (holders === 1) document.documentElement.classList.add(ROOT_CLASS)
  }

  function release() {
    if (!holding) return
    holding = false
    holders = Math.max(0, holders - 1)
    if (holders === 0) document.documentElement.classList.remove(ROOT_CLASS)
  }

  onMounted(hold)
  onActivated(hold)
  onDeactivated(release)
  onBeforeUnmount(release)
}
