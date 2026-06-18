<template>
  <div class="question-manage">
    <div class="page-header">
      <h2>题目管理</h2>
      <div class="header-actions">
        <el-dropdown trigger="click">
          <el-button :icon="Download">下载模板 <el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handleDownloadTemplate">Excel 模板 (.xlsx)</el-dropdown-item>
              <el-dropdown-item @click="handleDownloadMdTemplate">Markdown 模板 (.md)</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button :icon="Upload" @click="importDialogVisible = true">导入题目</el-button>
        <el-button :icon="FolderOpened" @click="handleExport">导出题目</el-button>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增题目</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索题干内容"
          :prefix-icon="Search"
          clearable
          style="width: 220px"
          @clear="fetchQuestions"
          @keyup.enter="fetchQuestions"
        />
        <el-select v-model="filters.questionType" placeholder="题型" clearable style="width: 130px" @change="fetchQuestions">
          <el-option label="单选题" value="SINGLE_CHOICE" />
          <el-option label="多选题" value="MULTIPLE_CHOICE" />
          <el-option label="判断题" value="TRUE_FALSE" />
          <el-option label="填空题" value="FILL_BLANK" />
          <el-option label="简答题" value="SHORT_ANSWER" />
        </el-select>
        <el-select v-model="filters.courseId" placeholder="所属课程" clearable style="width: 180px" @change="fetchQuestions">
          <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="filters.difficulty" placeholder="难度" clearable style="width: 110px" @change="fetchQuestions">
          <el-option v-for="d in 5" :key="d" :label="'⭐'.repeat(d)" :value="d" />
        </el-select>
      </div>

      <el-table :data="questions" v-loading="loading" stripe>
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
        <el-table-column prop="score" label="分值" width="70" align="center" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="(row as QuestionVO).status === 1 ? 'success' : 'info'" size="small">
              {{ (row as QuestionVO).status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDialog(row as QuestionVO)">编辑</el-button>
            <el-popconfirm title="确定清除该题目的 AI 学习资产缓存？" @confirm="handleClearAiCache((row as QuestionVO).id)">
              <template #reference>
                <el-button type="warning" link size="small">清除AI缓存</el-button>
              </template>
            </el-popconfirm>
            <el-popconfirm title="确定删除该题目？" @confirm="handleDelete((row as QuestionVO).id)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
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

    <!-- 导入结果弹窗 -->
    <el-dialog v-model="importResultVisible" title="导入结果" width="500px" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="总行数">{{ importResult.totalRows }}</el-descriptions-item>
        <el-descriptions-item label="成功数">
          <el-tag type="success">{{ importResult.successCount }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="失败数">
          <el-tag :type="importResult.failCount > 0 ? 'danger' : 'success'">{{ importResult.failCount }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <div v-if="importResult.errors.length > 0" style="margin-top: 12px;">
        <p style="color: #f56c6c; margin-bottom: 8px;">错误详情：</p>
        <el-scrollbar max-height="200px">
          <p v-for="(err, idx) in importResult.errors" :key="idx" style="font-size: 13px; color: #606266; margin: 4px 0;">
            {{ err }}
          </p>
        </el-scrollbar>
      </div>
      <template #footer>
        <el-button type="primary" @click="importResultVisible = false">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入弹窗 -->
    <el-dialog v-model="importDialogVisible" title="导入题目" width="540px" destroy-on-close>
      <el-tabs v-model="importTab">
        <el-tab-pane label="Excel 导入" name="excel">
          <el-upload
            ref="uploadRef"
            drag
            accept=".xlsx,.xls"
            :auto-upload="false"
            :limit="1"
            :on-change="onImportFileChange"
            :on-exceed="() => ElMessage.warning('只能上传一个文件')"
          >
            <el-icon style="font-size: 40px; color: #c0c4cc; margin-bottom: 8px;"><Upload /></el-icon>
            <div>将 Excel 文件拖到此处，或<em>点击上传</em></div>
            <template #tip>
              <div style="color: #909399; font-size: 12px; margin-top: 4px;">
                仅支持 .xlsx / .xls 文件，可先<a href="javascript:void(0)" @click.stop="handleDownloadTemplate" style="color: #409eff">下载模板</a>
              </div>
            </template>
          </el-upload>
        </el-tab-pane>
        <el-tab-pane label="Markdown 导入" name="markdown">
          <el-upload
            ref="mdUploadRef"
            drag
            accept=".md,.markdown"
            :auto-upload="false"
            :limit="1"
            :on-change="onMdFileChange"
            :on-exceed="() => ElMessage.warning('只能上传一个文件')"
          >
            <el-icon style="font-size: 40px; color: #c0c4cc; margin-bottom: 8px;"><Upload /></el-icon>
            <div>将 Markdown 文件拖到此处，或<em>点击上传</em></div>
            <template #tip>
              <div style="color: #909399; font-size: 12px; margin-top: 4px;">
                仅支持 .md / .markdown 文件，可先<a href="javascript:void(0)" @click.stop="handleDownloadMdTemplate" style="color: #409eff">下载模板</a>
              </div>
            </template>
          </el-upload>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">开始导入</el-button>
      </template>
    </el-dialog>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingQuestion ? '编辑题目' : '新增题目'"
      width="780px"
      destroy-on-close
      top="5vh"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
        @submit.prevent
      >
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属课程" prop="courseId">
              <el-select v-model="form.courseId" placeholder="选择课程" style="width: 100%">
                <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="题型" prop="questionType">
              <el-select v-model="form.questionType" placeholder="选择题型" style="width: 100%" @change="onTypeChange">
                <el-option label="单选题" value="SINGLE_CHOICE" />
                <el-option label="多选题" value="MULTIPLE_CHOICE" />
                <el-option label="判断题" value="TRUE_FALSE" />
                <el-option label="填空题" value="FILL_BLANK" />
                <el-option label="简答题" value="SHORT_ANSWER" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="题干内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="3"
            placeholder="请输入题干内容（支持 Markdown）"
          />
        </el-form-item>

        <!-- 选项区域（单选/多选/判断题） -->
        <el-form-item
          v-if="showOptions"
          label="选项"
          prop="options"
        >
          <div class="options-area">
            <div v-for="(opt, idx) in form.options" :key="idx" class="option-row">
              <el-input
                v-model="opt.content"
                :placeholder="'选项 ' + opt.optionLabel"
                style="flex: 1"
              />
              <el-checkbox v-model="opt.isCorrect" :true-value="1" :false-value="0" class="option-correct">
                正确
              </el-checkbox>
              <el-button
                :icon="Delete"
                circle
                size="small"
                type="danger"
                @click="removeOption(idx)"
                :disabled="form.options.length <= 2"
              />
            </div>
            <el-button type="primary" link @click="addOption" :disabled="form.options.length >= 8">
              + 添加选项
            </el-button>
          </div>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="难度" prop="difficulty">
              <el-rate v-model="form.difficulty" :max="5" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="分值" prop="score">
              <el-input-number v-model="form.score" :min="1" :max="100" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="标签" prop="tags">
              <el-input v-model="form.tags" placeholder="逗号分隔" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="题目解析">
          <el-input
            v-model="form.analysis"
            type="textarea"
            :rows="3"
            placeholder="请输入题目解析（选填）"
          />
        </el-form-item>

        <el-form-item label="关联知识点">
          <el-tree-select
            v-model="form.knowledgePointIds"
            :data="kpTreeData"
            :props="{ label: 'name', value: 'id', children: 'children' } as any"
            multiple
            check-strictly
            clearable
            placeholder="选择关联知识点"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ editingQuestion ? '更新' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus, Search, Delete, Download, Upload, FolderOpened, ArrowDown } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import {
  getAdminQuestionPage,
  createQuestion,
  updateQuestion,
  deleteQuestion,
  exportQuestions,
  downloadTemplate,
  importQuestions,
  importQuestionsMarkdown,
  downloadMarkdownTemplate,
  type QuestionVO,
  type QuestionForm,
  type OptionItem,
  type QuestionImportResult,
} from '@/api/question'
import { clearAssetCache } from '@/api/ai'
import { getAllCourses, type CourseVO } from '@/api/course'
import { getKnowledgeTree, type KnowledgePointVO } from '@/api/knowledgePoint'

const questions = ref<QuestionVO[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  keyword: '',
  questionType: '' as string,
  courseId: null as number | null,
  difficulty: null as number | null,
})

// 课程列表
const courseList = ref<CourseVO[]>([])

// 知识点树
const kpTreeData = ref<KnowledgePointVO[]>([])

// 导入/导出相关
const importDialogVisible = ref(false)
const importResultVisible = ref(false)
const importLoading = ref(false)
const importFile = ref<File | null>(null)
const uploadRef = ref()
const mdUploadRef = ref()
const importTab = ref('excel')
const importResult = reactive<QuestionImportResult>({
  totalRows: 0,
  successCount: 0,
  failCount: 0,
  errors: [],
})

// 弹窗相关
const dialogVisible = ref(false)
const editingQuestion = ref<QuestionVO | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  content: '',
  questionType: '' as string,
  courseId: null as number | null,
  difficulty: 3,
  analysis: '',
  tags: '',
  score: 1,
  options: [] as OptionItem[],
  knowledgePointIds: [] as number[],
})

