<template>
  <div class="submission-manage-page admin-page">
    <header class="admin-page-header">
      <div>
        <p class="admin-page-kicker">CONTENT REVIEW</p>
        <h2>投稿管理</h2>
        <p class="admin-page-description">处理用户投稿、AI 质检、知识点标注和正式入库，确保题库生产流程可追踪。</p>
      </div>
      <div class="admin-header-actions">
        <el-button :icon="Refresh" @click="refreshSubmissions" :loading="loading">刷新</el-button>
      </div>
    </header>

    <section class="admin-summary-grid">
      <el-card v-for="item in submissionStats" :key="item.label" shadow="never" class="admin-summary-card">
        <span class="admin-summary-icon">
          <el-icon><component :is="item.icon" /></el-icon>
        </span>
        <div class="admin-summary-copy">
          <p class="admin-summary-label">{{ item.label }}</p>
          <div class="admin-summary-value">{{ item.value }}</div>
          <div class="admin-summary-note">{{ item.note }}</div>
        </div>
      </el-card>
    </section>

    <el-card shadow="never" class="admin-table-card">
      <!-- 筛选栏 -->
      <div class="admin-toolbar">
        <div class="admin-filter-group">
          <el-radio-group v-model="statusFilter" @change="loadSubmissions">
            <el-radio-button :value="undefined">全部</el-radio-button>
            <el-radio-button :value="0">待审核</el-radio-button>
            <el-radio-button :value="1">已通过</el-radio-button>
            <el-radio-button :value="2">已拒绝</el-radio-button>
            <el-radio-button :value="3">已入库</el-radio-button>
          </el-radio-group>
          <el-input
            v-model="keywordFilter"
            placeholder="搜索题干关键词"
            clearable
            style="width: 220px"
            :prefix-icon="Search"
            @clear="loadSubmissions"
            @keyup.enter="loadSubmissions"
          />
          <el-button type="primary" :icon="Search" @click="loadSubmissions">搜索</el-button>
        </div>
        <span class="table-summary">当前筛选 {{ total }} 条投稿</span>
      </div>

      <!-- 列表 -->
      <div v-if="selectedSubmissions.length" class="admin-bulk-bar">
        <span class="admin-bulk-copy"
          >已选择 <strong>{{ selectedSubmissions.length }}</strong> 条投稿</span
        >
        <div class="admin-bulk-actions">
          <el-button size="small" type="success" :icon="Check" @click="handleBulkApprove">批量通过待审核</el-button>
          <el-button size="small" type="warning" :icon="FolderAdd" @click="handleBulkImport">批量入库已通过</el-button>
          <el-button size="small" @click="clearSubmissionSelection">清空选择</el-button>
        </div>
      </div>

      <el-table
        ref="submissionTableRef"
        :data="submissions"
        v-loading="loading"
        stripe
        class="admin-data-table"
        @selection-change="handleSubmissionSelectionChange"
      >
        <el-table-column type="selection" width="44" />
        <el-table-column label="ID" prop="id" width="60" />
        <el-table-column label="题干" prop="content" show-overflow-tooltip min-width="200" />
        <el-table-column label="投稿人" width="100">
          <template #default="{ row }">{{ row.nickname || row.username }}</template>
        </el-table-column>
        <el-table-column label="课程" prop="courseName" width="100" />
        <el-table-column label="题型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ questionTypeLabel(row.questionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="难度" width="100">
          <template #default="{ row }"><el-rate v-model="row.difficulty" disabled :max="5" /></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="投稿时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="admin-row-actions">
              <el-button type="primary" link :icon="View" @click="viewDetail(row as QuestionSubmissionVO)"
                >详情</el-button
              >
              <template v-if="(row as QuestionSubmissionVO).status === 0">
                <el-button type="success" link :icon="Check" @click="openReview(row as QuestionSubmissionVO, 1)"
                  >通过</el-button
                >
                <el-button type="danger" link :icon="Close" @click="openReview(row as QuestionSubmissionVO, 2)"
                  >拒绝</el-button
                >
              </template>
              <el-button
                v-else-if="(row as QuestionSubmissionVO).status === 1"
                type="warning"
                link
                :icon="FolderAdd"
                @click="handleImport(row as QuestionSubmissionVO)"
                >入库</el-button
              >
              <el-dropdown
                trigger="click"
                @command="(command) => handleSubmissionRowCommand(command as string, row as QuestionSubmissionVO)"
              >
                <el-button link :icon="MoreFilled">AI 工具</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="quality" :icon="MagicStick">AI 质检</el-dropdown-item>
                    <el-dropdown-item command="tagging" :icon="CollectionTag">知识点标注</el-dropdown-item>
                    <el-dropdown-item command="difficulty" :icon="TrendCharts">难度评估</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty class="admin-table-empty" description="没有匹配的投稿" />
        </template>
      </el-table>

      <div class="admin-pagination" v-if="total > pageSize">
        <el-pagination
          :current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 审核对话框 -->
    <el-dialog v-model="showReviewDialog" :title="reviewAction === 1 ? '通过投稿' : '拒绝投稿'" width="500px">
      <el-form label-width="80px">
        <el-form-item label="审核意见">
          <el-input
            v-model="reviewComment"
            type="textarea"
            :rows="5"
            :placeholder="reviewAction === 1 ? '审核通过意见（可选）' : '请输入拒绝原因'"
          />
          <el-button
            type="primary"
            link
            size="small"
            style="margin-top: 4px"
            @click="handleGenerateReviewComment"
            :loading="generatingComment"
          >
            🤖 AI 一键填充审核意见
          </el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReviewDialog = false">取消</el-button>
        <el-button :type="reviewAction === 1 ? 'success' : 'danger'" @click="handleReview" :loading="reviewing">
          {{ reviewAction === 1 ? '确认通过' : '确认拒绝' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="投稿详情" width="700px">
      <el-descriptions :column="2" border v-if="currentDetail">
        <el-descriptions-item label="ID">{{ currentDetail.id }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(currentDetail.status)">{{ statusLabel(currentDetail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="投稿人">{{
          currentDetail.nickname || currentDetail.username
        }}</el-descriptions-item>
        <el-descriptions-item label="投稿时间">{{ formatTime(currentDetail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="课程">{{ currentDetail.courseName }}</el-descriptions-item>
        <el-descriptions-item label="题型">{{ questionTypeLabel(currentDetail.questionType) }}</el-descriptions-item>
        <el-descriptions-item label="难度">
          <el-rate v-model="currentDetail.difficulty" disabled :max="5" />
        </el-descriptions-item>
        <el-descriptions-item label="来源" v-if="currentDetail.source">{{ currentDetail.source }}</el-descriptions-item>
        <el-descriptions-item label="题干内容" :span="2">
          <div class="detail-content">{{ currentDetail.content }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="选项" :span="2" v-if="currentDetail.optionsJson">
          <div v-for="(opt, idx) in parseOptions(currentDetail.optionsJson)" :key="idx">
            <strong>{{ opt.label || String.fromCharCode(65 + idx) }}.</strong> {{ opt.content }}
            <el-tag v-if="opt.isCorrect" type="success" size="small" style="margin-left: 4px">正确</el-tag>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="参考答案" :span="2" v-if="currentDetail.correctAnswer">
          {{ currentDetail.correctAnswer }}
        </el-descriptions-item>
        <el-descriptions-item label="解析" :span="2" v-if="currentDetail.analysis">
          <div class="detail-content">{{ currentDetail.analysis }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="标签" v-if="currentDetail.tags">{{ currentDetail.tags }}</el-descriptions-item>
        <el-descriptions-item label="知识点IDs" v-if="currentDetail.knowledgePointIds">{{
          currentDetail.knowledgePointIds
        }}</el-descriptions-item>
        <el-descriptions-item label="审核意见" :span="2" v-if="currentDetail.reviewComment">
          <el-text type="info">{{ currentDetail.reviewComment }}</el-text>
        </el-descriptions-item>
        <el-descriptions-item label="审核人" v-if="currentDetail.reviewedByName">{{
          currentDetail.reviewedByName
        }}</el-descriptions-item>
        <el-descriptions-item label="审核时间" v-if="currentDetail.reviewedTime">{{
          formatTime(currentDetail.reviewedTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="入库题目ID" v-if="currentDetail.importedQuestionId">
          <el-button type="primary" link @click="goToQuestion(currentDetail.importedQuestionId!)">
            #{{ currentDetail.importedQuestionId }}
          </el-button>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <SubmissionAiTools ref="submissionAiTools" @updated="loadSubmissions" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { SemanticTagType } from '@/utils/errors'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  Check,
  Close,
  CollectionTag,
  FolderAdd,
  MagicStick,
  MoreFilled,
  Refresh,
  Search,
  TrendCharts,
  View,
} from '@element-plus/icons-vue'
import {
  getAdminSubmissions,
  reviewSubmission,
  importSubmission,
  getSubmissionStats,
  generateReviewComment,
  type QuestionSubmissionVO,
  type SubmissionStats,
} from '@/api/submission'
import SubmissionAiTools from './submission/SubmissionAiTools.vue'

const router = useRouter()
const loading = ref(false)
const reviewing = ref(false)
const submissions = ref<QuestionSubmissionVO[]>([])
const submissionTableRef = ref<TableInstance>()
const selectedSubmissions = ref<QuestionSubmissionVO[]>([])
const statusFilter = ref<number | undefined>(undefined)
const keywordFilter = ref('')
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const stats = ref<SubmissionStats>({ pending: 0, approved: 0, rejected: 0, imported: 0 })
const submissionAiTools = ref<InstanceType<typeof SubmissionAiTools>>()

const submissionStats = computed(() => [
  { label: '待审核', value: stats.value.pending, note: '需要管理员处理', icon: Search },
  { label: '已通过', value: stats.value.approved, note: '可继续入库', icon: Check },
  { label: '已拒绝', value: stats.value.rejected, note: '保留审核记录', icon: Close },
  { label: '已入库', value: stats.value.imported, note: '进入正式题库', icon: FolderAdd },
])

const handleSubmissionRowCommand = (command: string, submission: QuestionSubmissionVO) => {
  if (command === 'quality' || command === 'tagging' || command === 'difficulty') {
    submissionAiTools.value?.open(command, submission)
  }
}

const handleSubmissionSelectionChange = (selection: QuestionSubmissionVO[]) => {
  selectedSubmissions.value = selection
}

const clearSubmissionSelection = () => {
  submissionTableRef.value?.clearSelection()
}

const showReviewDialog = ref(false)
const showDetailDialog = ref(false)
const currentDetail = ref<QuestionSubmissionVO | null>(null)
const reviewTarget = ref<QuestionSubmissionVO | null>(null)
const reviewAction = ref(1) // 1=通过 2=拒绝
const reviewComment = ref('')

// 一键填充审核意见
const generatingComment = ref(false)

const questionTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    FILL_BLANK: '填空题',
    SHORT_ANSWER: '简答题',
  }
  return map[type] || type
}

const statusLabel = (status: number) => {
  const map: Record<number, string> = { 0: '待审核', 1: '已通过', 2: '已拒绝', 3: '已入库' }
  return map[status] || '未知'
}

const statusTagType = (status: number) => {
  const map: Record<number, SemanticTagType> = { 0: 'warning', 1: 'success', 2: 'danger', 3: undefined }
  return map[status] || 'info'
}

const formatTime = (t: string | null) => (t ? t.replace('T', ' ').substring(0, 19) : '')

const parseOptions = (json: string | null): Array<{ content: string; label: string; isCorrect: boolean }> => {
  if (!json) return []
  try {
    return JSON.parse(json)
  } catch {
    return []
  }
}

const loadSubmissions = async () => {
  loading.value = true
  try {
    const res = await getAdminSubmissions({
      pageNum: pageNum.value,
      pageSize,
      status: statusFilter.value,
      keyword: keywordFilter.value || undefined,
    })
    if (res.code === 0 && res.data) {
      submissions.value = res.data.records
      total.value = res.data.total
      selectedSubmissions.value = []
    }
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  try {
    const res = await getSubmissionStats()
    if (res.code === 0 && res.data) {
      stats.value = res.data
    }
  } catch {
    /* ignore */
  }
}

const refreshSubmissions = () => {
  loadSubmissions()
  loadStats()
}

const handlePageChange = (page: number) => {
  pageNum.value = page
  loadSubmissions()
}

const viewDetail = (row: QuestionSubmissionVO) => {
  currentDetail.value = row
  showDetailDialog.value = true
}

const openReview = (row: QuestionSubmissionVO, action: number) => {
  reviewTarget.value = row
  reviewAction.value = action
  reviewComment.value = ''
  showReviewDialog.value = true
}

const handleReview = async () => {
  if (!reviewTarget.value) return
  if (reviewAction.value === 2 && !reviewComment.value.trim()) {
    ElMessage.warning('拒绝时请填写审核意见')
    return
  }
  reviewing.value = true
  try {
    const res = await reviewSubmission(reviewTarget.value.id, {
      status: reviewAction.value,
      reviewComment: reviewComment.value || undefined,
    })
    if (res.code === 0) {
      ElMessage.success(reviewAction.value === 1 ? '已通过' : '已拒绝')
      showReviewDialog.value = false
      loadSubmissions()
      loadStats()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } finally {
    reviewing.value = false
  }
}

const handleBulkApprove = async () => {
  if (!selectedSubmissions.value.length) return
  const targets = selectedSubmissions.value.filter((item) => item.status === 0)
  if (!targets.length) {
    ElMessage.info('选中的投稿中没有待审核记录')
    clearSubmissionSelection()
    return
  }
  try {
    await ElMessageBox.confirm(`确定通过选中的 ${targets.length} 条待审核投稿？`, '批量通过投稿', {
      type: 'warning',
      confirmButtonText: '通过',
      cancelButtonText: '取消',
    })
    loading.value = true
    const results = await Promise.allSettled(
      targets.map((item) =>
        reviewSubmission(item.id, {
          status: 1,
          reviewComment: '批量审核通过',
        }),
      ),
    )
    const failed = results.filter((result) => result.status === 'rejected').length
    if (failed > 0) {
      ElMessage.warning(`已通过 ${targets.length - failed} 条投稿，${failed} 条处理失败`)
    } else {
      ElMessage.success(`已通过 ${targets.length} 条投稿`)
    }
    await Promise.all([loadSubmissions(), loadStats()])
  } catch {
    /* cancelled */
  } finally {
    loading.value = false
  }
}

const handleImport = async (row: QuestionSubmissionVO) => {
  try {
    await ElMessageBox.confirm(`确认将投稿 #${row.id} 入库为正式题目？入库后投稿状态将变为"已入库"。`, '确认入库', {
      type: 'warning',
    })
    const res = await importSubmission(row.id)
    if (res.code === 0 && res.data) {
      ElMessage.success('入库成功，题目ID: ' + res.data.importedQuestionId)
      loadSubmissions()
      loadStats()
    } else {
      ElMessage.error(res.message || '入库失败')
    }
  } catch {
    /* cancelled */
  }
}

const handleBulkImport = async () => {
  if (!selectedSubmissions.value.length) return
  const targets = selectedSubmissions.value.filter((item) => item.status === 1)
  if (!targets.length) {
    ElMessage.info('选中的投稿中没有已通过记录')
    clearSubmissionSelection()
    return
  }
  try {
    await ElMessageBox.confirm(`确认将选中的 ${targets.length} 条已通过投稿入库为正式题目？`, '批量入库', {
      type: 'warning',
      confirmButtonText: '入库',
      cancelButtonText: '取消',
    })
    loading.value = true
    const results = await Promise.allSettled(targets.map((item) => importSubmission(item.id)))
    const failed = results.filter((result) => result.status === 'rejected').length
    if (failed > 0) {
      ElMessage.warning(`已入库 ${targets.length - failed} 条投稿，${failed} 条处理失败`)
    } else {
      ElMessage.success(`已入库 ${targets.length} 条投稿`)
    }
    await Promise.all([loadSubmissions(), loadStats()])
  } catch {
    /* cancelled */
  } finally {
    loading.value = false
  }
}

const goToQuestion = (id: number) => {
  showDetailDialog.value = false
  router.push({ name: 'AdminQuestionManage', query: { highlight: id } })
}

// ========== 一键填充审核意见 ==========

const handleGenerateReviewComment = async () => {
  if (!reviewTarget.value) return
  generatingComment.value = true
  try {
    const res = await generateReviewComment(reviewTarget.value.id)
    if (res.code === 0 && res.data) {
      reviewComment.value = res.data
      ElMessage.success('AI 审核意见已填充')
    } else {
      ElMessage.error(res.message || '生成审核意见失败')
    }
  } catch {
    ElMessage.error('生成审核意见请求失败')
  } finally {
    generatingComment.value = false
  }
}

onMounted(() => {
  loadSubmissions()
  loadStats()
})
</script>

<style scoped>
.table-summary {
  color: var(--lp-text-muted);
  font-size: 13px;
}
.detail-content {
  white-space: pre-wrap;
  line-height: 1.6;
}
</style>
