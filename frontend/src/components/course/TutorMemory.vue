<template>
  <details class="tutor-memory" data-testid="tutor-memory" @toggle="onToggle">
    <summary>本课程的学习记忆 <span>目标与讲解偏好</span></summary>
    <div class="memory-editor">
      <p>仅记住你主动保存的设置，供本课程后续提问使用。目标和偏好不代表学习完成。</p>
      <p v-if="loading" role="status">正在读取记忆…</p>
      <p v-if="failure" class="inline-error" role="alert">{{ failure }}</p>
      <template v-if="loaded">
        <label :for="`memory-style-${courseId}`">希望怎样讲解</label>
        <select :id="`memory-style-${courseId}`" v-model="style" data-testid="memory-style" :disabled="locked">
          <option value="">不指定偏好</option>
          <option value="STEP_BY_STEP">分步讲清楚</option>
          <option value="CONCISE">先简洁说明</option>
          <option value="EXAMPLES">多用例子解释</option>
        </select>
        <label :for="`memory-goal-${courseId}`">当前学习目标</label>
        <textarea
          :id="`memory-goal-${courseId}`"
          v-model="goal"
          data-testid="memory-goal"
          :maxlength="500"
          :disabled="locked"
          placeholder="例如：理解栈与队列的区别"
        />
        <p class="memory-note">删除会清除这里的设置；已有对话和真实学习记录仍会保留。</p>
        <div class="memory-actions">
          <LtButton data-testid="memory-save" variant="primary" :disabled="!canSave" @click="save"> 保存记忆 </LtButton>
          <LtButton data-testid="memory-delete" :disabled="locked || !hasSaved" @click="remove"> 删除记忆 </LtButton>
        </div>
        <p v-if="notice" role="status">{{ notice }}</p>
      </template>
      <LtButton
        v-if="syncRequired || (!loaded && !loading)"
        data-testid="memory-reload"
        :disabled="loading || saving"
        @click="load"
        >重新读取已保存内容</LtButton
      >
    </div>
  </details>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { LtButton } from '@/components/living'
import {
  deleteTutorMemory,
  getTutorMemory,
  saveTutorMemory,
  type TutorExplanationStyle,
  type TutorMemoryVO,
} from '@/api/tutorMemory'
import { errorMessage } from '@/utils/errors'
import { onAuthSessionChange } from '@/utils/auth'

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
let alive = true

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
    const response = await getTutorMemory(props.courseId, { errorDisplay: 'inline' })
    if (alive && current === generation) apply(response.data)
  } catch (error) {
    if (alive && current === generation) {
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
      ? await deleteTutorMemory(courseId, revision, { errorDisplay: 'inline' })
      : await saveTutorMemory(
          courseId,
          {
            revision,
            explanationStyle: style.value || null,
            goal: goal.value.trim() || null,
          },
          { errorDisplay: 'inline' },
        )
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

const unsubscribeAuth = onAuthSessionChange(() => {
  generation++
  saved.value = { revision: 0, explanationStyle: null, goal: null }
  style.value = goal.value = failure.value = notice.value = ''
  loading.value = saving.value = loaded.value = syncRequired.value = false
})

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
onBeforeUnmount(() => {
  alive = false
  generation++
  unsubscribeAuth()
})
</script>

<style scoped>
.tutor-memory {
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
  font-weight: 400;
  font-size: var(--lt-text-footnote);
}
.memory-editor {
  display: grid;
  gap: var(--lt-space-3);
  padding-block: var(--lt-space-2) var(--lt-space-4);
}
.memory-editor p {
  margin: 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
  line-height: var(--lt-leading-reading);
}
.memory-editor label {
  font-size: var(--lt-text-footnote);
  font-weight: 600;
}
select {
  width: 100%;
  min-height: var(--lt-control-lg);
  color: var(--lt-label);
  background: var(--lt-surface-raised);
  border: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
  border-radius: var(--lt-radius-pill);
  padding: var(--lt-space-2);
  font: inherit;
}
select:focus-visible,
summary:focus-visible {
  outline: var(--lt-space-1) solid var(--lt-accent);
  outline-offset: var(--lt-space-1);
}
select:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.memory-actions {
  display: flex;
  gap: var(--lt-space-2);
}
.memory-actions .lt-button + .lt-button {
  margin-left: 0;
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
