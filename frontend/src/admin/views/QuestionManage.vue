<template>
  <div class="question-manage admin-page">
    <header class="admin-page-header">
      <div>
        <p class="admin-page-kicker">QUESTION BANK</p>
        <h2>题目管理</h2>
        <p class="admin-page-description">
          维护正式题库、导入导出和内容复审，重点关注题目来源、状态与 AI 学习资产缓存。
        </p>
      </div>
      <div class="admin-header-actions">
        <el-dropdown trigger="click">
          <el-button :icon="Download"
            >下载模板 <el-icon class="el-icon--right"><ArrowDown /></el-icon
          ></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="questionImportExport?.downloadExcelTemplate()"
                >Excel 模板 (.xlsx)</el-dropdown-item
              >
              <el-dropdown-item @click="questionImportExport?.downloadMarkdownTemplate()"
                >Markdown 模板 (.md)</el-dropdown-item
              >
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button :icon="Upload" @click="questionImportExport?.openImport()">导入题目</el-button>
        <el-button :icon="FolderOpened" @click="questionImportExport?.exportQuestions()">导出题目</el-button>
        <el-button :icon="Warning" @click="openCorrectionDrawer">纠错记录</el-button>
        <el-button type="primary" :icon="Plus" @click="openQuestionEditor()">新增题目</el-button>
      </div>
    </header>

    <section class="admin-summary-grid">
      <el-card v-for="item in questionStats" :key="item.label" shadow="never" class="admin-summary-card">
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
      <div class="admin-toolbar">
        <div class="admin-filter-group">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索题干内容"
            :prefix-icon="Search"
            clearable
            style="width: 220px"
            @clear="fetchQuestions"
            @keyup.enter="fetchQuestions"
          />
          <el-select
            v-model="filters.questionType"
            placeholder="题型"
            clearable
            style="width: 130px"
            @change="fetchQuestions"
          >
            <el-option label="单选题" value="SINGLE_CHOICE" />
            <el-option label="多选题" value="MULTIPLE_CHOICE" />
            <el-option label="判断题" value="TRUE_FALSE" />
            <el-option label="填空题" value="FILL_BLANK" />
            <el-option label="简答题" value="SHORT_ANSWER" />
          </el-select>
          <el-select
            v-model="filters.courseId"
            placeholder="所属课程"
            clearable
            style="width: 180px"
            @change="fetchQuestions"
          >
            <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-select
            v-model="filters.difficulty"
            placeholder="难度"
            clearable
            style="width: 110px"
            @change="fetchQuestions"
          >
            <el-option v-for="d in 5" :key="d" :label="'⭐'.repeat(d)" :value="d" />
          </el-select>
          <el-select
            v-model="filters.sourceType"
            placeholder="来源"
            clearable
            style="width: 130px"
            @change="fetchQuestions"
          >
            <el-option label="手动创建" value="MANUAL" />
            <el-option label="投稿入库" value="SUBMISSION" />
            <el-option label="Excel导入" value="EXCEL_IMPORT" />
            <el-option label="Markdown导入" value="MARKDOWN_IMPORT" />
            <el-option label="AI生成" value="AI_GENERATED" />
          </el-select>
          <el-button :icon="Search" @click="fetchQuestions">查询</el-button>
          <el-button :icon="Connection" :loading="duplicateLoading" @click="handleDetectDuplicates">重复检测</el-button>
        </div>
        <span class="table-summary">当前筛选 {{ total }} 道题</span>
      </div>

      <div v-if="selectedQuestions.length" class="admin-bulk-bar">
        <span class="admin-bulk-copy"
          >已选择 <strong>{{ selectedQuestions.length }}</strong> 道题目</span
        >
        <div class="admin-bulk-actions">
          <el-button size="small" :icon="DeleteFilled" @click="handleBulkClearAiCache">批量清缓存</el-button>
          <el-button size="small" type="danger" :icon="Delete" @click="handleBulkDelete">批量删除</el-button>
          <el-button size="small" @click="clearQuestionSelection">清空选择</el-button>
        </div>
      </div>

      <el-table
        ref="questionTableRef"
        :data="questions"
        v-loading="loading"
        stripe
        class="admin-data-table"
        @selection-change="handleQuestionSelectionChange"
      >
        <el-table-column type="selection" width="44" />
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="content" label="题干" min-width="240" show-overflow-tooltip />
        <el-table-column prop="questionType" label="题型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="questionTypeTag((row as QuestionVO).questionType)">
              {{ questionTypeLabel((row as QuestionVO).questionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="courseName" label="课程" width="130" show-overflow-tooltip />
        <el-table-column prop="difficulty" label="难度" width="90" align="center">
          <template #default="{ row }">
            {{ '⭐'.repeat((row as QuestionVO).difficulty) }}
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="来源" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="sourceTypeTag((row as QuestionVO).sourceType)">
              {{ sourceTypeLabel((row as QuestionVO).sourceType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="分值" width="70" align="center" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="(row as QuestionVO).status === 1 ? 'success' : 'info'" size="small">
              {{ (row as QuestionVO).status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="185" fixed="right">
          <template #default="{ row }">
            <div class="admin-row-actions">
              <el-button type="primary" link size="small" :icon="RefreshRight" @click="openReReview(row as QuestionVO)"
                >复审</el-button
              >
              <el-button type="primary" link size="small" :icon="Edit" @click="openQuestionEditor(row as QuestionVO)"
                >编辑</el-button
              >
              <el-dropdown
                trigger="click"
                @command="(command) => handleQuestionRowCommand(command as string, row as QuestionVO)"
              >
                <el-button link size="small" :icon="MoreFilled">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="versions" :icon="Clock">版本记录</el-dropdown-item>
                    <el-dropdown-item command="cache" :icon="DeleteFilled">清缓存</el-dropdown-item>
                    <el-dropdown-item command="delete" :icon="Delete" class="danger-dropdown-item"
                      >删除题目</el-dropdown-item
                    >
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty class="admin-table-empty" description="没有匹配的题目">
            <el-button type="primary" :icon="Plus" @click="openQuestionEditor()">新增题目</el-button>
          </el-empty>
        </template>
      </el-table>

      <div class="admin-pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="fetchQuestions"
          @size-change="fetchQuestions"
        />
      </div>
    </el-card>

    <!-- 复审弹窗 -->
    <el-dialog v-model="reReviewVisible" title="题目复审" width="700px" destroy-on-close>
      <div v-if="reReviewQuestion" style="margin-bottom: 16px">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="题目ID">{{ reReviewQuestion.id }}</el-descriptions-item>
          <el-descriptions-item label="来源">
            <el-tag size="small" :type="sourceTypeTag(reReviewQuestion.sourceType)">
              {{ sourceTypeLabel(reReviewQuestion.sourceType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="题型">{{
            questionTypeLabel(reReviewQuestion.questionType)
          }}</el-descriptions-item>
          <el-descriptions-item label="难度">{{ '⭐'.repeat(reReviewQuestion.difficulty) }}</el-descriptions-item>
          <el-descriptions-item label="累计复审">{{ reReviewQuestion.reviewRounds ?? 0 }} 次</el-descriptions-item>
          <el-descriptions-item label="下次复审">{{
            reReviewQuestion.nextReviewTime ?? '未设置'
          }}</el-descriptions-item>
          <el-descriptions-item label="题干" :span="2">
            <div style="max-height: 120px; overflow-y: auto; white-space: pre-wrap">{{ reReviewQuestion.content }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <el-form :model="reReviewForm" label-width="90px">
        <div class="review-suggestion-actions">
          <el-button :icon="DataAnalysis" :loading="reviewSuggestionLoading" @click="handleReviewSuggestion">
            AI 复审建议
          </el-button>
          <span v-if="reviewSuggestion" class="review-suggestion-meta">
            建议：{{ reviewActionLabel(reviewSuggestion.recommendation) }} · 置信分
            {{ reviewSuggestion.confidenceScore }}
          </span>
        </div>

        <el-alert
          v-if="reviewSuggestion"
          class="review-suggestion-panel"
          :type="
            reviewSuggestion.recommendation === 'REJECT'
              ? 'error'
              : reviewSuggestion.recommendation === 'REVISE'
                ? 'warning'
                : 'success'
          "
          :closable="false"
          show-icon
        >
          <template #title>{{ reviewSuggestion.summary }}</template>
          <div class="review-suggestion-content">
            <p v-if="reviewSuggestion.answerAnalysis">{{ reviewSuggestion.answerAnalysis }}</p>
            <p v-if="reviewSuggestion.knowledgeAnalysis">{{ reviewSuggestion.knowledgeAnalysis }}</p>
            <div v-if="reviewSuggestion.riskPoints?.length">
              <strong>风险点</strong>
              <ul>
                <li v-for="item in reviewSuggestion.riskPoints" :key="item">{{ item }}</li>
              </ul>
            </div>
            <div v-if="reviewSuggestion.suggestions?.length">
              <strong>修订建议</strong>
              <ul>
                <li v-for="item in reviewSuggestion.suggestions" :key="item">{{ item }}</li>
              </ul>
            </div>
            <el-button size="small" text type="primary" @click="applyReviewSuggestion">应用到表单</el-button>
          </div>
        </el-alert>

        <el-form-item label="复审动作">
          <el-radio-group v-model="reReviewForm.action">
            <el-radio-button value="APPROVE">通过</el-radio-button>
            <el-radio-button value="REVISE">修订</el-radio-button>
            <el-radio-button value="REJECT">废弃</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="reReviewForm.action === 'REVISE'" label="修订内容">
          <el-input v-model="reReviewForm.newContent" type="textarea" :rows="3" placeholder="修订后的题干" />
        </el-form-item>
        <el-form-item v-if="reReviewForm.action === 'REVISE'" label="修订难度">
          <el-rate v-model="reReviewForm.newDifficulty" :max="5" />
        </el-form-item>
        <el-form-item label="复审意见">
          <el-input v-model="reReviewForm.comment" type="textarea" :rows="2" placeholder="请输入复审意见" />
        </el-form-item>
      </el-form>

      <div v-if="reviewRecords.length > 0" style="margin-top: 12px">
        <h4 style="margin-bottom: 8px">历史复审记录</h4>
        <el-timeline>
          <el-timeline-item
            v-for="record in reviewRecords"
            :key="record.id"
            :timestamp="record.createTime"
            placement="top"
          >
            <el-card shadow="never" body-style="padding: 8px 12px;">
              <div style="display: flex; gap: 8px; align-items: center">
                <el-tag
                  size="small"
                  :type="record.action === 'APPROVE' ? 'success' : record.action === 'REJECT' ? 'danger' : 'warning'"
                >
                  {{ record.action === 'APPROVE' ? '通过' : record.action === 'REVISE' ? '修订' : '废弃' }}
                </el-tag>
                <span style="font-size: 12px; color: #909399">{{ record.reviewerName }} · {{ record.reviewType }}</span>
              </div>
              <div style="margin-top: 4px; font-size: 13px">{{ record.comment }}</div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>

      <template #footer>
        <el-button @click="reReviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reReviewLoading" @click="handleReReview">提交复审</el-button>
      </template>
    </el-dialog>

    <QuestionEditorDialog ref="questionEditor" :courses="courseList" @saved="fetchQuestions" />
    <QuestionImportExport ref="questionImportExport" :filters="filters" @imported="fetchQuestions" />

    <QuestionGovernanceDrawers
      v-model:duplicate-visible="duplicateDrawerVisible"
      :duplicate-groups="duplicateGroups"
      v-model:correction-visible="correctionDrawerVisible"
      :correction-loading="correctionLoading"
      :correction-reports="correctionReports"
      v-model:correction-status="correctionFilters.status"
      v-model:correction-page-num="correctionPageNum"
      v-model:correction-page-size="correctionPageSize"
      :correction-total="correctionTotal"
      v-model:version-visible="versionDrawerVisible"
      :version-loading="versionLoading"
      :version-question="versionQuestion"
      :question-versions="questionVersions"
      @edit="openQuestionEditor"
      @review="openReReview"
      @refresh-corrections="fetchCorrectionReports"
      @process-correction="handleProcessCorrection"
      @refresh-versions="fetchQuestionVersions"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import {
  Plus,
  Search,
  Delete,
  Download,
  Upload,
  FolderOpened,
  ArrowDown,
  Edit,
  RefreshRight,
  DeleteFilled,
  Collection,
  DataAnalysis,
  DocumentChecked,
  MoreFilled,
  Connection,
  Warning,
  Clock,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance } from 'element-plus'
import {
  getAdminQuestionPage,
  deleteQuestion,
  getReviewRecords,
  getReviewSuggestion,
  performReReview,
  detectDuplicateQuestions,
  getQuestionVersions,
  getAdminQuestionCorrectionReports,
  processQuestionCorrectionReport,
  type QuestionVO,
  type QuestionReviewRecordVO,
  type QuestionReviewSuggestionVO,
  type QuestionDuplicateGroupVO,
  type QuestionCorrectionReportVO,
  type QuestionVersionVO,
} from '@/api/question'
import { clearAssetCache } from '@/api/ai'
import { getAllCourses, type CourseVO } from '@/api/course'
import {
  questionTypeLabel,
  questionTypeTag,
  reviewActionLabel,
  sourceTypeLabel,
  sourceTypeTag,
} from './question/questionManagePresentation'
import QuestionGovernanceDrawers from './question/QuestionGovernanceDrawers.vue'
import QuestionEditorDialog from './question/QuestionEditorDialog.vue'
import QuestionImportExport from './question/QuestionImportExport.vue'

const questions = ref<QuestionVO[]>([])
const questionTableRef = ref<TableInstance>()
const selectedQuestions = ref<QuestionVO[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  keyword: '',
  questionType: '' as string,
  courseId: null as number | null,
  difficulty: null as number | null,
  sourceType: '' as string,
})

// 课程列表
const courseList = ref<CourseVO[]>([])
const questionEditor = ref<InstanceType<typeof QuestionEditorDialog>>()
const questionImportExport = ref<InstanceType<typeof QuestionImportExport>>()

// 复审相关
const reReviewVisible = ref(false)
const reReviewQuestion = ref<QuestionVO | null>(null)
const reReviewLoading = ref(false)
const reviewRecords = ref<QuestionReviewRecordVO[]>([])
const reviewSuggestion = ref<QuestionReviewSuggestionVO | null>(null)
const reviewSuggestionLoading = ref(false)
const duplicateDrawerVisible = ref(false)
const duplicateLoading = ref(false)
const duplicateGroups = ref<QuestionDuplicateGroupVO[]>([])
const correctionDrawerVisible = ref(false)
const correctionLoading = ref(false)
const correctionReports = ref<QuestionCorrectionReportVO[]>([])
const correctionPageNum = ref(1)
const correctionPageSize = ref(10)
const correctionTotal = ref(0)
const correctionFilters = reactive({
  status: 'OPEN',
})
const versionDrawerVisible = ref(false)
const versionLoading = ref(false)
const versionQuestion = ref<QuestionVO | null>(null)
const questionVersions = ref<QuestionVersionVO[]>([])
const reReviewForm = reactive({
  action: 'APPROVE',
  newContent: '',
  newDifficulty: 3,
  comment: '',
})

const questionStats = computed(() => {
  const enabled = questions.value.filter((q) => q.status === 1).length
  const reviewable = questions.value.filter((q) => q.sourceType && q.sourceType !== 'MANUAL').length
  const avgScore = questions.value.length
    ? Math.round(questions.value.reduce((sum, q) => sum + (q.score || 0), 0) / questions.value.length)
    : 0
  return [
    { label: '筛选总量', value: total.value, note: `当前页 ${questions.value.length} 道`, icon: Collection },
    { label: '当前页启用', value: enabled, note: `${questions.value.length - enabled} 道禁用`, icon: DocumentChecked },
    { label: '来源追踪', value: reviewable, note: '当前页非手动来源', icon: RefreshRight },
    { label: '平均分值', value: avgScore || '-', note: '当前页题目均分', icon: DataAnalysis },
  ]
})

const handleQuestionRowCommand = async (command: string, question: QuestionVO) => {
  if (command === 'cache') {
    try {
      await ElMessageBox.confirm('确定清除该题目的 AI 学习资产缓存？', '清除缓存', {
        type: 'warning',
        confirmButtonText: '清除',
        cancelButtonText: '取消',
      })
      await handleClearAiCache(question.id)
    } catch {
      // 用户取消确认时不提示错误。
    }
    return
  }
  if (command === 'versions') {
    await openVersionDrawer(question)
    return
  }
  if (command === 'delete') {
    try {
      await ElMessageBox.confirm('确定删除该题目？', '删除题目', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      })
      await handleDelete(question.id)
    } catch {
      // 用户取消确认时不提示错误。
    }
  }
}

const handleQuestionSelectionChange = (selection: QuestionVO[]) => {
  selectedQuestions.value = selection
}

const clearQuestionSelection = () => {
  questionTableRef.value?.clearSelection()
}

const openQuestionEditor = (question?: QuestionVO) => questionEditor.value?.open(question)

// 打开复审弹窗
async function openReReview(question: QuestionVO) {
  reReviewQuestion.value = question
  reReviewForm.action = 'APPROVE'
  reReviewForm.newContent = question.content
  reReviewForm.newDifficulty = question.difficulty
  reReviewForm.comment = ''
  reviewRecords.value = []
  reviewSuggestion.value = null
  reReviewVisible.value = true
  try {
    const res = await getReviewRecords(question.id)
    reviewRecords.value = res.data
  } catch {
    // ignore
  }
}

async function handleReviewSuggestion() {
  if (!reReviewQuestion.value) return
  reviewSuggestionLoading.value = true
  try {
    const res = await getReviewSuggestion(reReviewQuestion.value.id)
    reviewSuggestion.value = res.data
    ElMessage.success('AI 复审建议已生成')
  } catch {
    // error handled by interceptor
  } finally {
    reviewSuggestionLoading.value = false
  }
}

function applyReviewSuggestion() {
  if (!reviewSuggestion.value) return
  reReviewForm.action = reviewSuggestion.value.recommendation
  if (reviewSuggestion.value.recommendation === 'REVISE') {
    reReviewForm.newContent = reviewSuggestion.value.suggestedContent || reReviewQuestion.value?.content || ''
    reReviewForm.newDifficulty = reviewSuggestion.value.suggestedDifficulty || reReviewQuestion.value?.difficulty || 3
  }
  reReviewForm.comment = reviewSuggestion.value.summary
  ElMessage.success('已填入复审表单')
}

// 提交复审
async function handleReReview() {
  if (!reReviewForm.comment.trim()) {
    ElMessage.warning('请输入复审意见')
    return
  }
  if (reReviewForm.action === 'REVISE' && !reReviewForm.newContent.trim()) {
    ElMessage.warning('修订时新题干不能为空')
    return
  }
  reReviewLoading.value = true
  try {
    await performReReview(reReviewQuestion.value!.id, {
      action: reReviewForm.action,
      newContent: reReviewForm.action === 'REVISE' ? reReviewForm.newContent : undefined,
      newDifficulty: reReviewForm.action === 'REVISE' ? reReviewForm.newDifficulty : undefined,
      comment: reReviewForm.comment,
    })
    ElMessage.success('复审完成')
    reReviewVisible.value = false
    fetchQuestions()
  } catch {
    // error handled by interceptor
  } finally {
    reReviewLoading.value = false
  }
}

async function fetchQuestions() {
  loading.value = true
  try {
    const res = await getAdminQuestionPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: filters.keyword || undefined,
      questionType: filters.questionType || undefined,
      courseId: filters.courseId || undefined,
      difficulty: filters.difficulty || undefined,
      sourceType: filters.sourceType || undefined,
    })
    questions.value = res.data.records
    total.value = res.data.total
    selectedQuestions.value = []
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

async function handleDetectDuplicates() {
  duplicateLoading.value = true
  try {
    const res = await detectDuplicateQuestions({
      courseId: filters.courseId || undefined,
      questionType: filters.questionType || undefined,
      minSimilarity: 92,
      limit: 20,
    })
    duplicateGroups.value = res.data
    duplicateDrawerVisible.value = true
    if (res.data.length > 0) {
      ElMessage.warning(`发现 ${res.data.length} 组疑似重复题目`)
    } else {
      ElMessage.success('未发现疑似重复题目')
    }
  } catch {
    // 错误已在拦截器中处理
  } finally {
    duplicateLoading.value = false
  }
}

async function openCorrectionDrawer() {
  correctionDrawerVisible.value = true
  correctionPageNum.value = 1
  await fetchCorrectionReports()
}

async function fetchCorrectionReports() {
  correctionLoading.value = true
  try {
    const res = await getAdminQuestionCorrectionReports({
      pageNum: correctionPageNum.value,
      pageSize: correctionPageSize.value,
      status: correctionFilters.status || undefined,
    })
    correctionReports.value = res.data.records
    correctionTotal.value = res.data.total
  } catch {
    // 错误已在拦截器中处理
  } finally {
    correctionLoading.value = false
  }
}

async function handleProcessCorrection(report: QuestionCorrectionReportVO, status: 'RESOLVED' | 'REJECTED') {
  const title = status === 'RESOLVED' ? '标记已处理' : '驳回纠错'
  const message = status === 'RESOLVED' ? '请输入处理说明' : '请输入驳回原因'
  try {
    const result = await ElMessageBox.prompt(message, title, {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPattern: /\S+/,
      inputErrorMessage: '处理说明不能为空',
    })
    await processQuestionCorrectionReport(report.id, {
      status,
      handlerComment: result.value.trim(),
    })
    ElMessage.success(status === 'RESOLVED' ? '已标记处理完成' : '已驳回纠错')
    await fetchCorrectionReports()
  } catch {
    // 用户取消确认时不提示错误。
  }
}

async function openVersionDrawer(question: QuestionVO) {
  versionQuestion.value = question
  versionDrawerVisible.value = true
  await fetchQuestionVersions()
}

async function fetchQuestionVersions() {
  if (!versionQuestion.value) return
  versionLoading.value = true
  try {
    const res = await getQuestionVersions(versionQuestion.value.id)
    questionVersions.value = res.data
  } catch {
    // 错误已在拦截器中处理
  } finally {
    versionLoading.value = false
  }
}

async function fetchCourses() {
  try {
    const res = await getAllCourses()
    courseList.value = res.data
  } catch {
    // ignore
  }
}

async function handleDelete(id: number) {
  try {
    await deleteQuestion(id)
    ElMessage.success('删除成功')
    fetchQuestions()
  } catch {
    // 错误已在拦截器中处理
  }
}

async function handleBulkDelete() {
  if (!selectedQuestions.value.length) return
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selectedQuestions.value.length} 道题目？此操作不可恢复。`,
      '批量删除题目',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )
    loading.value = true
    const targets = [...selectedQuestions.value]
    const results = await Promise.allSettled(targets.map((question) => deleteQuestion(question.id)))
    const failed = results.filter((result) => result.status === 'rejected').length
    if (failed > 0) {
      ElMessage.warning(`已删除 ${targets.length - failed} 道题，${failed} 道处理失败`)
    } else {
      ElMessage.success(`已删除 ${targets.length} 道题`)
    }
    await fetchQuestions()
  } catch {
    // 用户取消确认时不提示错误。
  } finally {
    loading.value = false
  }
}

async function handleClearAiCache(questionId: number) {
  try {
    await clearAssetCache(questionId)
    ElMessage.success('AI 学习资产缓存已清除')
  } catch {
    ElMessage.error('清除失败')
  }
}

async function handleBulkClearAiCache() {
  if (!selectedQuestions.value.length) return
  try {
    await ElMessageBox.confirm(
      `确定清除选中 ${selectedQuestions.value.length} 道题目的 AI 学习资产缓存？`,
      '批量清除缓存',
      {
        type: 'warning',
        confirmButtonText: '清除',
        cancelButtonText: '取消',
      },
    )
    loading.value = true
    const targets = [...selectedQuestions.value]
    const results = await Promise.allSettled(targets.map((question) => clearAssetCache(question.id)))
    const failed = results.filter((result) => result.status === 'rejected').length
    if (failed > 0) {
      ElMessage.warning(`已清除 ${targets.length - failed} 道题缓存，${failed} 道处理失败`)
    } else {
      ElMessage.success(`已清除 ${targets.length} 道题缓存`)
    }
    clearQuestionSelection()
  } catch {
    // 用户取消确认时不提示错误。
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchQuestions()
  fetchCourses()
})
</script>

<style scoped>
.table-summary {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.review-suggestion-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.review-suggestion-meta {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.review-suggestion-panel {
  margin-bottom: 16px;
}

.review-suggestion-content {
  color: var(--lp-text-regular);
  font-size: 13px;
  line-height: 1.6;
}

.review-suggestion-content p {
  margin: 6px 0;
}

.review-suggestion-content ul {
  margin: 6px 0 8px;
  padding-left: 18px;
}

@media (max-width: 720px) {
  .review-suggestion-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
