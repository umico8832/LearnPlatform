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

    <SubmissionReviewDialog ref="reviewDialog" @reviewed="refreshSubmissions" />
    <SubmissionDetailDialog ref="detailDialog" />
    <SubmissionAiTools ref="submissionAiTools" @updated="loadSubmissions" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance } from 'element-plus'
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
  type QuestionSubmissionVO,
  type SubmissionStats,
} from '@/api/submission'
import SubmissionAiTools from './submission/SubmissionAiTools.vue'
import SubmissionDetailDialog from './submission/SubmissionDetailDialog.vue'
import SubmissionReviewDialog from './submission/SubmissionReviewDialog.vue'
import {
  formatSubmissionTime as formatTime,
  questionTypeLabel,
  submissionStatusLabel as statusLabel,
  submissionStatusTag as statusTagType,
} from './submission/submissionPresentation'

const loading = ref(false)
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
const reviewDialog = ref<InstanceType<typeof SubmissionReviewDialog>>()
const detailDialog = ref<InstanceType<typeof SubmissionDetailDialog>>()

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
  detailDialog.value?.open(row)
}

const openReview = (row: QuestionSubmissionVO, action: number) => {
  reviewDialog.value?.open(row, action)
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
</style>
