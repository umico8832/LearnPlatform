<script setup lang="ts">
import { computed } from 'vue'
import type { CourseStageAssessmentVO } from '@/api/course'

/**
 * 阶段测评作答/复盘弹窗：进行中隐藏答案，完成后展示判分、题源与知识点汇总。
 * 行为契约与 CourseOverviewView 保持一致（E2E 依赖弹窗标题、.assessment-question 与按钮文案）。
 */
const props = defineProps<{
  visible: boolean
  assessment: CourseStageAssessmentVO | null
  submitting: boolean
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

/** el-dialog 使用 v-model 需要可写代理。 */
const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

const strategyLabel = computed(() =>
  props.assessment?.selectionStrategy === 'LEARNING_STATE_PRIORITY'
    ? '按当前错题、到期复习和近期错误记录优先选题'
    : '学习数据不足，采用确定性课程题序；本次不标记为 AI 个性化',
)

const isReviewed = (knowledgePointId: number) => props.reviewedKnowledgePointIds.includes(knowledgePointId)

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
  <el-dialog v-model="dialogVisible" title="课程阶段测评" width="min(780px, 94vw)">
    <template v-if="assessment">
      <el-alert
        :title="strategyLabel"
        :type="assessment.selectionStrategy === 'LEARNING_STATE_PRIORITY' ? 'info' : 'warning'"
        :closable="false"
        show-icon
      />
      <p class="assessment-source-composition">
        范围：{{ assessment.targetKnowledgePointName || '课程整体' }} · 题源构成：{{
          sourceCompositionText(assessment.sourceComposition)
        }}
      </p>
      <p v-if="assessment.status === 'COMPLETED'" class="assessment-summary">
        答对 {{ assessment.correctCount }} / {{ assessment.questionCount }} 题
      </p>
      <div
        v-if="assessment.status === 'COMPLETED' && assessment.knowledgePointSummary?.length"
        class="assessment-kp-summary"
      >
        <h4 class="assessment-kp-summary-heading">按知识点统计</h4>
        <p class="assessment-kp-summary-note">仅统计本轮各知识点的题数与正误数，不推断掌握度或趋势。</p>
        <ul class="assessment-kp-summary-list">
          <li v-for="point in assessment.knowledgePointSummary" :key="point.id" class="assessment-kp-summary-item">
            <span>知识点：{{ point.name }}</span>
            <span class="assessment-kp-summary-count"
              >答对 {{ point.correctCount }} / {{ point.questionCount }} 题</span
            >
            <el-button
              v-if="point.correctCount < point.questionCount"
              size="small"
              text
              type="danger"
              @click="emit('review-wrong-by-kp', { id: point.id, name: point.name })"
            >
              复习该知识点错题
            </el-button>
            <el-button
              v-if="isReviewed(point.id)"
              size="small"
              text
              type="primary"
              @click="emit('open-kp-tutor', point.id)"
            >
              进入教学
            </el-button>
          </li>
        </ul>
      </div>
      <div class="assessment-list">
        <article v-for="question in assessment.questions" :key="question.id" class="assessment-question">
          <div class="assessment-question-header">
            <strong>{{ question.sortOrder }}. {{ question.content }}</strong>
            <div class="assessment-question-tags">
              <el-tag v-if="question.sourceType === 'AI_GENERATED'" type="warning" effect="plain">
                AI 审查生成题<span v-if="question.originQuestionId"> · 母题 #{{ question.originQuestionId }}</span>
              </el-tag>
              <el-tag v-if="question.correct != null" :type="question.correct ? 'success' : 'danger'">
                {{ question.correct ? '正确' : '错误' }}
              </el-tag>
              <template v-for="point in question.knowledgePoints ?? []" :key="point.id">
                <el-button
                  v-if="isReviewed(point.id)"
                  size="small"
                  text
                  type="primary"
                  class="kp-entry"
                  @click="emit('open-kp-tutor', point.id)"
                >
                  知识点：{{ point.name }}
                </el-button>
                <el-tag v-else size="small" effect="plain">知识点：{{ point.name }}</el-tag>
              </template>
            </div>
          </div>
          <el-checkbox-group
            v-if="question.questionType === 'MULTIPLE_CHOICE'"
            v-model="answers[question.id]"
            :disabled="assessment.status === 'COMPLETED'"
            class="assessment-options"
          >
            <el-checkbox v-for="option in question.options" :key="option.label" :value="option.label">
              {{ option.label }}. {{ option.content }}
            </el-checkbox>
          </el-checkbox-group>
          <el-radio-group
            v-else
            v-model="answers[question.id][0]"
            :disabled="assessment.status === 'COMPLETED'"
            class="assessment-options"
          >
            <el-radio v-for="option in question.options" :key="option.label" :value="option.label">
              {{ option.content }}
            </el-radio>
          </el-radio-group>
          <div v-if="assessment.status === 'COMPLETED'" class="assessment-result">
            <p>参考答案：{{ question.correctAnswer }}</p>
            <p>{{ question.analysis || '暂无解析' }}</p>
            <el-button
              v-if="question.correct === false"
              size="small"
              type="primary"
              plain
              @click="emit('review-wrong', question.questionId)"
            >
              进入错题复习
            </el-button>
          </div>
        </article>
      </div>
    </template>
    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
      <el-button
        v-if="assessment?.status === 'IN_PROGRESS'"
        type="primary"
        :loading="submitting"
        @click="emit('submit')"
        >提交测评</el-button
      >
    </template>
  </el-dialog>
</template>

<style scoped>
.assessment-source-composition {
  margin: var(--lp-space-3) 0 var(--lp-space-4);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}
.assessment-summary {
  margin: var(--lp-space-4) 0 0;
  color: var(--lp-text);
  font-size: var(--lp-text-xl);
  font-weight: var(--lp-weight-bold);
}
.assessment-kp-summary {
  margin: var(--lp-space-4) 0 var(--lp-space-4);
  padding: var(--lp-space-3) var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface-soft);
}
.assessment-kp-summary-heading {
  margin: 0 0 4px;
  font-size: var(--lp-text-base);
  color: var(--lp-text);
}
.assessment-kp-summary-note {
  margin: 0 0 var(--lp-space-2);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
.assessment-kp-summary-list {
  display: grid;
  gap: var(--lp-space-1);
  margin: 0;
  padding: 0;
  list-style: none;
}
.assessment-kp-summary-item {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  font-size: var(--lp-text-sm);
  color: var(--lp-text);
}
.assessment-kp-summary-count {
  color: var(--lp-text-secondary);
}
.assessment-list {
  display: grid;
  gap: var(--lp-space-4);
  margin-top: var(--lp-space-4);
}
.assessment-question {
  padding: var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface-soft);
}
.assessment-question-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--lp-space-3);
  color: var(--lp-text);
  line-height: var(--lp-leading-body);
}
.assessment-question-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--lp-space-1);
}
.assessment-options {
  display: grid;
  gap: var(--lp-space-2);
  margin-top: var(--lp-space-3);
}
.assessment-result {
  margin-top: var(--lp-space-3);
  padding: var(--lp-space-3) var(--lp-space-4);
  border-radius: var(--lp-radius-sm);
  background: var(--lp-surface);
  color: var(--lp-text-secondary);
}
.assessment-result p {
  margin: 0;
  line-height: var(--lp-leading-body);
}
.assessment-result p + p {
  margin-top: var(--lp-space-1);
}
@media (max-width: 767px) {
  .assessment-question-header {
    flex-direction: column;
  }
}
</style>
