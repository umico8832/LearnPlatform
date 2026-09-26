<template>
  <details class="tutor-session-notes" data-testid="tutor-session-notes" @toggle="onToggle">
    <summary>本次会话复盘 <span>只整理理解检查</span></summary>
    <div class="notes-body">
      <p>写下本次会话的收获或疑问，后续提问会参考最近五条可用复盘。理解检查结果另行显示，小结不代表已经掌握。</p>
      <p v-if="loadingCurrent" role="status">正在读取本次会话…</p>
      <el-alert v-if="currentFailure" :title="currentFailure" type="error" :closable="false" show-icon />
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
          <el-input
            :id="`session-note-${courseId}-${current.sessionKey}`"
            v-model="draft"
            data-testid="session-note-input"
            type="textarea"
            :rows="3"
            :maxlength="500"
            show-word-limit
            :disabled="locked || !current.source.available"
            placeholder="例如：我能说明入栈和出栈如何改变栈顶。"
          />
          <div class="note-actions">
            <el-button
              data-testid="session-note-save"
              type="primary"
              :disabled="!canSave"
              :loading="saving"
              @click="save"
            >
              保存复盘
            </el-button>
            <el-button data-testid="session-note-delete" :disabled="locked || !hasSaved" @click="remove"
              >删除复盘</el-button
            >
          </div>
          <p v-if="notice" role="status">{{ notice }}</p>
          <el-button
            v-if="selectedSessionKey !== sessionKey"
            text
            type="primary"
            :disabled="saving"
            @click="returnToCurrentSession"
          >
            返回本次会话
          </el-button>
        </section>
      </template>
      <el-button
        v-if="syncRequired || (!current && !loadingCurrent)"
        data-testid="session-note-reload"
        :disabled="loadingCurrent || saving"
        @click="loadCurrent"
      >
        重新读取本次会话
      </el-button>

      <section class="saved-notes" aria-label="本课程已保存的会话复盘">
        <div class="saved-notes-heading">
          <h3>本课程已保存的复盘</h3>
          <el-button text :disabled="loadingList" @click="loadList(page)">刷新列表</el-button>
        </div>
        <p v-if="loadingList" role="status">正在读取已保存复盘…</p>
        <el-alert v-else-if="listFailure" :title="listFailure" type="error" :closable="false" show-icon />
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
            <el-button text type="primary" @click="select(item)">{{
              item.source.available ? '编辑此复盘' : '查看或删除'
            }}</el-button>
          </article>
        </div>
        <el-pagination
          v-if="total > pageSize"
          class="notes-pagination"
          layout="prev, pager, next"
          :current-page="page"
          :page-size="pageSize"
          :total="total"
          @current-change="loadList"
        />
      </section>
    </div>
  </details>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  deleteTutorSessionNote,
  getTutorSessionNote,
  getTutorSessionNotes,
  saveTutorSessionNote,
  type TutorCheckStatus,
  type TutorSessionNoteVO,
} from '@/api/tutorNotes'
import { errorMessage } from '@/utils/errors'
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
    const response = await getTutorSessionNote(courseId, sessionKey)
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
    const response = await getTutorSessionNotes(courseId, targetPage)
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
      ? await deleteTutorSessionNote(courseId, target.sessionKey, target.revision)
      : await saveTutorSessionNote(courseId, target.sessionKey, { revision: target.revision, note: draft.value.trim() })
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
    const response = await getTutorSessionNote(props.courseId, sessionKey)
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
})
</script>

<style scoped>
.tutor-session-notes {
  margin-block: var(--lp-space-4);
  border-block: var(--lp-border-hairline);
}
summary {
  cursor: pointer;
  padding-block: var(--lp-space-3);
  font-weight: var(--lp-weight-semibold);
}
summary span {
  margin-left: var(--lp-space-2);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  font-weight: normal;
}
summary:focus-visible {
  outline: var(--lp-space-1) solid var(--lp-primary);
  outline-offset: var(--lp-space-1);
}
.notes-body,
.note-editor,
.saved-notes,
.saved-note-list {
  display: grid;
  gap: var(--lp-space-3);
}
.notes-body {
  padding-block: var(--lp-space-2) var(--lp-space-4);
}
.notes-body > p,
.note-editor > p,
.saved-note p,
.saved-empty,
.note-source span {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: 1.6;
}
.note-editor,
.saved-notes {
  padding-top: var(--lp-space-3);
  border-top: var(--lp-border-hairline);
}
.note-source {
  display: grid;
  gap: var(--lp-space-1);
}
.note-editor label {
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
}
.source-unavailable {
  color: var(--lp-danger);
}
.note-actions,
.saved-notes-heading {
  display: flex;
  gap: var(--lp-space-2);
  align-items: center;
}
.note-actions .el-button + .el-button {
  margin-left: 0;
}
.saved-notes-heading {
  justify-content: space-between;
}
.saved-notes h3 {
  margin: 0;
  color: var(--lp-text);
  font-size: var(--lp-text-base);
}
.saved-note {
  display: grid;
  gap: var(--lp-space-2);
  padding: var(--lp-space-3);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface-soft);
}
.saved-note .el-button {
  justify-self: start;
  margin: 0;
}
.notes-pagination {
  justify-self: center;
}
</style>
