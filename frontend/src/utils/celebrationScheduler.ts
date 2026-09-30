import type { GamificationAchievement, GamificationPracticeSummaryData } from '@/components/gamification/types'

export type CelebrationKind = 'achievement' | 'level' | 'summary'
export type CelebrationContext = 'learning' | 'exam'

export interface CelebrationRequest {
  id: string
  kind: CelebrationKind
  /** Required so callers must explicitly declare that an event is not from an exam. */
  context: CelebrationContext
  xpGained?: number
  level?: number
  achievements?: GamificationAchievement[]
  summary?: GamificationPracticeSummaryData
}

export interface CelebrationScheduler {
  enqueue: (request: CelebrationRequest) => boolean
  dismiss: () => void
  reset: () => void
  subscribe: (listener: (request: CelebrationRequest | undefined) => void) => () => void
  readonly current: () => CelebrationRequest | undefined
}

function mergeAchievements(...groups: (GamificationAchievement[] | undefined)[]) {
  const entries = groups.flatMap((group) => group ?? [])
  return Array.from(new Map(entries.map((achievement) => [achievement.id, achievement])).values())
}

function canMerge(left: CelebrationRequest, right: CelebrationRequest) {
  return left.kind === right.kind && left.kind !== 'level'
}

function merge(left: CelebrationRequest, right: CelebrationRequest): CelebrationRequest {
  return {
    ...left,
    xpGained: (left.xpGained ?? 0) + (right.xpGained ?? 0),
    achievements: mergeAchievements(left.achievements, right.achievements),
  }
}

/**
 * Serialises rare celebrations. Requests from an exam are rejected here as a second
 * line of defense; callers still decide whether a result should ever be enqueued.
 */
export function createCelebrationScheduler(): CelebrationScheduler {
  const queue: CelebrationRequest[] = []
  const listeners = new Set<(request: CelebrationRequest | undefined) => void>()
  let active: CelebrationRequest | undefined

  const publish = () => listeners.forEach((listener) => listener(active))
  const advance = () => {
    active = queue.shift()
    publish()
  }

  return {
    enqueue(request) {
      if (request.context === 'exam') return false
      const normalized = { ...request, achievements: mergeAchievements(request.achievements) }
      if (active && canMerge(active, normalized)) {
        active = merge(active, normalized)
        publish()
        return true
      }
      const pendingIndex = queue.findIndex((item) => canMerge(item, normalized))
      if (pendingIndex >= 0) queue[pendingIndex] = merge(queue[pendingIndex], normalized)
      else queue.push(normalized)
      if (!active) advance()
      return true
    },
    dismiss() {
      if (!active) return
      if (active.kind === 'achievement' && (active.achievements?.length ?? 0) > 1) {
        const [shown, ...remaining] = active.achievements!
        active = { ...active, id: `${active.id}:${shown.id}`, achievements: remaining }
        publish()
        return
      }
      advance()
    },
    reset() {
      queue.splice(0)
      active = undefined
      publish()
    },
    subscribe(listener) {
      listeners.add(listener)
      listener(active)
      return () => listeners.delete(listener)
    },
    current: () => active,
  }
}
