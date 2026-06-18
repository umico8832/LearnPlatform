<template>
  <div class="submission-manage-page">
    <el-card>
      <template #header>
        <span>投稿管理</span>
      </template>

      <!-- 统计卡片 -->
      <el-row :gutter="16" style="margin-bottom: 20px">
        <el-col :span="6">
          <el-statistic title="待审核" :value="stats.pending">
            <template #suffix><el-tag type="warning" size="small">待处理</el-tag></template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic title="已通过" :value="stats.approved" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="已拒绝" :value="stats.rejected" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="已入库" :value="stats.imported" />
        </el-col>
      </el-row>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-radio-group v-model="statusFilter" @change="loadSubmissions">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button :value="0">待审核</el-radio-button>
          <el-radio-button :value="1">已通过</el-radio-button>
          <el-radio-button :value="2">已拒绝</el-radio-button>
          <el-radio-button :value="3">已入库</el-radio-button>
        </el-radio-group>
        <el-input v-model="keywordFilter" placeholder="搜索题干关键词" clearable
          style="width: 220px; margin-left: 12px" @clear="loadSubmissions"
          @keyup.enter="loadSubmissions" />
        <el-button type="primary" @click="loadSubmissions" style="margin-left: 8px">搜索</el-button>
      </div>

      <!-- 列表 -->
      <el-table :data="submissions" v-loading="loading" stripe>
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
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewDetail(row as QuestionSubmissionVO)">详情</el-button>
            <template v-if="(row as QuestionSubmissionVO).status === 0">
              <el-button type="success" link @click="openReview(row as QuestionSubmissionVO, 1)">通过</el-button>
              <el-button type="danger" link @click="openReview(row as QuestionSubmissionVO, 2)">拒绝</el-button>
            </template>
            <el-button v-if="(row as QuestionSubmissionVO).status === 1" type="warning" link @click="handleImport(row as QuestionSubmissionVO)">入库</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > pageSize"
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
        style="margin-top: 16px; justify-content: flex-end"
      />
    </el-card>

    <!-- 审核对话框 -->
    <el-dialog v-model="showReviewDialog" :title="reviewAction === 1 ? '通过投稿' : '拒绝投稿'" width="500px">
      <el-form label-width="80px">
        <el-form-item label="审核意见">
          <el-input v-model="reviewComment" type="textarea" :rows="3"
            :placeholder="reviewAction === 1 ? '审核通过意见（可选）' : '请输入拒绝原因'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReviewDialog = false">取消</el-button>
        <el-button :type="reviewAction === 1 ? 'success' : 'danger'"
          @click="handleReview" :loading="reviewing">
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
        <el-descriptions-item label="投稿人">{{ currentDetail.nickname || currentDetail.username }}</el-descriptions-item>
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
        <el-descriptions-item label="知识点IDs" v-if="currentDetail.knowledgePointIds">{{ currentDetail.knowledgePointIds }}</el-descriptions-item>
        <el-descriptions-item label="审核意见" :span="2" v-if="currentDetail.reviewComment">
          <el-text type="info">{{ currentDetail.reviewComment }}</el-text>
        </el-descriptions-item>
        <el-descriptions-item label="审核人" v-if="currentDetail.reviewedByName">{{ currentDetail.reviewedByName }}</el-descriptions-item>
        <el-descriptions-item label="审核时间" v-if="currentDetail.reviewedTime">{{ formatTime(currentDetail.reviewedTime) }}</el-descriptions-item>
        <el-descriptions-item label="入库题目ID" v-if="currentDetail.importedQuestionId">
          <el-button type="primary" link @click="goToQuestion(currentDetail.importedQuestionId!)">
            #{{ currentDetail.importedQuestionId }}
          </el-button>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  getAdminSubmissions,
  reviewSubmission,
  importSubmission,
  getSubmissionStats,
  type QuestionSubmissionVO,
  type SubmissionStats,
} from '@/api/submission'

const router = useRouter()
const loading = ref(false)
const reviewing = ref(false)
const submissions = ref<QuestionSubmissionVO[]>([])
const statusFilter = ref<number | undefined>(undefined)
const keywordFilter = ref('')
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const stats = ref<SubmissionStats>({ pending: 0, approved: 0, rejected: 0, imported: 0 })

const showReviewDialog = ref(false)
const showDetailDialog = ref(false)
const currentDetail = ref<QuestionSubmissionVO | null>(null)
const reviewTarget = ref<QuestionSubmissionVO | null>(null)
const reviewAction = ref(1) // 1=通过 2=拒绝
const reviewComment = ref('')

const questionTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题', FILL_BLANK: '填空题', SHORT_ANSWER: '简答题',
  }
  return map[type] || type
}

const statusLabel = (status: number) => {
  const map: Record<number, string> = { 0: '待审核', 1: '已通过', 2: '已拒绝', 3: '已入库' }
  return map[status] || '未知'
}

const statusTagType = (status: number) => {
  const map: Record<number, string> = { 0: 'warning', 1: 'success', 2: 'danger', 3: '' }
  return (map[status] || 'info') as any
}

const formatTime = (t: string | null) => t ? t.replace('T', ' ').substring(0, 19) : ''

const parseOptions = (json: string | null): Array<{ content: string; label: string; isCorrect: boolean }> => {
  if (!json) return []
  try { return JSON.parse(json) } catch { return [] }
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
    if (res.data.code === 200) {
      submissions.value = res.data.data.records
      total.value = res.data.data.total
    }
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  try {
    const res = await getSubmissionStats()
    if (res.data.code === 200) {
      stats.value = res.data.data
    }
  } catch { /* ignore */ }
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
    if (res.data.code === 200) {
      ElMessage.success(reviewAction.value === 1 ? '已通过' : '已拒绝')
      showReviewDialog.value = false
      loadSubmissions()
      loadStats()
    } else {
      ElMessage.error(res.data.message || '操作失败')
    }
  } finally {
    reviewing.value = false
  }
}

const handleImport = async (row: QuestionSubmissionVO) => {
  try {
    await ElMessageBox.confirm(
      `确认将投稿 #${row.id} 入库为正式题目？入库后投稿状态将变为"已入库"。`,
      '确认入库',
      { type: 'warning' }
    )
    const res = await importSubmission(row.id)
    if (res.data.code === 200) {
      ElMessage.success('入库成功，题目ID: ' + res.data.data.importedQuestionId)
      loadSubmissions()
      loadStats()
    } else {
      ElMessage.error(res.data.message || '入库失败')
    }
  } catch { /* cancelled */ }
}

const goToQuestion = (id: number) => {
  showDetailDialog.value = false
  router.push({ path: '/admin/questions', query: { highlight: id } })
}

onMounted(() => {
  loadSubmissions()
  loadStats()
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}
.detail-content {
  white-space: pre-wrap;
  line-height: 1.6;
}
</style>