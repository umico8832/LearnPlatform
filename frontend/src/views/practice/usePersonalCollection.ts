import { computed, onMounted, onScopeDispose, ref, shallowRef } from 'vue'
import type { ApiResponse } from '@/types/api'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'

export function usePersonalCollection<T>(
  fetchPage: (page: number, size: number) => Promise<ApiResponse<{ records: T[]; total: number }>>,
) {
  const records = shallowRef<T[]>([])
  const total = ref<number | null>(null)
  const page = ref(1)
  const size = ref(10)
  const loading = ref(true)
  const failed = ref(false)
  let alive = true
  let requestVersion = 0

  const state = computed(() =>
    loading.value ? 'loading' : failed.value ? 'error' : records.value.length ? 'ready' : 'empty',
  )

  function captureSession() {
    const session = getAuthSessionVersion()
    return () => alive && session === getAuthSessionVersion()
  }

  async function load() {
    const currentSession = captureSession()
    const request = ++requestVersion
    const current = () => currentSession() && request === requestVersion
    loading.value = true
    failed.value = false
    total.value = null
    records.value = []
    try {
      const response = await fetchPage(page.value, size.value)
      if (!current()) return
      if (response.code !== 0 || !response.data) throw new Error('Missing collection')
      records.value = response.data.records
      total.value = response.data.total
      const lastPage = Math.max(1, Math.ceil(response.data.total / size.value))
      if (page.value > lastPage) {
        page.value = lastPage
        await load()
      }
    } catch {
      if (current()) failed.value = true
    } finally {
      if (current()) loading.value = false
    }
  }

  function resetPage() {
    page.value = 1
    return load()
  }

  onMounted(load)
  onScopeDispose(
    onAuthSessionChange(() => {
      requestVersion++
      records.value = []
      total.value = null
      failed.value = false
      loading.value = false
      page.value = 1
      if (isAuthenticated()) void load()
    }),
  )
  onScopeDispose(() => {
    alive = false
    requestVersion++
  })

  return { records, total, page, size, loading, failed, state, load, resetPage, captureSession }
}
