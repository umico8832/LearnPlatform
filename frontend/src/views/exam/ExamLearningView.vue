<template>
  <div class="paper-learning" v-loading="loading">
    <template v-if="session && currentQuestion">
      <section class="learning-header">
        <div class="learning-header-copy">
          <span class="section-kicker">试卷学习模式</span>
          <h2 class="learning-title">{{ session.paperTitle }}</h2>
          <p v-if="session.paperType === 'OFFICIAL_EXAM' && session.sourceVerified" class="paper-source">
            {{ session.examYear }} · {{ session.examName }} · 来源：{{ session.sourceReference }}
          </p>
        </div>
        <div class="header-actions">
          <el-tag :type="session.status === 1 ? 'success' : 'primary'" class="learning-status-tag">
            {{
              session.status === 1
                ? '本轮已完成'
                : `${session.answeredQuestionCount}/${session.questions.length} 已作答`
            }}
          </el-tag>
          <el-button @click="router.push('/exams')">返回试卷列表</el-button>
        </div>
      </section>

      <section class="learning-layout">
        <article class="question-card">
          <div v-if="currentQuestion.sectionTitle" class="question-section">{{ currentQuestion.sectionTitle }}</div>
          <div class="question-meta">
            <strong>{{ currentQuestion.displayNumber || `第 ${currentIndex + 1} 题` }}</strong>
            <el-tag size="small" effect="plain">{{ questionTypeLabel(currentQuestion.questionType) }}</el-tag>
            <span>{{ currentQuestion.score }} 分</span>
          </div>
          <div class="question-content">{{ currentQuestion.content }}</div>

          <div v-if="currentQuestion.questionType === 'SINGLE_CHOICE'" class="option-list">
            <button
              v-for="option in currentQuestion.options"
              :key="option.id"
              type="button"
              :class="['option-item', { selected: userAnswer === option.optionLabel }]"
              :disabled="session.status === 1"
              @click="userAnswer = option.optionLabel"
            >
              <span class="option-badge">{{ option.optionLabel }}</span>
              <span class="option-content">{{ option.content }}</span>
            </button>
          </div>
          <div v-else-if="currentQuestion.questionType === 'MULTIPLE_CHOICE'" class="option-list">
            <button
              v-for="option in currentQuestion.options"
              :key="option.id"
              type="button"
              :class="['option-item', { selected: multiAnswers.has(option.optionLabel) }]"
              :disabled="session.status === 1"
              @click="toggleMulti(option.optionLabel)"
            >
              <span class="option-badge">{{ option.optionLabel }}</span>
              <span class="option-content">{{ option.content }}</span>
            </button>
          </div>
          <div v-else-if="currentQuestion.questionType === 'TRUE_FALSE'" class="option-list true-false-list">
            <button
              type="button"
              :class="['option-item', { selected: userAnswer === 'TRUE' }]"
              :disabled="session.status === 1"
              @click="userAnswer = 'TRUE'"
            >
              正确
            </button>
            <button
              type="button"
              :class="['option-item', { selected: userAnswer === 'FALSE' }]"
              :disabled="session.status === 1"
              @click="userAnswer = 'FALSE'"
            >
              错误
            </button>
          </div>
          <el-input
            v-else
            v-model="userAnswer"
            type="textarea"
            :rows="4"
            :disabled="session.status === 1"
            placeholder="请输入你的答案"
          />

          <div
            v-if="currentQuestion.latestAnswer"
            class="answer-result"
            :class="
              currentQuestion.latestAnswer.correct === true
                ? 'is-correct'
                : currentQuestion.latestAnswer.correct === false
                  ? 'is-wrong'
                  : 'is-review'
            "
          >
            <div class="result-title">
              {{
                currentQuestion.latestAnswer.correct === null
                  ? '已保存，按参考答案自评'
                  : currentQuestion.latestAnswer.correct
                    ? '回答正确'
                    : '回答错误'
              }}
              <span>第 {{ currentQuestion.latestAnswer.attemptNo }} 次尝试</span>
            </div>
            <p>你的答案：{{ currentQuestion.latestAnswer.userAnswer }}</p>
            <p v-if="currentQuestion.latestAnswer.correct === false">
              正确答案：{{ currentQuestion.latestAnswer.correctAnswer }}
            </p>
            <p v-if="currentQuestion.latestAnswer.analysis">解析：{{ currentQuestion.latestAnswer.analysis }}</p>
          </div>

          <div class="question-actions">
            <el-button :disabled="currentIndex === 0" @click="goTo(currentIndex - 1)">上一题</el-button>
            <el-button
              v-if="session.status === 0"
              type="primary"
              :disabled="!canSubmit"
              :loading="submitting"
              @click="submitCurrentAnswer"
            >
              {{ currentQuestion.latestAnswer ? '再次作答' : '提交答案' }}
            </el-button>
            <el-button :disabled="currentIndex >= session.questions.length - 1" @click="goTo(currentIndex + 1)">
              下一题
            </el-button>
          </div>

          <AiQuestionAssistant
            :question-id="currentQuestion.questionId"
            :learning-session-id="session.id"
            :disabled="!currentQuestion.latestAnswer"
            disabled-reason="先提交本题答案，再让 AI 结合本轮真实作答提供辅导。"
          />
        </article>

        <aside class="answer-sheet">
          <h3 class="sheet-title">本轮学习</h3>
          <div class="sheet-summary">
            <span>已答 {{ session.answeredQuestionCount }}</span>
            <span>当前答对 {{ session.correctQuestionCount }}</span>
          </div>
          <div class="sheet-grid">
            <button
              v-for="(question, index) in session.questions"
              :key="question.questionId"
              type="button"
              :class="[
                'sheet-item',
                {
                  current: index === currentIndex,
                  answered: question.latestAnswer,
                  correct: question.latestAnswer?.correct,
                },
              ]"
              :title="question.displayNumber || `第 ${index + 1} 题`"
              @click="goTo(index)"
            >
              {{ question.displayNumber || index + 1 }}
            </button>
          </div>
          <el-button
            v-if="session.status === 0"
            type="success"
            :disabled="session.answeredQuestionCount < session.questions.length"
            :loading="completing"
            class="complete-button"
            @click="completeLearning"
          >
            完成本轮学习
          </el-button>
          <p
            v-if="session.status === 0 && session.answeredQuestionCount < session.questions.length"
            class="complete-hint"
          >
            全部题目至少作答一次后可完成。
          </p>
        </aside>
      </section>
    </template>

    <LpEmptyState v-else-if="!loading" title="试卷学习会话不存在" description="该学习会话可能已失效或被删除。">
      <template #actions>
        <el-button type="primary" @click="router.push('/exams')">返回试卷列表</el-button>
      </template>
    </LpEmptyState>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { completeExamLearningSession, getExamLearningSession, submitExamLearningAnswer } from '@/api/exam'
