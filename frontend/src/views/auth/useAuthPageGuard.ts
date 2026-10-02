import { onBeforeUnmount } from 'vue'
import { getAuthSessionVersion } from '@/utils/auth'

export function useAuthPageGuard() {
  let alive = true
  onBeforeUnmount(() => {
    alive = false
  })
  return () => {
    const session = getAuthSessionVersion()
    return () => alive && session === getAuthSessionVersion()
  }
}
