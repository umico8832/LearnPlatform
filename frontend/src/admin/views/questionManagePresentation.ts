export type ElementTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const QUESTION_TYPE_LABELS: Record<string, string> = {
  SINGLE_CHOICE: '单选',
  MULTIPLE_CHOICE: '多选',
  TRUE_FALSE: '判断',
  FILL_BLANK: '填空',
  SHORT_ANSWER: '简答',
}

const QUESTION_TYPE_TAGS: Record<string, ElementTagType> = {
  SINGLE_CHOICE: 'primary',
  MULTIPLE_CHOICE: 'success',
  TRUE_FALSE: 'warning',
  FILL_BLANK: 'info',
  SHORT_ANSWER: 'danger',
}

const SOURCE_TYPE_LABELS: Record<string, string> = {
  MANUAL: '手动创建',
  SUBMISSION: '投稿入库',
  EXCEL_IMPORT: 'Excel导入',
  MARKDOWN_IMPORT: 'MD导入',
  AI_GENERATED: 'AI生成',
}

const SOURCE_TYPE_TAGS: Record<string, ElementTagType> = {
  MANUAL: 'primary',
  SUBMISSION: 'success',
  EXCEL_IMPORT: 'warning',
  MARKDOWN_IMPORT: 'info',
  AI_GENERATED: 'danger',
}

const REVIEW_ACTION_LABELS: Record<string, string> = {
  APPROVE: '通过',
  REVISE: '修订',
  REJECT: '废弃',
}

const REPORT_TYPE_LABELS: Record<string, string> = {
  CONTENT: '题干问题',
  ANSWER: '答案问题',
  ANALYSIS: '解析问题',
  KNOWLEDGE_POINT: '知识点问题',
  OTHER: '其他问题',
}

const CORRECTION_STATUS_LABELS: Record<string, string> = {
  OPEN: '待处理',
  RESOLVED: '已处理',
  REJECTED: '已驳回',
}

const CORRECTION_STATUS_TAGS: Record<string, ElementTagType> = {
  OPEN: 'warning',
  RESOLVED: 'success',
  REJECTED: 'info',
}

const CHANGE_TYPE_LABELS: Record<string, string> = {
  CREATE: '创建',
  UPDATE: '更新',
  DELETE: '删除',
  REVIEW_APPROVE: '复审通过',
  REVIEW_REVISE: '复审修订',
  REVIEW_REJECT: '复审废弃',
}

const CHANGE_TYPE_TAGS: Record<string, ElementTagType> = {
  CREATE: 'success',
  UPDATE: 'primary',
  DELETE: 'danger',
  REVIEW_APPROVE: 'success',
  REVIEW_REVISE: 'warning',
  REVIEW_REJECT: 'danger',
}

export const questionTypeLabel = (type: string) => QUESTION_TYPE_LABELS[type] || type
export const questionTypeTag = (type: string): ElementTagType => QUESTION_TYPE_TAGS[type] || 'primary'
export const sourceTypeLabel = (type?: string) => SOURCE_TYPE_LABELS[type || ''] || '手动创建'
export const sourceTypeTag = (type?: string): ElementTagType => SOURCE_TYPE_TAGS[type || ''] || 'info'
export const reviewActionLabel = (action: string) => REVIEW_ACTION_LABELS[action] || action
export const reportTypeLabel = (type: string) => REPORT_TYPE_LABELS[type] || type
export const correctionStatusLabel = (status: string) => CORRECTION_STATUS_LABELS[status] || status
export const correctionStatusTag = (status: string): ElementTagType => CORRECTION_STATUS_TAGS[status] || 'info'
export const changeTypeLabel = (type: string) => CHANGE_TYPE_LABELS[type] || type
export const changeTypeTag = (type: string): ElementTagType => CHANGE_TYPE_TAGS[type] || 'info'

export function formatQuestionSnapshot(snapshot?: string) {
  if (!snapshot) return ''
  try {
    return JSON.stringify(JSON.parse(snapshot), null, 2)
  } catch {
    return snapshot
  }
}
