import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAdminSubmissions,
  getSubmissionStats,
  importSubmission,
  reviewSubmission,
  type QuestionSubmissionVO,
  type SubmissionStats,
} from '@/api/submission'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
import { errorMessage } from '@/utils/errors'

export function useSubmissionAdminList() {
  const loading = ref(false),
    listError = ref(''),
    loaded = ref(false)
  const statsLoading = ref(false),
    statsError = ref('')
  const stats = ref<SubmissionStats | null>(null)
  const submissions = ref<QuestionSubmissionVO[]>([]),
    selectedSubmissions = ref<QuestionSubmissionVO[]>([])
  const statusFilter = ref<number | undefined>(),
    keywordFilter = ref(''),
    pageNum = ref(1),
    total = ref(0)
  const pageSize = 10
  const bulkAction = ref<'approve' | 'import' | null>(null)
  const actionMessage = ref(''),
    actionFailed = ref(false)
  let alive = true,
    listVersion = 0,
    statsVersion = 0,
    actionVersion = 0
  const current = (session: number) => alive && session === getAuthSessionVersion()

  async function loadSubmissions() {
    const version = ++listVersion,
      session = getAuthSessionVersion()
    loading.value = true
    listError.value = ''
    try {
      const res = await getAdminSubmissions(
        { pageNum: pageNum.value, pageSize, status: statusFilter.value, keyword: keywordFilter.value || undefined },
        { errorDisplay: 'inline' },
      )
      if (!current(session) || version !== listVersion) return
      if (res.code !== 0 || !res.data) throw new Error(res.message || '投稿列表暂时无法读取。')
      submissions.value = res.data.records
      total.value = res.data.total
      selectedSubmissions.value = []
      loaded.value = true
    } catch (cause) {
      if (current(session) && version === listVersion)
        listError.value = errorMessage(cause, '投稿列表暂时无法读取，请重试。')
    } finally {
      if (current(session) && version === listVersion) loading.value = false
    }
  }
  async function loadStats() {
    const version = ++statsVersion,
      session = getAuthSessionVersion()
    statsLoading.value = true
    statsError.value = ''
    try {
      const res = await getSubmissionStats({ errorDisplay: 'inline' })
      if (!current(session) || version !== statsVersion) return
      if (res.code !== 0 || !res.data) throw new Error(res.message || '投稿统计暂时无法读取。')
      stats.value = res.data
    } catch (cause) {
      if (current(session) && version === statsVersion)
        statsError.value = errorMessage(cause, '投稿统计暂时无法读取，请重试。')
    } finally {
      if (current(session) && version === statsVersion) statsLoading.value = false
    }
  }
  async function refreshSubmissions() {
    await Promise.all([loadSubmissions(), loadStats()])
  }
  function handlePageChange(page: number) {
    pageNum.value = page
    void loadSubmissions()
  }
  function handleSubmissionSelectionChange(selection: QuestionSubmissionVO[]) {
    if (!bulkAction.value) selectedSubmissions.value = selection
  }
  async function runAction(kind: 'approve' | 'import', candidates: QuestionSubmissionVO[], single = false) {
    if (bulkAction.value) return
    const targets = candidates.filter((item) => item.status === (kind === 'approve' ? 0 : 1)).map((item) => item.id)
    if (!targets.length) {
      ElMessage.info(kind === 'approve' ? '选中的投稿中没有待审核记录' : '选中的投稿中没有已通过记录')
      return
    }
    const version = ++actionVersion,
      session = getAuthSessionVersion()
    const valid = () => current(session) && version === actionVersion
    bulkAction.value = kind
    actionMessage.value = ''
    actionFailed.value = false
    const verb = kind === 'approve' ? '通过' : '入库'
    try {
      try {
        await ElMessageBox.confirm(
          single ? `确认将投稿 #${targets[0]} 入库为正式题目？` : `确定${verb}选中的 ${targets.length} 条投稿？`,
          single ? '确认入库' : `批量${verb}投稿`,
          { type: 'warning', confirmButtonText: verb, cancelButtonText: '取消' },
        )
      } catch {
        return
      }
      if (!valid()) return
      const results = await Promise.allSettled(
        targets.map(async (id) => {
          const res =
            kind === 'approve'
              ? await reviewSubmission(id, { status: 1, reviewComment: '批量审核通过' }, { errorDisplay: 'inline' })
              : await importSubmission(id, { errorDisplay: 'inline' })
          if (res.code !== 0) throw new Error(res.message || `${verb}失败`)
        }),
      )
      if (!valid()) return
      const failures = targets.filter((_, index) => results[index].status === 'rejected')
      actionFailed.value = failures.length > 0
      actionMessage.value = failures.length
        ? `已${verb} ${targets.length - failures.length} 条投稿，${failures.length} 条处理失败。失败项：${failures.map((id) => `#${id}`).join('、')}，请重新选择后重试。`
        : `已${verb} ${targets.length} 条投稿。`
      if (failures.length)
        ElMessage.warning(`已${verb} ${targets.length - failures.length} 条投稿，${failures.length} 条处理失败`)
      else ElMessage.success(`已${verb} ${targets.length} 条投稿`)
      await refreshSubmissions()
    } finally {
      if (valid()) bulkAction.value = null
    }
  }
  const handleBulkApprove = () => runAction('approve', [...selectedSubmissions.value])
  const handleBulkImport = () => runAction('import', [...selectedSubmissions.value])
  const handleImport = (row: QuestionSubmissionVO) => runAction('import', [row], true)
  const unsubscribe = onAuthSessionChange(() => {
    listVersion++
    statsVersion++
    actionVersion++
    submissions.value = []
    selectedSubmissions.value = []
    stats.value = null
    total.value = 0
    loaded.value = false
    loading.value = statsLoading.value = false
    listError.value = statsError.value = actionMessage.value = ''
    bulkAction.value = null
    if (alive && isAuthenticated()) void refreshSubmissions()
  })
  onBeforeUnmount(() => {
    alive = false
    listVersion++
    statsVersion++
    actionVersion++
    unsubscribe()
  })
  onMounted(refreshSubmissions)
  return {
    loading,
    listError,
    loaded,
    statsLoading,
    statsError,
    stats,
    submissions,
    selectedSubmissions,
    statusFilter,
    keywordFilter,
    pageNum,
    pageSize,
    total,
    bulkAction,
    actionMessage,
    actionFailed,
    loadSubmissions,
    loadStats,
    refreshSubmissions,
    handlePageChange,
    handleSubmissionSelectionChange,
    handleBulkApprove,
    handleBulkImport,
    handleImport,
  }
}