import type { ExamLearningSessionVO } from '@/api/exam'
import AiQuestionAssistant from '@/components/AiQuestionAssistant.vue'
import LpEmptyState from '@/components/ui/LpEmptyState.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const submitting = ref(false)
const completing = ref(false)
const session = ref<ExamLearningSessionVO | null>(null)
const currentIndex = ref(0)
const userAnswer = ref('')
const multiAnswers = ref<Set<string>>(new Set())
const answerStartedAt = ref(Date.now())

const currentQuestion = computed(() => session.value?.questions[currentIndex.value] || null)
const canSubmit = computed(() => {
  if (!currentQuestion.value) return false
  if (currentQuestion.value.questionType === 'MULTIPLE_CHOICE') return multiAnswers.value.size > 0
  return userAnswer.value.trim().length > 0
})

watch(currentQuestion, resetAnswerInput)

onMounted(async () => {
  const sessionId = Number(route.params.sessionId)
  if (!Number.isFinite(sessionId) || sessionId <= 0) {
    loading.value = false
    return
  }
  try {
    const response = await getExamLearningSession(sessionId)
    if (response.code === 0 && response.data) {
      session.value = response.data
      const savedIndex = response.data.questions.findIndex(
        (item) => item.questionId === response.data.currentQuestionId,
      )
      currentIndex.value = savedIndex >= 0 ? savedIndex : 0
      resetAnswerInput()
    } else {
      ElMessage.error(response.message || '获取试卷学习会话失败')
    }
  } catch {
    ElMessage.error('获取试卷学习会话失败')
  } finally {
    loading.value = false
  }
})

