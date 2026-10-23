<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Plus, Trash2 } from '@lucide/vue'
import LtButton from '@/components/living/LtButton.vue'
import LtSheet from '@/components/living/LtSheet.vue'
import LtSegmented from '@/components/living/LtSegmented.vue'
import { useQuestionSubmissionEditor } from './useQuestionSubmissionEditor'
import { difficultyLabel, practiceQuestionTypes } from './practiceLibraryPresentation'

const open = defineModel<boolean>({ default: false })
const sheetOpen = computed({
  get: () => open.value,
  set: (value: boolean) => {
    if (value || !submitting.value) open.value = value
  },
})
const emit = defineEmits<{ submitted: [] }>()
const {
  draft,
  options,
  courses,
  coursesLoading,
  coursesError,
  coursesLoaded,
  submitting,
  error,
  showOptions,
  loadCourses,
  typeChanged,
  setCorrect,
  addOption,
  removeOption,
  submit,
} = useQuestionSubmissionEditor(open, () => emit('submitted'))
const difficulty = computed({
  get: () => String(draft.difficulty),
  set: (value: string) => {
    draft.difficulty = Number(value)
  },
})
const difficultyOptions = computed(() =>
  Array.from({ length: 5 }, (_, index) => ({
    value: String(index + 1),
    label: difficultyLabel(index + 1),
    disabled: submitting.value,
  })),
)
const errorElement = ref<HTMLElement>()
watch(error, async (message) => {
  if (message) {
    await nextTick()
    errorElement.value?.focus({ preventScroll: false })
  }
})
</script>

<template>
  <LtSheet v-model:open="sheetOpen" title="投稿新题目" :width="760">
    <p class="editor-intro">填写题目与参考答案，审核并入库后供大家练习。</p>
    <form class="submission-form" @submit.prevent="submit">
      <div class="editor-fields">
        <div v-if="coursesLoading" class="form-state" aria-busy="true">
          <span class="sr-only" role="status">正在加载可投稿课程</span>正在加载可投稿课程…
        </div>
        <div v-else-if="coursesError" class="form-state">
          <p>课程暂时无法加载，重试后可继续填写，已输入内容会保留。</p>
          <LtButton @click="loadCourses">重试读取</LtButton>
        </div>
        <p v-else-if="coursesLoaded && !courses.length" class="form-note" role="status">
          当前没有可投稿的课程，请稍后再试。
        </p>
        <div class="form-columns">
          <label
            ><span>所属课程 <em>必填</em></span
            ><select v-model="draft.courseId" :disabled="submitting || !courses.length">
              <option :value="undefined" disabled>选择课程</option>
              <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }}</option>
            </select></label
          ><label
            ><span>题型 <em>必填</em></span
            ><select v-model="draft.questionType" :disabled="submitting" @change="typeChanged">
              <option value="" disabled>选择题型</option>
              <option v-for="type in practiceQuestionTypes" :key="type.value" :value="type.value">
                {{ type.label }}
              </option>
            </select></label
          >
        </div>
        <fieldset :disabled="submitting">
          <legend>难度 <em>必填</em></legend>
          <LtSegmented v-model="difficulty" :options="difficultyOptions" label="难度" />
        </fieldset>
        <label
          ><span>题干内容 <em>必填</em></span
          ><textarea
            v-model="draft.content"
            rows="4"
            maxlength="10000"
            placeholder="写下完整的题目"
            :disabled="submitting"
          />
        </label>
        <fieldset v-if="showOptions" class="options-field" :disabled="submitting">
          <legend>选项与正确答案 <em>至少填写两个选项</em></legend>
          <div v-for="(option, index) in options" :key="option.key" class="option-row">
            <span class="option-label">{{ String.fromCharCode(65 + index) }}</span
            ><input
              v-model="option.content"
              :aria-label="`选项 ${String.fromCharCode(65 + index)}`"
              :placeholder="`选项 ${String.fromCharCode(65 + index)} 的内容`"
            /><label class="check"
              ><input
                :checked="option.isCorrect"
                :aria-label="`标记 ${String.fromCharCode(65 + index)} 为正确答案`"
                type="checkbox"
                @change="setCorrect(index, ($event.target as HTMLInputElement).checked)"
              />正确</label
            ><LtButton
              size="sm"
              :disabled="options.length <= 2"
              :aria-label="`移除选项 ${String.fromCharCode(65 + index)}`"
              @click="removeOption(index)"
              ><template #icon><Trash2 aria-hidden="true" /></template
            ></LtButton>
          </div>
          <LtButton v-if="options.length < 8" size="sm" @click="addOption"
            ><template #icon><Plus aria-hidden="true" /></template>添加选项</LtButton
          >
        </fieldset>
        <fieldset v-else-if="draft.questionType === 'TRUE_FALSE'" :disabled="submitting">
          <legend>正确答案 <em>必填</em></legend>
          <div class="choice-row">
            <label class="radio-choice"
              ><input v-model="draft.correctAnswer" name="submission-boolean" type="radio" value="TRUE" /><span
                >正确</span
              ></label
            ><label class="radio-choice"
              ><input v-model="draft.correctAnswer" name="submission-boolean" type="radio" value="FALSE" /><span
                >错误</span
              ></label
            >
          </div>
        </fieldset>
        <label v-else-if="['FILL_BLANK', 'SHORT_ANSWER'].includes(draft.questionType)"
          ><span>参考答案 <em>必填</em></span
          ><textarea
            v-model="draft.correctAnswer"
            rows="3"
            maxlength="2000"
            placeholder="填写参考答案"
            :disabled="submitting"
          />
        </label>
        <label
          ><span>解析 <em>选填</em></span
          ><textarea
            v-model="draft.analysis"
            rows="3"
            maxlength="10000"
            placeholder="解释思路与关键步骤"
            :disabled="submitting"
          />
        </label>
        <div class="form-columns">
          <label
            ><span>标签 <em>选填</em></span
            ><input v-model="draft.tags" maxlength="500" placeholder="用逗号分隔" :disabled="submitting" /></label
          ><label
            ><span>来源 <em>选填</em></span
            ><input v-model="draft.source" maxlength="200" placeholder="例如：课本名称及章节" :disabled="submitting"
          /></label>
        </div>
        <p v-if="error" ref="errorElement" tabindex="-1" class="form-error" role="alert">{{ error }}</p>
      </div>
      <footer class="editor-footer">
        <span>关闭后，未提交内容保留在本页。</span
        ><LtButton :disabled="submitting" @click="open = false">暂时关闭</LtButton
        ><LtButton
          type="submit"
          variant="primary"
          :disabled="submitting || coursesLoading || coursesError || !courses.length"
          >{{ submitting ? '提交中…' : '提交投稿' }}</LtButton
        >
      </footer>
    </form>
  </LtSheet>
