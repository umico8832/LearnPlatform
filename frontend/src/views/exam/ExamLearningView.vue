<template>
  <div class="paper-learning page-container" v-loading="loading">
    <template v-if="session && currentQuestion">
      <section class="learning-header">
        <div>
          <span class="section-kicker">试卷学习模式</span>
          <h2>{{ session.paperTitle }}</h2>
          <p v-if="session.paperType === 'OFFICIAL_EXAM' && session.sourceVerified" class="paper-source">
            {{ session.examYear }} · {{ session.examName }} · 来源：{{ session.sourceReference }}
          </p>
        </div>
        <div class="header-actions">
          <el-tag :type="session.status === 1 ? 'success' : 'primary'">
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
        <el-card shadow="never" class="question-card">
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
              <span>{{ option.optionLabel }}</span
              >{{ option.content }}
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
              <span>{{ option.optionLabel }}</span
              >{{ option.content }}
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
            :class="currentQuestion.latestAnswer.correct ? 'is-correct' : 'is-wrong'"
          >
            <div class="result-title">
              {{ currentQuestion.latestAnswer.correct ? '回答正确' : '回答错误' }}
              <span>第 {{ currentQuestion.latestAnswer.attemptNo }} 次尝试</span>
            </div>
            <p>你的答案：{{ currentQuestion.latestAnswer.userAnswer }}</p>
            <p v-if="!currentQuestion.latestAnswer.correct">
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
            <el-button :disabled="currentIndex >= session.questions.length - 1" @click="goTo(currentIndex + 1)"
              >下一题</el-button
            >
          </div>

          <AiQuestionAssistant
            :question-id="currentQuestion.questionId"
            :learning-session-id="session.id"
            :disabled="!currentQuestion.latestAnswer"
            disabled-reason="先提交本题答案，再让 AI 结合本轮真实作答提供辅导。"
          />
        </el-card>

        <aside class="answer-sheet">
          <h3>本轮学习</h3>
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
    <el-empty v-else-if="!loading" description="试卷学习会话不存在">
      <el-button type="primary" @click="router.push('/exams')">返回试卷列表</el-button>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { completeExamLearningSession, getExamLearningSession, submitExamLearningAnswer } from '@/api/exam'
import type { ExamLearningSessionVO } from '@/api/exam'
import AiQuestionAssistant from '@/components/AiQuestionAssistant.vue'

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
  gap: 16px;
}
.learning-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 22px;
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  box-shadow: var(--lp-shadow-sm);
}
.section-kicker {
  color: var(--lp-primary);
  font-size: 12px;
  font-weight: 800;
}
.learning-header h2 {
  margin: 4px 0 6px;
  color: var(--lp-text);
  font-size: 24px;
}
.paper-source {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: 13px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.learning-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 230px;
  gap: 16px;
  align-items: start;
}
.question-card {
  min-width: 0;
}
.question-section {
  margin-bottom: 8px;
  color: var(--lp-text-secondary);
  font-size: 13px;
  font-weight: 700;
}
.question-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--lp-text-secondary);
  font-size: 13px;
}
.question-meta strong {
  color: var(--lp-text);
  font-size: 16px;
}
.question-content {
  margin: 18px 0;
  color: var(--lp-text);
  font-size: 16px;
  line-height: 1.8;
  white-space: pre-wrap;
}
.option-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.option-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 12px 14px;
  color: var(--lp-text);
  text-align: left;
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: 8px;
  cursor: pointer;
}
.option-item span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
  border-radius: 50%;
  font-weight: 800;
}
.option-item.selected {
  border-color: var(--lp-primary);
  background: var(--lp-primary-soft);
}
.option-item:disabled {
  cursor: default;
  opacity: 0.72;
}
.true-false-list {
  flex-direction: row;
}
.true-false-list .option-item {
  justify-content: center;
}
.answer-result {
  margin-top: 18px;
  padding: 14px;
  border: 1px solid var(--lp-border);
  border-left-width: 4px;
  border-radius: 8px;
}
.answer-result.is-correct {
  border-left-color: var(--lp-success);
  background: #f0f9eb;
}
.answer-result.is-wrong {
  border-left-color: var(--lp-danger);
  background: #fef0f0;
}
.answer-result p {
  margin: 8px 0 0;
  color: var(--lp-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.result-title {
  display: flex;
  justify-content: space-between;
  color: var(--lp-text);
  font-weight: 800;
}
.result-title span {
  color: var(--lp-text-secondary);
  font-size: 12px;
  font-weight: 500;
}
.question-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 20px;
}
.answer-sheet {
  position: sticky;
  top: 16px;
  padding: 16px;
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  box-shadow: var(--lp-shadow-sm);
}
.answer-sheet h3 {
  margin: 0 0 8px;
  color: var(--lp-text);
  font-size: 16px;
}
.sheet-summary {
  display: flex;
  justify-content: space-between;
  margin-bottom: 14px;
  color: var(--lp-text-secondary);
  font-size: 12px;
}
.sheet-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
}
.sheet-item {
  min-width: 0;
  height: 34px;
  overflow: hidden;
  color: var(--lp-text-secondary);
  background: var(--lp-surface-soft);
  border: 1px solid var(--lp-border);
  border-radius: 6px;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}
.sheet-item.answered {
  color: #fff;
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
  margin-top: 16px;
}
.complete-hint {
  margin: 8px 0 0;
  color: var(--lp-text-secondary);
  font-size: 12px;
  line-height: 1.5;
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
  .learning-header {
    align-items: stretch;
    flex-direction: column;
    padding: 16px;
  }
  .header-actions {
    justify-content: space-between;
  }
  .true-false-list {
    flex-direction: column;
  }
  .question-actions {
    flex-wrap: wrap;
  }
}
</style>
