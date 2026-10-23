<template>
  <LtSheet v-model:open="sheetOpen" :title="initialType === 'TOPIC' ? '发起讨论' : '贡献学习资料'" :width="720">
    <form class="composer lt" data-appearance="dark" @submit.prevent="submit">
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <div class="grid">
        <label
          >内容类型<select v-model="draft.contentType" :disabled="saving" @change="files = []">
            <option v-for="(label, value) in communityTypes" :key="value" :value="value">{{ label }}</option>
          </select></label
        >
        <label
          >考试分类<select v-model="examId" :disabled="saving" @change="draft.subjectId = ''">
            <option value="">请选择</option>
            <option v-for="exam in exams" :key="exam.id" :value="exam.id">{{ exam.name }}</option>
          </select></label
        >
        <label
          >科目<select v-model="draft.subjectId" :disabled="saving || !examId">
            <option value="">请选择</option>
            <option v-for="subject in subjects" :key="subject.id" :value="subject.id">{{ subject.name }}</option>
          </select></label
        >
        <label
          >学校分区<select v-model="draft.schoolId" :disabled="saving">
            <option :value="undefined">不限学校</option>
            <option v-for="school in schools" :key="school.id" :value="school.id">{{ school.name }}</option>
          </select></label
        >
        <label
          >关联课程<select v-model="draft.courseId" :disabled="saving" @change="loadKnowledge">
            <option :value="undefined">不关联课程</option>
            <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }}</option>
          </select></label
        >
        <label
          >关联知识点<select v-model="draft.knowledgePointId" :disabled="saving || !draft.courseId">
            <option :value="undefined">不关联知识点</option>
            <option v-for="point in knowledgeOptions" :key="point.id" :value="point.id">{{ point.label }}</option>
          </select></label
        >
      </div>
      <label
        >标题<input
          v-model="draft.title"
          :disabled="saving"
          maxlength="120"
          placeholder="用一句话说明你想讨论或分享的内容"
      /></label>
      <label
        >知识点或主题<input
          v-model="draft.conceptName"
          :disabled="saving"
          maxlength="80"
          placeholder="例如：栈的后进先出"
      /></label>
      <label
        >正文<textarea
          v-model="draft.body"
          :disabled="saving"
          maxlength="4000"
          rows="6"
          placeholder="描述问题、已有思路或资料内容"
        />
      </label>
      <label v-if="draft.contentType !== 'TOPIC'"
        >来源与使用许可<textarea
          v-model="draft.sourceNote"
          :disabled="saving"
          maxlength="500"
          rows="2"
          placeholder="说明原创、出处及允许分享或使用的范围"
        />
      </label>
      <fieldset v-if="draft.contentType === 'question_bank'">
        <legend>题库附件</legend>
        <input
          aria-label="选择题库附件"
          type="file"
          :accept="communityFormats"
          multiple
          :disabled="saving"
          @change="selectFiles"
        />
        <p>最多 8 个文件，每个不超过 30MB。</p>
        <ul v-if="files.length">
          <li v-for="(file, index) in files" :key="file.name">
            <span>{{ file.name }}</span
            ><button type="button" :disabled="saving" @click="files.splice(index, 1)">移除</button>
          </li>
        </ul>
      </fieldset>
      <footer>
        <LtButton type="button" :disabled="saving" @click="requestClose">取消</LtButton
        ><LtButton type="submit" variant="primary" :disabled="saving">{{
          saving ? '提交中…' : draft.contentType === 'TOPIC' ? '发布讨论' : '提交审核'
        }}</LtButton>
      </footer>
    </form>
  </LtSheet>
  <LtSheet v-model:open="confirming" title="放弃本次编辑？" :width="420"
    ><p class="confirm-copy">尚未提交的内容将丢失。</p>
    <div class="confirm-actions">
      <LtButton @click="confirming = false">继续编辑</LtButton
      ><LtButton variant="primary" @click="discard">放弃</LtButton>
    </div></LtSheet
  >
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { LtButton, LtSheet } from '@/components/living'
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
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'
const props = defineProps<{ categories: CommunityCategory[]; initialType: string }>()
const emit = defineEmits<{ close: []; created: [id: number] }>()
const open = ref(true)
const confirming = ref(false)
const sheetOpen = computed({
  get: () => open.value,
  set: (value: boolean) => {
    if (value) open.value = true
    else requestClose()
  },
})
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
const knowledgeOptions = computed(() => flatten(knowledge.value))
let knowledgeRequest = 0
let alive = true
let requestGeneration = 0
function flatten(nodes: KnowledgePointVO[], depth = 0): { id: number; label: string }[] {
  return nodes.flatMap((node) => [
    { id: node.id, label: `${'— '.repeat(depth)}${node.name}` },
    ...flatten(node.children || [], depth + 1),
  ])
}
function current(request: number, session: number) {
  return alive && request === requestGeneration && session === getAuthSessionVersion() && isAuthenticated()
}
async function loadKnowledge() {
  const request = ++knowledgeRequest
  const session = getAuthSessionVersion()
  draft.knowledgePointId = undefined
  knowledge.value = []
  if (!draft.courseId) return
  try {
    const result = await getKnowledgeTree(draft.courseId)
    if (request === knowledgeRequest && current(requestGeneration, session)) knowledge.value = result.data
  } catch (e) {
    if (request === knowledgeRequest && current(requestGeneration, session))
      error.value = errorMessage(e, '知识点加载失败')
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
        f.size === 0 || f.size > 30 * 1024 * 1024 || !formats.includes(`.${f.name.split('.').pop()?.toLowerCase()}`),
    )
  ) {
    error.value = '请选择最多8个受支持的非空文件，每个不超过30MB。'
  } else {
    files.value.push(...selected)
    error.value = ''
  }
  input.value = ''
}
function requestClose() {
  if (saving.value) return
  if (draft.title || draft.body || files.value.length) {
    open.value = true
    confirming.value = true
    return
  }
  emit('close')
}
function discard() {
  confirming.value = false
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
  const request = ++requestGeneration
  const session = getAuthSessionVersion()
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
      { errorDisplay: 'inline' },
    )
    if (current(request, session)) emit('created', result.data)
  } catch (e) {
    if (current(request, session)) error.value = errorMessage(e, '提交失败，内容已保留，请重试。')
  } finally {
    if (current(request, session)) saving.value = false
  }
}
onMounted(async () => {
  const request = requestGeneration
  const session = getAuthSessionVersion()
  try {
    courses.value = (await getAllCourses({ errorDisplay: 'inline' })).data
  } catch (e) {
    if (current(request, session)) error.value = errorMessage(e, '课程加载失败，请重新打开表单')
  }
})
const unsubscribeAuth = onAuthSessionChange(() => {
  requestGeneration++
  knowledgeRequest++
  saving.value = false
  if (!isAuthenticated()) emit('close')
})
onUnmounted(() => {
  alive = false
  requestGeneration++
  knowledgeRequest++
  unsubscribeAuth()
})
</script>
<style scoped>
.composer {
  display: grid;
  gap: var(--lt-space-4);
}
.composer label,
.composer fieldset {
  display: grid;
  gap: var(--lt-space-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lt-space-4);
}
input,
select,
textarea {
  width: 100%;
  box-sizing: border-box;
  padding: var(--lt-space-3);
  border: 0;
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 1px var(--lt-separator);
  color: var(--lt-label);
  font: var(--lt-text-body)/var(--lt-leading-ui) var(--lt-font);
}
select {
  box-shadow: none;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  height: var(--lt-control-lg);
}
textarea {
  resize: vertical;
}
input:focus-visible,
select:focus-visible,
textarea:focus-visible {
  outline: 2px solid var(--lt-accent);
  outline-offset: 2px;
}
.form-error {
  margin: 0;
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-md);
  background: var(--lt-red-soft);
  color: var(--lt-red-text);
}
fieldset {
  padding: var(--lt-space-3);
  border: 0;
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-2);
}
fieldset p {
  margin: 0;
  color: var(--lt-label-3);
}
fieldset ul {
  display: grid;
  gap: var(--lt-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
fieldset li {
  display: flex;
  justify-content: space-between;
  gap: var(--lt-space-3);
}
fieldset button {
  border: 0;
  background: none;
  color: var(--lt-red-text);
  font: inherit;
}
footer,
.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--lt-space-3);
}
.confirm-copy {
  margin: 0;
  color: var(--lt-label-2);
}
:is(input, textarea)::placeholder {
  color: var(--lt-label-2);
  opacity: 1;
}
</style>
