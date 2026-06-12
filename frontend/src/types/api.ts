/**
 * 统一响应结构
 */
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

/**
 * 分页响应结构
 */
export interface PageData<T = any> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

/**
 * 分页请求参数
 */
export interface PageQuery {
  page?: number
  pageSize?: number
}