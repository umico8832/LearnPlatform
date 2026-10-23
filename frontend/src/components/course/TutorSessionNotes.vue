<template>
  <details class="tutor-session-notes" data-testid="tutor-session-notes" @toggle="onToggle">
    <summary>本次会话复盘 <span>只整理理解检查</span></summary>
    <div class="notes-body">
      <p>写下本次会话的收获或疑问，后续提问会参考最近五条可用复盘。理解检查结果另行显示，小结不代表已经掌握。</p>
      <p v-if="loadingCurrent" role="status">正在读取本次会话…</p>
      <p v-if="currentFailure" class="inline-error" role="alert">{{ currentFailure }}</p>
      <template v-if="current">
        <section class="note-editor" aria-label="会话复盘编辑器">
          <div class="note-source">
            <strong>{{ current.source.available ? current.source.title : '原课节已不可用' }}</strong>
            <template v-if="current.source.available">
              <span>会话开始：{{ formatDateTime(current.source.sessionStartedAt) }}</span>
              <span>理解检查：{{ checkLabel(current.source.checkStatus) }}</span>
            </template>
          </div>
          <p v-if="!current.source.available" class="source-unavailable">
            原课节已不可用，仍可查看或删除已保存复盘，不能新增或修改。
          </p>
          <label :for="`session-note-${courseId}-${current.sessionKey}`">我的复盘</label>
          <textarea
            :id="`session-note-${courseId}-${current.sessionKey}`"
            v-model="draft"
            data-testid="session-note-input"
            :maxlength="500"
            :disabled="locked || !current.source.available"
            placeholder="例如：我能说明入栈和出栈如何改变栈顶。"
          />
          <div class="note-actions">
            <LtButton data-testid="session-note-save" variant="primary" :disabled="!canSave" @click="save">
              保存复盘
            </LtButton>
            <LtButton data-testid="session-note-delete" :disabled="locked || !hasSaved" @click="remove"
              >删除复盘</LtButton
            >
          </div>
          <p v-if="notice" role="status">{{ notice }}</p>
          <LtButton
            v-if="selectedSessionKey !== sessionKey"
            variant="quiet"
            :disabled="saving"
            @click="returnToCurrentSession"
          >
            返回本次会话
          </LtButton>
        </section>
      </template>
      <LtButton
        v-if="syncRequired || (!current && !loadingCurrent)"
        data-testid="session-note-reload"
        :disabled="loadingCurrent || saving"
        @click="loadCurrent"
      >
        重新读取本次会话
      </LtButton>

      <section class="saved-notes" aria-label="本课程已保存的会话复盘">
        <div class="saved-notes-heading">
          <h3>本课程已保存的复盘</h3>
          <LtButton variant="quiet" :disabled="loadingList" @click="loadList(page)">刷新列表</LtButton>
        </div>
        <p v-if="loadingList" role="status">正在读取已保存复盘…</p>
        <p v-else-if="listFailure" class="inline-error" role="alert">{{ listFailure }}</p>
        <p v-else-if="!records.length" class="saved-empty">还没有保存的会话复盘。</p>
        <div v-else class="saved-note-list">
          <article v-for="item in records" :key="item.sessionKey" class="saved-note">
            <strong>{{ item.source.available ? item.source.title : '原课节已不可用' }}</strong>
            <p>{{ item.note }}</p>
            <template v-if="item.source.available">
              <span>会话开始：{{ formatDateTime(item.source.sessionStartedAt) }}</span>
              <span>保存于：{{ formatDateTime(item.updatedAt) }}</span>
              <span>理解检查：{{ checkLabel(item.source.checkStatus) }}</span>
            </template>
            <LtButton @click="select(item)">{{ item.source.available ? '编辑此复盘' : '查看或删除' }}</LtButton>
          </article>
        </div>
        <nav v-if="total > pageSize" class="notes-pagination" aria-label="复盘分页">
          <LtButton :disabled="loadingList || page <= 1" @click="loadList(page - 1)">上一页</LtButton
          ><span>第 {{ page }} 页</span
          ><LtButton :disabled="loadingList || page * pageSize >= total" @click="loadList(page + 1)">下一页</LtButton>
        </nav>
      </section>
    </div>
  </details>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { LtButton } from '@/components/living'
