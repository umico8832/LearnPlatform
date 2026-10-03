<template>
  <el-dialog
    v-model="visible"
    :title="editingQuestion ? '编辑题目' : '新增题目'"
    width="780px"
    destroy-on-close
    :close-on-click-modal="!submitting"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    :before-close="requestClose"
    top="5vh"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" :disabled="submitting" @submit.prevent>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="所属课程" prop="courseId">
            <el-select
              v-model="form.courseId"
              placeholder="选择课程"
              style="width: 100%"
              :disabled="submitting"
              @change="onCourseChange"
            >
              <el-option v-for="course in courses" :key="course.id" :label="course.name" :value="course.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="题型" prop="questionType">
            <el-select
              v-model="form.questionType"
              placeholder="选择题型"
              style="width: 100%"
              :disabled="submitting"
              @change="onTypeChange"
            >
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
          :disabled="submitting"
          placeholder="请输入题干内容（支持 Markdown）"
        />
      </el-form-item>

      <el-form-item v-if="showOptions" label="选项" prop="options">
        <div class="options-area">
          <el-radio-group
            v-if="isSingleChoice || isTrueFalse"
            :model-value="exclusiveCorrectIndex"
            :disabled="submitting"
            aria-label="正确答案"
            class="exclusive-answer-group"
            @update:model-value="setExclusiveCorrect"
          >
            <div v-for="(option, index) in form.options" :key="index" class="option-row">
              <el-input
                v-if="!isTrueFalse"
                v-model="option.content"
                :disabled="submitting"
                :placeholder="`选项 ${option.optionLabel}`"
                style="flex: 1"
              />
              <span v-else class="true-false-option">{{ option.content }}</span>
              <el-radio :value="index" :aria-label="`正确答案：选项 ${option.optionLabel}`">正确答案</el-radio>
              <el-button
                :icon="Delete"
                circle
                size="small"
                type="danger"
                :disabled="submitting || isTrueFalse || form.options.length <= 2"
                @click="removeOption(index)"
              />
            </div>
          </el-radio-group>
          <template v-else>
            <div v-for="(option, index) in form.options" :key="index" class="option-row">
              <el-input
                v-model="option.content"
                :disabled="submitting"
                :placeholder="`选项 ${option.optionLabel}`"
                style="flex: 1"
              />
              <el-checkbox
                v-model="option.isCorrect"
                :disabled="submitting"
                :true-value="1"
                :false-value="0"
                class="option-correct"
              >
                正确
              </el-checkbox>
              <el-button
                :icon="Delete"
                circle
                size="small"
                type="danger"
                :disabled="submitting || form.options.length <= 2"
                @click="removeOption(index)"
              />
            </div>
          </template>
          <el-button
            type="primary"
            link
            :disabled="submitting || isTrueFalse || form.options.length >= 8"
            @click="addOption"
            >+ 添加选项</el-button
          >
          <el-alert
            v-if="optionConfigurationError"
            :title="optionConfigurationError"
            type="warning"
            :closable="false"
            class="option-configuration-error"
          />
        </div>
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="难度" prop="difficulty">
            <el-rate v-model="form.difficulty" :max="5" :disabled="submitting" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="分值" prop="score">
            <el-input-number v-model="form.score" :min="1" :max="100" :disabled="submitting" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="标签" prop="tags">
            <el-input v-model="form.tags" :disabled="submitting" placeholder="逗号分隔" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="题目解析">
        <el-input
          v-model="form.analysis"
          type="textarea"
          :rows="3"
          :disabled="submitting"
          placeholder="请输入题目解析（选填）"
        />
      </el-form-item>

      <el-form-item label="关联知识点">
        <el-tree-select
          v-model="form.knowledgePointIds"
          :data="knowledgeTree"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          multiple
          check-strictly
          clearable
          :disabled="submitting"
          placeholder="选择关联知识点"
          style="width: 100%"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="submitting" @click="requestClose()">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submit">
        {{ editingQuestion ? '更新' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createQuestion, updateQuestion, type OptionItem, type QuestionForm, type QuestionVO } from '@/api/question'
import { getKnowledgeTree, type KnowledgePointVO } from '@/api/knowledgePoint'

defineProps<{ courses: Array<{ id: number; name: string }> }>()
const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const editingQuestion = ref<QuestionVO | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const knowledgeTree = ref<KnowledgePointVO[]>([])
let formVersion = 0

const form = reactive(initialForm())

const rules: FormRules = {
  content: [{ required: true, message: '请输入题干内容', trigger: 'blur' }],
  questionType: [{ required: true, message: '请选择题型', trigger: 'change' }],
  courseId: [{ required: true, message: '请选择所属课程', trigger: 'change' }],
}

const showOptions = computed(() => ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(form.questionType))
const isSingleChoice = computed(() => form.questionType === 'SINGLE_CHOICE')
const isTrueFalse = computed(() => form.questionType === 'TRUE_FALSE')
const optionConfigurationError = ref('')
const exclusiveCorrectIndex = computed<number | undefined>(() => {
  const correctIndexes = form.options.flatMap((option, index) => (option.isCorrect === 1 ? [index] : []))
  return correctIndexes.length === 1 ? correctIndexes[0] : undefined
})

function initialForm() {
  return {
    content: '',
    questionType: '',
    courseId: null as number | null,
    difficulty: 3,
    analysis: '',
    tags: '',
    score: 1,
    options: [] as OptionItem[],
    knowledgePointIds: [] as number[],
  }
}

function open(question?: QuestionVO) {
  if (submitting.value) return
  formVersion++
  editingQuestion.value = question || null
  Object.assign(form, initialForm())
  if (question) {
    Object.assign(form, {
      content: question.content,
      questionType: question.questionType,
      courseId: question.courseId,
      difficulty: question.difficulty,
      analysis: question.analysis || '',
      tags: question.tags || '',
      score: question.score,
      options: normalizeOptions(question.questionType, question.options),
      knowledgePointIds: [...(question.knowledgePointIds || [])],
    })
  }
  optionConfigurationError.value = validateOptionConfiguration()
  visible.value = true
  void loadKnowledgeTree(form.courseId)
}

async function loadKnowledgeTree(courseId?: number | null) {
  if (!courseId) {
    knowledgeTree.value = []
    return
  }
  try {
    const response = await getKnowledgeTree(courseId)
    knowledgeTree.value = response.data
  } catch {
    knowledgeTree.value = []
  }
}

function onCourseChange(courseId?: number | null) {
  form.knowledgePointIds = []
  void loadKnowledgeTree(courseId)
}

function onTypeChange(type: string) {
  optionConfigurationError.value = ''
  if (type === 'TRUE_FALSE') {
    form.options = trueFalseOptions(0)
  } else if (['SINGLE_CHOICE', 'MULTIPLE_CHOICE'].includes(type)) {
    if (form.options.length < 2 || form.options[0].content === '正确') {
      form.options = Array.from({ length: 4 }, (_, index) => ({
        content: '',
        optionLabel: optionLabel(index),
        isCorrect: 0,
        sortOrder: index,
      }))
    }
  } else {
    form.options = []
  }
}

function trueFalseOptions(correctIndex: number | undefined): OptionItem[] {
  return [
    { content: '正确', optionLabel: 'A', isCorrect: correctIndex === 0 ? 1 : 0, sortOrder: 0 },
    { content: '错误', optionLabel: 'B', isCorrect: correctIndex === 1 ? 1 : 0, sortOrder: 1 },
  ]
}

function trueFalseValue(value: string | undefined): 0 | 1 | undefined {
  const normalized = value?.trim().toUpperCase()
  if (['TRUE', '正确', '对', 'A'].includes(normalized || '')) return 0
  if (['FALSE', '错误', '错', 'B'].includes(normalized || '')) return 1
  return undefined
}

function normalizeOptions(questionType: string, options: OptionItem[]) {
  if (questionType !== 'TRUE_FALSE') return options.map((option) => ({ ...option }))
  const correctOptions = options.filter((option) => option.isCorrect === 1)
  const correctIndex = correctOptions.length === 1 ? trueFalseValue(correctOptions[0].content) : undefined
  return trueFalseOptions(correctIndex)
}

function setExclusiveCorrect(value: unknown) {
  const index = typeof value === 'number' ? value : Number(value)
  if (!Number.isInteger(index) || index < 0 || index >= form.options.length) return
  form.options.forEach((option, optionIndex) => {
    option.isCorrect = optionIndex === index ? 1 : 0
  })
  optionConfigurationError.value = ''
}

function validateOptionConfiguration(): string {
  if (!showOptions.value) return ''
  const correctCount = form.options.filter((option) => option.isCorrect === 1).length
  if (isSingleChoice.value && correctCount !== 1) return '单选题必须且只能有 1 个正确答案'
  if (isTrueFalse.value) {
    const validShape =
      form.options.length === 2 &&
      form.options[0]?.content === '正确' &&
      form.options[1]?.content === '错误' &&
      correctCount === 1
    if (!validShape) return '判断题答案只能是正确/错误'
  }
  if (form.questionType === 'MULTIPLE_CHOICE' && correctCount === 0) return '请至少设置一个正确选项'
  if (form.options.some((option) => !option.content.trim())) return '选项内容不能为空'
  return ''
}

function addOption() {
  const index = form.options.length
  form.options.push({ content: '', optionLabel: optionLabel(index), isCorrect: 0, sortOrder: index })
}

function removeOption(index: number) {
  form.options.splice(index, 1)
  form.options.forEach((option, optionIndex) => {
    option.optionLabel = optionLabel(optionIndex)
    option.sortOrder = optionIndex
  })
}

function optionLabel(index: number): string {
  return String.fromCharCode(65 + index)
}

async function submit() {
  if (!formRef.value || submitting.value) return
  const submitVersion = formVersion
  submitting.value = true
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    submitting.value = false
    return
  }
  const configurationError = validateOptionConfiguration()
  if (configurationError) {
    optionConfigurationError.value = configurationError
    ElMessage.warning(configurationError)
    submitting.value = false
    return
  }

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

  try {
    if (editingQuestion.value) {
      await updateQuestion(editingQuestion.value.id, data)
      ElMessage.success('更新成功')
    } else {
      await createQuestion(data)
      ElMessage.success('创建成功')
    }
    if (submitVersion === formVersion) {
      visible.value = false
      emit('saved')
    }
  } catch {
    // 错误已在拦截器中处理。
  } finally {
    if (submitVersion === formVersion) submitting.value = false
  }
}

function requestClose(done?: () => void) {
  if (submitting.value) return
  visible.value = false
  done?.()
}

defineExpose({ open })
</script>

<style scoped>
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

.exclusive-answer-group {
  display: block;
}

.true-false-option {
  flex: 1;
  color: var(--lp-text-primary);
  font-size: var(--lp-text-sm);
}

.option-configuration-error {
  margin-top: var(--lp-space-3);
}
</style>
