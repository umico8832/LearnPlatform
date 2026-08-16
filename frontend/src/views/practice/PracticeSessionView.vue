<template>
  <div class="practice-session">
    <!-- 顶部进度栏 -->
    <div class="session-header">
      <div class="header-left">
        <el-button @click="handleBack" text>
          <el-icon><ArrowLeft /></el-icon> 退出练习
        </el-button>
        <el-tag v-if="isWrongPractice" type="danger" size="small" effect="dark" class="mode-tag"> 错题重练 </el-tag>
        <el-tag v-if="isFavoritePractice" type="warning" size="small" effect="dark" class="mode-tag"> 收藏练习 </el-tag>
      </div>
      <div class="header-center">
        <LpProgress
          :percent="((currentIndex + 1) / questions.length) * 100"
          show-label
          :label="`${currentIndex + 1} / ${questions.length}`"
        />
      </div>
      <div class="header-right">
        <el-tag :type="correctCount > wrongCount ? 'success' : 'danger'" size="large">
          ✓ {{ correctCount }} / ✗ {{ wrongCount }}
        </el-tag>
      </div>
    </div>

    <!-- 答题结果弹窗 -->
    <el-dialog
      v-model="showResult"
      :title="currentResult?.correct ? '答对了！' : '答错了！'"
      :width="isMobile ? '95%' : '680px'"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      class="result-dialog"
      @closed="handleResultClosed"
    >
      <div class="result-content">
        <div class="result-icon" :class="currentResult?.correct ? 'is-correct' : 'is-wrong'">
          <span>{{ currentResult?.correct ? '✓' : '✗' }}</span>
        </div>

        <div class="result-answer">
          <p><strong>你的答案：</strong>{{ currentResult?.userAnswer }}</p>
          <p v-if="!currentResult?.correct">
            <strong>正确答案：</strong>
            <span class="correct-answer">{{ currentResult?.correctAnswer }}</span>
          </p>
        </div>

        <div v-if="currentResult?.analysis" class="result-analysis">
          <LpDivider />
          <p class="analysis-title">解析</p>
          <p class="analysis-text">{{ currentResult.analysis }}</p>
        </div>

        <AiQuestionAssistant v-if="currentResult" :question-id="currentResult.questionId" />

        <!-- 答错后展示 AI 深度学习资产（答错后的 AI 讲解入口），折叠模式减少弹窗长度 -->
        <QuestionLearningAsset
          v-if="currentResult && !currentResult.correct"
          :question-id="currentResult.questionId"
          collapsible
        />
      </div>

      <template #footer>
        <el-button type="primary" @click="nextQuestion" size="large">
          {{ currentIndex < questions.length - 1 ? '下一题' : '查看结果' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 练习完成总结 -->
    <div v-if="finished" class="finish-container">
      <div class="finish-card">
        <div class="finish-icon" aria-hidden="true">✓</div>
        <h2>练习完成！</h2>
        <p class="finish-note">本轮练习已结束，可以返回或再练一次。</p>
        <div class="finish-stats">
          <div class="finish-stat">
            <div class="fs-value">{{ questions.length }}</div>
            <div class="fs-label">总题数</div>
          </div>
          <div class="finish-stat">
            <div class="fs-value correct">{{ correctCount }}</div>
            <div class="fs-label">答对</div>
          </div>
          <div class="finish-stat">
            <div class="fs-value wrong">{{ wrongCount }}</div>
            <div class="fs-label">答错</div>
          </div>
          <div class="finish-stat">
            <div class="fs-value rate">
              {{ questions.length > 0 ? Math.round((correctCount / questions.length) * 100) : 0 }}%
            </div>
            <div class="fs-label">正确率</div>
          </div>
        </div>
        <div class="finish-actions">
          <el-button @click="handleBack" size="large">返回</el-button>
          <el-button type="primary" @click="restartPractice" size="large">再练一次</el-button>
        </div>
      </div>
    </div>

    <!-- 题目卡片 -->
    <div v-if="!finished && currentQuestion" class="question-card-wrapper">
      <el-card class="question-card" shadow="never">
        <div class="question-meta">
          <el-tag :type="getQuestionTypeTag(currentQuestion.questionType)" size="small">
            {{ getQuestionTypeLabel(currentQuestion.questionType) }}
          </el-tag>
          <el-tag v-if="currentQuestion.courseName" type="info" size="small">
            {{ currentQuestion.courseName }}
          </el-tag>
          <div class="difficulty-stars">
            <el-rate v-model="currentQuestion.difficulty" disabled :max="5" />
          </div>
          <span class="score-tag">分值：{{ currentQuestion.score }}</span>
        </div>

        <div class="question-content">{{ currentQuestion.content }}</div>

        <div class="knowledge-points" v-if="currentQuestion.knowledgePointNames?.length">
          <el-tag
            v-for="name in currentQuestion.knowledgePointNames"
            :key="name"
            size="small"
            type="info"
            effect="plain"
          >
            {{ name }}
          </el-tag>
        </div>

        <!-- 选项区域 -->
        <div class="answer-area">
          <!-- 单选题 -->
          <div v-if="currentQuestion.questionType === 'SINGLE_CHOICE'" class="option-list">
            <div
              v-for="opt in currentQuestion.options"
              :key="opt.id"
              :class="['option-item', { selected: userAnswer === opt.optionLabel }]"
              @click="userAnswer = opt.optionLabel"
            >
              <span class="option-label">{{ opt.optionLabel }}</span>
              <span class="option-content">{{ opt.content }}</span>
            </div>
          </div>

          <!-- 多选题 -->
          <div v-else-if="currentQuestion.questionType === 'MULTIPLE_CHOICE'" class="option-list">
            <div
              v-for="opt in currentQuestion.options"
              :key="opt.id"
              :class="['option-item', { selected: multiAnswers.has(opt.optionLabel) }]"
              @click="toggleMulti(opt.optionLabel)"
            >
              <el-checkbox
                :model-value="multiAnswers.has(opt.optionLabel)"
                @click.stop="toggleMulti(opt.optionLabel)"
              />
              <span class="option-label">{{ opt.optionLabel }}</span>
              <span class="option-content">{{ opt.content }}</span>
            </div>
          </div>

          <!-- 判断题 -->
          <div v-else-if="currentQuestion.questionType === 'TRUE_FALSE'" class="option-list tf-options">
            <div :class="['option-item tf-item', { selected: userAnswer === 'TRUE' }]" @click="userAnswer = 'TRUE'">
              <span class="option-content">✓ 正确</span>
            </div>
            <div :class="['option-item tf-item', { selected: userAnswer === 'FALSE' }]" @click="userAnswer = 'FALSE'">
              <span class="option-content">✗ 错误</span>
            </div>
          </div>

          <!-- 填空题 / 简答题 -->
          <div v-else class="text-answer">
            <el-input
              v-model="userAnswer"
              type="textarea"
              :rows="currentQuestion.questionType === 'SHORT_ANSWER' ? 4 : 2"
              placeholder="请输入你的答案..."
            />
          </div>
        </div>

        <!-- 提交按钮 -->
        <div class="submit-area">
          <el-button type="primary" size="large" @click="handleSubmit" :loading="submitting" :disabled="!canSubmit">
            提交答案
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { SemanticTagType } from '@/utils/errors'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { submitAnswer } from '@/api/practice'
import type { PracticeQuestionVO, PracticeResultVO } from '@/api/practice'
import AiQuestionAssistant from '@/components/AiQuestionAssistant.vue'
import QuestionLearningAsset from '@/components/QuestionLearningAsset.vue'

const router = useRouter()

const questions = ref<PracticeQuestionVO[]>([])
const currentIndex = ref(0)
const userAnswer = ref('')
const multiAnswers = ref<Set<string>>(new Set())
const submitting = ref(false)
const showResult = ref(false)
const currentResult = ref<PracticeResultVO | null>(null)
const pendingResultAction = ref(false)
const finished = ref(false)
const correctCount = ref(0)
const wrongCount = ref(0)
const startTime = ref(Date.now())
const practiceMode = ref<string>('')
const isMobile = ref(false)

function checkMobile() {
  isMobile.value = window.innerWidth < 768
}

const currentQuestion = computed(() => questions.value[currentIndex.value] || null)
const isWrongPractice = computed(() => practiceMode.value === 'wrong_question')
const isFavoritePractice = computed(() => practiceMode.value === 'favorite')

const canSubmit = computed(() => {
  if (!currentQuestion.value) return false
  const q = currentQuestion.value
  if (q.questionType === 'MULTIPLE_CHOICE') {
    return multiAnswers.value.size > 0
  }
  return userAnswer.value.trim().length > 0
})

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  const stored = sessionStorage.getItem('practice_questions')
  if (stored) {
    questions.value = JSON.parse(stored)
    startTime.value = Date.now()
    practiceMode.value = sessionStorage.getItem('practice_mode') || ''
  } else {
    ElMessage.warning('没有练习题目，请先选择刷题模式')
    router.replace({ name: 'Practice' })
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', checkMobile)
})

const toggleMulti = (label: string) => {
  const s = new Set(multiAnswers.value)
  if (s.has(label)) {
    s.delete(label)
  } else {
    s.add(label)
  }
  multiAnswers.value = s
}

const handleSubmit = async () => {
  if (!currentQuestion.value) return
  submitting.value = true
  try {
    const answer =
      currentQuestion.value.questionType === 'MULTIPLE_CHOICE'
        ? Array.from(multiAnswers.value).sort().join(',')
        : userAnswer.value.trim()

    const elapsed = Math.round((Date.now() - startTime.value) / 1000)

    const res = await submitAnswer({
      questionId: currentQuestion.value.id,
      userAnswer: answer,
      answerTime: elapsed,
    })

    if (res.code === 0 && res.data) {
      currentResult.value = res.data
      if (res.data.correct) {
        correctCount.value++
      } else {
        wrongCount.value++
      }
      showResult.value = true
    } else {
      ElMessage.error(res.message || '提交失败')
    }
  } catch {
    ElMessage.error('提交答案失败')
  } finally {
    submitting.value = false
  }
}

const nextQuestion = () => {
  // Keep the current result intact until Element Plus finishes its closing
  // transition. Clearing it immediately makes the leaving dialog briefly
  // render as an empty "wrong answer" result.
  pendingResultAction.value = true
  showResult.value = false
}

const handleResultClosed = () => {
  if (!pendingResultAction.value) return
  pendingResultAction.value = false
  currentResult.value = null

  if (currentIndex.value < questions.value.length - 1) {
    currentIndex.value++
    userAnswer.value = ''
    multiAnswers.value = new Set()
    startTime.value = Date.now()
  } else {
    finished.value = true
  }
}

const handleBack = () => {
  sessionStorage.removeItem('practice_questions')
  sessionStorage.removeItem('practice_mode')
  if (isWrongPractice.value || practiceMode.value === 'similar') {
    router.push({ name: 'WrongQuestions' })
  } else if (isFavoritePractice.value) {
    router.push({ name: 'Favorites' })
  } else if (practiceMode.value === 'recommended') {
    router.push({ name: 'LearningDiagnosis' })
  } else {
    router.push({ name: 'Practice' })
  }
}

const restartPractice = () => {
  sessionStorage.removeItem('practice_questions')
  sessionStorage.removeItem('practice_mode')
  if (isWrongPractice.value || practiceMode.value === 'similar') {
    router.push({ name: 'WrongQuestions' })
  } else if (isFavoritePractice.value) {
    router.push({ name: 'Favorites' })
  } else if (practiceMode.value === 'recommended') {
    router.push({ name: 'LearningDiagnosis' })
  } else {
    router.push({ name: 'Practice' })
  }
}

const getQuestionTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    FILL_BLANK: '填空题',
    SHORT_ANSWER: '简答题',
  }
  return map[type] || type
}

