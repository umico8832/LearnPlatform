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

    <QuestionReviewDialog ref="questionReviewDialog" @reviewed="fetchQuestions" />
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
import { ref, reactive, computed } from 'vue'
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
import {
  detectDuplicateQuestions,
  getQuestionVersions,
  getAdminQuestionCorrectionReports,
  processQuestionCorrectionReport,
  type QuestionVO,
  type QuestionDuplicateGroupVO,
  type QuestionCorrectionReportVO,
  type QuestionVersionVO,
} from '@/api/question'
import {
  questionTypeLabel,
  questionTypeTag,
  sourceTypeLabel,
  sourceTypeTag,
} from './question/questionManagePresentation'
import QuestionGovernanceDrawers from './question/QuestionGovernanceDrawers.vue'
import QuestionEditorDialog from './question/QuestionEditorDialog.vue'
import QuestionImportExport from './question/QuestionImportExport.vue'
import QuestionReviewDialog from './question/QuestionReviewDialog.vue'
import { useQuestionAdminList } from './question/useQuestionAdminList'

const {
  questions,
  questionTableRef,
  selectedQuestions,
  loading,
  pageNum,
  pageSize,
  total,
  filters,
  courseList,
  fetchQuestions,
  handleQuestionSelectionChange,
  clearQuestionSelection,
  handleDelete,
  handleBulkDelete,
  handleClearAiCache,
  handleBulkClearAiCache,
} = useQuestionAdminList()
const questionEditor = ref<InstanceType<typeof QuestionEditorDialog>>()
const questionImportExport = ref<InstanceType<typeof QuestionImportExport>>()
const questionReviewDialog = ref<InstanceType<typeof QuestionReviewDialog>>()

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

const openQuestionEditor = (question?: QuestionVO) => questionEditor.value?.open(question)

function openReReview(question: QuestionVO) {
  void questionReviewDialog.value?.open(question)
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
</script>

<style scoped>
.table-summary {
  color: var(--lp-text-muted);
  font-size: 13px;
}
</style>
