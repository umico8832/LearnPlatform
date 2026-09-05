<template>
  <el-dialog
    v-model="dialogVisible"
    :title="editingId ? '编辑试卷' : '新增试卷'"
    width="min(1120px, 94vw)"
    destroy-on-close
  >
    <el-form :model="form" label-width="80px">
      <el-form-item label="试卷名称" required>
        <el-input v-model="form.title" placeholder="请输入试卷名称" />
      </el-form-item>
      <el-form-item label="课程">
        <el-select v-model="form.courseId" placeholder="选择课程（可选）" clearable style="width: 100%">
          <el-option v-for="course in courses" :key="course.id" :label="course.name" :value="course.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="试卷描述（可选）" />
      </el-form-item>
      <el-form-item label="试卷性质">
        <el-select v-model="form.paperType" style="width: 100%">
          <el-option label="普通练习" value="PRACTICE" />
          <el-option label="官方原题试卷" value="OFFICIAL_EXAM" />
        </el-select>
      </el-form-item>
      <template v-if="form.paperType === 'OFFICIAL_EXAM'">
        <el-alert
          title="官方原题必须保留可核验来源与完整题号；AI 生成题和自拟题不能标记为官方原题。"
          type="warning"
          :closable="false"
          show-icon
          class="provenance-alert"
        />
        <el-row :gutter="16">
          <el-col :span="16">
            <el-form-item label="考试名称" required>
              <el-input v-model="form.examName" placeholder="例如：全国硕士研究生招生考试" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="考试年份" required>
              <el-input-number v-model="form.examYear" :min="1900" :max="currentYear" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="来源引用" required>
          <el-input
            v-model="form.sourceReference"
            type="textarea"
            :rows="2"
            placeholder="填写可复核的出版物、文件或页面引用"
          />
        </el-form-item>
        <el-form-item label="来源核验">
          <el-switch v-model="form.sourceVerified" active-text="已人工核验" inactive-text="尚未核验" />
        </el-form-item>
      </template>
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
        <el-table v-if="form.questions.length > 0" :data="form.questions as any" size="small" max-height="300">
          <el-table-column type="index" width="40" />
          <el-table-column label="题干" min-width="220">
            <template #default="{ row }">
              <span class="q-content-preview">{{ getQuestionContent((row as ExamPaperFormQuestion).questionId) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="分区" width="130">
            <template #default="{ row }">
              <el-input v-model="row.sectionTitle" size="small" placeholder="第一部分" />
            </template>
          </el-table-column>
          <el-table-column label="大/小/子题" width="210">
            <template #default="{ row }">
              <div class="question-number-parts">
                <el-input v-model="row.majorQuestionNumber" size="small" placeholder="大题" />
                <el-input v-model="row.minorQuestionNumber" size="small" placeholder="小题" />
                <el-input v-model="row.subquestionNumber" size="small" placeholder="子题" />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="展示题号" width="120">
            <template #default="{ row }">
              <el-input v-model="row.displayNumber" size="small" placeholder="1(1)(a)" />
            </template>
          </el-table-column>
          <el-table-column label="分值" width="120">
            <template #default="{ row }">
              <el-input-number
                :model-value="(row as ExamPaperFormQuestion).score"
                :min="1"
                size="small"
                @change="
                  (value: number | undefined) =>
                    value !== undefined && updateQuestionScore((row as ExamPaperFormQuestion).questionId, value)
                "
              />
            </template>
          </el-table-column>
          <el-table-column label="排序" width="100">
            <template #default="{ row }">
              <el-input-number
                :model-value="(row as ExamPaperFormQuestion).sortOrder"
                :min="0"
                size="small"
                @change="
                  (value: number | undefined) =>
                    value !== undefined && updateQuestionSort((row as ExamPaperFormQuestion).questionId, value)
                "
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60">
            <template #default="{ $index }">
              <el-button type="danger" link size="small" @click="form.questions.splice($index, 1)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂未选择题目" :image-size="60" />
      </div>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="showQuestionPicker" title="选择题目" width="900px" destroy-on-close @open="loadPickerQuestions">
    <div class="q-picker-filter">
      <el-input
        v-model="pickerKeyword"
        placeholder="搜索题干关键词"
        clearable
        style="width: 200px"
        @keyup.enter="loadPickerQuestions"
      />
      <el-select v-model="pickerType" placeholder="题型" clearable style="width: 120px" @change="loadPickerQuestions">
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
          <el-checkbox
            :model-value="isQuestionSelected((row as QuestionVO).id)"
            @change="togglePickQuestion(row as QuestionVO)"
          />
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
          <el-tag size="small">{{ questionTypeLabel((row as QuestionVO).questionType) }}</el-tag>
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
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createExamPaper, getExamPaperDetail, updateExamPaper } from '@/api/exam'
import type { ExamPaperVO } from '@/api/exam'
import { getAdminQuestionPage } from '@/api/question'
import type { QuestionVO } from '@/api/question'
import { questionTypeLabel } from './examManagePresentation'
import {
  createEmptyExamPaperForm,
  createExamPaperForm,
  createExamPaperRequest,
  validateExamPaperForm,
} from './examPaperEditorModel'
import type { ExamPaperFormQuestion } from './examPaperEditorModel'

defineProps<{ courses: { id: number; name: string }[] }>()
const emit = defineEmits<{ saved: [] }>()

const currentYear = new Date().getFullYear()
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const form = ref(createEmptyExamPaperForm())
const pickedQuestionMap = ref<Map<number, QuestionVO>>(new Map())

const showQuestionPicker = ref(false)
const pickerLoading = ref(false)
const pickerQuestions = ref<QuestionVO[]>([])
const pickerTotal = ref(0)
const pickerPageNum = ref(1)
const pickerKeyword = ref('')
const pickerType = ref('')

const totalFormScore = computed(() => form.value.questions.reduce((sum, question) => sum + question.score, 0))

async function open(paper?: ExamPaperVO) {
  if (!paper) {
    editingId.value = null
    form.value = createEmptyExamPaperForm()
    pickedQuestionMap.value.clear()
    dialogVisible.value = true
    return
  }

  editingId.value = paper.id
  try {
    const response = await getExamPaperDetail(paper.id)
    if (response.code !== 0 || !response.data) return
    form.value = createExamPaperForm(response.data)
    pickedQuestionMap.value = new Map(
      response.data.questions.map((question) => [
        question.questionId,
        {
          id: question.questionId,
          content: question.content,
          questionType: question.questionType,
          score: question.score,
        } as QuestionVO,
      ]),
    )
    dialogVisible.value = true
  } catch {
    ElMessage.error('获取试卷详情失败')
  }
}

async function handleSubmit() {
  const validationMessage = validateExamPaperForm(form.value)
  if (validationMessage) {
    ElMessage.warning(validationMessage)
    return
  }

  submitting.value = true
  try {
    const request = createExamPaperRequest(form.value)
    const response = editingId.value ? await updateExamPaper(editingId.value, request) : await createExamPaper(request)
    if (response.code !== 0) {
      ElMessage.error(response.message || '操作失败')
      return
    }
    ElMessage.success(editingId.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    emit('saved')
  } catch {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

async function loadPickerQuestions() {
  pickerLoading.value = true
  try {
    const response = await getAdminQuestionPage({
      pageNum: pickerPageNum.value,
      pageSize: 10,
      keyword: pickerKeyword.value || undefined,
      questionType: pickerType.value || undefined,
    })
    pickerQuestions.value = response.data?.records ?? []
    pickerTotal.value = response.data?.total ?? 0
  } finally {
    pickerLoading.value = false
  }
}

function isQuestionSelected(questionId: number) {
  return form.value.questions.some((question) => question.questionId === questionId)
}

function togglePickQuestion(question: QuestionVO) {
  const index = form.value.questions.findIndex((item) => item.questionId === question.id)
  if (index >= 0) {
    form.value.questions.splice(index, 1)
    return
  }
  form.value.questions.push({
    questionId: question.id,
    sortOrder: form.value.questions.length,
    score: question.score || 1,
    sectionTitle: '',
    majorQuestionNumber: '',
    minorQuestionNumber: '',
    subquestionNumber: '',
    displayNumber: '',
  })
  pickedQuestionMap.value.set(question.id, question)
}

function updateQuestionScore(questionId: number, value: number) {
  const question = form.value.questions.find((item) => item.questionId === questionId)
  if (question) question.score = value
}

function updateQuestionSort(questionId: number, value: number) {
  const question = form.value.questions.find((item) => item.questionId === questionId)
  if (question) question.sortOrder = value
}

function getQuestionContent(questionId: number) {
  const question = pickedQuestionMap.value.get(questionId)
  if (!question) return `题目 #${questionId}`
  return question.content.length > 80 ? `${question.content.substring(0, 80)}...` : question.content
}

defineExpose({ open })
</script>

<style scoped>
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.q-content-preview {
  font-size: 13px;
  color: #606266;
}
.question-picker {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
}
.provenance-alert {
  margin-bottom: 16px;
}
.question-number-parts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px;
}
.picker-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.picker-info {
  font-size: 13px;
  color: #909399;
}
.q-picker-filter {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
