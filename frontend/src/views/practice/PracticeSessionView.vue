<template>
  <div class="practice-session">
    <!-- 顶部进度栏 -->
    <div class="session-header">
      <div class="header-left">
        <el-button @click="handleBack" text>
          <el-icon><ArrowLeft /></el-icon> 退出练习
        </el-button>
        <el-tag v-if="isWrongPractice" type="danger" size="small" effect="dark" style="margin-left: 8px">
          错题重练
        </el-tag>
      </div>
      <div class="header-center">
        <span class="progress-text">{{ currentIndex + 1 }} / {{ questions.length }}</span>
        <el-progress
          :percentage="((currentIndex + 1) / questions.length) * 100"
          :stroke-width="8"
          :show-text="false"
          style="width: 200px"
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
      :title="currentResult?.correct ? '🎉 答对了！' : '😢 答错了'"
      :width="isMobile ? '95%' : '680px'"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      class="result-dialog"
    >
      <div class="result-content">
        <div class="result-icon">
          <span v-if="currentResult?.correct" class="icon-correct">✓</span>
          <span v-else class="icon-wrong">✗</span>
        </div>

        <div class="result-answer">
          <p><strong>你的答案：</strong>{{ currentResult?.userAnswer }}</p>
          <p v-if="!currentResult?.correct">
            <strong>正确答案：</strong>
            <span class="correct-answer">{{ currentResult?.correctAnswer }}</span>
          </p>
        </div>

        <div v-if="currentResult?.analysis" class="result-analysis">
          <el-divider />
          <p class="analysis-title">📝 解析</p>
          <p class="analysis-text">{{ currentResult.analysis }}</p>
        </div>

        <AiQuestionAssistant
          v-if="currentResult"
          :question-id="currentResult.questionId"
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
      <el-card class="finish-card">
        <div class="finish-icon">🏆</div>
        <h2>练习完成！</h2>
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
            <div class="fs-value rate">{{ questions.length > 0 ? Math.round(correctCount / questions.length * 100) : 0 }}%</div>
            <div class="fs-label">正确率</div>
          </div>
        </div>
        <div class="finish-actions">
          <el-button @click="handleBack" size="large">返回</el-button>
          <el-button type="primary" @click="restartPractice" size="large">再练一次</el-button>
        </div>
      </el-card>
    </div>

    <!-- 题目卡片 -->
    <div v-if="!finished && currentQuestion" class="question-card-wrapper">
      <el-card class="question-card" shadow="hover">
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
          <el-tag v-for="name in currentQuestion.knowledgePointNames" :key="name" size="small" type="info" effect="plain">
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
              <el-checkbox :model-value="multiAnswers.has(opt.optionLabel)" @click.stop="toggleMulti(opt.optionLabel)" />
              <span class="option-label">{{ opt.optionLabel }}</span>
              <span class="option-content">{{ opt.content }}</span>
            </div>
          </div>

          <!-- 判断题 -->
          <div v-else-if="currentQuestion.questionType === 'TRUE_FALSE'" class="option-list tf-options">
            <div
              :class="['option-item tf-item', { selected: userAnswer === 'TRUE' }]"
              @click="userAnswer = 'TRUE'"
            >
              <span class="option-content">✓ 正确</span>
            </div>
            <div
              :class="['option-item tf-item', { selected: userAnswer === 'FALSE' }]"
              @click="userAnswer = 'FALSE'"
            >
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
          <el-button
            type="primary"
            size="large"
            @click="handleSubmit"
            :loading="submitting"
            :disabled="!canSubmit"
          >
            提交答案
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { submitAnswer } from '@/api/practice'
import type { PracticeQuestionVO, PracticeResultVO } from '@/api/practice'
import AiQuestionAssistant from '@/components/AiQuestionAssistant.vue'

const router = useRouter()

const questions = ref<PracticeQuestionVO[]>([])
const currentIndex = ref(0)
const userAnswer = ref('')
const multiAnswers = ref<Set<string>>(new Set())
const submitting = ref(false)
const showResult = ref(false)
const currentResult = ref<PracticeResultVO | null>(null)
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
    const answer = currentQuestion.value.questionType === 'MULTIPLE_CHOICE'
      ? Array.from(multiAnswers.value).sort().join(',')
      : userAnswer.value.trim()

    const elapsed = Math.round((Date.now() - startTime.value) / 1000)

    const res = await submitAnswer({
      questionId: currentQuestion.value.id,
      userAnswer: answer,
      answerTime: elapsed
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
  } catch (e) {
    ElMessage.error('提交答案失败')
  } finally {
    submitting.value = false
  }
}

