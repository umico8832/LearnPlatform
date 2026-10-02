<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useQuestionSubmissionEditor } from './useQuestionSubmissionEditor'
import { difficultyLabel, practiceQuestionTypes } from './practiceLibraryPresentation'

const open = defineModel<boolean>({ default: false })
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
const errorElement = ref<HTMLElement>()
watch(error, async (message) => {
  if (message) {
    await nextTick()
    errorElement.value?.focus({ preventScroll: false })
  }
})
</script>

<template>
  <el-dialog
    v-model="open"
    title="投稿新题目"
    width="720px"
    :close-on-click-modal="!submitting"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    class="submission-editor"
  >
    <p class="editor-intro">填写题目与参考答案，审核并入库后供大家练习。</p>
    <form class="submission-form" @submit.prevent="submit">
      <LpStatePanel
        v-if="coursesLoading || coursesError"
        :state="coursesLoading ? 'loading' : 'error'"
        title="课程暂时无法加载"
        description="重试后可继续填写，已输入内容会保留。"
        loading-label="正在加载可投稿课程"
        @retry="loadCourses"
      />
      <p v-else-if="coursesLoaded && !courses.length" class="form-note" role="status">
        当前没有可投稿的课程，请稍后再试。
      </p>
      <div class="form-columns">
        <div class="field">
          <label for="submission-course">所属课程 <span>必填</span></label
          ><el-select
            id="submission-course"
            v-model="draft.courseId"
            placeholder="选择课程"
            filterable
            :disabled="submitting || coursesLoading || coursesError || !courses.length"
            ><el-option v-for="course in courses" :key="course.id" :value="course.id" :label="course.name"
          /></el-select>
        </div>
        <div class="field">
          <label for="submission-type">题型 <span>必填</span></label
          ><el-select
            id="submission-type"
            v-model="draft.questionType"
            placeholder="选择题型"
            :disabled="submitting"
            @change="typeChanged"
            ><el-option v-for="type in practiceQuestionTypes" :key="type.value" :label="type.label" :value="type.value"
          /></el-select>
        </div>
      </div>
      <div class="field">
        <label for="submission-difficulty">难度 <span>必填</span></label
        ><el-radio-group
          id="submission-difficulty"
          v-model="draft.difficulty"
          aria-label="题目难度"
          :disabled="submitting"
          ><el-radio-button v-for="value in 5" :key="value" :value="value">{{
            difficultyLabel(value)
          }}</el-radio-button></el-radio-group
        >
      </div>
      <div class="field">
        <label for="submission-content">题干内容 <span>必填</span></label
        ><el-input
          id="submission-content"
          v-model="draft.content"
          type="textarea"
          :rows="4"
          :maxlength="10000"
          show-word-limit
          placeholder="写下完整的题目"
          :disabled="submitting"
        />
      </div>
      <fieldset v-if="showOptions" class="options-field" :disabled="submitting">
        <legend>选项与正确答案 <span>至少填写两个选项</span></legend>
        <div v-for="(option, index) in options" :key="option.key" class="option-row">
          <span class="option-label" aria-hidden="true">{{ String.fromCharCode(65 + index) }}</span>
          <el-input
            v-model="option.content"
            :aria-label="`选项 ${String.fromCharCode(65 + index)}`"
            :placeholder="`选项 ${String.fromCharCode(65 + index)} 的内容`"
            :disabled="submitting"
          />
          <el-checkbox
            :model-value="option.isCorrect"
            :aria-label="`标记 ${String.fromCharCode(65 + index)} 为正确答案`"
            :disabled="submitting"
            @change="setCorrect(index, $event === true)"
            >正确</el-checkbox
          >
          <el-button
            text
            :disabled="submitting || options.length <= 2"
            :aria-label="`移除选项 ${String.fromCharCode(65 + index)}`"
            @click="removeOption(index)"
            >移除</el-button
          >
        </div>
        <el-button v-if="options.length < 8" text :disabled="submitting" @click="addOption">添加选项</el-button>
      </fieldset>
      <div v-else-if="draft.questionType === 'TRUE_FALSE'" class="field">
        <label>正确答案 <span>必填</span></label
        ><el-radio-group v-model="draft.correctAnswer" aria-label="判断题正确答案" :disabled="submitting"
          ><el-radio-button value="TRUE">正确</el-radio-button
          ><el-radio-button value="FALSE">错误</el-radio-button></el-radio-group
        >
      </div>
      <div v-else-if="['FILL_BLANK', 'SHORT_ANSWER'].includes(draft.questionType)" class="field">
        <label for="submission-answer">参考答案 <span>必填</span></label
        ><el-input
          id="submission-answer"
          v-model="draft.correctAnswer"
          type="textarea"
          :rows="3"
          :maxlength="2000"
          placeholder="填写参考答案"
          :disabled="submitting"
        />
      </div>
      <div class="field">
        <label for="submission-analysis">解析 <span>选填</span></label
        ><el-input
          id="submission-analysis"
          v-model="draft.analysis"
          type="textarea"
          :rows="3"
          :maxlength="10000"
          placeholder="解释思路与关键步骤"
          :disabled="submitting"
        />
      </div>
      <div class="form-columns">
        <div class="field">
          <label for="submission-tags">标签 <span>选填</span></label
          ><el-input
            id="submission-tags"
            v-model="draft.tags"
            :maxlength="500"
            placeholder="用逗号分隔"
            :disabled="submitting"
          />
        </div>
        <div class="field">
          <label for="submission-source">来源 <span>选填</span></label
          ><el-input
            id="submission-source"
            v-model="draft.source"
            :maxlength="200"
            placeholder="例如：课本名称及章节"
            :disabled="submitting"
          />
        </div>
      </div>
      <p v-if="error" ref="errorElement" tabindex="-1" class="form-error" role="alert">{{ error }}</p>
    </form>
    <template #footer
      ><div class="editor-footer">
        <span>关闭后，未提交内容保留在本页。</span
        ><el-button :disabled="submitting" @click="open = false">暂时关闭</el-button
        ><el-button
          type="primary"
          :loading="submitting"
          :disabled="coursesLoading || coursesError || !courses.length"
          @click="submit"
          >提交投稿</el-button
        >
      </div></template
    >
  </el-dialog>