</template>

<style scoped>
.editor-intro {
  margin: 0 0 var(--lt-space-5);
  color: var(--lt-label-2);
  line-height: var(--lt-leading-ui);
}
.submission-form {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: var(--lt-space-4);
  height: calc(82vh - var(--lt-space-10) * 2 - var(--lt-space-6));
}
.editor-fields {
  min-height: 0;
  overflow: auto;
  display: grid;
  gap: var(--lt-space-5);
  padding: var(--lt-space-1) var(--lt-space-2) var(--lt-space-5) var(--lt-space-1);
  scroll-padding-block: var(--lt-space-3);
}
label,
fieldset {
  display: grid;
  gap: var(--lt-space-2);
  min-width: 0;
  color: var(--lt-label);
  font-size: var(--lt-text-footnote);
  font-weight: 600;
}
em {
  font-style: normal;
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
  font-weight: 400;
}
fieldset {
  margin: 0;
  padding: 0;
  border: 0;
}
legend {
  padding: 0;
}
.form-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lt-space-4);
}
input,
select,
textarea {
  box-sizing: border-box;
  width: 100%;
  border: 1px solid var(--lt-separator);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font: 400 var(--lt-text-body)/var(--lt-leading-ui) var(--lt-font);
}
input,
select {
  min-height: var(--lt-control-md);
  padding: 0 var(--lt-space-3);
}
textarea {
  padding: var(--lt-space-3);
  resize: vertical;
}
input:focus-visible,
select:focus-visible,
textarea:focus-visible,
.radio-choice:has(input:focus-visible) {
  outline: 2px solid var(--lt-accent);
  outline-offset: 2px;
}
.submission-form :deep(.lt-segmented) {
  width: max-content;
  max-width: 100%;
}
.choice-row {
  display: flex;
  gap: var(--lt-space-2);
  flex-wrap: wrap;
}
.radio-choice,
.check {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-2);
  width: auto;
  padding: var(--lt-space-2) var(--lt-space-3);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  cursor: pointer;
}
.radio-choice {
  position: relative;
}
.radio-choice input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  opacity: 0;
  cursor: pointer;
}
.radio-choice:has(input:checked) {
  background: var(--lt-accent-soft);
  box-shadow: inset 0 0 0 1.5px var(--lt-accent);
}
.check input {
  width: auto;
  min-height: 0;
  accent-color: var(--lt-accent-fill);
}
.option-row {
  display: grid;
  grid-template-columns: calc(var(--lt-space-6) + var(--lt-space-2) / 4) minmax(0, 1fr) auto auto;
  align-items: center;
  gap: var(--lt-space-3);
  margin-bottom: var(--lt-space-3);
}
.option-label {
  display: grid;
  place-items: center;
  width: calc(var(--lt-space-6) + var(--lt-space-2) / 4);
  height: calc(var(--lt-space-6) + var(--lt-space-2) / 4);
  border-radius: var(--lt-radius-sm);
  background: var(--lt-fill-2);
  color: var(--lt-label-2);
}
.form-state,
.form-note {
  margin: 0;
  padding: var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-2);
  color: var(--lt-label-2);
}
.form-error {
  margin: 0;
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-md);
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
  line-height: var(--lt-leading-ui);
}
footer {
  display: flex;
  align-items: center;
  justify-content: end;
  gap: var(--lt-space-3);
}
.editor-footer {
  margin: 0;
  padding: var(--lt-space-4) 0 0;
  background: var(--lt-surface-raised);
  border-top: 1px solid var(--lt-separator);
}
footer span {
  margin-right: auto;
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}
@media (max-width: 700px) {
  .form-columns {
    grid-template-columns: 1fr;
  }
  .option-row {
    grid-template-columns: calc(var(--lt-space-6) + var(--lt-space-2) / 4) minmax(0, 1fr);
  }
  .check {
    grid-column: 2;
  }
  .option-row .lt-button {
    grid-column: 2;
    width: max-content;
  }
  footer {
    flex-wrap: wrap;
  }
  footer span {
    width: 100%;
  }
}
:is(input, textarea)::placeholder {
  color: var(--lt-label-2);
  opacity: 1;
}
</style>
