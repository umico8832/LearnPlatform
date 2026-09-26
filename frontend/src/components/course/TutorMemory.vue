<template>
  <details class="tutor-memory" data-testid="tutor-memory" @toggle="onToggle">
    <summary>本课程的学习记忆 <span>目标与讲解偏好</span></summary>
    <div class="memory-editor">
      <p>仅记住你主动保存的设置，供本课程后续提问使用。目标和偏好不代表学习完成。</p>
      <p v-if="loading" role="status">正在读取记忆…</p>
      <el-alert v-if="failure" :title="failure" type="error" :closable="false" show-icon />
      <template v-if="loaded">
        <label :for="`memory-style-${courseId}`">希望怎样讲解</label>
        <select :id="`memory-style-${courseId}`" v-model="style" data-testid="memory-style" :disabled="locked">
          <option value="">不指定偏好</option>
          <option value="STEP_BY_STEP">分步讲清楚</option>
          <option value="CONCISE">先简洁说明</option>
          <option value="EXAMPLES">多用例子解释</option>
        </select>
        <label :for="`memory-goal-${courseId}`">当前学习目标</label>
        <el-input
          :id="`memory-goal-${courseId}`"
          v-model="goal"
          data-testid="memory-goal"
          type="textarea"
          :rows="2"
          :maxlength="500"
          show-word-limit
          :disabled="locked"
          placeholder="例如：理解栈与队列的区别"
        />
        <p class="memory-note">删除会清除这里的设置；已有对话和真实学习记录仍会保留。</p>
        <div class="memory-actions">
          <el-button data-testid="memory-save" type="primary" :disabled="!canSave" :loading="saving" @click="save">
            保存记忆
          </el-button>
          <el-button data-testid="memory-delete" :disabled="locked || !hasSaved" @click="remove"> 删除记忆 </el-button>
        </div>
        <p v-if="notice" role="status">{{ notice }}</p>
      </template>
      <el-button
        v-if="syncRequired || (!loaded && !loading)"
        data-testid="memory-reload"
        :disabled="loading || saving"
        @click="load"
        >重新读取已保存内容</el-button
      >
    </div>
  </details>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  deleteTutorMemory,
  getTutorMemory,
  saveTutorMemory,
  type TutorExplanationStyle,
  type TutorMemoryVO,
} from '@/api/tutorMemory'
import { errorMessage } from '@/utils/errors'

const props = withDefaults(defineProps<{ courseId: number; busy?: boolean }>(), { busy: false })
const style = ref<TutorExplanationStyle | ''>('')
const goal = ref('')
const saved = ref<TutorMemoryVO>({ revision: 0, explanationStyle: null, goal: null })
const loaded = ref(false)
const opened = ref(false)
const loading = ref(false)
const saving = ref(false)
const syncRequired = ref(false)
const failure = ref('')
const notice = ref('')
const locked = computed(() => props.busy || loading.value || saving.value || syncRequired.value || !loaded.value)
const hasSaved = computed(() => saved.value.explanationStyle !== null || saved.value.goal !== null)
const canSave = computed(
  () =>
    !locked.value &&
    !!(style.value || goal.value.trim()) &&
    goal.value.length <= 500 &&
    (style.value !== (saved.value.explanationStyle ?? '') || goal.value.trim() !== (saved.value.goal ?? '')),
)
let generation = 0

function apply(memory: TutorMemoryVO) {
  saved.value = memory
  style.value = memory.explanationStyle ?? ''
  goal.value = memory.goal ?? ''
  loaded.value = true
  syncRequired.value = false
}

async function load() {
  if (loading.value || saving.value) return
  const current = generation
  loading.value = true
  failure.value = notice.value = ''
  try {
    const response = await getTutorMemory(props.courseId)
    if (current === generation) apply(response.data)
  } catch (error) {
    if (current === generation) {
      syncRequired.value = true
      failure.value = errorMessage(error, '暂时无法读取记忆，请重试')
    }
  } finally {
    if (current === generation) loading.value = false
  }
}

async function save() {
  if (!canSave.value) return
  await change(false)
}

async function remove() {
  if (locked.value || !hasSaved.value) return
  await change(true)
}

async function change(removing: boolean) {
  const current = generation
  const courseId = props.courseId
  const revision = saved.value.revision
  saving.value = true
  failure.value = notice.value = ''
  try {
    const response = removing
      ? await deleteTutorMemory(courseId, revision)
      : await saveTutorMemory(courseId, {
          revision,
          explanationStyle: style.value || null,
          goal: goal.value.trim() || null,
        })
    if (current !== generation) return
    apply(response.data)
    notice.value = removing ? '记忆已删除，后续新提问不再使用这些设置。' : '记忆已保存，将用于后续新提问。'
  } catch (error) {
    if (current !== generation) return
    syncRequired.value = true
    failure.value = `${errorMessage(error, '操作结果暂时无法确认')}。请重新读取已保存内容，再继续编辑。`
  } finally {
    if (current === generation) saving.value = false
  }
}

function onToggle(event: Event) {
  opened.value = (event.target as HTMLDetailsElement).open
  if (opened.value && !loaded.value && !syncRequired.value) void load()
}

watch(
  () => props.courseId,
  () => {
    generation++
    saved.value = { revision: 0, explanationStyle: null, goal: null }
    style.value = ''
    goal.value = failure.value = notice.value = ''
    loading.value = saving.value = loaded.value = syncRequired.value = false
    if (opened.value) void load()
  },
)
onBeforeUnmount(() => generation++)
</script>

<style scoped>
.tutor-memory {
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
  font-weight: normal;
  font-size: var(--lp-text-sm);
}
.memory-editor {
  display: grid;
  gap: var(--lp-space-3);
  padding-block: var(--lp-space-2) var(--lp-space-4);
}
.memory-editor p {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: 1.6;
}
.memory-editor label {
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
}
select {
  width: 100%;
  color: var(--lp-text-primary);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-sm);
  padding: var(--lp-space-2);
  font: inherit;
}
select:focus-visible,
summary:focus-visible {
  outline: var(--lp-space-1) solid var(--lp-primary);
  outline-offset: var(--lp-space-1);
}
select:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.memory-actions {
  display: flex;
  gap: var(--lp-space-2);
}
.memory-actions .el-button + .el-button {
  margin-left: 0;
}
</style>
