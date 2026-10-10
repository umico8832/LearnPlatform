import { onBeforeUnmount, onMounted } from 'vue'

const ROOT_CLASS = 'lt'
let holders = 0

/**
 * Put the whole document on the new baseline (ADR-0011) while a redesigned
 * page is mounted: canvas, text colour and scrollbars follow the `.lt` tokens.
 * Embedded components carry their own `.lt` root instead and do not call this.
 */
export function useLivingTheme(): void {
  onMounted(() => {
    holders += 1
    if (holders === 1) document.documentElement.classList.add(ROOT_CLASS)
  })
  onBeforeUnmount(() => {
    holders = Math.max(0, holders - 1)
    if (holders === 0) document.documentElement.classList.remove(ROOT_CLASS)
  })
}
