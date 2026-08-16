import request from '@/utils/request'
import type { ApiResponse, PageData } from '@/types/api'

// ======================== 类型定义 ========================

/** 练习题目 VO */
export interface PracticeQuestionVO {
  id: number
  content: string
  questionType: string
  courseId: number
  courseName: string
  difficulty: number
  score: number
  tags: string
  options: PracticeOptionVO[]
  knowledgePointIds: number[]
  knowledgePointNames: string[]
}

/** 练习选项 VO（不暴露正确答案） */
export interface PracticeOptionVO {
  id: number
  content: string
  optionLabel: string
  sortOrder: number
}

/** 提交答案请求 */
export interface PracticeSubmitRequest {
  questionId: number
  userAnswer: string
  answerTime?: number
}

/** 答题结果 */
export interface PracticeResultVO {
  recordId: number
  questionId: number
  userAnswer: string
  correct: boolean
  correctAnswer: string
  analysis: string
  score: number
}

/** 练习记录 */
export interface PracticeRecordVO {
  id: number
  questionId: number
  questionContent: string
  questionType: string
  courseName: string
  difficulty: number
  userAnswer: string
  isCorrect: number
  answerTime: number
  createTime: string
}

/** 练习统计 */
export interface PracticeStatsVO {
  totalAnswered: number
  correctCount: number
  wrongCount: number
  correctRate: number
}

// ======================== API 方法 ========================

/** 获取练习题目（随机抽取） */
export function getPracticeQuestions(params?: {
  courseId?: number
  knowledgePointId?: number
  questionType?: string
  difficulty?: number
  count?: number
}) {
  return request.get<unknown, ApiResponse<PracticeQuestionVO[]>>('/practice/questions', { params })
}

/** 提交答案 */
export function submitAnswer(data: PracticeSubmitRequest) {
  return request.post<unknown, ApiResponse<PracticeResultVO>>('/practice/submit', data)
}

/** 获取练习记录（分页） */
export function getPracticeRecords(params: {
  pageNum?: number
  pageSize?: number
  questionType?: string
  courseId?: number
  isCorrect?: number
}) {
  return request.get<unknown, ApiResponse<PageData<PracticeRecordVO>>>('/practice/records', { params })
}

/** 获取练习统计 */
export function getPracticeStats() {
  return request.get<unknown, ApiResponse<PracticeStatsVO>>('/practice/stats')
}

/** 获取错题重练题目 */
export function getWrongQuestionPractice(params?: { masteryLevel?: number; count?: number }) {
  return request.get<unknown, ApiResponse<PracticeQuestionVO[]>>('/practice/wrong-questions', { params })
}

/** 获取收藏题练习题目 */
export function getFavoritePractice(params?: { count?: number; questionId?: number }) {
  return request.get<unknown, ApiResponse<PracticeQuestionVO[]>>('/practice/favorites', { params })
}

/** 自适应智能推荐题目 */
export function getAdaptiveQuestions(params?: {
  courseId?: number
  knowledgePointId?: number
  questionType?: string
  count?: number
}) {
  return request.get<unknown, ApiResponse<PracticeQuestionVO[]>>('/practice/adaptive', { params })
}

/** 获取自适应推荐摘要（各难度答题表现和推荐权重） */
export function getAdaptiveSummary() {
  return request.get<unknown, ApiResponse<AdaptiveSummaryVO>>('/practice/adaptive/summary')
}

/** 自适应推荐摘要 VO */
export interface AdaptiveSummaryVO {
  totalAnswered: number
  overallCorrectRate: number
  recommendedDifficulty: number
  difficultyDetails: AdaptiveDifficultyDetail[]
}

/** 自适应难度详情 */
export interface AdaptiveDifficultyDetail {
  difficulty: number
  label: string
  total: number
  correct: number
  correctRate: number
  weight: number
}
