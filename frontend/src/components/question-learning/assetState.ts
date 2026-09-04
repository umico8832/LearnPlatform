import type { AiAssetType, AiVariantTrainingStatus } from '@/api/ai'

export interface AssetTab {
  type: AiAssetType
  label: string
  icon: string
  description: string
}

export const QUESTION_ASSET_TABS: AssetTab[] = [
  { type: 'FULL_EXPLANATION', label: '标准解析', icon: '📖', description: '包含知识点、正确答案分析、错误选项分析、关键思路和总结。' },
  { type: 'BEGINNER_EXPLANATION', label: '小白版', icon: '🌱', description: '少术语、多铺垫，用最简单的方式一步一步讲解。' },
  { type: 'STEP_BY_STEP', label: '步骤拆解', icon: '🪜', description: '将解题过程拆成明确的、可执行的步骤。' },
  { type: 'WRONG_OPTION_ANALYSIS', label: '错误选项', icon: '🎯', description: '分析每个错误选项利用了什么思维陷阱。' },
  { type: 'COMMON_MISTAKES', label: '常见误区', icon: '🚫', description: '列出学生最容易犯的错误和正确的理解。' },
  { type: 'VISUAL_INTERACTIVE', label: '可视化讲解', icon: '📊', description: '用图表、数组、树等可视化元素展示解题过程，适合算法和数据结构题目。' },
  { type: 'VARIANT', label: '变式题', icon: '🔄', description: '生成 1 道可提交、可判分的单选变式题，检验知识迁移。' },
]

const ASSET_TYPES = QUESTION_ASSET_TABS.map((tab) => tab.type)

export function createAssetContent() {
  return Object.fromEntries(ASSET_TYPES.map((type) => [type, ''])) as Record<AiAssetType, string>
}

export function createFeedbackState() {
  return Object.fromEntries(
    ASSET_TYPES.map((type) => [type, { helpful: null, comment: '' }]),
  ) as Record<AiAssetType, { helpful: boolean | null; comment: string }>
}

export interface VariantTrainingState {
  status: '' | 'STARTED' | 'COMPLETED'
  completed: boolean
  answered: boolean
  correct: boolean | null
  userAnswer: string
  correctAnswer: string
  analysis: string
  startedTime: string
  answeredTime: string
  completedTime: string
}

export function createVariantTrainingState(): VariantTrainingState {
  return {
    status: '', completed: false, answered: false, correct: null,
    userAnswer: '', correctAnswer: '', analysis: '', startedTime: '', answeredTime: '', completedTime: '',
  }
}

export function applyVariantTrainingState(target: VariantTrainingState, source: AiVariantTrainingStatus) {
  target.status = source.status
  target.completed = source.completed
  target.answered = Boolean(source.answered)
  target.correct = source.correct ?? null
  target.userAnswer = source.userAnswer || ''
  target.correctAnswer = source.correctAnswer || ''
  target.analysis = source.analysis || ''
  target.startedTime = source.startedTime || ''
  target.answeredTime = source.answeredTime || ''
  target.completedTime = source.completedTime || ''
}

export function resetVariantTrainingState(target: VariantTrainingState) {
  Object.assign(target, createVariantTrainingState())
}
