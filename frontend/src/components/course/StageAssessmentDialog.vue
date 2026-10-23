<script setup lang="ts">
import { computed, nextTick, watch } from 'vue'
import './course-choice.css'
import { LtButton, LtSheet } from '@/components/living'
import type { CourseStageAssessmentVO } from '@/api/course'

const props = defineProps<{
  visible: boolean
  assessment: CourseStageAssessmentVO | null
  submitting: boolean
  submitError: string
  focusQuestionId: number | null
  reviewedKnowledgePointIds: number[]
}>()
const answers = defineModel<Record<number, string[]>>('answers', { required: true })
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'submit'): void
  (e: 'review-wrong', questionId: number): void
  (e: 'review-wrong-by-kp', point: { id: number; name: string }): void
  (e: 'open-kp-tutor', knowledgePointId: number): void
}>()
const sheetOpen = computed({
  get: () => props.visible,
  set: (value: boolean) => {
    if (value || !props.submitting) emit('update:visible', value)
  },
})
const strategyLabel = computed(() =>
  props.assessment?.selectionStrategy === 'LEARNING_STATE_PRIORITY'
    ? '根据当前错题、到期复习和近期作答情况安排题目'
    : '学习记录不足时，按课程题序安排题目',
)
const isReviewed = (knowledgePointId: number) => props.reviewedKnowledgePointIds.includes(knowledgePointId)
const questionElements = new Map<number, HTMLElement>()
watch(
  () => props.focusQuestionId,
  async (questionId) => {
    if (!questionId) return
    await nextTick()
    const element = questionElements.get(questionId)
    element?.scrollIntoView({ block: 'center', behavior: 'auto' })
    element?.focus({ preventScroll: true })
  },
)
function optionState(question: CourseStageAssessmentVO['questions'][number], label: string) {
  if (props.assessment?.status !== 'COMPLETED') {
    return answers.value[question.id]?.includes(label) ? 'selected' : 'idle'
  }
  if (question.correctAnswer?.split(',').includes(label)) return 'right'
  if (question.userAnswer?.split(',').includes(label)) return 'wrong'
  return 'dim'
}
function sourceCompositionText(composition: CourseStageAssessmentVO['sourceComposition']) {
  if (!composition) return '暂无题源快照'
  return [
    ['官方原题', composition.officialExamCount],
    ['平台人工题', composition.manualCount],
    ['用户私有题', composition.userPrivateCount],
    ['AI 生成题', composition.aiGeneratedCount],
  ]
    .filter(([, count]) => Number(count) > 0)
    .map(([label, count]) => `${label} ${count}`)
    .join(' · ')
}
</script>
<template>
  <LtSheet v-model:open="sheetOpen" title="课程阶段测评">
    <div v-if="assessment" class="stage-assessment" :aria-busy="submitting">
      <p
        class="assessment-notice"
        :data-priority="assessment.selectionStrategy === 'LEARNING_STATE_PRIORITY' ? 'info' : 'warning'"
      >
        {{ strategyLabel }}
      </p>
      <p class="assessment-source-composition">
        范围：{{ assessment.targetKnowledgePointName || '课程整体' }} · 题源构成：{{
          sourceCompositionText(assessment.sourceComposition)
        }}
      </p>
      <p v-if="submitError" class="assessment-submit-error" role="alert">{{ submitError }}</p>
      <p v-if="assessment.status === 'COMPLETED'" class="assessment-summary">
        答对 {{ assessment.correctCount }} / {{ assessment.questionCount }} 题
      </p>
      <section
        v-if="assessment.status === 'COMPLETED' && assessment.knowledgePointSummary?.length"
        class="assessment-kp-summary"
        aria-labelledby="assessment-kp-summary-heading"
      >
        <h3 id="assessment-kp-summary-heading">按知识点统计</h3>
        <ul>
          <li v-for="point in assessment.knowledgePointSummary" :key="point.id">
            <span>知识点：{{ point.name }}</span
            ><span class="assessment-kp-summary-count"
              >答对 {{ point.correctCount }} / {{ point.questionCount }} 题</span
            ><LtButton
              v-if="point.correctCount < point.questionCount"
              size="sm"
              variant="plain"
              @click="emit('review-wrong-by-kp', { id: point.id, name: point.name })"
              >复习该知识点错题</LtButton
            ><LtButton v-if="isReviewed(point.id)" size="sm" variant="plain" @click="emit('open-kp-tutor', point.id)"
              >进入教学</LtButton
            >
          </li>
        </ul>
      </section>
      <div class="assessment-list">
        <article
          v-for="question in assessment.questions"
          :key="question.id"
          :ref="(element) => element && questionElements.set(question.id, element as HTMLElement)"
          class="assessment-question"
          tabindex="-1"
        >
          <header class="assessment-question-header">
            <strong>{{ question.sortOrder }}. {{ question.content }}</strong>
            <div class="assessment-question-tags">
              <span v-if="question.sourceType === 'AI_GENERATED'" class="assessment-tag assessment-tag--warning"
                >AI 审查生成题<span v-if="question.originQuestionId">
                  · 母题 #{{ question.originQuestionId }}</span
                ></span
              ><span
                v-if="question.correct != null"
                class="assessment-tag"
                :data-result="question.correct ? 'correct' : 'wrong'"
                >{{ question.correct ? '正确' : '错误' }}</span
              ><template v-for="point in question.knowledgePoints ?? []" :key="point.id"
                ><LtButton
                  v-if="isReviewed(point.id)"
                  size="sm"
                  variant="plain"
                  class="kp-entry"
                  @click="emit('open-kp-tutor', point.id)"
                  >知识点：{{ point.name }}</LtButton
                ><span v-else class="assessment-tag">知识点：{{ point.name }}</span></template
              >
            </div>
          </header>
          <fieldset
            v-if="question.questionType === 'MULTIPLE_CHOICE'"
            class="assessment-options"
            :disabled="assessment.status === 'COMPLETED' || submitting"
          >
            <legend class="lt-sr-only">第 {{ question.sortOrder }} 题，多选</legend>
            <label
              v-for="option in question.options"
              :key="option.label"
              class="assessment-option course-choice"
              :data-state="optionState(question, option.label)"
            >
              <input class="course-choice-input" v-model="answers[question.id]" type="checkbox" :value="option.label" />
              <span class="course-choice-key" aria-hidden="true">{{ option.label }}</span>
              <span class="course-choice-text">{{ option.content }}</span>
              <span
                v-if="assessment.status === 'COMPLETED' && question.userAnswer?.split(',').includes(option.label)"
                class="course-choice-tag"
              >
                你的选择，{{ optionState(question, option.label) === 'right' ? '正确' : '错误' }}
              </span>
              <span v-else-if="optionState(question, option.label) === 'right'" class="course-choice-tag"
                >正确答案</span
              >
            </label>
          </fieldset>
          <fieldset v-else class="assessment-options" :disabled="assessment.status === 'COMPLETED' || submitting">
            <legend class="lt-sr-only">第 {{ question.sortOrder }} 题，单选</legend>
            <label
              v-for="option in question.options"
              :key="option.label"
              class="assessment-option course-choice"
              :data-state="optionState(question, option.label)"
            >
              <input
                class="course-choice-input"
                v-model="answers[question.id][0]"
                type="radio"
                :name="`assessment-${question.id}`"
                :value="option.label"
              />
              <span class="course-choice-key" aria-hidden="true">{{ option.label }}</span>
              <span class="course-choice-text">{{ option.content }}</span>
              <span
                v-if="assessment.status === 'COMPLETED' && question.userAnswer === option.label"
                class="course-choice-tag"
              >
                你的选择，{{ question.correct ? '正确' : '错误' }}
              </span>
              <span v-else-if="optionState(question, option.label) === 'right'" class="course-choice-tag"
                >正确答案</span
              >
            </label>
          </fieldset>
          <div v-if="assessment.status === 'COMPLETED'" class="assessment-result">
            <p>参考答案：{{ question.correctAnswer }}</p>
            <p>{{ question.analysis || '暂无解析' }}</p>
            <LtButton
              v-if="question.correct === false"
              size="sm"
              variant="primary"
              @click="emit('review-wrong', question.questionId)"
              >进入错题复习</LtButton
            >
          </div>
        </article>
      </div>
      <div class="assessment-actions">
        <LtButton
          v-if="assessment.status === 'IN_PROGRESS'"
          variant="primary"
          :disabled="submitting"
          @click="emit('submit')"
          >{{ submitting ? '正在提交…' : '提交测评' }}</LtButton
        >
      </div>
    </div>
  </LtSheet>
