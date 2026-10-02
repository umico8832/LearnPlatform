import { reactive, type Ref } from 'vue'
import { getAllCourses } from '@/api/course'
import type { CourseVO } from '@/api/course'
import { getPrivateExamDrafts, getPrivateExamStorageUsage } from '@/api/exam'
import type { PrivateExamDraft, PrivateExamStorageUsage } from '@/api/exam'
import { getAuthSessionVersion } from '@/utils/auth'
import { errorMessage } from '@/utils/errors'

export function usePrivateExamImportLoaders(
  courses: Ref<CourseVO[]>,
  drafts: Ref<PrivateExamDraft[]>,
  usage: Ref<PrivateExamStorageUsage | null>,
  active: () => boolean,
) {
  const errors = reactive({ courses: '', drafts: '', usage: '' })
  const loading = reactive({ courses: false, drafts: false, usage: false })
  const loaded = reactive({ courses: false, drafts: false, usage: false })
  let coursesVersion = 0
  let draftsVersion = 0
  let usageVersion = 0
  const valid = (version: number, expected: number, session: number) =>
    active() && version === expected && session === getAuthSessionVersion()
  async function loadCourses() {
    const version = ++coursesVersion,
      session = getAuthSessionVersion()
    errors.courses = ''
    loading.courses = true
    try {
      const res = await getAllCourses({ errorDisplay: 'inline' })
      if (!valid(version, coursesVersion, session)) return
      if (res.code !== 0 || !res.data) throw new Error(res.message || '课程列表暂时无法读取。')
      courses.value = res.data
      loaded.courses = true
    } catch (cause) {
      if (valid(version, coursesVersion, session)) errors.courses = errorMessage(cause, '课程列表暂时无法读取。')
    } finally {
      if (valid(version, coursesVersion, session)) loading.courses = false
    }
  }
  async function loadDrafts() {
    const version = ++draftsVersion,
      session = getAuthSessionVersion()
    errors.drafts = ''
    loading.drafts = true
    try {
      const res = await getPrivateExamDrafts({ errorDisplay: 'inline' })
      if (!valid(version, draftsVersion, session)) return
      if (res.code !== 0 || !res.data) throw new Error(res.message || '待复核草稿暂时无法读取。')
      drafts.value = res.data
      loaded.drafts = true
    } catch (cause) {
      if (valid(version, draftsVersion, session)) errors.drafts = errorMessage(cause, '待复核草稿暂时无法读取。')
    } finally {
      if (valid(version, draftsVersion, session)) loading.drafts = false
    }
  }
  async function loadUsage() {
    const version = ++usageVersion,
      session = getAuthSessionVersion()
    errors.usage = ''
    loading.usage = true
    try {
      const res = await getPrivateExamStorageUsage({ errorDisplay: 'inline' })
      if (!valid(version, usageVersion, session)) return
      if (res.code !== 0 || !res.data) throw new Error(res.message || '原文件存储暂时无法读取。')
      usage.value = res.data
      loaded.usage = true
    } catch (cause) {
      if (valid(version, usageVersion, session)) errors.usage = errorMessage(cause, '原文件存储暂时无法读取。')
    } finally {
      if (valid(version, usageVersion, session)) loading.usage = false
    }
  }
  function invalidate() {
    coursesVersion++
    draftsVersion++
    usageVersion++
    Object.assign(errors, { courses: '', drafts: '', usage: '' })
    Object.assign(loading, { courses: false, drafts: false, usage: false })
    Object.assign(loaded, { courses: false, drafts: false, usage: false })
  }
  function invalidateDrafts() {
    draftsVersion++
    loading.drafts = false
  }
  return { loadCourses, loadDrafts, loadUsage, invalidate, invalidateDrafts, errors, loading, loaded }
}