import {
  deleteTutorSessionNote,
  getTutorSessionNote,
  getTutorSessionNotes,
  saveTutorSessionNote,
  type TutorCheckStatus,
  type TutorSessionNoteVO,
} from '@/api/tutorNotes'
import { errorMessage } from '@/utils/errors'
import { onAuthSessionChange } from '@/utils/auth'
import { formatDateTime } from '@/utils/format'

const props = withDefaults(
  defineProps<{ courseId: number; sessionKey: string; checkResult?: { correct: boolean } | null; busy?: boolean }>(),
  { busy: false, checkResult: null },
)
const current = ref<TutorSessionNoteVO>()
const selectedSessionKey = ref(props.sessionKey)
const draft = ref('')
const records = ref<TutorSessionNoteVO[]>([])
const page = ref(1)
const pageSize = ref(5)
const total = ref(0)
const opened = ref(false)
const loadingCurrent = ref(false)
const loadingList = ref(false)
const saving = ref(false)
const syncRequired = ref(false)
const currentFailure = ref('')
const listFailure = ref('')
const notice = ref('')
const locked = computed(
  () => props.busy || loadingCurrent.value || saving.value || syncRequired.value || !current.value,
)
const hasSaved = computed(() => current.value?.note !== null)
const canSave = computed(
  () =>
    !locked.value &&
    !!current.value?.source.available &&
    !!draft.value.trim() &&
    draft.value.length <= 500 &&
    draft.value.trim() !== (current.value?.note ?? ''),
)
let generation = 0
let editorRequest = 0
let sourceRequest = 0
let listRequest = 0

function applyCurrent(value: TutorSessionNoteVO) {
  current.value = value
  draft.value = value.note ?? ''
  syncRequired.value = false
}

function checkLabel(status: TutorCheckStatus) {
  if (status === 'CORRECT') return '已答对'
  if (status === 'INCORRECT') return '已作答，待复习'
  if (status === 'UNANSWERED') return '尚未作答'
  return '本次无理解检查'
}

async function loadCurrent() {
  if (saving.value) return
  const currentGeneration = generation
  const { courseId } = props
  const sessionKey = selectedSessionKey.value
  const currentEditorRequest = ++editorRequest
  sourceRequest++
  loadingCurrent.value = true
  currentFailure.value = notice.value = ''
  try {
    const response = await getTutorSessionNote(courseId, sessionKey, { errorDisplay: 'inline' })
    if (
      currentGeneration === generation &&
      currentEditorRequest === editorRequest &&
      sessionKey === selectedSessionKey.value &&
      courseId === props.courseId
    ) {
      applyCurrent(response.data)
    }
  } catch (error) {
    if (
      currentGeneration === generation &&
      currentEditorRequest === editorRequest &&
      sessionKey === selectedSessionKey.value &&
      courseId === props.courseId
    ) {
      syncRequired.value = true
      currentFailure.value = errorMessage(error, '暂时无法读取本次会话，请重试')
    }
  } finally {
    if (currentGeneration === generation && currentEditorRequest === editorRequest) loadingCurrent.value = false
  }
}

