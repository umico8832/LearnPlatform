<template>
  <div class="exam-manage-container">
    <div class="page-header">
      <h2>试卷管理</h2>
      <el-button type="primary" @click="openDialog()">新增试卷</el-button>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 120px" @change="loadPapers">
        <el-option label="草稿" :value="0" />
        <el-option label="已发布" :value="1" />
      </el-select>
      <el-button @click="loadPapers" :icon="Refresh">刷新</el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="papers as any" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="试卷名称" min-width="200" />
      <el-table-column prop="courseName" label="课程" width="120" />
      <el-table-column prop="questionCount" label="题数" width="80" />
      <el-table-column prop="totalScore" label="总分" width="80" />
      <el-table-column prop="duration" label="时长(分)" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="(row as ExamPaperVO).status === 1 ? 'success' : 'info'" size="small">
            {{ (row as ExamPaperVO).status === 1 ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime((row as ExamPaperVO).createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openDialog(row as ExamPaperVO)">编辑</el-button>
          <el-button v-if="(row as ExamPaperVO).status === 0" type="success" link size="small" @click="handlePublish(row as ExamPaperVO)">发布</el-button>
          <el-button type="danger" link size="small" @click="handleDelete(row as ExamPaperVO)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper" v-if="total > 0">
      <el-pagination
        v-model:current-page="pageNum"
        :total="total"
        :page-size="10"
        layout="total, prev, pager, next"
        @current-change="loadPapers"
      />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑试卷' : '新增试卷'" width="800px" destroy-on-close>
      <el-form :model="form" label-width="80px">
        <el-form-item label="试卷名称" required>
          <el-input v-model="form.title" placeholder="请输入试卷名称" />
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="form.courseId" placeholder="选择课程（可选）" clearable style="width: 100%">
            <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="试卷描述（可选）" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="考试时长">
              <el-input-number v-model="form.duration" :min="1" :max="600" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status">
                <el-option label="草稿" :value="0" />
                <el-option label="已发布" :value="1" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider>组卷（选择题目）</el-divider>

        <div class="question-picker">
          <div class="picker-toolbar">
            <el-button type="primary" size="small" @click="showQuestionPicker = true">添加题目</el-button>
            <span class="picker-info">已选 {{ form.questions.length }} 题，总分 {{ totalFormScore }} 分</span>
          </div>
          <el-table :data="form.questions as any" size="small" max-height="300" v-if="form.questions.length > 0">
            <el-table-column type="index" width="40" />
            <el-table-column label="题干" min-width="300">
              <template #default="{ row }">
                <span class="q-content-preview">{{ getQuestionContent((row as FormQuestionItem).questionId) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="分值" width="120">
              <template #default="{ row }">
                <el-input-number :model-value="(row as FormQuestionItem).score" :min="1" size="small" @change="(val: number | undefined) => val !== undefined && updateQuestionScore((row as FormQuestionItem).questionId, val)" />
              </template>
            </el-table-column>
            <el-table-column label="排序" width="100">
              <template #default="{ row }">
                <el-input-number :model-value="(row as FormQuestionItem).sortOrder" :min="0" size="small" @change="(val: number | undefined) => val !== undefined && updateQuestionSort((row as FormQuestionItem).questionId, val)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60">
              <template #default="{ $index }">
                <el-button type="danger" link size="small" @click="removeQuestion($index)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂未选择题目" :image-size="60" />
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>

    <!-- 题目选择弹窗 -->
    <el-dialog v-model="showQuestionPicker" title="选择题目" width="900px" destroy-on-close @open="loadPickerQuestions">
      <div class="q-picker-filter">
        <el-input v-model="qPickerKeyword" placeholder="搜索题干关键词" clearable style="width: 200px" @keyup.enter="loadPickerQuestions" />
        <el-select v-model="qPickerType" placeholder="题型" clearable style="width: 120px" @change="loadPickerQuestions">
          <el-option label="单选" value="SINGLE_CHOICE" />
          <el-option label="多选" value="MULTIPLE_CHOICE" />
          <el-option label="判断" value="TRUE_FALSE" />
          <el-option label="填空" value="FILL_BLANK" />
          <el-option label="简答" value="SHORT_ANSWER" />
        </el-select>
        <el-button type="primary" @click="loadPickerQuestions">搜索</el-button>
      </div>
      <el-table :data="pickerQuestions as any" v-loading="pickerLoading" size="small" max-height="400">
        <el-table-column width="50">
          <template #default="{ row }">
            <el-checkbox :model-value="isQuestionSelected((row as QuestionVO).id)" @change="togglePickQuestion(row as QuestionVO)" />
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="题干" min-width="350">
          <template #default="{ row }">
            <span class="q-content-preview">{{ (row as QuestionVO).content }}</span>
          </template>
        </el-table-column>
        <el-table-column label="题型" width="80">
          <template #default="{ row }">
            <el-tag size="small">{{ getTypeLabel((row as QuestionVO).questionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="默认分值" width="80" />
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pickerPageNum"
          :total="pickerTotal"
          :page-size="10"
          layout="total, prev, pager, next"
          small
          @current-change="loadPickerQuestions"
        />
      </div>
      <template #footer>
        <el-button @click="showQuestionPicker = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getExamPaperList, getExamPaperDetail, createExamPaper, updateExamPaper, deleteExamPaper, publishExamPaper } from '@/api/exam'
import type { ExamPaperVO, ExamPaperCreateRequest } from '@/api/exam'
import { getAdminQuestionPage } from '@/api/question'
import type { QuestionVO } from '@/api/question'
import { getCoursePage } from '@/api/course'

// 试卷列表
const loading = ref(false)
const papers = ref<ExamPaperVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const filterStatus = ref<number | undefined>(undefined)

// 课程列表
const courseList = ref<{ id: number; name: string }[]>([])

// 弹窗
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const form = ref<{
  title: string
  description: string
  courseId: number | undefined
  duration: number
  status: number
  questions: FormQuestionItem[]
}>({
  title: '',
  description: '',
  courseId: undefined,
  duration: 60,
  status: 0,
  questions: []
})

interface FormQuestionItem {
  questionId: number
  sortOrder: number
  score: number
}

// 题目选择器
const showQuestionPicker = ref(false)
const pickerLoading = ref(false)
const pickerQuestions = ref<QuestionVO[]>([])
const pickerTotal = ref(0)
const pickerPageNum = ref(1)
const qPickerKeyword = ref('')
const qPickerType = ref('')
const pickedQuestionMap = ref<Map<number, QuestionVO>>(new Map())

const totalFormScore = computed(() => {
  return form.value.questions.reduce((sum, q) => sum + (q.score || 0), 0)
})

onMounted(() => {
  loadPapers()
  loadCourses()
})

const loadPapers = async () => {
  loading.value = true
  try {
    const res = await getExamPaperList({ pageNum: pageNum.value, pageSize: 10, status: filterStatus.value })
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

const loadCourses = async () => {
  try {
    const res = await getCoursePage({ pageNum: 1, pageSize: 100 })
    if ((res as any).code === 0 && (res as any).data) {
      courseList.value = ((res as any).data.records || []).map((c: any) => ({ id: c.id, name: c.name }))
    }
  } catch {}
}

const openDialog = async (paper?: ExamPaperVO) => {
  if (paper) {
    editingId.value = paper.id
    try {
      const res = await getExamPaperDetail(paper.id)
      if (res.code === 0 && res.data) {
        const d = res.data
        form.value = {
          title: d.title,
          description: d.description || '',
          courseId: d.courseId || undefined,
          duration: d.duration || 60,
          status: d.status || 0,
          questions: (d.questions || []).map((q, idx) => ({
            questionId: q.questionId,
            sortOrder: q.sortOrder || idx,
            score: q.score || 1
          }))
        }
        // 预填充题目内容到 map
        pickedQuestionMap.value.clear()
        for (const q of (d.questions || [])) {
          pickedQuestionMap.value.set(q.questionId, {
            id: q.questionId,
            content: q.content,
            questionType: q.questionType,
            score: q.score
          } as QuestionVO)
        }
      }
    } catch {
      ElMessage.error('获取试卷详情失败')
      return
    }
  } else {
    editingId.value = null
    form.value = { title: '', description: '', courseId: undefined, duration: 60, status: 0, questions: [] }
    pickedQuestionMap.value.clear()
  }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!form.value.title.trim()) {
    ElMessage.warning('请输入试卷名称')
    return
  }
  submitting.value = true
  const data: ExamPaperCreateRequest = {
    title: form.value.title,
    description: form.value.description || undefined,
    courseId: form.value.courseId,
    duration: form.value.duration,
    status: form.value.status,
    questions: form.value.questions.map((q, idx) => ({
      questionId: q.questionId,
      sortOrder: q.sortOrder ?? idx,
      score: q.score
    }))
  }
  try {
    const res = editingId.value
      ? await updateExamPaper(editingId.value, data)
      : await createExamPaper(data)
    if (res.code === 0) {
      ElMessage.success(editingId.value ? '更新成功' : '创建成功')
      dialogVisible.value = false
      loadPapers()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

const handlePublish = async (paper: ExamPaperVO) => {
  try {
    await ElMessageBox.confirm(`确定发布试卷「${paper.title}」？发布后用户可见`, '发布确认', { type: 'warning' })
    const res = await publishExamPaper(paper.id)
    if (res.code === 0) {
      ElMessage.success('发布成功')
      loadPapers()
    }
  } catch {}
}

const handleDelete = async (paper: ExamPaperVO) => {
  try {
    await ElMessageBox.confirm(`确定删除试卷「${paper.title}」？`, '删除确认', { type: 'warning' })
    const res = await deleteExamPaper(paper.id)
    if (res.code === 0) {
      ElMessage.success('删除成功')
      loadPapers()
    }
  } catch {}
}

// 题目选择器
const loadPickerQuestions = async () => {
  pickerLoading.value = true
  try {
    const res = await getAdminQuestionPage({
      pageNum: pickerPageNum.value,
      pageSize: 10,
      keyword: qPickerKeyword.value || undefined,
      questionType: qPickerType.value || undefined
    })
    if ((res as any).code === 0 && (res as any).data) {
      pickerQuestions.value = (res as any).data.records || []
      pickerTotal.value = (res as any).data.total || 0
    }
  } catch {} finally {
    pickerLoading.value = false
  }
}

const isQuestionSelected = (id: number) => {
  return form.value.questions.some(q => q.questionId === id)
}

const togglePickQuestion = (q: QuestionVO) => {
  const idx = form.value.questions.findIndex(item => item.questionId === q.id)
  if (idx >= 0) {
    form.value.questions.splice(idx, 1)
  } else {
    form.value.questions.push({ questionId: q.id, sortOrder: form.value.questions.length, score: q.score || 1 })
    pickedQuestionMap.value.set(q.id, q)
  }
}

const removeQuestion = (index: number) => {
  form.value.questions.splice(index, 1)
}

const updateQuestionScore = (questionId: number, val: number) => {
  const q = form.value.questions.find(item => item.questionId === questionId)
  if (q) q.score = val
}

const updateQuestionSort = (questionId: number, val: number) => {
  const q = form.value.questions.find(item => item.questionId === questionId)
  if (q) q.sortOrder = val
}

const getQuestionContent = (questionId: number) => {
  const q = pickedQuestionMap.value.get(questionId)
  return q ? (q.content.length > 80 ? q.content.substring(0, 80) + '...' : q.content) : `题目 #${questionId}`
}

const getTypeLabel = (type: string) => {
  const map: Record<string, string> = { SINGLE_CHOICE: '单选', MULTIPLE_CHOICE: '多选', TRUE_FALSE: '判断', FILL_BLANK: '填空', SHORT_ANSWER: '简答' }
  return map[type] || type
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

</script>

<style scoped>
.exam-manage-container { padding: 24px; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; }
.filter-bar { display: flex; gap: 12px; margin-bottom: 16px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
.q-content-preview { font-size: 13px; color: #606266; }
.question-picker { border: 1px solid #ebeef5; border-radius: 8px; padding: 16px; }
.picker-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.picker-info { font-size: 13px; color: #909399; }
.q-picker-filter { display: flex; gap: 12px; margin-bottom: 16px; }
</style>