const getQuestionTypeTag = (type: string) => {
  const map: Record<string, SemanticTagType> = {
    SINGLE_CHOICE: undefined,
    MULTIPLE_CHOICE: 'warning',
    TRUE_FALSE: 'success',
    FILL_BLANK: 'info',
    SHORT_ANSWER: 'danger',
  }
  return map[type]
}
</script>

<style scoped>
.practice-session {
  padding: var(--lp-space-6);
  max-width: 800px;
  margin: 0 auto;
}

/* 顶部进度栏 */
.session-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-4);
  margin-bottom: var(--lp-space-6);
  padding: var(--lp-space-3) var(--lp-space-5);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
}

.header-center {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 160px;
  max-width: 320px;
}

.header-center :deep(.lp-progress) {
  width: 100%;
}

.mode-tag {
  margin-left: var(--lp-space-2);
}

/* 题目卡片 */
.question-card-wrapper {
  animation: lp-fade-in var(--lp-duration-normal) var(--lp-ease-out);
}

@keyframes lp-fade-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.question-card {
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.question-meta {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  margin-bottom: var(--lp-space-4);
  flex-wrap: wrap;
}

.difficulty-stars {
  margin-left: auto;
}

.score-tag {
  font-size: var(--lp-text-sm);
  color: var(--lp-text-muted);
  font-variant-numeric: tabular-nums;
}

.question-content {
  font-size: var(--lp-text-xl);
  line-height: var(--lp-leading-relaxed);
  color: var(--lp-text);
  margin-bottom: var(--lp-space-4);
  white-space: pre-wrap;
}

.knowledge-points {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lp-space-2);
  margin-bottom: var(--lp-space-5);
}

