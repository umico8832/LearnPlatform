<template>
  <el-dialog
    :model-value="true"
    title="整理为题目投稿"
    width="720px"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    @close="emit('close')"
  >
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="所属课程" required
        ><el-select v-model="form.courseId" aria-label="题目所属课程" :disabled="!!post.courseId"
          ><el-option v-for="course in courses" :key="course.id" :label="course.name" :value="course.id" /></el-select
      ></el-form-item>
      <el-form-item label="题型"
        ><el-select v-model="form.questionType" aria-label="题型" @change="form.correctAnswer = ''"
          ><el-option v-for="(label, key) in types" :key="key" :label="label" :value="key" /></el-select
      ></el-form-item>
      <el-form-item label="题干" required
        ><el-input
          v-model="form.content"
          type="textarea"
          :rows="4"
          maxlength="10000"
          placeholder="核对附件后填写完整题干"
      /></el-form-item>
      <el-form-item v-if="choice" label="选项与正确答案" required>
        <div>
          <div v-for="(option, index) in options" :key="index" class="community-actions">
            <el-input v-model="option.content" :aria-label="`选项 ${index + 1}`" /><el-checkbox
              v-model="option.isCorrect"
              >{{ String.fromCharCode(65 + index) }} 正确</el-checkbox
            ><el-button :disabled="options.length <= 2" link @click="options.splice(index, 1)">移除</el-button>
          </div>
          <el-button :disabled="options.length >= 8" @click="options.push({ content: '', isCorrect: false })"
            >添加选项</el-button
          >
        </div>
      </el-form-item>
      <el-form-item v-else-if="form.questionType === 'TRUE_FALSE'" label="正确答案" required
        ><el-radio-group v-model="form.correctAnswer"
          ><el-radio value="TRUE">正确</el-radio><el-radio value="FALSE">错误</el-radio></el-radio-group
        ></el-form-item
      >
      <el-form-item v-else label="参考答案" required
        ><el-input v-model="form.correctAnswer" type="textarea" :rows="3" maxlength="2000" placeholder="填写参考答案"
      /></el-form-item>
      <el-form-item label="解析"
        ><el-input v-model="form.analysis" type="textarea" :rows="3" maxlength="10000"
      /></el-form-item>
      <el-form-item label="难度"><el-rate v-model="form.difficulty" /></el-form-item>
      <p class="community-muted">来源将关联本条社区投稿，保存后前往投稿管理审核与入库。</p>
    </el-form>
    <template #footer
      ><el-button :disabled="saving" @click="emit('close')">取消</el-button
      ><el-button type="primary" :loading="saving" @click="submit">保存题目投稿</el-button></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { prepareCommunityQuestion, type CommunityPost } from '@/api/community'
import { getAllCourses, type CourseVO } from '@/api/course'
import type { SubmissionForm } from '@/api/submission'
import { errorMessage } from '@/utils/errors'
const props = defineProps<{ post: CommunityPost }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const requestKey = crypto.randomUUID()
const types = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  TRUE_FALSE: '判断题',
  FILL_BLANK: '填空题',
  SHORT_ANSWER: '简答题',
}
const form = reactive<SubmissionForm>({
  courseId: props.post.courseId || 0,
  content: '',
  questionType: 'SHORT_ANSWER',
  difficulty: 3,
  correctAnswer: '',
  analysis: '',
})
const options = ref([
  { content: '', isCorrect: false },
  { content: '', isCorrect: false },
])
const courses = ref<CourseVO[]>([]),
  saving = ref(false),
  error = ref('')
const choice = computed(() => ['SINGLE_CHOICE', 'MULTIPLE_CHOICE'].includes(form.questionType))
async function submit() {
  if (saving.value) return
  const count = options.value.filter((o) => o.isCorrect).length
  if (
    !form.courseId ||
    !form.content.trim() ||
    !form.difficulty ||
    (choice.value
      ? options.value.some((o) => !o.content.trim()) ||
        count === 0 ||
        (form.questionType === 'SINGLE_CHOICE' && count !== 1)
      : !form.correctAnswer?.trim())
  ) {
    error.value = '请补全课程、题干和正确答案，并检查选项。'
    return
  }
  saving.value = true
  error.value = ''
  try {
    await prepareCommunityQuestion(
      props.post.id,
      {
        ...form,
        knowledgePointIds: props.post.knowledgePointId?.toString(),
        optionsJson: choice.value
          ? JSON.stringify(options.value.map((o, i) => ({ ...o, label: String.fromCharCode(65 + i) })))
          : undefined,
      },
      requestKey,
    )
    emit('saved')
  } catch (e) {
    error.value = errorMessage(e, '保存失败，请重试')
  } finally {
    saving.value = false
  }
}
onMounted(async () => {
  try {
    courses.value = (await getAllCourses()).data
  } catch (e) {
    error.value = errorMessage(e, '课程加载失败')
  }
})
</script>
