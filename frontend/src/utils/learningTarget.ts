/**
 * 学习目标导航：课程总览与「我的课程」共用同一套目标跳转规则，
 * 避免两处各自实现导致行为漂移。
 */
import type { Router } from 'vue-router'
import type { LearningTargetVO } from '@/api/course'

export function openLearningTarget(router: Router, courseId: number, target: LearningTargetVO) {
  if (target.type === 'TUTOR' && target.knowledgePointId) {
    void router.push({
      name: 'TutorSession',
      params: { id: courseId },
      query: { knowledgePointId: String(target.knowledgePointId) },
    })
    return
  }
  const query: Record<string, string> = { courseId: String(courseId) }
  if (target.questionId) query.questionId = String(target.questionId)
  if (target.knowledgePointId) query.knowledgePointId = String(target.knowledgePointId)
  if (target.type === 'DUE_REVIEW') {
    void router.push({ name: 'Review', query })
    return
  }
  if (target.type === 'WRONG_QUESTION') {
    void router.push({ name: 'WrongQuestions', query })
    return
  }
  void router.push({ name: 'QuestionList', query })
}
