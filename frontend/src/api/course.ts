import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/** 课程 VO */
export interface CourseVO {
  id: number
  name: string
  description: string
  coverImage: string | null
  sortOrder: number
  status: number
  createTime: string
}

/** 创建/更新课程请求 */
export interface CourseForm {
  name: string
  description?: string
  sortOrder?: number
}

/** 分页结果 */
interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** 获取所有启用课程（不分页） */
export function getAllCourses() {
  return request.get<ApiResponse<CourseVO[]>>('/courses/list')
}

/** 获取课程分页 */
export function getCoursePage(params: { pageNum?: number; pageSize?: number; keyword?: string }) {
  return request.get<ApiResponse<PageResult<CourseVO>>>('/courses', { params })
}

/** 获取课程详情 */
export function getCourseById(id: number) {
  return request.get<ApiResponse<CourseVO>>(`/courses/${id}`)
}

/** 创建课程（管理端） */
export function createCourse(data: CourseForm) {
  return request.post<ApiResponse<CourseVO>>('/admin/courses', data)
}

/** 更新课程（管理端） */
export function updateCourse(id: number, data: CourseForm) {
  return request.put<ApiResponse<CourseVO>>(`/admin/courses/${id}`, data)
}

/** 删除课程（管理端） */
export function deleteCourse(id: number) {
  return request.delete<ApiResponse<void>>(`/admin/courses/${id}`)
}