import { computed, onScopeDispose, ref, watch, type ComputedRef } from 'vue'
import { acknowledgeAiUsageAlert, getAiUsageAlerts, type AiUsageAlert } from '@/api/aiUsage'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'

export function useOpsAlerts(isAdmin: ComputedRef<boolean>) {
  const alerts = ref<AiUsageAlert[]>([])
  const loading = ref(false)
  const error = ref('')
  const acknowledgingIds = ref(new Set<number>())
  const acknowledgeErrors = ref<Record<number, string>>({})
  let requestVersion = 0
  let mutationVersion = 0

  const count = computed(() => alerts.value.length)
  const isAcknowledging = (id: number) => acknowledgingIds.value.has(id)

  function reset() {
    requestVersion++
    mutationVersion++
    alerts.value = []
    loading.value = false
    error.value = ''
    acknowledgingIds.value = new Set()
    acknowledgeErrors.value = {}
  }

  async function refresh() {
    if (!isAdmin.value || loading.value) return
    const session = getAuthSessionVersion()
    const request = ++requestVersion
    loading.value = true
    error.value = ''
    try {
      const response = await getAiUsageAlerts(20, { errorDisplay: 'inline' })
      if (request === requestVersion && session === getAuthSessionVersion() && isAdmin.value)
        alerts.value = response.data || []
    } catch {
      if (request === requestVersion && session === getAuthSessionVersion() && isAdmin.value)
        error.value = '运营提醒暂时无法加载'
    } finally {
      if (request === requestVersion && session === getAuthSessionVersion()) loading.value = false
    }
  }

  async function acknowledge(id: number) {
    if (acknowledgingIds.value.has(id)) return
    const session = getAuthSessionVersion()
    const mutation = mutationVersion
    const current = () => mutation === mutationVersion && session === getAuthSessionVersion() && isAdmin.value
    acknowledgingIds.value = new Set(acknowledgingIds.value).add(id)
    acknowledgeErrors.value = { ...acknowledgeErrors.value, [id]: '' }
    try {
      await acknowledgeAiUsageAlert(id, { errorDisplay: 'inline' })
      if (current()) alerts.value = alerts.value.filter((alert) => alert.id !== id)
    } catch {
      if (current()) acknowledgeErrors.value = { ...acknowledgeErrors.value, [id]: '确认提醒失败，请重试' }
    } finally {
      if (current()) acknowledgingIds.value = new Set([...acknowledgingIds.value].filter((item) => item !== id))
    }
  }

  watch(
    isAdmin,
    (admin) => {
      if (admin) void refresh()
      else reset()
    },
    { immediate: true },
  )
  onScopeDispose(
    onAuthSessionChange(() => {
      reset()
      if (isAdmin.value && isAuthenticated()) void refresh()
    }),
  )

  return { alerts, loading, error, count, acknowledgeErrors, refresh, acknowledge, isAcknowledging }
}
