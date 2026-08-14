<template>
  <div class="exam-list-container page-container">
    <section class="exam-hero">
      <div>
        <span class="section-kicker">考试测评</span>
        <h2>考试中心</h2>
        <p>先参加已发布试卷，完成后在考试记录中查看得分和答题明细。</p>
      </div>
      <div class="hero-summary">
        <span>{{ total }} 份可用试卷</span>
        <span>{{ recordsTotal }} 条考试记录</span>
        <el-button type="primary" :icon="Upload" @click="openImportDialog">导入私有试卷</el-button>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="exam-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="可用试卷" name="papers">
        <div v-loading="loading" class="paper-list">
          <el-empty v-if="!loading && papers.length === 0" description="暂无可用试卷" />

          <el-card v-for="paper in papers" :key="paper.id" class="exam-card" shadow="never">
            <div class="exam-card-header">
              <div>
                <div class="exam-title-line">
                  <h3>{{ paper.title }}</h3>
                  <el-tag :type="paperTypeTag(paper)" size="small">{{ paperTypeLabel(paper) }}</el-tag>
                </div>
                <p class="exam-desc" v-if="paper.description">{{ paper.description }}</p>
              </div>
              <el-tag type="success" size="small">可参加</el-tag>
            </div>
            <div v-if="isVerifiedOfficial(paper)" class="official-source">
              <strong>{{ paper.examYear }} · {{ paper.examName }}</strong>
              <span>来源：{{ paper.sourceReference }}</span>
            </div>
            <div v-else-if="paper.visibility === 'PRIVATE'" class="private-source">
              <strong>仅你可见 · 已确认导入</strong>
              <el-button link type="primary" @click="showOriginalSource(paper.id)">查看原始资料</el-button>
            </div>
            <div class="exam-metrics">
              <span v-if="paper.courseName"
                ><el-icon><Reading /></el-icon>{{ paper.courseName }}</span
              >
              <span
                ><el-icon><Document /></el-icon>{{ paper.questionCount }} 题</span
              >
              <span
                ><el-icon><Timer /></el-icon>{{ paper.duration }} 分钟</span
              >
              <span
                ><el-icon><Medal /></el-icon>{{ paper.totalScore }} 分</span
              >
            </div>
            <div class="exam-actions">
              <el-button
                v-if="paper.courseId"
                :icon="Reading"
                @click="handleStartLearning(paper.id)"
                :loading="learningId === paper.id"
              >
                学习模式
              </el-button>
              <el-button
                type="primary"
                :icon="EditPen"
                @click="handleStartExam(paper.id)"
                :loading="startingId === paper.id"
              >
                考试模式
              </el-button>
            </div>
          </el-card>
        </div>

        <div class="pagination-wrapper" v-if="total > 0">
          <el-pagination
            v-model:current-page="pageNum"
            :total="total"
            :page-size="10"
            layout="total, prev, pager, next"
            @current-change="loadPapers"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="考试记录" name="records">
        <div v-loading="recordsLoading" class="record-panel">
          <el-empty v-if="!recordsLoading && records.length === 0" description="暂无考试记录" />

          <el-table v-else :data="records as any" stripe>
            <el-table-column prop="examTitle" label="试卷名称" min-width="200" />
            <el-table-column label="得分" width="120">
              <template #default="{ row }">
                <span :class="['score-text', getScoreClass(row as ExamRecordVO)]">
                  {{ formatScore(row as ExamRecordVO) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="recordStatusTag((row as ExamRecordVO).status)" size="small">
                  {{ recordStatusLabel((row as ExamRecordVO).status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="开始时间" width="180">
              <template #default="{ row }">{{ formatTime((row as ExamRecordVO).startTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="[1, 3].includes((row as ExamRecordVO).status)"
                  type="primary"
                  link
                  size="small"
                  :icon="View"
                  @click="viewResult((row as ExamRecordVO).id)"
                >
                  查看结果
                </el-button>
                <el-button
                  v-else-if="(row as ExamRecordVO).status === 0"
                  type="warning"
                  link
                  size="small"
                  :icon="EditPen"
                  @click="continueExam(row as ExamRecordVO)"
                >
                  继续考试
                </el-button>
                <span v-else class="record-finished-hint">不可继续</span>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="records.length > 0" class="record-mobile-list" aria-label="考试记录">
            <article v-for="record in records" :key="record.id" class="record-mobile-card">
              <div class="record-mobile-header">
                <h3>{{ record.examTitle }}</h3>
                <el-tag :type="recordStatusTag(record.status)" size="small">
                  {{ recordStatusLabel(record.status) }}
                </el-tag>
              </div>
              <dl class="record-mobile-meta">
                <div>
                  <dt>得分</dt>
                  <dd :class="['score-text', getScoreClass(record)]">{{ formatScore(record) }}</dd>
                </div>
                <div>
                  <dt>开始时间</dt>
                  <dd>{{ formatTime(record.startTime) }}</dd>
                </div>
              </dl>
              <div class="record-mobile-action">
                <el-button
                  v-if="[1, 3].includes(record.status)"
                  type="primary"
                  :icon="View"
                  @click="viewResult(record.id)"
                >
                  查看结果
                </el-button>
                <el-button v-else-if="record.status === 0" type="warning" :icon="EditPen" @click="continueExam(record)">
                  继续考试
                </el-button>
                <span v-else class="record-finished-hint">考试已超时，不可继续</span>
              </div>
            </article>
          </div>
        </div>

        <div class="pagination-wrapper" v-if="recordsTotal > 0">
          <el-pagination
            v-model:current-page="recordsPageNum"
            :total="recordsTotal"
            :page-size="10"
            layout="total, prev, pager, next"
            @current-change="loadRecords"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="importDialogVisible" title="导入私有试卷" width="min(760px, 92vw)" @closed="resetImport">
      <el-alert
        title="仅支持结构化 Markdown 或文本；预览确认后才会保存，内容默认仅本人可见。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form v-if="!importPreview" label-position="top" class="import-form">
        <div class="import-grid">
          <el-form-item label="试卷标题">
            <el-input v-model="importForm.title" maxlength="200" />
          </el-form-item>
          <el-form-item label="所属课程">
            <el-select v-model="importForm.courseId" filterable placeholder="选择课程">
              <el-option v-for="course in courses" :key="course.id" :label="course.name" :value="course.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="原始资料名称">
            <el-input v-model="importForm.sourceName" maxlength="255" placeholder="例如：数据结构练习.md" />
          </el-form-item>
          <el-form-item label="格式">
            <el-select v-model="importForm.sourceFormat">
              <el-option label="Markdown" value="MARKDOWN" />
              <el-option label="结构化文本" value="TEXT" />
            </el-select>
          </el-form-item>
          <el-form-item label="考试时长（分钟）">
            <el-input-number v-model="importForm.duration" :min="1" :max="600" />
          </el-form-item>
        </div>
        <el-form-item label="原始内容">
          <el-input
            v-model="importForm.content"
            type="textarea"
            :rows="14"
            maxlength="100000"
            show-word-limit
            :placeholder="importPlaceholder"
          />
        </el-form-item>
      </el-form>

      <section v-else class="import-preview">
        <div class="preview-summary">
          <strong>{{ importPreview.title }}</strong>
          <span
            >{{ importPreview.questionCount }} 题 · {{ importPreview.totalScore }} 分 ·
            {{ importPreview.duration }} 分钟</span
          >
        </div>
        <article v-for="(question, index) in importPreview.questions" :key="index" class="preview-question">
          <div>
            <strong>{{ index + 1 }}. {{ question.content }}</strong
            ><el-tag size="small">{{ question.score }} 分</el-tag>
          </div>
          <ul>
            <li v-for="option in question.options" :key="option.label" :class="{ correct: option.correct }">
              {{ option.label }}. {{ option.content }}
            </li>
          </ul>
          <p>确认答案：{{ question.answer }}</p>
        </article>
      </section>

      <template #footer>
        <el-button v-if="importPreview" @click="importPreview = null">返回修改</el-button>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button v-if="!importPreview" type="primary" :loading="previewLoading" @click="previewImport"
          >解析并预览</el-button
        >
        <el-button v-else type="primary" :loading="confirmLoading" @click="confirmImport">确认导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sourceDialogVisible" title="私有试卷原始资料" width="min(760px, 92vw)">
      <template v-if="privateSource">
        <p class="source-meta">
          {{ privateSource.sourceName }} · {{ privateSource.sourceFormat }} · {{ privateSource.contentHash }}
        </p>
        <pre class="source-content">{{ privateSource.originalContent }}</pre>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Document, EditPen, Medal, Reading, Timer, Upload, View } from '@element-plus/icons-vue'
import {
  confirmPrivateExamImport,
  getMyExamRecords,
  getPrivateExamSource,
  getPublishedPapers,
  previewPrivateExamImport,
  startExam,
  startExamLearningSession,
} from '@/api/exam'
import type {
  ExamPaperVO,
  ExamRecordVO,
  ExamStatus,
  PrivateExamImportPreview,
  PrivateExamImportRequest,
  PrivateExamSource,
} from '@/api/exam'
import { getAllCourses } from '@/api/course'
import type { CourseVO } from '@/api/course'

const router = useRouter()
const route = useRoute()
const activeTab = ref(route.query.tab === 'records' ? 'records' : 'papers')

// 试卷列表
const loading = ref(false)
const papers = ref<ExamPaperVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const startingId = ref<number | null>(null)
const learningId = ref<number | null>(null)
const courseId = Number(route.query.courseId)
const courses = ref<CourseVO[]>([])
const importDialogVisible = ref(false)
const previewLoading = ref(false)
const confirmLoading = ref(false)
const importPreview = ref<PrivateExamImportPreview | null>(null)
const sourceDialogVisible = ref(false)
const privateSource = ref<PrivateExamSource | null>(null)
const emptyImportForm = (): PrivateExamImportRequest => ({
  title: '',
  courseId: Number.isFinite(courseId) && courseId > 0 ? courseId : 0,
  duration: 60,
  sourceName: '',
  sourceFormat: 'MARKDOWN',
  content: '',
})
const importForm = ref<PrivateExamImportRequest>(emptyImportForm())
const importPlaceholder = `## 1. 单选题\n**题干**: 栈遵循哪种访问顺序？\n**选项**:\n- A. 先进先出\n- B. 先进后出\n**答案**: B\n**解析**: 栈遵循 LIFO。\n**分值**: 2`

// 考试记录
const recordsLoading = ref(false)
const records = ref<ExamRecordVO[]>([])
const recordsTotal = ref(0)
const recordsPageNum = ref(1)

onMounted(() => {
  loadPapers()
  loadRecords()
})

const openImportDialog = async () => {
  importDialogVisible.value = true
  if (courses.value.length) return
  try {
    const res = await getAllCourses()
    if (res.code === 0 && res.data) courses.value = res.data
  } catch {
    ElMessage.error('获取课程列表失败')
  }
}

const validateImportForm = () => {
  if (
    !importForm.value.title.trim() ||
    !importForm.value.sourceName.trim() ||
    !importForm.value.courseId ||
    !importForm.value.content.trim()
  ) {
    ElMessage.warning('请完整填写标题、课程、资料名称和原始内容')
    return false
  }
  return true
}

const previewImport = async () => {
  if (!validateImportForm()) return
  previewLoading.value = true
  try {
    const res = await previewPrivateExamImport(importForm.value)
    if (res.code === 0 && res.data) importPreview.value = res.data
    else ElMessage.error(res.message || '解析失败')
  } catch {
    ElMessage.error('解析失败，请检查结构化格式')
  } finally {
    previewLoading.value = false
  }
}

const confirmImport = async () => {
  if (!importPreview.value) return
  confirmLoading.value = true
  try {
    const res = await confirmPrivateExamImport({
      ...importForm.value,
      expectedContentHash: importPreview.value.contentHash,
      confirmed: true,
    })
    if (res.code === 0 && res.data) {
      ElMessage.success('私有试卷已导入')
      importDialogVisible.value = false
      pageNum.value = 1
      await loadPapers()
    } else ElMessage.error(res.message || '导入失败')
  } catch {
    ElMessage.error('导入失败')
  } finally {
    confirmLoading.value = false
  }
}

const showOriginalSource = async (paperId: number) => {
  try {
    const res = await getPrivateExamSource(paperId)
    if (res.code === 0 && res.data) {
      privateSource.value = res.data
      sourceDialogVisible.value = true
    }
  } catch {
    ElMessage.error('原始资料不可用')
  }
}

const resetImport = () => {
  importPreview.value = null
  importForm.value = emptyImportForm()
}

const loadPapers = async () => {
  loading.value = true
  try {
    const res = await getPublishedPapers({
      pageNum: pageNum.value,
      pageSize: 10,
      courseId: Number.isFinite(courseId) && courseId > 0 ? courseId : undefined,
    })
    if (res.code === 0 && res.data) {
      papers.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch {
    ElMessage.error('获取试卷列表失败')
  } finally {
    loading.value = false
  }
}

const loadRecords = async () => {
  recordsLoading.value = true
  try {
    const res = await getMyExamRecords({ pageNum: recordsPageNum.value, pageSize: 10 })
    if (res.code === 0 && res.data) {
      records.value = res.data.records || []
      recordsTotal.value = res.data.total || 0
    }
  } catch {
    ElMessage.error('获取考试记录失败')
  } finally {
    recordsLoading.value = false
  }
}

const handleTabChange = (tab: string | number) => {
  if (tab === 'records') loadRecords()
}

const handleStartExam = async (paperId: number) => {
  startingId.value = paperId
  try {
    const startRes = await startExam(paperId)
    if (startRes.code === 0 && startRes.data) {
      router.push({ name: 'ExamTake', params: { recordId: String(startRes.data.id) } })
    } else {
      ElMessage.error(startRes.message || '开始考试失败')
    }
  } catch {
    ElMessage.error('开始考试失败')
  } finally {
    startingId.value = null
  }
}

const handleStartLearning = async (paperId: number) => {
  learningId.value = paperId
  try {
    const res = await startExamLearningSession(paperId)
    if (res.code === 0 && res.data) {
      router.push({ name: 'ExamLearning', params: { sessionId: String(res.data.id) } })
    } else {
      ElMessage.error(res.message || '开始试卷学习失败')
    }
  } catch {
    ElMessage.error('开始试卷学习失败，请确认课程已加入课程库')
  } finally {
    learningId.value = null
  }
}

const viewResult = (recordId: number) => {
  router.push({ name: 'ExamResult', params: { recordId: String(recordId) } })
}

const continueExam = (record: ExamRecordVO) => {
  router.push({ name: 'ExamTake', params: { recordId: String(record.id) } })
}

const getScoreClass = (row: ExamRecordVO) => {
  if (row.status !== 1 || row.score == null || !row.totalScore || row.totalScore === 0) return ''
  const ratio = row.score / row.totalScore
  if (ratio >= 0.8) return 'score-high'
  if (ratio >= 0.6) return 'score-mid'
  return 'score-low'
}

const formatScore = (row: ExamRecordVO) => {
  if (row.status === 3) return `${row.score ?? 0} / ${row.totalScore}（暂定）`
  if (row.status !== 1 || row.score == null) return `— / ${row.totalScore}`
  return `${row.score} / ${row.totalScore}`
}

const recordStatusLabel = (status: ExamStatus) => {
  if (status === 0) return '进行中'
  if (status === 1) return '已完成'
  if (status === 2) return '已超时'
  if (status === 3) return '待人工批阅'
  return '未知状态'
}

const recordStatusTag = (status: ExamStatus): 'success' | 'warning' | 'danger' | 'info' => {
  if (status === 0) return 'warning'
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  if (status === 3) return 'warning'
  return 'info'
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

const isVerifiedOfficial = (paper: ExamPaperVO) => {
  return paper.paperType === 'OFFICIAL_EXAM' && paper.sourceVerified
}

const paperTypeLabel = (paper: ExamPaperVO) => {
  if (paper.visibility === 'PRIVATE') return '我的私有试卷'
  if (isVerifiedOfficial(paper)) return '官方原题'
  if (paper.paperType === 'OFFICIAL_EXAM') return '来源未核验'
  return '普通练习'
}

const paperTypeTag = (paper: ExamPaperVO) => {
  if (paper.visibility === 'PRIVATE') return 'warning'
  if (isVerifiedOfficial(paper)) return 'success'
  if (paper.paperType === 'OFFICIAL_EXAM') return 'warning'
  return 'info'
}
</script>

<style scoped>
.exam-list-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.exam-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 22px;
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  box-shadow: var(--lp-shadow-sm);
}

.section-kicker {
  color: var(--lp-primary);
  font-size: 12px;
  font-weight: 800;
}

.exam-hero h2 {
  margin: 4px 0 8px;
  font-size: 24px;
  color: var(--lp-text);
}

.exam-hero p {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: 14px;
}

.hero-summary {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.hero-summary span {
  padding: 7px 10px;
  color: var(--lp-text-secondary);
  background: var(--lp-surface-soft);
  border: 1px solid var(--lp-border);
  border-radius: 999px;
  font-size: 13px;
}

.exam-tabs {
  padding: 6px 18px 18px;
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  box-shadow: var(--lp-shadow-sm);
}

.paper-list,
.record-panel {
  min-height: 180px;
}

.exam-card {
  margin-bottom: 14px;
}

.exam-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.exam-card-header h3 {
  margin: 0;
  font-size: 18px;
  color: var(--lp-text);
}

.exam-title-line {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.official-source {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: -2px 0 14px;
  padding: 10px 12px;
  color: var(--lp-text-secondary);
  background: var(--lp-success-soft, #f0f9eb);
  border: 1px solid color-mix(in srgb, var(--lp-success) 28%, transparent);
  border-radius: 7px;
  font-size: 13px;
}

.official-source strong {
  color: var(--lp-text);
  font-weight: 700;
}

.private-source {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: -2px 0 14px;
  padding: 10px 12px;
  color: var(--lp-text-secondary);
  background: var(--lp-warning-soft, #fdf6ec);
  border: 1px solid color-mix(in srgb, var(--lp-warning) 28%, transparent);
  border-radius: 7px;
  font-size: 13px;
}

.import-form {
  margin-top: 18px;
}
.import-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}
.import-grid .el-select {
  width: 100%;
}
.preview-summary {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 16px 0;
}
.preview-question {
  padding: 14px;
  margin-bottom: 10px;
  border: 1px solid var(--lp-border);
  border-radius: 8px;
}
.preview-question > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.preview-question ul {
  margin: 10px 0;
  padding-left: 22px;
  color: var(--lp-text-secondary);
}
.preview-question li.correct {
  color: var(--lp-success);
  font-weight: 700;
}
.preview-question p {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: 13px;
}
.source-meta {
  overflow-wrap: anywhere;
  color: var(--lp-text-secondary);
  font-size: 13px;
}
.source-content {
  max-height: 56vh;
  overflow: auto;
  padding: 14px;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--lp-surface-soft);
  border: 1px solid var(--lp-border);
  border-radius: 8px;
}

.exam-desc {
  color: var(--lp-text-secondary);
  font-size: 14px;
  line-height: 1.6;
  margin: 6px 0 0;
}

.exam-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  color: var(--lp-text-secondary);
  font-size: 13px;
  margin-bottom: 16px;
}

.exam-metrics span {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 8px 10px;
  background: var(--lp-surface-soft);
  border: 1px solid var(--lp-border);
  border-radius: 7px;
}

.exam-metrics .el-icon {
  color: var(--lp-primary);
}

.exam-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.score-text {
  font-weight: 600;
}
.score-high {
  color: var(--lp-success);
}
.score-mid {
  color: var(--lp-warning);
}
.score-low {
  color: var(--lp-danger);
}
.record-finished-hint {
  color: var(--lp-text-secondary);
  font-size: 13px;
}
.record-mobile-list {
  display: none;
}

@media (max-width: 860px) {
  .exam-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .exam-hero,
  .exam-card-header {
    align-items: stretch;
    flex-direction: column;
  }

  .exam-hero {
    padding: 16px;
  }

  .hero-summary {
    justify-content: flex-start;
  }

  .exam-metrics {
    grid-template-columns: 1fr;
  }

  .exam-actions .el-button {
    width: 100%;
  }

  .import-grid {
    grid-template-columns: 1fr;
  }
  .preview-summary {
    flex-direction: column;
  }

  .record-panel > .el-table {
    display: none;
  }

  .record-mobile-list {
    display: grid;
    gap: 12px;
  }

  .record-mobile-card {
    padding: 16px;
    background: var(--lp-surface);
    border: 1px solid var(--lp-border);
    border-radius: var(--lp-radius-sm, 8px);
  }

  .record-mobile-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  .record-mobile-header h3 {
    min-width: 0;
    margin: 0;
    color: var(--lp-text);
    font-size: 16px;
    line-height: 1.5;
  }

  .record-mobile-meta {
    display: grid;
    grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
    gap: 12px;
    margin: 16px 0;
  }

  .record-mobile-meta div {
    min-width: 0;
  }

  .record-mobile-meta dt {
    margin-bottom: 4px;
    color: var(--lp-text-secondary);
    font-size: 12px;
  }

  .record-mobile-meta dd {
    margin: 0;
    color: var(--lp-text);
    font-size: 13px;
    line-height: 1.5;
    overflow-wrap: anywhere;
  }

  .record-mobile-action {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    min-height: 44px;
  }

  .record-mobile-action .el-button {
    min-height: 44px;
  }
}
</style>
