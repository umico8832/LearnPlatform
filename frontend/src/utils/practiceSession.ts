import type { PracticeQuestionVO } from '@/api/practice'

export function clearPracticeSession(): void {
  for (const key of ['practice_questions', 'practice_mode', 'practice_user_id']) {
    sessionStorage.removeItem(key)
  }
}

export function savePracticeSession(userId: number | undefined, questions: PracticeQuestionVO[], mode = ''): boolean {
  if (!userId) return false
  sessionStorage.setItem('practice_questions', JSON.stringify(questions))
  sessionStorage.setItem('practice_mode', mode)
  sessionStorage.setItem('practice_user_id', String(userId))
  return true
}

export function loadPracticeSession(
  userId: number | undefined,
): { questions: PracticeQuestionVO[]; mode: string } | null {
  if (userId && sessionStorage.getItem('practice_user_id') === String(userId)) {
    try {
      const questions: unknown = JSON.parse(sessionStorage.getItem('practice_questions') || 'null')
      if (Array.isArray(questions) && questions.length > 0) {
        return { questions, mode: sessionStorage.getItem('practice_mode') || '' }
      }
    } catch {
      // 损坏或旧版本缓存不能阻塞重新选择练习。
    }
  }
  clearPracticeSession()
  return null
}