</template>
<style scoped>
.stage-assessment,
.assessment-list {
  display: grid;
  gap: var(--lt-space-4);
}
.assessment-notice,
.assessment-source-composition,
.assessment-submit-error,
.assessment-summary {
  margin: 0;
}
.assessment-notice,
.assessment-submit-error {
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-sm);
}
.assessment-notice {
  background: var(--lt-accent-soft);
  color: var(--lt-label);
}
.assessment-notice[data-priority='warning'] {
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
.assessment-source-composition {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.assessment-submit-error {
  background: var(--lt-red-soft);
  color: var(--lt-red-text);
}
.assessment-summary {
  color: var(--lt-label);
  font-size: var(--lt-text-title3);
  font-weight: 600;
}
.assessment-kp-summary,
.assessment-question,
.assessment-result {
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
  box-shadow: 0 0 0 calc(var(--lt-space-1) / 4) var(--lt-hairline);
}
.assessment-kp-summary,
.assessment-question {
  padding: var(--lt-space-4);
}
.assessment-question {
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}
.assessment-kp-summary h3,
.assessment-kp-summary ul,
.assessment-result p {
  margin: 0;
}
.assessment-kp-summary ul {
  display: grid;
  gap: var(--lt-space-2);
  margin-top: var(--lt-space-3);
  padding: 0;
  list-style: none;
}
.assessment-kp-summary li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--lt-space-2);
  color: var(--lt-label);
  font-size: var(--lt-text-footnote);
}
.assessment-kp-summary-count {
  color: var(--lt-label-2);
}
.assessment-question-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--lt-space-3);
  color: var(--lt-label);
  line-height: var(--lt-leading-reading);
}
.assessment-question-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--lt-space-1);
}
.assessment-tag {
  padding: var(--lt-space-1) var(--lt-space-2);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill);
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
  white-space: nowrap;
}
.assessment-tag--warning {
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
.assessment-tag[data-result='correct'] {
  background: var(--lt-green-soft);
  color: var(--lt-green-text);
}
.assessment-tag[data-result='wrong'] {
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
.assessment-options {
  display: grid;
  gap: var(--lt-space-2);
  margin: var(--lt-space-3) 0 0;
  padding: 0;
  border: 0;
}
.assessment-result {
  display: grid;
  gap: var(--lt-space-2);
  margin-top: var(--lt-space-3);
  padding: var(--lt-space-3);
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
}
.assessment-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--lt-space-2);
}
</style>
