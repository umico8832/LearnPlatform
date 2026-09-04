import { onBeforeUnmount, onMounted, ref } from 'vue'

const MOBILE_BREAKPOINT = 768

export function useResponsiveSidebar() {
  const isMobile = ref(false)
  const sidebarOpen = ref(false)

  const syncViewport = () => {
    isMobile.value = window.innerWidth < MOBILE_BREAKPOINT
    if (!isMobile.value) sidebarOpen.value = false
  }

  onMounted(() => {
    syncViewport()
    window.addEventListener('resize', syncViewport)
  })

  onBeforeUnmount(() => window.removeEventListener('resize', syncViewport))

  return { isMobile, sidebarOpen }
}