.answer-area {
  margin: var(--lp-space-5) 0;
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
  padding: var(--lp-space-4) var(--lp-space-5);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  cursor: pointer;
  transition:
    border-color var(--lp-duration-fast) var(--lp-ease-out),
    background-color var(--lp-duration-fast) var(--lp-ease-out);
}

.option-item:hover {
  border-color: var(--lp-border-strong);
  background: var(--lp-surface-subtle);
}

.option-item.selected {
  border-color: var(--lp-primary);
  background: var(--lp-primary-soft);
}

.option-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  border-radius: var(--lp-radius-full);
  background: var(--lp-surface-inset);
  color: var(--lp-text-secondary);
  font-weight: var(--lp-weight-bold);
  font-size: var(--lp-text-sm);
  flex-shrink: 0;
}

.option-item.selected .option-label {
  background: var(--lp-primary);
  color: var(--lp-on-primary);
}

.option-content {
  flex: 1;
  font-size: var(--lp-text-md);
  color: var(--lp-text);
  line-height: var(--lp-leading-snug);
}

.tf-options {
  flex-direction: row;
  gap: var(--lp-space-4);
}

.tf-item {
  flex: 1;
  justify-content: center;
  font-size: var(--lp-text-lg);
  font-weight: var(--lp-weight-semibold);
}

