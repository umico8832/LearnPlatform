<template>
  <el-dialog
    :model-value="true"
    :title="initialType === 'TOPIC' ? '发起讨论' : '贡献学习资料'"
    width="720px"
    :close-on-click-modal="false"
    :before-close="close"
  >
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-form label-position="top" @submit.prevent="submit">
      <div class="community-form-grid">
        <el-form-item label="内容类型" required>
          <el-select v-model="draft.contentType" aria-label="内容类型" :disabled="saving" @change="files = []">
            <el-option v-for="(label, value) in communityTypes" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试分类" required>
          <el-select v-model="examId" aria-label="考试分类" filterable @change="draft.subjectId = ''">
            <el-option v-for="exam in exams" :key="exam.id" :label="exam.name" :value="exam.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="科目" required>
          <el-select v-model="draft.subjectId" aria-label="科目" :disabled="!examId">
            <el-option v-for="subject in subjects" :key="subject.id" :label="subject.name" :value="subject.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学校分区">
          <el-select v-model="draft.schoolId" aria-label="学校分区" clearable filterable placeholder="不限学校">
            <el-option v-for="school in schools" :key="school.id" :label="school.name" :value="school.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联课程">
          <el-select v-model="draft.courseId" aria-label="关联课程" clearable @change="loadKnowledge">
            <el-option v-for="course in courses" :key="course.id" :label="course.name" :value="course.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联知识点">
          <el-tree-select
            v-model="draft.knowledgePointId"
            :data="knowledge"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            check-strictly
            clearable
            :disabled="!draft.courseId"
            aria-label="关联知识点"
          />
        </el-form-item>
      </div>
      <el-form-item label="标题" required
        ><el-input v-model="draft.title" maxlength="120" show-word-limit placeholder="用一句话说明你想讨论或分享的内容"
      /></el-form-item>
      <el-form-item label="知识点或主题"
        ><el-input v-model="draft.conceptName" maxlength="80" placeholder="例如：栈的后进先出"
      /></el-form-item>
      <el-form-item label="正文" required
        ><el-input
          v-model="draft.body"
          type="textarea"
          :rows="6"
          maxlength="4000"
          show-word-limit
          placeholder="描述问题、已有思路或资料内容"
      /></el-form-item>
      <el-form-item v-if="draft.contentType !== 'TOPIC'" label="来源与使用许可" required>
        <el-input
          v-model="draft.sourceNote"
          type="textarea"
          :rows="2"
          maxlength="500"
          placeholder="说明原创、出处及允许分享或使用的范围"
        />
      </el-form-item>
      <el-form-item v-if="draft.contentType === 'question_bank'" label="题库附件" required>
        <input
          aria-label="选择题库附件"
          type="file"
          :accept="communityFormats"
          multiple
          :disabled="saving"
          @change="selectFiles"
        />
        <p class="community-muted">最多8个文件，每个不超过30MB。支持 PDF、Word、WPS、Excel、CSV、JSON、文本和 ZIP。</p>
        <ul v-if="files.length">
          <li v-for="(file, index) in files" :key="index">
            {{ file.name }}
            <el-button link type="danger" :disabled="saving" @click="files.splice(index, 1)">移除</el-button>
          </li>
        </ul>
      </el-form-item>
    </el-form>
    <template #footer
      ><el-button :disabled="saving" @click="close">取消</el-button
      ><el-button type="primary" :loading="saving" @click="submit">{{
        draft.contentType === 'TOPIC' ? '发布讨论' : '提交审核'
      }}</el-button></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  createCommunityPost,
  communityTypes,
  communityFormats,
  type CommunityCategory,
  type CommunityDraft,
} from '@/api/community'
import { getAllCourses, type CourseVO } from '@/api/course'
import { getKnowledgeTree, type KnowledgePointVO } from '@/api/knowledgePoint'
import { errorMessage } from '@/utils/errors'
const props = defineProps<{ categories: CommunityCategory[]; initialType: string }>()
const emit = defineEmits<{ close: []; created: [id: number] }>()
const draft = reactive<CommunityDraft>({
  title: '',
  body: '',
  contentType: props.initialType,
  subjectId: '',
  conceptName: '',
  sourceNote: '',
})
const examId = ref('')
const courses = ref<CourseVO[]>([])
const knowledge = ref<KnowledgePointVO[]>([])
const files = ref<File[]>([])
const saving = ref(false)
const error = ref('')
const exams = computed(() => props.categories.filter((c) => c.kind === 'EXAM'))
const subjects = computed(() => props.categories.filter((c) => c.parentId === examId.value))
const schools = computed(() => props.categories.filter((c) => c.kind === 'SCHOOL'))
let knowledgeRequest = 0
async function loadKnowledge() {
  const request = ++knowledgeRequest
  draft.knowledgePointId = undefined
  knowledge.value = []
  if (!draft.courseId) return
  try {
    const result = await getKnowledgeTree(draft.courseId)
    if (request === knowledgeRequest) knowledge.value = result.data
  } catch (e) {
    if (request === knowledgeRequest) error.value = errorMessage(e, '知识点加载失败')
  }
}
function selectFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = Array.from(input.files || [])
  const formats = communityFormats.split(',')
  if (
    selected.length + files.value.length > 8 ||
    selected.some(
      (f) =>
        f.size === 0 || f.size > 30 * 1024 * 1024 || !formats.includes('.' + f.name.split('.').pop()?.toLowerCase()),
    )
  ) {
    error.value = '请选择最多8个受支持的非空文件，每个不超过30MB。'
  } else {
    files.value.push(...selected)
    error.value = ''
  }
  input.value = ''
}
async function close() {
  if (saving.value) return
  if (draft.title || draft.body || files.value.length) {
    try {
      await ElMessageBox.confirm('尚未提交的内容将丢失。', '放弃本次编辑？', {
        confirmButtonText: '放弃',
        cancelButtonText: '继续编辑',
        type: 'warning',
      })
    } catch {
      return
    }
  }
  emit('close')
}
async function submit() {
  if (saving.value) return
  if (
    !draft.title.trim() ||
    !draft.body.trim() ||
    !draft.subjectId ||
    (draft.contentType !== 'TOPIC' && !draft.sourceNote.trim()) ||
    (draft.contentType === 'question_bank' && !files.value.length)
  ) {
    error.value = '请填写标题、正文和科目，投稿还需来源说明，题库投稿需选择附件。'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const result = await createCommunityPost(
      {
        ...draft,
        schoolId: draft.schoolId || undefined,
        courseId: draft.courseId || undefined,
        knowledgePointId: draft.knowledgePointId || undefined,
      },
      files.value,
    )
    emit('created', result.data)
  } catch (e) {
    error.value = errorMessage(e, '提交失败，内容已保留，请重试。')
  } finally {
    saving.value = false
  }
}
onMounted(async () => {
  try {
    courses.value = (await getAllCourses()).data
  } catch (e) {
    error.value = errorMessage(e, '课程加载失败，请重新打开表单')
  }
})
</script>