const nextQuestion = () => {
  showResult.value = false
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
  if (isWrongPractice.value) {
    router.push({ name: 'WrongQuestions' })
  } else {
    router.push({ name: 'Practice' })
  }
}

const restartPractice = () => {
  sessionStorage.removeItem('practice_questions')
  sessionStorage.removeItem('practice_mode')
  if (isWrongPractice.value) {
    router.push({ name: 'WrongQuestions' })
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
    SHORT_ANSWER: '简答题'
  }
  return map[type] || type
}

const getQuestionTypeTag = (type: string) => {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '',
    MULTIPLE_CHOICE: 'warning',
    TRUE_FALSE: 'success',
    FILL_BLANK: 'info',
    SHORT_ANSWER: 'danger'
  }
  return (map[type] || '') as any
}
</script>

<style scoped>
.practice-session {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
}

.session-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  padding: 16px 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.header-center {
  display: flex;
  align-items: center;
  gap: 12px;
}

.progress-text {
  font-weight: 600;
  color: #303133;
}

.question-card-wrapper {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.question-card {
  margin-bottom: 24px;
}

.question-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.difficulty-stars {
  margin-left: auto;
}

.score-tag {
  font-size: 13px;
  color: #909399;
}

.question-content {
  font-size: 17px;
  line-height: 1.8;
  color: #303133;
  margin-bottom: 16px;
  white-space: pre-wrap;
}

.knowledge-points {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 20px;
}

.answer-area {
  margin: 20px 0;
}

.option-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border: 2px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.option-item:hover {
  border-color: #c0c4cc;
  background: #f5f7fa;
}

.option-item.selected {
  border-color: #409eff;
  background: #ecf5ff;
}

.option-label {
  font-weight: 700;
  color: #409eff;
  min-width: 24px;
}

.option-content {
  flex: 1;
  font-size: 15px;
  color: #303133;
}

.tf-options {
  flex-direction: row;
  gap: 20px;
}

.tf-item {
  flex: 1;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
}

.text-answer {
  margin-top: 8px;
}

.submit-area {
  text-align: center;
  margin-top: 24px;
}

/* 结果弹窗 */
.result-content {
  text-align: center;
}

.result-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.icon-correct {
  color: #67c23a;
  font-weight: 700;
}

.icon-wrong {
  color: #f56c6c;
  font-weight: 700;
}

.result-answer {
  text-align: left;
  font-size: 15px;
  line-height: 2;
}

.correct-answer {
  color: #67c23a;
  font-weight: 700;
}

.analysis-title {
  font-weight: 700;
  margin-bottom: 8px;
}

.analysis-text {
  color: #606266;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  text-align: left;
}

/* 完成页 */
.finish-container {
  display: flex;
  justify-content: center;
  padding-top: 60px;
}

.finish-card {
  width: 100%;
  max-width: 500px;
  text-align: center;
  padding: 20px;
}

.finish-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.finish-card h2 {
  margin-bottom: 32px;
  color: #303133;
}

.finish-stats {
  display: flex;
  justify-content: center;
  gap: 40px;
  margin-bottom: 32px;
}

.fs-value {
  font-size: 32px;
  font-weight: 700;
  color: #409eff;
}

.fs-value.correct {
  color: #67c23a;
}

.fs-value.wrong {
  color: #f56c6c;
}

.fs-value.rate {
  color: #e6a23c;
}

.fs-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.finish-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
}

/* 移动端适配 */
@media (max-width: 767px) {
  .practice-session {
    padding: 12px;
  }

  .session-header {
    flex-wrap: wrap;
    gap: 8px;
    padding: 12px;
  }

  .header-center {
    order: 3;
    width: 100%;
    justify-content: center;
  }

  .header-left .el-button span {
    display: none;
  }

  .question-meta {
    flex-wrap: wrap;
    gap: 6px;
  }

  .difficulty-stars {
    margin-left: 0;
    width: 100%;
    order: 5;
  }

  .question-content {
    font-size: 15px;
  }

  .option-item {
    padding: 12px 14px;
    gap: 8px;
    /* 触摸友好的最小高度 */
    min-height: 48px;
  }

  .tf-options {
    flex-direction: column;
    gap: 10px;
  }

  .finish-stats {
    gap: 20px;
    flex-wrap: wrap;
  }

  .fs-value {
    font-size: 24px;
  }

  .finish-container {
    padding-top: 24px;
  }

  .el-dialog {
    margin: 8px auto !important;
  }
}
</style>