.text-answer {
  margin-top: var(--lp-space-2);
}

.submit-area {
  text-align: center;
  margin-top: var(--lp-space-6);
}

/* 结果弹窗 */
.result-content {
  text-align: center;
}

.result-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin: 0 auto var(--lp-space-4);
  border-radius: var(--lp-radius-full);
  font-size: var(--lp-text-3xl);
  font-weight: var(--lp-weight-bold);
}

.result-icon.is-correct {
  background: var(--lp-success-soft);
  color: var(--lp-success);
}

.result-icon.is-wrong {
  background: var(--lp-danger-soft);
  color: var(--lp-danger);
}

.result-answer {
  text-align: left;
  font-size: var(--lp-text-md);
  line-height: var(--lp-leading-relaxed);
  color: var(--lp-text);
}

.result-answer strong {
  color: var(--lp-text-secondary);
  font-weight: var(--lp-weight-semibold);
}

.correct-answer {
  color: var(--lp-success);
  font-weight: var(--lp-weight-bold);
}

.analysis-title {
  font-weight: var(--lp-weight-bold);
  margin-bottom: var(--lp-space-2);
  color: var(--lp-text);
}

.analysis-text {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-relaxed);
  white-space: pre-wrap;
  text-align: left;
}

/* 完成页 */
.finish-container {
  display: flex;
  justify-content: center;
  padding-top: var(--lp-space-16);
}

.finish-card {
  width: 100%;
  max-width: 500px;
  text-align: center;
  padding: var(--lp-space-8) var(--lp-space-6);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.finish-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  margin: 0 auto var(--lp-space-4);
  border-radius: var(--lp-radius-full);
  background: var(--lp-success-soft);
  color: var(--lp-success);
  font-size: var(--lp-text-4xl);
  font-weight: var(--lp-weight-bold);
}

.finish-card h2 {
  margin-bottom: var(--lp-space-2);
  color: var(--lp-text);
  font-size: var(--lp-text-3xl);
}

.finish-note {
  margin: 0 0 var(--lp-space-6);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-base);
}

.finish-stats {
  display: flex;
  justify-content: center;
  gap: var(--lp-space-8);
  margin-bottom: var(--lp-space-8);
}

.fs-value {
  font-size: var(--lp-text-4xl);
  font-weight: var(--lp-weight-bold);
  color: var(--lp-primary);
  font-variant-numeric: tabular-nums;
}

.fs-value.correct {
  color: var(--lp-success);
}

.fs-value.wrong {
  color: var(--lp-danger);
}

.fs-value.rate {
  color: var(--lp-warning);
}

.fs-label {
  font-size: var(--lp-text-sm);
  color: var(--lp-text-muted);
  margin-top: var(--lp-space-1);
}

.finish-actions {
  display: flex;
  justify-content: center;
  gap: var(--lp-space-4);
}

/* 移动端适配 */
@media (max-width: 767px) {
  .practice-session {
    padding: var(--lp-space-3);
  }

  .session-header {
    flex-wrap: wrap;
    gap: var(--lp-space-2);
    padding: var(--lp-space-3);
  }

  .header-center {
    order: 3;
    width: 100%;
    max-width: none;
    justify-content: center;
  }

  .header-left .el-button span {
    display: none;
  }

  .question-content {
    font-size: var(--lp-text-lg);
  }

  .option-item {
    padding: var(--lp-space-3) var(--lp-space-4);
    gap: var(--lp-space-2);
    /* 触摸友好的最小高度 */
    min-height: 48px;
  }

  .tf-options {
    flex-direction: column;
    gap: var(--lp-space-3);
  }

  .finish-stats {
    gap: var(--lp-space-4);
    flex-wrap: wrap;
  }

  .fs-value {
    font-size: var(--lp-text-3xl);
  }

  .finish-container {
    padding-top: var(--lp-space-6);
  }

  .el-dialog {
    margin: var(--lp-space-2) auto !important;
  }
}
</style>