const rules: FormRules = {
  content: [{ required: true, message: '请输入题干内容', trigger: 'blur' }],
  questionType: [{ required: true, message: '请选择题型', trigger: 'change' }],
  courseId: [{ required: true, message: '请选择所属课程', trigger: 'change' }],
}

// 是否显示选项区域
const showOptions = computed(() => {
  return ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(form.questionType)
})

// 题型标签
function questionTypeLabel(type: string) {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选',
    MULTIPLE_CHOICE: '多选',
    TRUE_FALSE: '判断',
    FILL_BLANK: '填空',
    SHORT_ANSWER: '简答',
  }
  return map[type] || type
}

function questionTypeTag(type: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    SINGLE_CHOICE: 'primary',
    MULTIPLE_CHOICE: 'success',
    TRUE_FALSE: 'warning',
    FILL_BLANK: 'info',
    SHORT_ANSWER: 'danger',
  }
  return map[type] || 'primary'
}

// 获取选项标签 A/B/C/D...
function getOptionLabel(index: number): string {
  return String.fromCharCode(65 + index)
}

// 题型切换时重置选项
function onTypeChange(type: string) {
  if (type === 'TRUE_FALSE') {
    form.options = [
      { content: '正确', optionLabel: 'A', isCorrect: 1, sortOrder: 0 },
      { content: '错误', optionLabel: 'B', isCorrect: 0, sortOrder: 1 },
    ]
  } else if (['SINGLE_CHOICE', 'MULTIPLE_CHOICE'].includes(type)) {
    if (form.options.length < 2 || form.options[0].content === '正确') {
      form.options = [
        { content: '', optionLabel: 'A', isCorrect: 0, sortOrder: 0 },
        { content: '', optionLabel: 'B', isCorrect: 0, sortOrder: 1 },
        { content: '', optionLabel: 'C', isCorrect: 0, sortOrder: 2 },
        { content: '', optionLabel: 'D', isCorrect: 0, sortOrder: 3 },
      ]
    }
  } else {
    form.options = []
  }
}

