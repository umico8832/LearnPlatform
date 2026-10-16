import { computed, ref, shallowRef, watch, type Ref } from 'vue'
import { getCourseOverview, type CourseOverviewVO } from '@/api/course'
import { getKnowledgeTree, type KnowledgePointVO } from '@/api/knowledgePoint'
import { getQuestionPage, type QuestionVO } from '@/api/question'

/** The learner's own standing on one question (`learnerStatus` in the question list, B16). */
export type LearnerStatus = NonNullable<QuestionVO['learnerStatus']>

export interface CourseQuestion {
  id: number
  stem: string
  status: LearnerStatus | null
  redoDueDate: string | null
}

export interface Chapter {
  id: number
  name: string
  questions: CourseQuestion[]
}

export interface NextQuestion {
  question: CourseQuestion
  chapter: Chapter
  reason: string
}

type Status = 'loading' | 'error' | 'ready'

const PAGE_SIZE = 50
const MAX_PAGES = 10

interface Placed {
  point: KnowledgePointVO
  root: KnowledgePointVO
}

/** Depth-first catalogue order of the course's knowledge points, each with its top-level chapter. */
function flatten(tree: KnowledgePointVO[], root?: KnowledgePointVO): Placed[] {
  const sorted = [...tree].sort((a, b) => a.sortOrder - b.sortOrder)
  return sorted.flatMap((point) => [{ point, root: root ?? point }, ...flatten(point.children ?? [], root ?? point)])
}

async function loadQuestions(courseId: number): Promise<QuestionVO[]> {
  const rows: QuestionVO[] = []
  for (let pageNum = 1; pageNum <= MAX_PAGES; pageNum += 1) {
    const { data } = await getQuestionPage({ courseId, pageNum, pageSize: PAGE_SIZE, hasUnderstanding: true })
    rows.push(...data.records)
    if (pageNum >= data.pages) break
  }
  return rows
}

function toQuestion(row: QuestionVO): CourseQuestion {
  return { id: row.id, stem: row.content, status: row.learnerStatus ?? null, redoDueDate: row.redoDueDate ?? null }
}

/**
 * Questions that open the understanding page, grouped by top-level chapter. A question linked to several
 * knowledge points sits under the earliest one in the catalogue; within a chapter questions follow that order, then
 * the order they were added to the course.
 */
export function groupByChapter(points: KnowledgePointVO[], rows: QuestionVO[]): Chapter[] {
  const order = flatten(points)
  const placed = new Map(order.map((entry, rank) => [entry.point.id, { ...entry, rank }]))
  const chapters = new Map<number, Chapter>()
  for (const { root } of order) {
    if (!chapters.has(root.id)) chapters.set(root.id, { id: root.id, name: root.name, questions: [] })
  }
  const homed = rows
    .filter((row) => row.hasUnderstanding)
    .map((row) => {
      const home = row.knowledgePointIds
        .map((id) => placed.get(id))
        .filter((entry) => entry !== undefined)
        .sort((a, b) => a.rank - b.rank)[0]
      return { row, home }
    })
    .filter((item) => item.home !== undefined)
    .sort((a, b) => a.home!.rank - b.home!.rank || a.row.id - b.row.id)
  for (const { row, home } of homed) chapters.get(home!.root.id)?.questions.push(toQuestion(row))
  return [...chapters.values()].filter((chapter) => chapter.questions.length > 0)
}

function isDue(date: string | null, today: Date) {
  const match = date ? /^(\d{4})-(\d{2})-(\d{2})/.exec(date) : null
  if (!match) return false
  const [year, month, day] = match.slice(1).map(Number)
  return Date.UTC(year, month - 1, day) <= Date.UTC(today.getFullYear(), today.getMonth(), today.getDate())
}

/** A redo that is due comes first, then the first untouched question in catalogue order, then any redo. */
export function pickNext(chapters: Chapter[], today = new Date()): NextQuestion | null {
  const all = chapters.flatMap((chapter) => chapter.questions.map((question) => ({ question, chapter })))
  const due = all.find(({ question }) => question.status === 'TO_REDO' && isDue(question.redoDueDate, today))
  if (due) return { ...due, reason: '上次答错了，今天该重做' }
  const fresh = all.find(({ question }) => question.status === 'NEW')
  if (fresh) return { ...fresh, reason: `${fresh.chapter.name}的下一道` }
  const redo = all.find(({ question }) => question.status === 'TO_REDO')
  if (redo) return { ...redo, reason: '上次答错了，可以提前再做一次' }
  return null
}

export function useCourseHome(courseId: Ref<number>) {
  const status = ref<Status>('loading')
  const overview = shallowRef<CourseOverviewVO | null>(null)
  const chapters = shallowRef<Chapter[]>([])
  let version = 0

  async function load() {
    const id = courseId.value
    const current = ++version
    status.value = 'loading'
    try {
      const [summary, tree, rows] = await Promise.all([
        getCourseOverview(id, { errorDisplay: 'inline' }),
        getKnowledgeTree(id, { errorDisplay: 'inline' }),
        loadQuestions(id),
      ])
      if (current !== version) return
      overview.value = summary.data
      chapters.value = groupByChapter(tree.data, rows)
      status.value = 'ready'
    } catch {
      if (current !== version) return
      status.value = 'error'
    }
  }

  watch(courseId, load)

  const next = computed(() => pickNext(chapters.value))
  const total = computed(() => chapters.value.reduce((sum, chapter) => sum + chapter.questions.length, 0))

  return { status, overview, chapters, next, total, load }
}
