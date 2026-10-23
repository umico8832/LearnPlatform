import { ref, watch } from 'vue'

/**
 * A yes/no question answered in an LtSheet. `ask()` opens the sheet and resolves
 * with the answer; closing it any other way (Escape, the close button, the scrim)
 * counts as no. Asking again while open answers the earlier question with no.
 */
export function useConfirmSheet() {
  const open = ref(false)
  let settle: ((ok: boolean) => void) | null = null

  function answer(ok: boolean) {
    const done = settle
    settle = null
    open.value = false
    done?.(ok)
  }

  function ask(): Promise<boolean> {
    answer(false)
    open.value = true
    return new Promise((resolve) => {
      settle = resolve
    })
  }

  watch(open, (value) => {
    if (!value) answer(false)
  })

  return { open, ask, answer }
}

export type ConfirmSheet = ReturnType<typeof useConfirmSheet>