async function loadList(targetPage = page.value, force = false) {
  if (loadingList.value && !force) return
  const currentGeneration = generation
  const courseId = props.courseId
  const currentListRequest = ++listRequest
  loadingList.value = true
  listFailure.value = ''
  try {
    const response = await getTutorSessionNotes(courseId, targetPage, { errorDisplay: 'inline' })
    if (currentGeneration !== generation || currentListRequest !== listRequest || courseId !== props.courseId) return
    const responsePageSize = response.data.size || 5
    const lastPage = Math.max(1, Math.ceil(response.data.total / responsePageSize))
    if (!response.data.records.length && response.data.total > 0 && response.data.current > lastPage) {
      await loadList(lastPage, true)
      return
    }
    records.value = response.data.records
    total.value = response.data.total
    page.value = response.data.total === 0 ? 1 : response.data.current
    pageSize.value = responsePageSize
  } catch (error) {
    if (currentGeneration === generation && currentListRequest === listRequest && courseId === props.courseId) {
      listFailure.value = errorMessage(error, '暂时无法读取已保存复盘，请重试')
    }
  } finally {
    if (currentGeneration === generation && currentListRequest === listRequest) loadingList.value = false
  }
}

async function save() {
  if (!canSave.value || !current.value) return
  await change(false)
}

async function remove() {
  if (locked.value || !hasSaved.value || !current.value) return
  await change(true)
}

async function change(removing: boolean) {
  const currentGeneration = generation
  const { courseId } = props
  const target = current.value!
  saving.value = true
  currentFailure.value = notice.value = ''
  try {
    const response = removing
      ? await deleteTutorSessionNote(courseId, target.sessionKey, target.revision, { errorDisplay: 'inline' })
      : await saveTutorSessionNote(
          courseId,
          target.sessionKey,
          { revision: target.revision, note: draft.value.trim() },
          { errorDisplay: 'inline' },
        )
    if (currentGeneration !== generation) return
    applyCurrent(response.data)
    notice.value = removing ? '复盘已删除；已有会话历史和理解检查记录仍会保留。' : '复盘已保存。'
    void loadList(page.value, true)
  } catch (error) {
    if (currentGeneration !== generation) return
    syncRequired.value = true
    currentFailure.value = `${errorMessage(error, '操作结果暂时无法确认')}。请重新读取本次会话，再继续编辑。`
  } finally {
    if (currentGeneration === generation) saving.value = false
  }
}

function select(item: TutorSessionNoteVO) {
  if (saving.value) return
  selectedSessionKey.value = item.sessionKey
  editorRequest++
  sourceRequest++
  loadingCurrent.value = false
  current.value = undefined
  draft.value = ''
  syncRequired.value = false
  currentFailure.value = notice.value = ''
  void loadCurrent()
}

function returnToCurrentSession() {
  if (saving.value || selectedSessionKey.value === props.sessionKey) return
  selectedSessionKey.value = props.sessionKey
  editorRequest++
  sourceRequest++
  loadingCurrent.value = false
  current.value = undefined
  draft.value = ''
  syncRequired.value = false
  currentFailure.value = notice.value = ''
  void loadCurrent()
}

async function refreshCurrentSource() {
  if (!opened.value || !current.value || selectedSessionKey.value !== props.sessionKey) return
  const currentGeneration = generation
  const sessionKey = selectedSessionKey.value
  const currentEditorRequest = editorRequest
  const currentSourceRequest = ++sourceRequest
  try {
    const response = await getTutorSessionNote(props.courseId, sessionKey, { errorDisplay: 'inline' })
    if (
      currentGeneration !== generation ||
      currentEditorRequest !== editorRequest ||
      currentSourceRequest !== sourceRequest ||
      selectedSessionKey.value !== sessionKey ||
      props.sessionKey !== sessionKey ||
      current.value?.sessionKey !== sessionKey
    ) {
      return
    }
    current.value = { ...current.value, source: response.data.source }
    records.value = records.value.map((item) =>
      item.sessionKey === sessionKey ? { ...item, source: response.data.source } : item,
    )
  } catch (error) {
    if (
      currentGeneration !== generation ||
      currentEditorRequest !== editorRequest ||
      currentSourceRequest !== sourceRequest ||
      selectedSessionKey.value !== sessionKey ||
      props.sessionKey !== sessionKey
    ) {
      return
    }
    syncRequired.value = true
    currentFailure.value = `${errorMessage(error, '暂时无法刷新理解检查结果')}。请重新读取本次会话，再继续编辑。`
  }
}

