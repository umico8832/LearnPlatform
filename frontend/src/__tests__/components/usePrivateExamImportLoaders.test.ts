import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { usePrivateExamImportLoaders } from '@/components/exam/usePrivateExamImportLoaders'
import type { PrivateExamDraft, PrivateExamStorageUsage } from '@/api/exam'
import type { CourseVO } from '@/api/course'

const { coursesApi, draftsApi, usageApi } = vi.hoisted(() => ({
  coursesApi: vi.fn(),
  draftsApi: vi.fn(),
  usageApi: vi.fn(),
}))
vi.mock('@/api/course', () => ({ getAllCourses: coursesApi }))
vi.mock('@/api/exam', () => ({ getPrivateExamDrafts: draftsApi, getPrivateExamStorageUsage: usageApi }))
function setup() {
  const courses = ref<CourseVO[]>([]),
    drafts = ref<PrivateExamDraft[]>([]),
    usage = ref<PrivateExamStorageUsage | null>(null)
  const loaders = usePrivateExamImportLoaders(courses, drafts, usage, () => true)
  return { loaders, courses, drafts, usage }
}
describe('independent private import reads', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    coursesApi.mockResolvedValue({ code: 0, data: [] })
    draftsApi.mockResolvedValue({ code: 0, data: [] })
    usageApi.mockResolvedValue({ code: 0, data: { usedBytes: 0, limitBytes: 100, fileCount: 0 } })
  })
  it('retains independent errors and clears only the read that successfully retries', async () => {
    coursesApi.mockRejectedValueOnce(new Error('课程失败'))
    draftsApi.mockRejectedValueOnce(new Error('草稿失败'))
    const { loaders } = setup()
    await Promise.all([loaders.loadCourses(), loaders.loadDrafts(), loaders.loadUsage()])
    expect(loaders).toHaveProperty('errors', { courses: '课程失败', drafts: '草稿失败', usage: '' })
    await loaders.loadCourses()
    expect(loaders).toHaveProperty('errors', { courses: '', drafts: '草稿失败', usage: '' })
    expect(loaders).toHaveProperty('loaded', { courses: true, drafts: false, usage: true })
  })
  it('ignores all late results after invalidation and resets read states', async () => {
    let resolve: (value: unknown) => void = () => undefined
    draftsApi.mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    const { loaders, drafts } = setup()
    const pending = loaders.loadDrafts()
    expect(loaders).toHaveProperty('loading.drafts', true)
    loaders.invalidate()
    resolve({ code: 0, data: [{ id: 99 }] })
    await pending
    expect(drafts.value).toEqual([])
    expect(loaders).toHaveProperty('loading.drafts', false)
  })
})
