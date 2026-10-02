import { defineStore } from 'pinia'
import { onScopeDispose, ref } from 'vue'
import {
  getGamificationSummary,
  updateDailyGoal,
  type GamificationSummary,
  type RewardFeedback,
} from '@/api/gamification'
import { getAuthSessionVersion, getToken, onAuthSessionChange } from '@/utils/auth'

export const useGamificationStore = defineStore('gamification', () => {
  const summary = ref<GamificationSummary | null>(null)
  const loading = ref(false)
  const error = ref('')
  const seenEvents = new Set<number>()
  let requestVersion = 0
  let silent = false

  function reset() {
    requestVersion++
    summary.value = null
    loading.value = false
    error.value = ''
    seenEvents.clear()
  }
  onScopeDispose(onAuthSessionChange(reset))

  function applySummary(incoming: GamificationSummary) {
    if (!summary.value || incoming.version >= summary.value.version) summary.value = incoming
  }
  async function load() {
    if (!getToken() || silent) return
    const session = getAuthSessionVersion()
    const request = ++requestVersion
    loading.value = true
    error.value = ''
    try {
      const response = await getGamificationSummary()
      if (session === getAuthSessionVersion() && request === requestVersion && getToken()) applySummary(response.data)
    } catch {
      if (session === getAuthSessionVersion() && request === requestVersion) error.value = '学习进度暂时无法加载'
    } finally {
      if (session === getAuthSessionVersion() && request === requestVersion) loading.value = false
    }
  }
  async function saveGoal(goal: number) {
    const session = getAuthSessionVersion()
    const response = await updateDailyGoal(goal)
    if (session === getAuthSessionVersion() && getToken()) applySummary(response.data)
  }
  function setExamMode(value: boolean) {
    silent = value
    if (value) {
      requestVersion++
      loading.value = false
    }
  }
  function acceptReward(reward: RewardFeedback | null | undefined, session = getAuthSessionVersion()) {
    if (!reward || session !== getAuthSessionVersion() || !getToken() || silent) return false
    applySummary(reward.summary)
    if (seenEvents.has(reward.eventId)) return false
    seenEvents.add(reward.eventId)
    return true
  }
  return { summary, loading, error, load, saveGoal, acceptReward, setExamMode, reset }
})