function onToggle(event: Event) {
  opened.value = (event.target as HTMLDetailsElement).open
  if (opened.value && !current.value && !syncRequired.value) void loadCurrent()
  if (opened.value && !records.value.length && !loadingList.value) void loadList(1)
}

const unsubscribeAuth = onAuthSessionChange(() => {
  generation++
  editorRequest++
  sourceRequest++
  listRequest++
  current.value = undefined
  selectedSessionKey.value = props.sessionKey
  draft.value = ''
  records.value = []
  page.value = 1
  pageSize.value = 5
  total.value = 0
  loadingCurrent.value = loadingList.value = saving.value = syncRequired.value = false
  currentFailure.value = listFailure.value = notice.value = ''
})

watch(
  () => [props.courseId, props.sessionKey] as const,
  () => {
    generation++
    editorRequest++
    sourceRequest++
    listRequest++
    current.value = undefined
    selectedSessionKey.value = props.sessionKey
    draft.value = ''
    records.value = []
    page.value = 1
    pageSize.value = 5
    total.value = 0
    loadingCurrent.value = loadingList.value = saving.value = syncRequired.value = false
    currentFailure.value = listFailure.value = notice.value = ''
    if (opened.value) {
      void loadCurrent()
      void loadList(1)
    }
  },
)

watch(
  () => props.checkResult,
  () => void refreshCurrentSource(),
)
onBeforeUnmount(() => {
  generation++
  editorRequest++
  sourceRequest++
  listRequest++
  unsubscribeAuth()
})
</script>

<style scoped>
.tutor-session-notes {
  margin-block: var(--lt-space-4);
  border-block: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
}
summary {
  cursor: pointer;
  padding-block: var(--lt-space-3);
  font-weight: 600;
}
summary span {
  margin-left: var(--lt-space-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
  font-weight: 400;
}
summary:focus-visible {
  outline: var(--lt-space-1) solid var(--lt-accent);
  outline-offset: var(--lt-space-1);
}
.notes-body,
.note-editor,
.saved-notes,
.saved-note-list {
  display: grid;
  gap: var(--lt-space-3);
}
.notes-body {
  padding-block: var(--lt-space-2) var(--lt-space-4);
}
.notes-body > p,
.note-editor > p,
.saved-note p,
.saved-empty,
.note-source span {
  margin: 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
  line-height: var(--lt-leading-reading);
}
.note-editor,
.saved-notes {
  padding-top: var(--lt-space-3);
  border-top: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
}
.note-source {
  display: grid;
  gap: var(--lt-space-1);
}
.note-editor label {
  font-size: var(--lt-text-footnote);
  font-weight: 600;
}
.source-unavailable {
  color: var(--lt-red-text);
}
.note-actions,
.saved-notes-heading {
  display: flex;
  gap: var(--lt-space-2);
  align-items: center;
}
.note-actions .lt-button + .lt-button {
  margin-left: 0;
}
.saved-notes-heading {
  justify-content: space-between;
}
.saved-notes h3 {
  margin: 0;
  color: var(--lt-label);
  font-size: var(--lt-text-body);
}
.saved-note {
  display: grid;
  gap: var(--lt-space-2);
  padding: var(--lt-space-3);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
}
.saved-note .lt-button {
  justify-self: start;
  margin: 0;
}
.notes-pagination {
  justify-self: center;
}
</style>

<style scoped>
.inline-error {
  margin: 0;
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-sm);
  background: var(--lt-red-soft);
  color: var(--lt-red-text);
}
textarea {
  width: 100%;
  min-height: calc(var(--lt-control-lg) * 2);
  padding: var(--lt-space-2);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-sm);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font: inherit;
  resize: vertical;
}
</style>
