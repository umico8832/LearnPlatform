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
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewDetail(row as QuestionSubmissionVO)">详情</el-button>
            <template v-if="(row as QuestionSubmissionVO).status === 0">
              <el-button type="success" link @click="openReview(row as QuestionSubmissionVO, 1)">通过</el-button>
              <el-button type="danger" link @click="openReview(row as QuestionSubmissionVO, 2)">拒绝</el-button>
            </template>
            <el-button type="info" link @click="handleQualityCheck(row as QuestionSubmissionVO)">AI 质检</el-button>
            <el-button type="primary" link @click="handleKPTagging(row as QuestionSubmissionVO)">AI 标注</el-button>
            <el-button type="success" link @click="handleDifficultyAssessment(row as QuestionSubmissionVO)">AI 测难度</el-button>
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

    <!-- AI 知识点标注对话框 -->
    <el-dialog v-model="showKPTaggingDialog" title="AI 知识点标注" width="720px">
      <div v-if="kpTaggingLoading" v-loading="true" element-loading-text="AI 正在分析题目知识点归属，请稍候..." style="min-height: 120px" />
      <div v-else-if="kpTaggingResult">
        <!-- AI 分析说明 -->
        <el-alert :title="kpTaggingResult.analysis" type="info" show-icon :closable="false" style="margin-bottom: 16px" />

        <!-- 推荐知识点列表 -->
        <div v-if="kpTaggingResult.recommendations.length > 0">
          <div style="font-weight: 600; margin-bottom: 8px">推荐知识点（共 {{ kpTaggingResult.recommendations.length }} 个）</div>
          <el-table :data="kpTaggingResult.recommendations" border size="small" style="margin-bottom: 16px">
            <el-table-column label="知识点" prop="name" min-width="120" />
            <el-table-column label="课程" prop="courseName" width="120" />
            <el-table-column label="置信度" width="100">
              <template #default="{ row }">
                <el-tag :type="confidenceType(row.confidence)" size="small">{{ confidenceLabel(row.confidence) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="推荐理由" prop="reason" min-width="200" show-overflow-tooltip />
          </el-table>

          <el-card shadow="never" style="background: #f0f9ff">
            <div style="font-size: 13px; color: #606266; margin-bottom: 8px">
              <strong>一键应用：</strong>将以下知识点 ID 应用到投稿的「知识点IDs」字段
            </div>
            <div style="display: flex; align-items: center; gap: 12px">
              <el-input v-model="kpTaggingSuggestedIds" readonly style="flex: 1" />
              <el-button type="primary" @click="handleApplyKP" :loading="applyingKP">应用到投稿</el-button>
            </div>
          </el-card>
        </div>
        <el-empty v-else description="未找到匹配的知识点" />
      </div>
      <template #footer>
        <el-button @click="showKPTaggingDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- AI 质检结果对话框 -->
    <el-dialog v-model="showQualityDialog" title="AI 质检报告" width="720px">
      <div v-if="qualityLoading" v-loading="true" element-loading-text="AI 正在分析题目质量，请稍候..." style="min-height: 120px" />
      <div v-else-if="qualityResult">
        <!-- 总评 -->
        <el-card shadow="never" style="margin-bottom: 16px">
          <div style="display: flex; align-items: center; justify-content: space-between">
            <div>
              <span style="font-size: 16px; font-weight: 600">综合评分：</span>
              <el-tag :type="qualityResult.qualityScore >= 80 ? 'success' : qualityResult.qualityScore >= 50 ? 'warning' : 'danger'" size="large" style="font-size: 18px; margin-left: 8px">
                {{ qualityResult.qualityScore }} 分
              </el-tag>
            </div>
            <el-tag :type="recommendationType(qualityResult.recommendation)" size="large">
              {{ recommendationLabel(qualityResult.recommendation) }}
            </el-tag>
          </div>
          <p style="margin-top: 10px; color: #606266">{{ qualityResult.summary }}</p>
        </el-card>

        <!-- 五维检查 -->
        <el-row :gutter="12" style="margin-bottom: 16px">
          <el-col v-for="(item, idx) in qualityCheckItems" :key="idx" :span="12" style="margin-bottom: 8px">
            <div style="display: flex; align-items: flex-start; gap: 8px; padding: 8px 12px; background: #f5f7fa; border-radius: 6px">
              <el-tag :type="checkStatusType(item.status)" size="small" style="flex-shrink: 0">{{ checkStatusLabel(item.status) }}</el-tag>
              <div>
                <div style="font-weight: 600; font-size: 13px">{{ item.label }}</div>
                <div style="font-size: 12px; color: #909399; margin-top: 2px">{{ item.detail }}</div>
              </div>
            </div>
          </el-col>
        </el-row>

        <!-- 风险点 -->
        <el-card v-if="qualityResult.riskPoints && qualityResult.riskPoints.length > 0" shadow="never" style="margin-bottom: 12px">
          <template #header><span style="color: #e6a23c; font-weight: 600">⚠ 风险点</span></template>
          <ul style="margin: 0; padding-left: 20px">
            <li v-for="(point, idx) in qualityResult.riskPoints" :key="idx" style="color: #e6a23c; margin-bottom: 4px">{{ point }}</li>
          </ul>
        </el-card>

        <!-- 修改建议 -->
        <el-card v-if="qualityResult.suggestions && qualityResult.suggestions.length > 0" shadow="never">
          <template #header><span style="color: #409eff; font-weight: 600">💡 修改建议</span></template>
          <ul style="margin: 0; padding-left: 20px">
            <li v-for="(sug, idx) in qualityResult.suggestions" :key="idx" style="color: #409eff; margin-bottom: 4px">{{ sug }}</li>
          </ul>
        </el-card>
      </div>
      <template #footer>
        <el-button @click="showQualityDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- AI 难度评估对话框 -->
    <el-dialog v-model="showDifficultyDialog" title="AI 难度评估报告" width="720px">
      <div v-if="difficultyLoading" v-loading="true" element-loading-text="AI 正在评估题目难度，请稍候..." style="min-height: 120px" />
      <div v-else-if="difficultyResult">
        <!-- 总评 -->
        <el-card shadow="never" style="margin-bottom: 16px">
          <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px">
            <div>
              <span style="font-size: 16px; font-weight: 600">AI 评估难度：</span>
              <el-rate :model-value="difficultyResult.suggestedDifficulty" disabled :max="5" style="display: inline-flex; margin-left: 8px" />
              <el-tag :type="difficultyConfidenceType(difficultyResult.confidence)" size="small" style="margin-left: 8px">
                {{ difficultyConfidenceLabel(difficultyResult.confidence) }}
              </el-tag>
            </div>
            <div v-if="difficultyResult.originalDifficulty">
              <span style="font-size: 13px; color: #909399">投稿者标注：</span>
              <el-rate :model-value="difficultyResult.originalDifficulty" disabled :max="5" style="display: inline-flex; margin-left: 4px" />
              <el-tag v-if="difficultyResult.difficultyMatch" type="success" size="small" style="margin-left: 4px">一致</el-tag>
              <el-tag v-else type="warning" size="small" style="margin-left: 4px">不一致</el-tag>
            </div>
          </div>
          <div style="display: flex; align-items: center; gap: 16px; margin-bottom: 8px">
            <span style="font-size: 13px; color: #606266">认知层次：<el-tag size="small">{{ difficultyResult.cognitiveLevel }}</el-tag></span>
          </div>
          <p style="color: #606266; margin: 0">{{ difficultyResult.reason }}</p>
        </el-card>

        <!-- 难度影响因素 -->
        <div v-if="difficultyResult.factors && difficultyResult.factors.length > 0" style="margin-bottom: 16px">
          <div style="font-weight: 600; margin-bottom: 8px">影响难度的因素</div>
          <el-table :data="difficultyResult.factors" border size="small">
            <el-table-column label="因素" prop="name" width="120" />
            <el-table-column label="说明" prop="description" min-width="200" show-overflow-tooltip />
            <el-table-column label="影响" width="100">
              <template #default="{ row }">
                <el-tag :type="impactType(row.impact)" size="small">{{ impactLabel(row.impact) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 总结 -->
        <el-card shadow="never" style="background: #f0f9ff">
          <div style="font-size: 13px; color: #606266">
            <strong>总结：</strong>{{ difficultyResult.summary }}
          </div>
        </el-card>
      </div>
      <template #footer>
        <el-button @click="showDifficultyDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  getAdminSubmissions,
  reviewSubmission,
  importSubmission,
  getSubmissionStats,
  qualityCheckSubmission,
  kpTaggingSubmission,
  applyKnowledgePoints,
  assessDifficulty,
  type QuestionSubmissionVO,
  type SubmissionStats,
  type SubmissionQualityCheck,
  type SubmissionKPTagging,
  type SubmissionDifficultyAssessment,
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
const showQualityDialog = ref(false)
const qualityLoading = ref(false)
const qualityResult = ref<SubmissionQualityCheck | null>(null)
const currentDetail = ref<QuestionSubmissionVO | null>(null)
const reviewTarget = ref<QuestionSubmissionVO | null>(null)
const reviewAction = ref(1) // 1=通过 2=拒绝
const reviewComment = ref('')

// AI 难度评估
const showDifficultyDialog = ref(false)
const difficultyLoading = ref(false)
const difficultyResult = ref<SubmissionDifficultyAssessment | null>(null)

// AI 知识点标注
const showKPTaggingDialog = ref(false)
const kpTaggingLoading = ref(false)
const kpTaggingResult = ref<SubmissionKPTagging | null>(null)
const kpTaggingSuggestedIds = ref('')
const kpTaggingTarget = ref<QuestionSubmissionVO | null>(null)
const applyingKP = ref(false)

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
    if (res.code === 0 && res.data) {
      submissions.value = res.data.records
      total.value = res.data.total
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

const handleImport = async (row: QuestionSubmissionVO) => {
  try {
    await ElMessageBox.confirm(
      `确认将投稿 #${row.id} 入库为正式题目？入库后投稿状态将变为"已入库"。`,
      '确认入库',
      { type: 'warning' }
    )
    const res = await importSubmission(row.id)
    if (res.code === 0 && res.data) {
      ElMessage.success('入库成功，题目ID: ' + res.data.importedQuestionId)
      loadSubmissions()
      loadStats()
    } else {
      ElMessage.error(res.message || '入库失败')
    }
  } catch { /* cancelled */ }
}

const goToQuestion = (id: number) => {
  showDetailDialog.value = false
  router.push({ path: '/admin/questions', query: { highlight: id } })
}

// ========== AI 质检 ==========

const handleQualityCheck = async (row: QuestionSubmissionVO) => {
  qualityResult.value = null
  qualityLoading.value = true
  showQualityDialog.value = true
  try {
    const res = await qualityCheckSubmission(row.id)
    if (res.code === 0 && res.data) {
      qualityResult.value = res.data
    } else {
      ElMessage.error(res.message || '质检失败')
      showQualityDialog.value = false
    }
  } catch {
    ElMessage.error('质检请求失败')
    showQualityDialog.value = false
  } finally {
    qualityLoading.value = false
  }
}

const recommendationLabel = (rec: string) => {
  const map: Record<string, string> = { APPROVE: '推荐通过', REVISE: '建议修改', REJECT: '建议拒绝' }
  return map[rec] || rec
}

const recommendationType = (rec: string) => {
  const map: Record<string, string> = { APPROVE: 'success', REVISE: 'warning', REJECT: 'danger' }
  return (map[rec] || 'info') as any
}

const checkStatusLabel = (status: string) => {
  const map: Record<string, string> = { PASS: '通过', WARNING: '警告', FAIL: '不通过' }
  return map[status] || status
}

const checkStatusType = (status: string) => {
  const map: Record<string, string> = { PASS: 'success', WARNING: 'warning', FAIL: 'danger' }
  return (map[status] || 'info') as any
}

const confidenceLabel = (c: string) => {
  const map: Record<string, string> = { HIGH: '高度相关', MEDIUM: '中等相关', LOW: '可能相关' }
  return map[c] || c
}

const confidenceType = (c: string) => {
  const map: Record<string, string> = { HIGH: 'success', MEDIUM: '', LOW: 'info' }
  return (map[c] || 'info') as any
}

const qualityCheckItems = computed(() => {
  if (!qualityResult.value) return []
  const r = qualityResult.value
  return [
    { label: '格式规范', status: r.formatCheck.status, detail: r.formatCheck.detail },
    { label: '内容完整性', status: r.completenessCheck.status, detail: r.completenessCheck.detail },
    { label: '答案正确性', status: r.answerCheck.status, detail: r.answerCheck.detail },
    { label: '解析质量', status: r.analysisCheck.status, detail: r.analysisCheck.detail },
    { label: '知识点相关性', status: r.knowledgePointCheck.status, detail: r.knowledgePointCheck.detail },
  ]
})

// ========== AI 知识点标注 ==========

const handleKPTagging = async (row: QuestionSubmissionVO) => {
  kpTaggingResult.value = null
  kpTaggingSuggestedIds.value = ''
  kpTaggingTarget.value = row
  kpTaggingLoading.value = true
  showKPTaggingDialog.value = true
  try {
    const res = await kpTaggingSubmission(row.id)
    if (res.code === 0 && res.data) {
      kpTaggingResult.value = res.data
      kpTaggingSuggestedIds.value = res.data.suggestedIds
    } else {
      ElMessage.error(res.message || '知识点标注失败')
      showKPTaggingDialog.value = false
    }
  } catch {
    ElMessage.error('标注请求失败')
    showKPTaggingDialog.value = false
  } finally {
    kpTaggingLoading.value = false
  }
}

const handleApplyKP = async () => {
  if (!kpTaggingTarget.value || !kpTaggingSuggestedIds.value) {
    ElMessage.warning('没有可应用的知识点')
    return
  }
  applyingKP.value = true
  try {
    const res = await applyKnowledgePoints(kpTaggingTarget.value.id, kpTaggingSuggestedIds.value)
    if (res.code === 0) {
      ElMessage.success('知识点已应用到投稿')
      showKPTaggingDialog.value = false
      loadSubmissions()
    } else {
      ElMessage.error(res.message || '应用失败')
    }
  } finally {
    applyingKP.value = false
  }
}

// ========== AI 难度评估 ==========

const handleDifficultyAssessment = async (row: QuestionSubmissionVO) => {
  difficultyResult.value = null
  difficultyLoading.value = true
  showDifficultyDialog.value = true
  try {
    const res = await assessDifficulty(row.id)
    if (res.code === 0 && res.data) {
      difficultyResult.value = res.data
    } else {
      ElMessage.error(res.message || '难度评估失败')
      showDifficultyDialog.value = false
    }
  } catch {
    ElMessage.error('难度评估请求失败')
    showDifficultyDialog.value = false
  } finally {
    difficultyLoading.value = false
  }
}

const difficultyConfidenceLabel = (c: string) => {
  const map: Record<string, string> = { HIGH: '高度可信', MEDIUM: '较为可信', LOW: '仅供参考' }
  return map[c] || c
}

const difficultyConfidenceType = (c: string) => {
  const map: Record<string, string> = { HIGH: 'success', MEDIUM: '', LOW: 'info' }
  return (map[c] || 'info') as any
}

const impactLabel = (impact: string) => {
  const map: Record<string, string> = { INCREASE: '↑ 增难', DECREASE: '↓ 降难', NEUTRAL: '— 中性' }
  return map[impact] || impact
}

const impactType = (impact: string) => {
  const map: Record<string, string> = { INCREASE: 'danger', DECREASE: 'success', NEUTRAL: 'info' }
  return (map[impact] || 'info') as any
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