function resetAnswerInput() {
  userAnswer.value = ''
  multiAnswers.value = new Set()
  answerStartedAt.value = Date.now()
}

function toggleMulti(label: string) {
  const next = new Set(multiAnswers.value)
  if (next.has(label)) {
    next.delete(label)
  } else {
    next.add(label)
  }
  multiAnswers.value = next
}

function goTo(index: number) {
  if (!session.value || index < 0 || index >= session.value.questions.length) return
  currentIndex.value = index
}

async function submitCurrentAnswer() {
  if (!session.value || !currentQuestion.value || !canSubmit.value) return
  submitting.value = true
  const answer =
    currentQuestion.value.questionType === 'MULTIPLE_CHOICE'
      ? Array.from(multiAnswers.value).sort().join(',')
      : userAnswer.value.trim()
  try {
    const response = await submitExamLearningAnswer(session.value.id, {
      questionId: currentQuestion.value.questionId,
      userAnswer: answer,
      answerTime: Math.max(0, Math.round((Date.now() - answerStartedAt.value) / 1000)),
    })
    if (response.code === 0 && response.data) {
      currentQuestion.value.latestAnswer = response.data
      refreshSummary()
      ElMessage.success(response.data.correct ? '回答正确' : '已保存本次作答')
    } else {
      ElMessage.error(response.message || '提交答案失败')
    }
  } catch {
    ElMessage.error('提交答案失败')
  } finally {
    submitting.value = false
  }
}

function refreshSummary() {
  if (!session.value) return
  session.value.answeredQuestionCount = session.value.questions.filter((item) => item.latestAnswer).length
  session.value.correctQuestionCount = session.value.questions.filter((item) => item.latestAnswer?.correct).length
}

async function completeLearning() {
  if (!session.value) return
  completing.value = true
  try {
    const response = await completeExamLearningSession(session.value.id)
    if (response.code === 0 && response.data) {
      session.value = response.data
      ElMessage.success('本轮试卷学习已完成，可继续查看逐题复盘')
    } else {
      ElMessage.error(response.message || '完成学习失败')
    }
  } catch {
    ElMessage.error('完成学习失败')
  } finally {
    completing.value = false
  }
}

function questionTypeLabel(type: string) {
  const labels: Record<string, string> = {
    SINGLE_CHOICE: '单选',
    MULTIPLE_CHOICE: '多选',
    TRUE_FALSE: '判断',
    FILL_BLANK: '填空',
    SHORT_ANSWER: '简答',
  }
  return labels[type] || type
}
</script>

<style scoped>
.paper-learning {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-5);
  width: min(100%, var(--lp-container-narrow));
  margin: 0 auto;
  padding: var(--lp-space-4) 0;
}

.learning-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--lp-space-5);
  padding: var(--lp-space-6);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.learning-header-copy {
  min-width: 0;
}

.learning-title {
  margin: var(--lp-space-1) 0 0;
  color: var(--lp-text);
  font-size: var(--lp-text-3xl);
  line-height: var(--lp-leading-tight);
}

.paper-source {
  margin: var(--lp-space-2) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-snug);
  overflow-wrap: anywhere;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  flex-shrink: 0;
}

.learning-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: var(--lp-space-4);
  align-items: start;
}

.question-card {
  min-width: 0;
  padding: var(--lp-space-6);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.question-section {
  margin-bottom: var(--lp-space-2);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-bold);
}

.question-meta {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}

.question-meta strong {
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
}

.question-content {
  margin: var(--lp-space-5) 0;
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
  line-height: var(--lp-leading-relaxed);
  white-space: pre-wrap;
}