</template>

<style scoped>
:global(.submission-editor) {
  display: flex;
  flex-direction: column;
  margin-top: var(--lp-space-8);
  max-height: calc(100dvh - var(--lp-space-8) - var(--lp-space-8));
}
:global(.submission-editor .el-dialog__body) {
  overflow-y: auto;
  min-height: 0;
  scrollbar-gutter: stable;
}
.editor-intro {
  margin: 0 0 var(--lp-space-5);
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-body);
}
.submission-form {
  display: grid;
  gap: var(--lp-space-5);
}
.form-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lp-space-4);
}
.field {
  display: grid;
  gap: var(--lp-space-2);
}
.field label,
.options-field legend {
  color: var(--lp-text);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-medium);
}
.field label span,
.options-field legend span {
  margin-left: var(--lp-space-2);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-normal);
}
.options-field {
  margin: 0;
  padding: 0;
  border: 0;
  min-width: 0;
}
.options-field legend {
  margin-bottom: var(--lp-space-3);
}
.option-row {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  margin-bottom: var(--lp-space-3);
}
.option-label {
  width: 16px;
  flex-shrink: 0;
  color: var(--lp-text-secondary);
}
.option-row :deep(.el-checkbox) {
  margin-right: 0;
}
.form-note {
  margin: 0;
  color: var(--lp-text-secondary);
}
.form-error {
  margin: 0;
  padding: var(--lp-space-3);
  border-left: 3px solid var(--lp-danger);
  background: var(--lp-danger-soft);
  color: var(--lp-danger);
  line-height: var(--lp-leading-body);
}
.editor-footer {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
}
.editor-footer span {
  margin-right: auto;
  text-align: left;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
.editor-footer .el-button + .el-button {
  margin-left: 0;
}
@media (max-width: 767px) {
  .form-columns {
    grid-template-columns: 1fr;
  }
  .editor-footer {
    flex-wrap: wrap;
  }
  .editor-footer span {
    width: 100%;
  }
  .option-row {
    gap: var(--lp-space-2);
  }
}
</style>
