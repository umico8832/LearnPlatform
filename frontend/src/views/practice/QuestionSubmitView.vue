<template>
  <div class="question-submit-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>题目投稿</span>
          <el-button type="primary" @click="showSubmitDialog = true">
            <el-icon><Plus /></el-icon> 投稿新题目
          </el-button>
        </div>
      </template>

      <!-- 状态筛选 -->
      <div class="filter-bar">
        <el-radio-group v-model="statusFilter" @change="loadSubmissions">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button :value="0">待审核</el-radio-button>
          <el-radio-button :value="1">已通过</el-radio-button>
          <el-radio-button :value="2">已拒绝</el-radio-button>
          <el-radio-button :value="3">已入库</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 投稿列表 -->
      <el-table :data="submissions" v-loading="loading" stripe>
        <el-table-column label="题干" prop="content" show-overflow-tooltip min-width="200" />
        <el-table-column label="课程" prop="courseName" width="120" />
        <el-table-column label="题型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ questionTypeLabel(row.questionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="难度" width="100">
          <template #default="{ row }">
            <el-rate v-model="row.difficulty" disabled :max="5" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核意见" prop="reviewComment" show-overflow-tooltip width="160" />
        <el-table-column label="投稿时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewDetail(row as QuestionSubmissionVO)">详情</el-button>
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

    <!-- 投稿对话框 -->
    <el-dialog v-model="showSubmitDialog" title="投稿新题目" width="700px" destroy-on-close>
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="所属课程" prop="courseId">
          <el-select v-model="form.courseId" placeholder="选择课程" filterable style="width: 100%">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型" prop="questionType">
          <el-select v-model="form.questionType" placeholder="选择题型" @change="onTypeChange">
            <el-option label="单选题" value="SINGLE_CHOICE" />
            <el-option label="多选题" value="MULTIPLE_CHOICE" />
            <el-option label="判断题" value="TRUE_FALSE" />
            <el-option label="填空题" value="FILL_BLANK" />
            <el-option label="简答题" value="SHORT_ANSWER" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度" prop="difficulty">
          <el-rate v-model="form.difficulty" :max="5" />
        </el-form-item>
        <el-form-item label="题干内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="4" placeholder="输入题目内容，支持 Markdown" />
        </el-form-item>

        <!-- 选择题选项 -->
        <template v-if="showOptions">
          <el-form-item label="选项">
            <div v-for="(opt, idx) in optionList" :key="idx" class="option-row">
              <el-input
                v-model="opt.content"
                :placeholder="'选项 ' + String.fromCharCode(65 + idx)"
                style="width: 300px"
              />
              <el-checkbox v-model="opt.isCorrect" style="margin-left: 8px">正确答案</el-checkbox>
              <el-button
                v-if="optionList.length > 2"
                type="danger"
                link
                @click="optionList.splice(idx, 1)"
                style="margin-left: 4px"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button v-if="optionList.length < 8" type="primary" link @click="addOption" style="margin-top: 4px">
              + 添加选项
            </el-button>
          </el-form-item>
        </template>

        <!-- 判断题答案 -->
        <template v-if="form.questionType === 'TRUE_FALSE'">
          <el-form-item label="正确答案">
            <el-radio-group v-model="form.correctAnswer">
              <el-radio-button value="TRUE">正确</el-radio-button>
              <el-radio-button value="FALSE">错误</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </template>

        <!-- 填空/简答答案 -->
        <template v-else-if="form.questionType === 'FILL_BLANK' || form.questionType === 'SHORT_ANSWER'">
          <el-form-item label="正确答案">
            <el-input v-model="form.correctAnswer" type="textarea" :rows="2" placeholder="输入参考答案" />
          </el-form-item>
        </template>

        <el-form-item label="解析">
          <el-input v-model="form.analysis" type="textarea" :rows="3" placeholder="题目解析（可选）" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="多个标签用逗号分隔" />
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="form.source" placeholder="题目来源（如：课本第X章、网络等）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSubmitDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">提交投稿</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="投稿详情" width="650px">
      <el-descriptions :column="2" border v-if="currentDetail">
        <el-descriptions-item label="状态" :span="2">
          <el-tag :type="statusTagType(currentDetail.status)">{{ statusLabel(currentDetail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="课程">{{ currentDetail.courseName }}</el-descriptions-item>
        <el-descriptions-item label="题型">{{ questionTypeLabel(currentDetail.questionType) }}</el-descriptions-item>
        <el-descriptions-item label="难度"
          ><el-rate v-model="currentDetail.difficulty" disabled :max="5"
        /></el-descriptions-item>
        <el-descriptions-item label="投稿时间">{{ formatTime(currentDetail.createTime) }}</el-descriptions-item>
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
        <el-descriptions-item label="来源" v-if="currentDetail.source">{{ currentDetail.source }}</el-descriptions-item>
        <el-descriptions-item label="审核意见" :span="2" v-if="currentDetail.reviewComment">
          <el-text type="info">{{ currentDetail.reviewComment }}</el-text>
        </el-descriptions-item>
        <el-descriptions-item label="审核人" v-if="currentDetail.reviewedByName">{{
          currentDetail.reviewedByName
        }}</el-descriptions-item>
        <el-descriptions-item label="审核时间" v-if="currentDetail.reviewedTime">{{
          formatTime(currentDetail.reviewedTime)
        }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { SemanticTagType } from '@/utils/errors'
import { ElMessage, type FormInstance } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { submitQuestion, getMySubmissions, type QuestionSubmissionVO, type SubmissionForm } from '@/api/submission'
import { getAllCourses, type CourseVO } from '@/api/course'

const loading = ref(false)
const submitting = ref(false)
const showSubmitDialog = ref(false)
const showDetailDialog = ref(false)
const submissions = ref<QuestionSubmissionVO[]>([])
const courses = ref<CourseVO[]>([])
const currentDetail = ref<QuestionSubmissionVO | null>(null)
const statusFilter = ref<number | undefined>(undefined)
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const formRef = ref<FormInstance>()

interface OptionItem {
  content: string
  label: string
  isCorrect: boolean
}

const optionList = ref<OptionItem[]>([
  { content: '', label: 'A', isCorrect: false },
  { content: '', label: 'B', isCorrect: false },
  { content: '', label: 'C', isCorrect: false },
  { content: '', label: 'D', isCorrect: false },
])

const form = reactive<SubmissionForm & { correctAnswer?: string }>({
  content: '',
  questionType: '',
  courseId: 0,
  difficulty: 3,
  analysis: '',
  tags: '',
  source: '',
  correctAnswer: '',
})

const rules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  questionType: [{ required: true, message: '请选择题型', trigger: 'change' }],
  content: [{ required: true, message: '请输入题干内容', trigger: 'blur' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
}

const showOptions = computed(() => form.questionType === 'SINGLE_CHOICE' || form.questionType === 'MULTIPLE_CHOICE')

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

const addOption = () => {
  const nextLabel: string = String.fromCharCode(65 + optionList.value.length)
  optionList.value.push({ content: '', label: nextLabel, isCorrect: false })
}

const onTypeChange = () => {
  form.correctAnswer = ''
}

const loadSubmissions = async () => {
  loading.value = true
  try {
    const res = await getMySubmissions({ pageNum: pageNum.value, pageSize, status: statusFilter.value })
    if (res.code === 0 && res.data) {
      submissions.value = res.data.records
      total.value = res.data.total
    }
  } finally {
    loading.value = false
  }
}

const loadCourses = async () => {
  try {
    const res = await getAllCourses()
    if (res.code === 0 && res.data) {
      courses.value = res.data
    }
  } catch {
    /* ignore */
  }
}

const handlePageChange = (page: number) => {
  pageNum.value = page
  loadSubmissions()
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    const payload: SubmissionForm = {
      content: form.content,
      questionType: form.questionType,
      courseId: form.courseId,
      difficulty: form.difficulty,
      analysis: form.analysis || undefined,
      tags: form.tags || undefined,
      source: form.source || undefined,
      knowledgePointIds: undefined,
    }

    // 组装选项 JSON
    if (showOptions.value) {
      const filled = optionList.value.filter((o) => o.content.trim())
      if (filled.length < 2) {
        ElMessage.warning('至少需要填写 2 个选项')
        return
      }
      if (!filled.some((o) => o.isCorrect)) {
        ElMessage.warning('请标记至少一个正确答案')
        return
      }
      if (form.questionType === 'SINGLE_CHOICE' && filled.filter((o) => o.isCorrect).length !== 1) {
        ElMessage.warning('单选题必须且只能标记 1 个正确答案')
        return
      }
      payload.optionsJson = JSON.stringify(
        filled.map((o, i) => ({
          content: o.content,
          label: String.fromCharCode(65 + i),
          isCorrect: o.isCorrect,
        })),
      )
    }

    if (form.questionType === 'TRUE_FALSE') {
      if (!form.correctAnswer) {
        ElMessage.warning('请选择判断题正确答案')
        return
      }
      payload.correctAnswer = form.correctAnswer
    }

    if (form.questionType === 'FILL_BLANK' || form.questionType === 'SHORT_ANSWER') {
      if (!form.correctAnswer?.trim()) {
        ElMessage.warning('请输入参考答案')
        return
      }
      payload.correctAnswer = form.correctAnswer.trim()
    }

    const res = await submitQuestion(payload)
    if (res.code === 0) {
      ElMessage.success('投稿提交成功，等待管理员审核')
      showSubmitDialog.value = false
      resetForm()
      loadSubmissions()
    } else {
      ElMessage.error(res.message || '提交失败')
    }
  } finally {
    submitting.value = false
  }
}

const resetForm = () => {
  form.content = ''
  form.questionType = ''
  form.courseId = 0
  form.difficulty = 3
  form.analysis = ''
  form.tags = ''
  form.source = ''
  form.correctAnswer = ''
  optionList.value = [
    { content: '', label: 'A', isCorrect: false },
    { content: '', label: 'B', isCorrect: false },
    { content: '', label: 'C', isCorrect: false },
    { content: '', label: 'D', isCorrect: false },
  ]
}

const viewDetail = (row: QuestionSubmissionVO) => {
  currentDetail.value = row
  showDetailDialog.value = true
}

onMounted(() => {
  loadCourses()
  loadSubmissions()
})
</script>

<style scoped>
.question-submit-page {
  max-width: 1100px;
  margin: 0 auto;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.filter-bar {
  margin-bottom: 16px;
}
.option-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}
.detail-content {
  white-space: pre-wrap;
  line-height: 1.6;
}
</style>
