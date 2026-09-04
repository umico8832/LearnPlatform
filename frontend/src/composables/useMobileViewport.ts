import { onBeforeUnmount, onMounted, ref } from 'vue'

export function useMobileViewport(breakpoint = 768) {
  const isMobile = ref(window.innerWidth < breakpoint)
  const sync = () => (isMobile.value = window.innerWidth < breakpoint)

  onMounted(() => window.addEventListener('resize', sync))
  onBeforeUnmount(() => window.removeEventListener('resize', sync))

  return isMobile
}
