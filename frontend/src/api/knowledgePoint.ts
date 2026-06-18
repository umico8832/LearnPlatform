import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/** 知识点 VO（含 children 用于树形结构） */
export interface KnowledgePointVO {
  id: number
  name: string
  description: string
  courseId: number
  parentId: number
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

/** 获取课程下的知识点树 */
export function getKnowledgeTree(courseId: number) {
  return request.get<any, ApiResponse<KnowledgePointVO[]>>(`/knowledge-points/tree/${courseId}`)
}

/** 创建知识点（管理端） */
export function createKnowledgePoint(data: KnowledgePointForm) {
  return request.post<any, ApiResponse<KnowledgePointVO>>('/admin/knowledge-points', data)
}

/** 更新知识点（管理端） */
export function updateKnowledgePoint(id: number, data: KnowledgePointForm) {
  return request.put<any, ApiResponse<KnowledgePointVO>>(`/admin/knowledge-points/${id}`, data)
}

/** 删除知识点（管理端） */
export function deleteKnowledgePoint(id: number) {
  return request.delete<any, ApiResponse<void>>(`/admin/knowledge-points/${id}`)
}