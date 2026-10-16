import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getAllReviewCards,
  getDueReviewCards,
  getReviewStats,
  removeFromReviewPlan,
  resetReviewProgress,
  type ReviewScheduleVO,
  type ReviewStatsVO,
} from '@/api/review'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import { errorMessage } from '@/utils/errors'

type Status = 'loading' | 'error' | 'ready'

const DUE_LIMIT = 50
const inline = { errorDisplay: 'inline' as const }

function positive(value: unknown) {
  const number = typeof value === 'string' ? Number(value) : NaN
  return Number.isInteger(number) && number > 0 ? number : undefined
}

function without(set: ReadonlySet<number>, id: number) {
  const next = new Set(set)
  next.delete(id)
  return next
}

/** Why a due card is in today's queue, from its latest FSRS rating and state (B14). */
export function dueReason(card: Pick<ReviewScheduleVO, 'lastRating' | 'schedulingState'>) {
  if (card.lastRating === 'AGAIN') return { text: '上次答错', tone: 'orange' as const }
  if (card.lastRating === 'HARD') return { text: '上次觉得难', tone: 'orange' as const }
  if (card.schedulingState === 'UNRATED') return { text: '首次复习', tone: 'none' as const }
  return { text: '按排期到期', tone: 'none' as const }
}

/** The earliest scheduled date among cards that are not due yet, for an empty queue. */
export function nextDueDate(cards: ReviewScheduleVO[], due: ReviewScheduleVO[]) {
  const dueIds = new Set(due.map((card) => card.questionId))
  return (
    cards
      .filter((card) => !dueIds.has(card.questionId) && card.availableForReview !== false && card.nextReviewDate)
      .map((card) => card.nextReviewDate)
      .sort()[0] ?? null
  )
}

/**
 * The learner's review plan: today's queue first, then every card with its schedule.
 * Course, knowledge-point and question scopes come from the route (course targets link here),
 * and every response is dropped if the page, the scope or the signed-in user changed.
 */
export function useReview() {
  const route = useRoute()
  const router = useRouter()

  const courseId = computed(() => positive(route.query.courseId))
  const questionId = computed(() => positive(route.query.questionId))
  const knowledgePointId = computed(() => positive(route.query.knowledgePointId))
  const knowledgePointName = computed(() =>
    typeof route.query.knowledgePointName === 'string' ? route.query.knowledgePointName : '',
  )
  const scoped = computed(() => Boolean(courseId.value || questionId.value || knowledgePointId.value))

  const status = ref<Status>('loading')
  const due = shallowRef<ReviewScheduleVO[]>([])
  const cards = shallowRef<ReviewScheduleVO[]>([])
  const stats = shallowRef<ReviewStatsVO | null>(null)
  const removing = ref<ReadonlySet<number>>(new Set())
  const resetting = ref<ReadonlySet<number>>(new Set())
  const rowErrors = ref<Record<number, string>>({})

  let alive = true
  let generation = 0
  function ticket() {
    const mine = generation
    const session = getAuthSessionVersion()
    return () => alive && mine === generation && session === getAuthSessionVersion()
  }

  function setRowError(id: number, message: string | null) {
    const next = { ...rowErrors.value }
    if (message) next[id] = message
    else delete next[id]
    rowErrors.value = next
  }

  async function load() {
    generation += 1
    const current = ticket()
    status.value = 'loading'
    try {
      const [dueResponse, cardsResponse, statsResponse] = await Promise.all([
        getDueReviewCards(courseId.value, DUE_LIMIT, questionId.value, knowledgePointId.value, inline),
        getAllReviewCards(courseId.value, inline),
        getReviewStats(inline),
      ])
      if (!current()) return
      due.value = dueResponse.data ?? []
      // A question scope narrows both lists; otherwise the card list stays at course scope, soonest first.
      cards.value = (cardsResponse.data ?? [])
        .filter((card) => !questionId.value || card.questionId === questionId.value)
        .sort((a, b) => (a.nextReviewDate ?? '9999').localeCompare(b.nextReviewDate ?? '9999'))
      stats.value = statsResponse.data ?? null
      status.value = 'ready'
    } catch {
      if (current()) status.value = 'error'
    }
  }

  /** Same way back into a question as the wrong page's “重做”. */
  async function open(card: ReviewScheduleVO) {
    if (card.hasUnderstanding) {
      await router.push({ name: 'QuestionUnderstanding', params: { id: card.questionId } })
      return
    }
    await router.push({
      name: 'ReviewClassic',
      query: { courseId: String(card.courseId), questionId: String(card.questionId) },
    })
  }

  async function remove(card: ReviewScheduleVO) {
    const id = card.questionId
    if (removing.value.has(id) || resetting.value.has(id)) return
    const current = ticket()
    setRowError(id, null)
    removing.value = new Set(removing.value).add(id)
    try {
      await removeFromReviewPlan(id, inline)
      if (current()) await load()
    } catch (e) {
      if (current()) setRowError(id, errorMessage(e, '没能移出这道题，请重试'))
    } finally {
      removing.value = without(removing.value, id)
    }
  }

  async function reset(card: ReviewScheduleVO) {
    const id = card.questionId
    if (removing.value.has(id) || resetting.value.has(id)) return
    const current = ticket()
    setRowError(id, null)
    resetting.value = new Set(resetting.value).add(id)
    try {
      await resetReviewProgress(id, inline)
      if (current()) await load()
    } catch (e) {
      if (current()) setRowError(id, errorMessage(e, '没能重置这道题，请重试'))
    } finally {
      resetting.value = without(resetting.value, id)
    }
  }

  async function clearScope(key: 'courseId' | 'questionId' | 'knowledgePointId') {
    const query = { ...route.query }
    delete query[key]
    if (key === 'knowledgePointId') delete query.knowledgePointName
    await router.replace({ query })
  }

  watch([courseId, questionId, knowledgePointId], () => void load())

  const unsubscribe = onAuthSessionChange(() => {
    generation += 1
    due.value = []
    cards.value = []
    stats.value = null
    rowErrors.value = {}
    status.value = 'loading'
  })
  onBeforeUnmount(() => {
    alive = false
    generation += 1
    unsubscribe()
  })

  const upcoming = computed(() => nextDueDate(cards.value, due.value))
  const courseName = computed(
    () => [...due.value, ...cards.value].find((card) => card.courseId === courseId.value)?.courseName,
  )

  return {
    courseId,
    questionId,
    knowledgePointId,
    knowledgePointName,
    courseName,
    scoped,
    status,
    due,
    cards,
    stats,
    upcoming,
    removing,
    resetting,
    rowErrors,
    load,
    open,
    remove,
    reset,
    clearScope,
  }
}
