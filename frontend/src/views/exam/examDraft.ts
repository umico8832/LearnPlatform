const STORAGE_PREFIX = 'lp:exam-draft:'
const DRAFT_VERSION = 1
const MAX_ANSWER_COUNT = 500
const MAX_ANSWER_LENGTH = 8_000

export interface ExamDraftInput {
  userId: number
  recordId: number
  answers: Record<number, string>
  currentQuestionId: number | null
}

export interface ExamDraftLoadInput {
  userId: number
  recordId: number
  questionIds: number[]
}

interface StoredExamDraft {
  version: number
  userId: number
  recordId: number
  answers: Record<string, string>
  currentQuestionId: number | null
}

export interface RestoredExamDraft {
  answers: Record<number, string>
  currentQuestionId: number | null
}

function isPositiveInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

function draftKey(userId: number, recordId: number) {
  return `${STORAGE_PREFIX}${userId}:${recordId}`
}

function isAnswerMap(value: unknown): value is Record<string, string> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const entries = Object.entries(value)
  return (
    entries.length <= MAX_ANSWER_COUNT &&
    entries.every(
      ([questionId, answer]) =>
        /^\d+$/.test(questionId) && typeof answer === 'string' && answer.length <= MAX_ANSWER_LENGTH,
    )
  )
}

function isStoredDraft(value: unknown, userId: number, recordId: number): value is StoredExamDraft {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const candidate = value as Partial<StoredExamDraft>
  return (
    candidate.version === DRAFT_VERSION &&
    candidate.userId === userId &&
    candidate.recordId === recordId &&
    isAnswerMap(candidate.answers) &&
    (candidate.currentQuestionId === null || isPositiveInteger(candidate.currentQuestionId))
  )
}

export function saveExamDraft(draft: ExamDraftInput): boolean {
  if (!isPositiveInteger(draft.userId) || !isPositiveInteger(draft.recordId) || !isAnswerMap(draft.answers))
    return false
  if (draft.currentQuestionId !== null && !isPositiveInteger(draft.currentQuestionId)) return false
  const payload: StoredExamDraft = {
    version: DRAFT_VERSION,
    userId: draft.userId,
    recordId: draft.recordId,
    answers: draft.answers,
    currentQuestionId: draft.currentQuestionId,
  }
  try {
    sessionStorage.setItem(draftKey(draft.userId, draft.recordId), JSON.stringify(payload))
    return true
  } catch {
    return false
  }
}

export function loadExamDraft(input: ExamDraftLoadInput): RestoredExamDraft | null {
  if (!isPositiveInteger(input.userId) || !isPositiveInteger(input.recordId)) return null
  const activeQuestionIds = new Set(input.questionIds.filter(isPositiveInteger))
  if (!activeQuestionIds.size) return null
  const key = draftKey(input.userId, input.recordId)
  try {
    const raw = sessionStorage.getItem(key)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    if (!isStoredDraft(parsed, input.userId, input.recordId)) {
      sessionStorage.removeItem(key)
      return null
    }
    const answers = Object.fromEntries(
      Object.entries(parsed.answers)
        .map(([questionId, answer]) => [Number(questionId), answer] as const)
        .filter(([questionId]) => activeQuestionIds.has(questionId)),
    )
    return {
      answers,
      currentQuestionId:
        parsed.currentQuestionId !== null && activeQuestionIds.has(parsed.currentQuestionId)
          ? parsed.currentQuestionId
          : null,
    }
  } catch {
    return null
  }
}

export function clearExamDraft(userId: number, recordId: number): void {
  if (!isPositiveInteger(userId) || !isPositiveInteger(recordId)) return
  try {
    sessionStorage.removeItem(draftKey(userId, recordId))
  } catch {
    // Storage availability must not block the active exam.
  }
}

export function clearExamDraftsForRecord(recordId: number): void {
  if (!isPositiveInteger(recordId)) return
  try {
    const suffix = `:${recordId}`
    for (let index = sessionStorage.length - 1; index >= 0; index--) {
      const key = sessionStorage.key(index)
      if (key?.startsWith(STORAGE_PREFIX) && key.endsWith(suffix)) sessionStorage.removeItem(key)
    }
  } catch {
    // Storage availability must not block the active exam.
  }
}

export function clearAllExamDrafts(): void {
  try {
    for (let index = sessionStorage.length - 1; index >= 0; index--) {
      const key = sessionStorage.key(index)
      if (key?.startsWith(STORAGE_PREFIX)) sessionStorage.removeItem(key)
    }
  } catch {
    // Storage availability must not block the active exam.
  }
}
