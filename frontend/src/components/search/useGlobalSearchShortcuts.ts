import { onBeforeUnmount, onMounted, type Ref } from 'vue'
import { useMobileViewport } from '@/composables/useMobileViewport'

export function useGlobalSearchShortcuts(visible: Ref<boolean>, open: () => void, close: () => void) {
  const isMobile = useMobileViewport()

  const inputFocused = () => {
    const active = document.activeElement
    const tag = active?.tagName?.toLowerCase()
    return tag === 'input' || tag === 'textarea' || active?.getAttribute('contenteditable') === 'true'
  }

  const handleKeydown = (event: KeyboardEvent) => {
    if ((event.metaKey || event.ctrlKey) && event.key === 'k') {
      event.preventDefault()
      if (visible.value) close()
      else open()
    } else if (event.key === '/' && !visible.value && !inputFocused()) {
      event.preventDefault()
      open()
    }
  }

  onMounted(() => window.addEventListener('keydown', handleKeydown))
  onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))

  return isMobile
}