.option-list {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-3);
}

.option-item {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  width: 100%;
  padding: var(--lp-space-3) var(--lp-space-4);
  color: var(--lp-text);
  text-align: left;
  font: inherit;
  background: var(--lp-surface);
  border: 1px solid var(--lp-border-strong);
  border-radius: var(--lp-radius-md);
  cursor: pointer;
  transition:
    background-color var(--lp-duration-fast) var(--lp-ease-out),
    border-color var(--lp-duration-fast) var(--lp-ease-out);
}

.option-item:hover {
  border-color: var(--lp-primary);
  background: var(--lp-surface-subtle);
}

.option-item.selected {
  border-color: var(--lp-primary);
  background: var(--lp-primary-soft);
}

.option-item:disabled {
  cursor: default;
  opacity: 0.72;
}

.option-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
  border-radius: var(--lp-radius-full);
  font-weight: var(--lp-weight-bold);
  font-variant-numeric: tabular-nums;
}

.option-item.selected .option-badge {
  color: var(--lp-on-primary);
  background: var(--lp-primary);
}

.option-content {
  min-width: 0;
}

.true-false-list {
  flex-direction: row;
}

.true-false-list .option-item {
  justify-content: center;
  flex: 1;
  font-weight: var(--lp-weight-semibold);
}

.answer-result {
  margin-top: var(--lp-space-5);
  padding: var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-left-width: 4px;
  border-radius: var(--lp-radius-md);
}

.answer-result.is-correct {
  border-left-color: var(--lp-success);
  background: var(--lp-success-soft);
}

.answer-result.is-wrong {
  border-left-color: var(--lp-danger);
  background: var(--lp-danger-soft);
}

.answer-result.is-review {
  border-left-color: var(--lp-info);
  background: var(--lp-info-soft);
}

.answer-result p {
  margin: var(--lp-space-2) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}

.result-title {
  display: flex;
  justify-content: space-between;
  color: var(--lp-text);
  font-weight: var(--lp-weight-bold);
}

.result-title span {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-medium);
}

.question-actions {
  display: flex;
  justify-content: center;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-5);
}

.answer-sheet {
  position: sticky;
  top: var(--lp-space-4);
  padding: var(--lp-space-4);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.sheet-title {
  margin: 0 0 var(--lp-space-2);
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
}

.sheet-summary {
  display: flex;
  justify-content: space-between;
  margin-bottom: var(--lp-space-4);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
}

.sheet-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--lp-space-2);
}

.sheet-item {
  min-width: 0;
  height: 34px;
  overflow: hidden;
  color: var(--lp-text-secondary);
  background: var(--lp-surface-soft);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius-sm);
  font-size: var(--lp-text-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color var(--lp-duration-fast) var(--lp-ease-out),
    color var(--lp-duration-fast) var(--lp-ease-out);
}

.sheet-item.answered {
  color: var(--lp-on-primary);
  background: var(--lp-warning);
  border-color: var(--lp-warning);
}

.sheet-item.correct {
  background: var(--lp-success);
  border-color: var(--lp-success);
}

.sheet-item.current {
  box-shadow: 0 0 0 2px var(--lp-primary);
}

.complete-button {
  width: 100%;
  margin-top: var(--lp-space-4);
}

.complete-hint {
  margin: var(--lp-space-2) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
  line-height: var(--lp-leading-snug);
}

@media (max-width: 860px) {
  .learning-layout {
    grid-template-columns: 1fr;
  }

  .answer-sheet {
    position: static;
  }
}

@media (max-width: 640px) {
  .paper-learning {
    padding: 0;
  }

  .learning-header {
    align-items: stretch;
    flex-direction: column;
    padding: var(--lp-space-4);
  }

  .header-actions {
    justify-content: space-between;
  }

  .question-card {
    padding: var(--lp-space-4);
  }

  .true-false-list {
    flex-direction: column;
  }

  .question-actions {
    flex-wrap: wrap;
  }
}
</style>