function addOption() {
  const idx = form.options.length
  form.options.push({
    content: '',
    optionLabel: getOptionLabel(idx),
    isCorrect: 0,
    sortOrder: idx,
  })
}

function removeOption(idx: number) {
  form.options.splice(idx, 1)
  // 重新分配标签
  form.options.forEach((opt, i) => {
    opt.optionLabel = getOptionLabel(i)
    opt.sortOrder = i
  })
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
    })
    questions.value = res.data.records
    total.value = res.data.total
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
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

async function fetchKPTree(courseId?: number) {
  try {
    if (!courseId) {
      kpTreeData.value = []
      return
    }
    const res = await getKnowledgeTree(courseId)
    kpTreeData.value = res.data
  } catch {
    kpTreeData.value = []
  }
}

function openDialog(question?: QuestionVO) {
  editingQuestion.value = question || null
  if (question) {
    form.content = question.content
    form.questionType = question.questionType
    form.courseId = question.courseId
    form.difficulty = question.difficulty
    form.analysis = question.analysis || ''
    form.tags = question.tags || ''
    form.score = question.score
    form.options = question.options.map(o => ({ ...o }))
    form.knowledgePointIds = [...(question.knowledgePointIds || [])]
  } else {
    form.content = ''
    form.questionType = ''
    form.courseId = null
    form.difficulty = 3
    form.analysis = ''
    form.tags = ''
    form.score = 1
    form.options = []
    form.knowledgePointIds = []
  }
  dialogVisible.value = true
  // 加载知识点树
  if (form.courseId) {
    fetchKPTree(form.courseId)
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  // 校验选项
  if (showOptions.value) {
    const hasCorrect = form.options.some(o => o.isCorrect === 1)
    if (!hasCorrect) {
      ElMessage.warning('请至少设置一个正确选项')
      return
    }
    const hasEmpty = form.options.some(o => !o.content.trim())
    if (hasEmpty) {
      ElMessage.warning('选项内容不能为空')
      return
    }
  }

  submitting.value = true
  try {
    const data: QuestionForm = {
      content: form.content,
      questionType: form.questionType,
      courseId: form.courseId!,
      difficulty: form.difficulty,
      analysis: form.analysis || undefined,
      tags: form.tags || undefined,
      score: form.score,
      options: showOptions.value ? form.options : undefined,
      knowledgePointIds: form.knowledgePointIds.length > 0 ? form.knowledgePointIds : undefined,
    }

    if (editingQuestion.value) {
      await updateQuestion(editingQuestion.value.id, data)
      ElMessage.success('更新成功')
    } else {
      await createQuestion(data)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchQuestions()
  } catch {
    // 错误已在拦截器中处理
  } finally {
    submitting.value = false
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

async function handleClearAiCache(questionId: number) {
  try {
    await clearAssetCache(questionId)
    ElMessage.success('AI 学习资产缓存已清除')
  } catch {
    ElMessage.error('清除失败')
  }
}

// 导出题目
async function handleExport() {
  try {
    const res = await exportQuestions({
      questionType: filters.questionType || undefined,
      courseId: filters.courseId || undefined,
      difficulty: filters.difficulty || undefined,
    })
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '题目导出.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

// 下载导入模板
async function handleDownloadTemplate() {
  try {
    const res = await downloadTemplate()
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '题目导入模板.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('模板下载失败')
  }
}

// 文件选择
function onImportFileChange(file: UploadFile) {
  importFile.value = file.raw || null
}

function onMdFileChange(file: UploadFile) {
  importFile.value = file.raw || null
}

// 下载 Markdown 导入模板
async function handleDownloadMdTemplate() {
  try {
    const res = await downloadMarkdownTemplate()
    const blob = new Blob([res.data], {
      type: 'text/markdown; charset=utf-8',
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '题目导入模板.md'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('模板下载失败')
  }
}

// 导入题目（根据当前 Tab 选择 Excel 或 Markdown）
async function handleImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  importLoading.value = true
  try {
    const isMarkdown = importTab.value === 'markdown'
    const res = isMarkdown
      ? await importQuestionsMarkdown(importFile.value)
      : await importQuestions(importFile.value)
    const result = res.data
    importResult.totalRows = result.totalRows
    importResult.successCount = result.successCount
    importResult.failCount = result.failCount
    importResult.errors = result.errors || []
    importDialogVisible.value = false
    importResultVisible.value = true
    importFile.value = null
    if (uploadRef.value) {
      uploadRef.value.clearFiles()
    }
    if (mdUploadRef.value) {
      mdUploadRef.value.clearFiles()
    }
    if (result.successCount > 0) {
      fetchQuestions()
    }
  } catch {
    ElMessage.error('导入失败')
  } finally {
    importLoading.value = false
  }
}

onMounted(() => {
  fetchQuestions()
  fetchCourses()
  fetchKPTree()
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.options-area {
  width: 100%;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.option-correct {
  white-space: nowrap;
}
</style>
