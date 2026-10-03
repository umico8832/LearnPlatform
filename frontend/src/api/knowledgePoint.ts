import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/** 知识点 VO（含 children 用于树形结构） */
export interface KnowledgePointVO {
  id: number
  name: string
  description: string
  courseId: number
  parentId: number
  contentKey?: string
  contentSource?: string
  contentVersion?: number
  contentReviewStatus?: string
  sortOrder: number
  createTime: string
  children?: KnowledgePointVO[]
}

/** 创建/更新知识点请求 */
export interface KnowledgePointForm {
  courseId?: number
  parentId?: number
  name: string
  description?: string
  sortOrder?: number
}

export interface KnowledgePointRequestOptions {
  errorDisplay?: 'inline'
}

/** 获取课程下的知识点树 */
export function getKnowledgeTree(courseId: number, options?: KnowledgePointRequestOptions) {
  return request.get<unknown, ApiResponse<KnowledgePointVO[]>>(
    `/knowledge-points/tree/${courseId}`,
    ...(options ? [options] : []),
  )
}

/** 创建知识点（管理端） */
export function createKnowledgePoint(data: KnowledgePointForm, options?: KnowledgePointRequestOptions) {
  return options
    ? request.post<unknown, ApiResponse<KnowledgePointVO>>('/admin/knowledge-points', data, options)
    : request.post<unknown, ApiResponse<KnowledgePointVO>>('/admin/knowledge-points', data)
}

/** 更新知识点（管理端） */
export function updateKnowledgePoint(id: number, data: KnowledgePointForm, options?: KnowledgePointRequestOptions) {
  return options
    ? request.put<unknown, ApiResponse<KnowledgePointVO>>(`/admin/knowledge-points/${id}`, data, options)
    : request.put<unknown, ApiResponse<KnowledgePointVO>>(`/admin/knowledge-points/${id}`, data)
}

/** 删除知识点（管理端） */
export function deleteKnowledgePoint(id: number, options?: KnowledgePointRequestOptions) {
  return options
    ? request.delete<unknown, ApiResponse<void>>(`/admin/knowledge-points/${id}`, options)
    : request.delete<unknown, ApiResponse<void>>(`/admin/knowledge-points/${id}`)
}
