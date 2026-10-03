<template>
  <div class="submission-manage-page admin-page">
    <header class="admin-page-header">
      <div>
        <h1>投稿管理</h1>
      </div>
      <div class="admin-header-actions">
        <el-button :icon="Refresh" @click="refreshSubmissions" :loading="loading" :disabled="!!bulkAction"
          >刷新</el-button
        >
      </div>
    </header>

    <LpStatePanel
      :state="statsLoading ? 'loading' : statsError ? 'error' : stats ? 'ready' : 'loading'"
      title="投稿统计暂时无法读取"
      :description="statsError"
      loading-label="正在读取投稿统计"
      @retry="loadStats"
    >
      <section class="admin-summary-grid" aria-label="投稿统计">
        <el-card v-for="item in submissionStats" :key="item.label" shadow="never" class="admin-summary-card">
          <span class="admin-summary-icon">
            <el-icon><component :is="item.icon" /></el-icon>
          </span>
          <div class="admin-summary-copy">
            <p class="admin-summary-label">{{ item.label }}</p>
            <div class="admin-summary-value">{{ item.value }}</div>
          </div>
        </el-card>
      </section>
    </LpStatePanel>
    <p v-if="actionMessage" role="status" class="submission-action-message" :class="{ 'is-error': actionFailed }">
      {{ actionMessage }}
    </p>

    <el-card shadow="never" class="admin-table-card">
      <!-- 筛选栏 -->
      <div class="admin-toolbar">
        <div class="admin-filter-group">
          <el-radio-group v-model="statusFilter" :disabled="!!bulkAction" @change="loadSubmissions">
            <el-radio-button :value="undefined">全部</el-radio-button>
            <el-radio-button :value="0">待审核</el-radio-button>
            <el-radio-button :value="1">已通过</el-radio-button>
            <el-radio-button :value="2">已拒绝</el-radio-button>
            <el-radio-button :value="3">已入库</el-radio-button>
          </el-radio-group>
          <el-input
            v-model="keywordFilter"
            :disabled="!!bulkAction"
            placeholder="搜索题干关键词"
            clearable
            style="width: 220px"
            :prefix-icon="Search"
            @clear="loadSubmissions"
            @keyup.enter="loadSubmissions"
          />
          <el-button type="primary" :icon="Search" :disabled="!!bulkAction" @click="loadSubmissions">搜索</el-button>
        </div>
        <span v-if="loaded && !loading && !listError" class="table-summary">当前筛选 {{ total }} 条投稿</span>
      </div>

      <!-- 列表 -->
      <div v-if="selectedSubmissions.length && !loading && !listError" class="admin-bulk-bar">
        <span class="admin-bulk-copy"
          >已选择 <strong>{{ selectedSubmissions.length }}</strong> 条投稿</span
        >
        <div class="admin-bulk-actions">
          <el-button
            size="small"
            type="success"
            :icon="Check"
            :disabled="!!bulkAction"
            :loading="bulkAction === 'approve'"
            @click="handleBulkApprove"
            >批量通过待审核</el-button
          >
          <el-button
            size="small"
            type="warning"
            :icon="FolderAdd"
            :disabled="!!bulkAction"
            :loading="bulkAction === 'import'"
            @click="handleBulkImport"
            >批量入库已通过</el-button
          >
          <el-button size="small" :disabled="!!bulkAction" @click="clearSubmissionSelection">清空选择</el-button>
        </div>
      </div>

      <LpStatePanel
        :state="loading ? 'loading' : listError ? 'error' : loaded ? 'ready' : 'loading'"
        title="投稿列表暂时无法读取"
        :description="listError"
        loading-label="正在读取投稿列表"
        @retry="loadSubmissions"
      >
        <el-table
          ref="submissionTableRef"
          :data="submissions"
          stripe
          class="admin-data-table"
          @selection-change="handleSubmissionSelectionChange"
        >
          <el-table-column type="selection" width="44" :selectable="() => !bulkAction" />
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
            <template #default="{ row }">{{ row.difficulty }} / 5</template>
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
                  <el-button
                    type="success"
                    link
                    :icon="Check"
                    :disabled="!!bulkAction"
                    @click="openReview(row as QuestionSubmissionVO, 1)"
                    >通过</el-button
                  >
                  <el-button
                    type="danger"
                    link
                    :icon="Close"
                    :disabled="!!bulkAction"
                    @click="openReview(row as QuestionSubmissionVO, 2)"
                    >拒绝</el-button
                  >
                </template>
                <el-button
                  v-else-if="(row as QuestionSubmissionVO).status === 1"
                  type="warning"
                  link
                  :icon="FolderAdd"
                  :disabled="!!bulkAction"
                  @click="handleImport(row as QuestionSubmissionVO)"
                  >入库</el-button
                >
                <el-dropdown
                  trigger="click"
                  @command="(command) => handleSubmissionRowCommand(command as string, row as QuestionSubmissionVO)"
                >
                  <el-button link :icon="MoreFilled" :disabled="!!bulkAction">AI 工具</el-button>
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
            <LpEmptyState title="没有匹配的投稿" description="可调整筛选条件后再查看。" />
          </template>
        </el-table>
      </LpStatePanel>

      <div class="admin-pagination" v-if="!loading && !listError && total > pageSize">
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
import { ref, computed } from 'vue'
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
import type { QuestionSubmissionVO } from '@/api/submission'
import { LpEmptyState, LpStatePanel } from '@/components/ui'
import { useSubmissionAdminList } from '@/composables/useSubmissionAdminList'
import SubmissionAiTools from './submission/SubmissionAiTools.vue'
import SubmissionDetailDialog from './submission/SubmissionDetailDialog.vue'
import SubmissionReviewDialog from './submission/SubmissionReviewDialog.vue'
import {
  formatSubmissionTime as formatTime,
  questionTypeLabel,
  submissionStatusLabel as statusLabel,
  submissionStatusTag as statusTagType,
} from './submission/submissionPresentation'
const {
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
} = useSubmissionAdminList()
const submissionTableRef = ref<TableInstance>()
const submissionAiTools = ref<InstanceType<typeof SubmissionAiTools>>()
const reviewDialog = ref<InstanceType<typeof SubmissionReviewDialog>>()
const detailDialog = ref<InstanceType<typeof SubmissionDetailDialog>>()
const submissionStats = computed(() =>
  stats.value
    ? [
        { label: '待审核', value: stats.value.pending, icon: Search },
        { label: '已通过', value: stats.value.approved, icon: Check },
        { label: '已拒绝', value: stats.value.rejected, icon: Close },
        { label: '已入库', value: stats.value.imported, icon: FolderAdd },
      ]
    : [],
)
function handleSubmissionRowCommand(command: string, submission: QuestionSubmissionVO) {
  if (bulkAction.value) return
  if (command === 'quality' || command === 'tagging' || command === 'difficulty')
    submissionAiTools.value?.open(command, submission)
}
function clearSubmissionSelection() {
  if (!bulkAction.value) {
    selectedSubmissions.value = []
    submissionTableRef.value?.clearSelection()
  }
}
function viewDetail(row: QuestionSubmissionVO) {
  detailDialog.value?.open(row)
}
function openReview(row: QuestionSubmissionVO, action: number) {
  if (!bulkAction.value) reviewDialog.value?.open(row, action)
}
</script>

<style scoped>
.submission-action-message {
  color: var(--lp-text-secondary);
  padding: var(--lp-space-3);
  background: var(--lp-surface-subtle);
  border-left: 3px solid var(--lp-primary);
}
.submission-action-message.is-error {
  border-color: var(--lp-danger);
}
.table-summary {
  color: var(--lp-text-muted);
  font-size: 13px;
}
</style>
