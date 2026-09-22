import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface KnowledgeBundle {
  bundleId: number
  courseKey: string
  version: string
  manifestHash: string
  sourceRevision: string
  sourceQualityStatus: string
  reviewStatus: string
  chunkCount: number
  importedBy: number
  importedAt: string | null
  reviewedBy: number | null
  reviewedAt: string | null
  reviewNote: string | null
}
export interface KnowledgeChunk {
  id: number
  bundleId: number
  chunkId: string
  conceptId: string
  title: string
  text: string
  contentHash: string
  metadataJson: string
  sourceQualityStatus: string
}
export interface KnowledgeIndex {
  indexId: number
  bundleId: number
  indexKey: string
  model: string
  dimensions: number
  status: string
  indexedCount: number
  leaseUntil: string | null
}
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

export function getKnowledgeBundles(params: {
  courseKey?: string
  reviewStatus?: string
  pageNum: number
  pageSize: number
}) {
  return request.get<unknown, ApiResponse<PageResult<KnowledgeBundle>>>('/admin/knowledge', { params })
}
export function getKnowledgeBundle(id: number) {
  return request.get<unknown, ApiResponse<KnowledgeBundle>>(`/admin/knowledge/${id}`)
}
export function getKnowledgeChunks(id: number, params: { pageNum: number; pageSize: number; conceptId?: string }) {
  return request.get<unknown, ApiResponse<PageResult<KnowledgeChunk>>>(`/admin/knowledge/${id}/chunks`, { params })
}
export function getKnowledgeIndexes(id: number, params: { pageNum: number; pageSize: number }) {
  return request.get<unknown, ApiResponse<PageResult<KnowledgeIndex>>>(`/admin/knowledge/${id}/indexes`, { params })
}
export function reviewKnowledgeBundle(id: number, decision: 'REVIEWED' | 'WITHDRAWN', note: string) {
  return request.post<unknown, ApiResponse<void>>(`/admin/knowledge/${id}/review`, { decision, note })
}
export function withdrawKnowledgeBundle(id: number) {
  return request.post<unknown, ApiResponse<void>>(`/admin/knowledge/${id}/withdraw`)
}
