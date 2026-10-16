import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getWrongQuestions,
  getWrongQuestionStats,
  removeWrongQuestion,
  updateMasteryLevel,
  type WrongQuestionStatsVO,
  type WrongQuestionVO,
} from '@/api/wrongQuestion'
import { getWrongQuestionPractice } from '@/api/practice'
import { useUserStore } from '@/stores/user'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import { errorMessage } from '@/utils/errors'
import { savePracticeSession } from '@/utils/practiceSession'

export type WrongScope = 'due' | 'all' | 'mastered'
export type ListStatus = 'loading' | 'ready' | 'error'

const PAGE_SIZE = 10
const inline = { errorDisplay: 'inline' as const }

function positive(value: unknown) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined
}

function without<T>(set: ReadonlySet<T>, value: T) {
  const next = new Set(set)
  next.delete(value)
  return next
}

/**
 * The learner's wrong questions: why each went wrong and when to redo it.
 * Course, knowledge-point and question scopes come from the route, so deep
 * links from the course space and exam review keep their meaning. Every
 * response is dropped if the page, the scope or the signed-in user changed.
 */
export function useWrongQuestions() {
  const route = useRoute()
  const router = useRouter()

  const courseId = computed(() => positive(route.query.courseId))
  const questionId = computed(() => positive(route.query.questionId))
  const knowledgePointId = computed(() => positive(route.query.knowledgePointId))
  const knowledgePointName = computed(() =>
    typeof route.query.knowledgePointName === 'string' ? route.query.knowledgePointName : '',
  )

  const scope = ref<WrongScope>('all')
  const page = ref(1)
  const status = ref<ListStatus>('loading')
  const records = shallowRef<WrongQuestionVO[]>([])
  const total = ref(0)
  const stats = shallowRef<WrongQuestionStatsVO | null>(null)

  const updating = ref<ReadonlySet<number>>(new Set())
  const removing = ref<ReadonlySet<number>>(new Set())
  const rowErrors = ref<Record<number, string>>({})
  const practicePending = ref(false)
  const practiceError = ref('')
  const redoing = ref<number | null>(null)

  let listVersion = 0
  let generation = 0
  let alive = true

  /** Captures who and what a request was for; `current()` is false once either has moved on. */
  function ticket() {
    const own = generation
    const session = getAuthSessionVersion()
    return () => alive && own === generation && session === getAuthSessionVersion()
  }

  const pages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
  const scoped = computed(
    () => courseId.value !== undefined || questionId.value !== undefined || knowledgePointId.value !== undefined,
  )

  function routeScope() {
    return { courseId: courseId.value, questionId: questionId.value, knowledgePointId: knowledgePointId.value }
  }

  /** Practice takes the route scope and mastery; it has no due filter, so "due" practises the whole scope. */
  function scopeParams() {
    return { ...routeScope(), masteryLevel: scope.value === 'mastered' ? 2 : undefined }
  }

  async function loadList() {
    const version = ++listVersion
    const current = ticket()
    status.value = 'loading'
    try {
      // Soonest redo first, so what is due today leads even the full list.
      const response = await getWrongQuestions(
        {
          pageNum: page.value,
          pageSize: PAGE_SIZE,
          ...scopeParams(),
          dueOnly: scope.value === 'due' || undefined,
          sortBy: 'nextReviewDate',
        },
        inline,
      )
      if (!current() || version !== listVersion) return
      records.value = response.data?.records ?? []
      total.value = response.data?.total ?? 0
      status.value = 'ready'
    } catch {
      if (current() && version === listVersion) status.value = 'error'
    }
  }

  async function loadStats() {
    const current = ticket()
    try {
      const response = await getWrongQuestionStats(routeScope(), inline)
      if (current()) stats.value = response.data
    } catch {
      // The totals are secondary; the list stays usable without them.
    }
  }

  function load() {
    void loadList()
    void loadStats()
  }

  function setRowError(id: number, message: string | null) {
    const next = { ...rowErrors.value }
    if (message) next[id] = message
    else delete next[id]
    rowErrors.value = next
  }

  async function setMastery(item: WrongQuestionVO, level: number) {
    if (updating.value.has(item.id) || item.masteryLevel === level) return
    const current = ticket()
    setRowError(item.id, null)
    updating.value = new Set(updating.value).add(item.id)
    try {
      await updateMasteryLevel(item.id, level, inline)
      if (!current()) return
      records.value = records.value.map((row) => (row.id === item.id ? { ...row, masteryLevel: level } : row))
      void loadStats()
    } catch (e) {
      if (current()) setRowError(item.id, errorMessage(e, '没能更新掌握程度，请重试'))
    } finally {
      if (current()) updating.value = without(updating.value, item.id)
    }
  }

  async function remove(item: WrongQuestionVO) {
    if (removing.value.has(item.id)) return
    const current = ticket()
    setRowError(item.id, null)
    removing.value = new Set(removing.value).add(item.id)
    try {
      await removeWrongQuestion(item.id, inline)
      if (!current()) return
      // The last row of a later page: step back so the page is not left empty.
      if (records.value.length === 1 && page.value > 1) page.value -= 1
      else load()
    } catch (e) {
      if (current()) setRowError(item.id, errorMessage(e, '没能移出这道题，请重试'))
    } finally {
      if (current()) removing.value = without(removing.value, item.id)
    }
  }

  /** Open a practice session on the old practice page with the given wrong questions. */
  async function practise(params: Parameters<typeof getWrongQuestionPractice>[0], onEmpty: string) {
    const current = ticket()
    const userId = useUserStore().userInfo?.id
    const response = await getWrongQuestionPractice(params, inline)
    if (!current()) return null
    const questions = response.data ?? []
    if (questions.length === 0) return onEmpty
    if (userId === undefined || userId !== useUserStore().userInfo?.id) return null
    if (!savePracticeSession(userId, questions, 'wrong_question')) return '没能打开练习，请重试'
    await router.push({ name: 'PracticeSession' })
    return null
  }

  async function startPractice() {
    if (practicePending.value) return
    practicePending.value = true
    practiceError.value = ''
    const current = ticket()
    try {
      const problem = await practise({ count: 10, ...scopeParams() }, '这个范围里暂时没有可以重练的题。')
      if (current() && problem) practiceError.value = problem
    } catch (e) {
      if (current()) practiceError.value = errorMessage(e, '没能取到重练题目，请重试')
    } finally {
      if (current()) practicePending.value = false
    }
  }

  async function redo(item: WrongQuestionVO) {
    if (redoing.value !== null) return
    // Main-line questions come back to the tutor page; a fresh attempt there is rated like any independent answer.
    if (item.hasUnderstanding) {
      await router.push({ name: 'QuestionUnderstanding', params: { id: item.questionId } })
      return
    }
    redoing.value = item.id
    setRowError(item.id, null)
    const current = ticket()
    try {
      const problem = await practise(
        { count: 1, questionId: item.questionId, courseId: item.courseId },
        '这道题暂时不能重做，可能缺少判分依据。',
      )
      if (current() && problem) setRowError(item.id, problem)
    } catch (e) {
      if (current()) setRowError(item.id, errorMessage(e, '没能打开这道题，请重试'))
    } finally {
      if (current()) redoing.value = null
    }
  }

  async function clearScope(key: 'courseId' | 'questionId' | 'knowledgePointId') {
    const query = { ...route.query }
    delete query[key]
    if (key === 'knowledgePointId') delete query.knowledgePointName
    await router.replace({ query })
  }

  watch(scope, () => {
    if (page.value === 1) void loadList()
    else page.value = 1
  })
  watch(page, () => void loadList())
  watch([courseId, questionId, knowledgePointId], () => {
    if (page.value === 1) load()
    else page.value = 1
  })

  const unsubscribe = onAuthSessionChange(() => {
    generation += 1
    records.value = []
    total.value = 0
    stats.value = null
    rowErrors.value = {}
    updating.value = new Set()
    removing.value = new Set()
    practicePending.value = false
    practiceError.value = ''
    redoing.value = null
    status.value = 'loading'
  })
  onBeforeUnmount(() => {
    alive = false
    generation += 1
    unsubscribe()
  })

  return {
    courseId,
    questionId,
    knowledgePointId,
    knowledgePointName,
    scoped,
    scope,
    page,
    pages,
    status,
    records,
    total,
    stats,
    updating,
    removing,
    rowErrors,
    practicePending,
    practiceError,
    redoing,
    load,
    loadList,
    setMastery,
    remove,
    startPractice,
    redo,
    clearScope,
  }
}

export type WrongQuestions = ReturnType<typeof useWrongQuestions>
