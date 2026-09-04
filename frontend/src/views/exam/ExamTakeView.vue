<template>
  <div class="exam-take-container">
    <header class="take-header">
      <div class="take-header-title">
        <span class="section-kicker">限时考试</span>
        <h1 class="take-heading">考试进行中</h1>
      </div>
      <div class="take-header-progress">
        <span class="progress-text">{{ currentIndex + 1 }} / {{ questions.length }}</span>
        <LpProgress :percent="progressPercent" tone="primary" />
        <span class="answered-text">已答 {{ answeredCount }} / {{ questions.length }}</span>
      </div>
      <div class="take-header-right">
        <span :class="['countdown', { 'countdown-warn': remainSeconds < 300 }]">
          <el-icon><Timer /></el-icon> {{ countdownText }}
        </span>
        <el-button type="danger" size="small" :loading="submitted" :disabled="submitted" @click="handleSubmit">
          提交试卷
        </el-button>
      </div>
    </header>

    <div v-if="loading" v-loading="true" class="take-loading"></div>

    <div v-else-if="currentQuestion" class="question-area">
      <article class="question-card">
        <div v-if="currentQuestion.sectionTitle" class="q-section">{{ currentQuestion.sectionTitle }}</div>
        <div class="q-meta">
          <strong class="q-number">{{ currentQuestion.displayNumber || `第 ${currentIndex + 1} 题` }}</strong>
          <el-tag size="small">{{ getTypeLabel(currentQuestion.questionType) }}</el-tag>
          <span class="q-score">分值：{{ currentQuestion.score }} 分</span>
        </div>
        <div class="q-content">{{ currentQuestion.content }}</div>

        <div v-if="currentQuestion.questionType === 'SINGLE_CHOICE'" class="option-list">
          <button
            v-for="opt in currentQuestion.options"
            :key="opt.id"
            type="button"
            :class="['option-item', { selected: answers[currentQuestion.questionId] === opt.optionLabel }]"
            :aria-pressed="answers[currentQuestion.questionId] === opt.optionLabel"
            @click="answers[currentQuestion.questionId] = opt.optionLabel"
          >
            <span class="opt-label">{{ opt.optionLabel }}</span
            ><span>{{ opt.content }}</span>
          </button>
        </div>

        <div v-else-if="currentQuestion.questionType === 'MULTIPLE_CHOICE'" class="option-list">
          <button
            v-for="opt in currentQuestion.options"
            :key="opt.id"
            type="button"
            :class="['option-item', { selected: isMultiSelected(currentQuestion.questionId, opt.optionLabel) }]"
            :aria-pressed="isMultiSelected(currentQuestion.questionId, opt.optionLabel)"
            @click="toggleMulti(currentQuestion.questionId, opt.optionLabel)"
          >
            <span aria-hidden="true" class="multi-check">
              {{ isMultiSelected(currentQuestion.questionId, opt.optionLabel) ? '✓' : '' }}
            </span>
            <span class="opt-label">{{ opt.optionLabel }}</span
            ><span>{{ opt.content }}</span>
          </button>
        </div>

        <div v-else-if="currentQuestion.questionType === 'TRUE_FALSE'" class="option-list tf-list">
          <button
            type="button"
            :class="['option-item', { selected: answers[currentQuestion.questionId] === 'TRUE' }]"
            :aria-pressed="answers[currentQuestion.questionId] === 'TRUE'"
            @click="answers[currentQuestion.questionId] = 'TRUE'"
          >
            正确
          </button>
          <button
            type="button"
            :class="['option-item', { selected: answers[currentQuestion.questionId] === 'FALSE' }]"
            :aria-pressed="answers[currentQuestion.questionId] === 'FALSE'"
            @click="answers[currentQuestion.questionId] = 'FALSE'"
          >
            错误
          </button>
        </div>

        <div v-else>
          <el-input
            v-model="answers[currentQuestion.questionId]"
            type="textarea"
            :rows="3"
            :aria-label="`${currentQuestion.displayNumber || `第 ${currentIndex + 1} 题`}答案`"
            placeholder="请输入答案"
          />
        </div>

        <div class="nav-btns">
          <el-button @click="currentIndex--" :disabled="currentIndex === 0">上一题</el-button>
          <el-button v-if="currentIndex < questions.length - 1" type="primary" @click="currentIndex++">
            下一题
          </el-button>
          <el-button v-else type="danger" :loading="submitted" :disabled="submitted" @click="handleSubmit">
            提交试卷
          </el-button>
        </div>
      </article>

      <div class="answer-sheet">
        <h4 class="sheet-heading">答题卡</h4>
        <div class="sheet-legend">
          <span><i class="legend-dot is-current"></i>当前</span>
          <span><i class="legend-dot is-answered"></i>已答</span>
        </div>
        <div class="sheet-grid">
          <button
            v-for="(q, idx) in questions"
            :key="q.questionId"
            type="button"
            :class="['sheet-item', { answered: answers[q.questionId], current: idx === currentIndex }]"
            :title="q.displayNumber || `第 ${idx + 1} 题`"
            :aria-label="`前往${q.displayNumber || `第 ${idx + 1} 题`}${answers[q.questionId] ? '，已作答' : '，未作答'}`"
            :aria-current="idx === currentIndex ? 'step' : undefined"
            @click="currentIndex = idx"
          >
            {{ q.displayNumber || idx + 1 }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Timer } from '@element-plus/icons-vue'
import { getExamSession, getPaperDetail, submitExam } from '@/api/exam'
import type { ExamQuestionItem } from '@/api/exam'
import LpProgress from '@/components/ui/LpProgress.vue'
import { useExamCountdown } from './useExamCountdown'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const questions = ref<ExamQuestionItem[]>([])
const currentIndex = ref(0)
const answers = ref<Record<number, string>>({})
const multiAnswers = ref<Record<number, Set<string>>>({})
const submitted = ref(false)
const recordId = ref(0)
const { remainSeconds, countdownText, configure: configureCountdown, start: startCountdown } = useExamCountdown({
  submitted,
  hasQuestions: () => questions.value.length > 0,
  onExpired: async () => {
    submitted.value = true
    ElMessage.warning('考试时间已结束，已返回考试列表')
    await router.replace({ name: 'ExamList', query: { tab: 'records' } })
  },
})

const currentQuestion = computed(() => questions.value[currentIndex.value] || null)

const answeredCount = computed(() => Object.values(answers.value).filter(Boolean).length)

const progressPercent = computed(() => {
  if (!questions.value.length) return 0
  return Math.round((answeredCount.value / questions.value.length) * 100)
})

const isMultiSelected = (qId: number, label: string) => multiAnswers.value[qId]?.has(label) || false
const toggleMulti = (qId: number, label: string) => {
  if (!multiAnswers.value[qId]) multiAnswers.value[qId] = new Set()
  const s = multiAnswers.value[qId]
  if (s.has(label)) {
    s.delete(label)
  } else {
    s.add(label)
  }
  answers.value[qId] = Array.from(s).sort().join(',')
}

const doSubmit = async () => {
  if (submitted.value) return
  submitted.value = true
  const answerList = questions.value.map((q) => ({
    questionId: q.questionId,
    userAnswer: answers.value[q.questionId] || '',
  }))
  try {
    const res = await submitExam({ examRecordId: recordId.value, answers: answerList })
    if (res.code === 0 && res.data) {
      router.replace({ name: 'ExamResult', params: { recordId: String(res.data.id) } })
    } else {
      if (remainSeconds.value === 0) {
        ElMessage.warning('考试时间已结束，已返回考试列表')
        await router.replace({ name: 'ExamList', query: { tab: 'records' } })
        return
      }
      ElMessage.error(res.message || '提交失败')
      submitted.value = false
    }
  } catch {
    if (remainSeconds.value === 0) {
      ElMessage.warning('考试时间已结束，已返回考试列表')
      await router.replace({ name: 'ExamList', query: { tab: 'records' } })
      return
    }
    ElMessage.error('提交失败')
    submitted.value = false
  }
}

const getTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选',
    MULTIPLE_CHOICE: '多选',
    TRUE_FALSE: '判断',
    FILL_BLANK: '填空',
    SHORT_ANSWER: '简答',
  }
  return map[type] || type
}

const handleSubmit = () => {
  ElMessageBox.confirm('确定提交试卷？提交后不可修改', '提交确认', { type: 'warning' })
    .then(() => doSubmit())
    .catch(() => {})
}

onMounted(async () => {
  recordId.value = Number(route.params.recordId)
  if (!Number.isInteger(recordId.value) || recordId.value <= 0) {
    ElMessage.error('考试记录无效')
    await router.replace({ name: 'ExamList' })
    loading.value = false
    return
  }

  try {
    const sessionRequestStartedAt = Date.now()
    const sessionRes = await getExamSession(recordId.value)
    if (sessionRes.code !== 0 || !sessionRes.data) {
      ElMessage.error(sessionRes.message || '恢复考试失败')
      await router.replace({ name: 'ExamList', query: { tab: 'records' } })
      return
    }

    const session = sessionRes.data
    if (session.status === 1 || session.status === 3) {
      await router.replace({ name: 'ExamResult', params: { recordId: String(recordId.value) } })
      return
    }
    if (session.status === 2) {
      ElMessage.warning('考试已超时，已返回考试列表')
      await router.replace({ name: 'ExamList', query: { tab: 'records' } })
      return
    }

    if (!configureCountdown(session.deadline || '', session.serverTime || '', sessionRequestStartedAt)) {
      ElMessage.error('考试时间信息无效，请返回列表重试')
      await router.replace({ name: 'ExamList', query: { tab: 'records' } })
      return
    }

    const paperRes = await getPaperDetail(session.examPaperId)
    if (paperRes.code !== 0 || !paperRes.data) {
      ElMessage.error(paperRes.message || '获取试卷详情失败')
      await router.replace({ name: 'ExamList', query: { tab: 'records' } })
      return
    }

    questions.value = paperRes.data.questions || []
    startCountdown()
  } catch {
    ElMessage.error('恢复考试失败')
    await router.replace({ name: 'ExamList', query: { tab: 'records' } })
  } finally {
    loading.value = false
  }
})

</script>

<style scoped>
.exam-take-container {
  padding: var(--lp-space-6) var(--lp-space-4);
  max-width: var(--lp-container-narrow);
  margin: 0 auto;
}

.take-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-5);
  margin-bottom: var(--lp-space-5);
  padding: var(--lp-space-4) var(--lp-space-5);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-sm);
}

.take-header-title {
  min-width: 0;
}

.take-heading {
  margin: var(--lp-space-1) 0 0;
  color: var(--lp-text);
  font-size: var(--lp-text-xl);
  line-height: var(--lp-leading-tight);
}

.take-header-progress {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-2);
  flex: 1;
  min-width: 140px;
}

.progress-text {
  font-weight: var(--lp-weight-semibold);
  font-size: var(--lp-text-base);
  font-variant-numeric: tabular-nums;
}

.answered-text {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  font-variant-numeric: tabular-nums;
}

.take-header-right {
  display: flex;
  align-items: center;
  gap: var(--lp-space-4);
  flex-shrink: 0;
}

.take-loading {
  height: 300px;
}

.question-area {
  display: flex;
  gap: var(--lp-space-5);
  align-items: flex-start;
}

.question-card {
  flex: 1;
  min-width: 0;
  padding: var(--lp-space-6);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.q-meta {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  margin-bottom: var(--lp-space-3);
}

.q-section {
  margin-bottom: var(--lp-space-2);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
}

.q-number {
  color: var(--lp-text);
  font-size: var(--lp-text-md);
}

.q-score {
  font-size: var(--lp-text-sm);
  color: var(--lp-text-muted);
}

.q-content {
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
  line-height: var(--lp-leading-relaxed);
  margin-bottom: var(--lp-space-5);
  white-space: pre-wrap;
}

.option-list {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-3);
  margin-bottom: var(--lp-space-5);
}

.option-item {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  width: 100%;
  min-height: 48px;
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

.option-item:focus-visible {
  outline: var(--lp-shadow-focus);
  outline-offset: 2px;
  border-color: var(--lp-primary);
}

.option-item.selected {
  border-color: var(--lp-primary);
  background: var(--lp-primary-soft);
}

.opt-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  font-weight: var(--lp-weight-bold);
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
  border-radius: var(--lp-radius-full);
}

.option-item.selected .opt-label {
  color: var(--lp-on-primary);
  background: var(--lp-primary);
}

.multi-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 20px;
  width: 20px;
  height: 20px;
  color: var(--lp-surface);
  background: var(--lp-surface);
  border: 1px solid var(--lp-border-strong);
  border-radius: var(--lp-radius-xs);
}

.option-item.selected .multi-check {
  color: var(--lp-surface);
  background: var(--lp-primary);
  border-color: var(--lp-primary);
}

.tf-list {
  flex-direction: row;
  gap: var(--lp-space-4);
}

.tf-list .option-item {
  flex: 1;
  justify-content: center;
  font-size: var(--lp-text-lg);
  font-weight: var(--lp-weight-semibold);
}

.nav-btns {
  display: flex;
  justify-content: center;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-5);
}

.answer-sheet {
  width: 200px;
  padding: var(--lp-space-4);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
  height: fit-content;
  position: sticky;
  top: var(--lp-space-5);
}

.sheet-heading {
  margin: 0 0 var(--lp-space-3);
  font-size: var(--lp-text-base);
}

.sheet-legend {
  display: flex;
  justify-content: space-between;
  margin-bottom: var(--lp-space-3);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}

.sheet-legend span {
  display: inline-flex;
  align-items: center;
  gap: var(--lp-space-1);
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: var(--lp-radius-full);
  background: var(--lp-surface-inset);
  border: 1px solid var(--lp-border-strong);
}

.legend-dot.is-answered {
  background: var(--lp-primary);
  border-color: var(--lp-primary);
}

.legend-dot.is-current {
  background: var(--lp-warning);
  border-color: var(--lp-warning);
}

.sheet-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: var(--lp-space-2);
}

.sheet-item {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  height: 44px;
  padding: 0 var(--lp-space-1);
  overflow: hidden;
  color: var(--lp-text);
  background: var(--lp-surface);
  border: 1px solid var(--lp-border-strong);
  border-radius: var(--lp-radius-xs);
  font: inherit;
  font-size: var(--lp-text-xs);
  white-space: nowrap;
  text-overflow: ellipsis;
  cursor: pointer;
  transition:
    background-color var(--lp-duration-fast) var(--lp-ease-out),
    color var(--lp-duration-fast) var(--lp-ease-out);
}

.sheet-item:hover {
  background: var(--lp-surface-soft);
}

.sheet-item:focus-visible {
  outline: var(--lp-shadow-focus);
  outline-offset: 2px;
}

.sheet-item.answered {
  background: var(--lp-primary);
  color: var(--lp-on-primary);
  border-color: var(--lp-primary);
}

.sheet-item.current {
  border-color: var(--lp-warning);
  box-shadow: 0 0 0 2px var(--lp-warning);
}

.countdown {
  display: inline-flex;
  align-items: center;
  gap: var(--lp-space-1);
  font-size: var(--lp-text-lg);
  font-weight: var(--lp-weight-semibold);
  font-variant-numeric: tabular-nums;
  color: var(--lp-primary);
}

.countdown-warn {
  color: var(--lp-danger);
  animation: blink 1s infinite;
}

@keyframes blink {
  50% {
    opacity: 0.5;
  }
}

@media (max-width: 720px) {
  .exam-take-container {
    padding: var(--lp-space-4);
  }

  .take-header {
    align-items: stretch;
    flex-direction: column;
    gap: var(--lp-space-3);
  }

  .take-header-progress {
    order: 2;
  }

  .take-header-right {
    justify-content: space-between;
  }

  .take-header-right .el-button {
    width: 100%;
    min-height: 44px;
  }

  .question-area {
    flex-direction: column;
  }

  .answer-sheet {
    position: static;
    width: auto;
    order: -1;
  }

  .sheet-grid {
    grid-template-columns: repeat(5, minmax(44px, 1fr));
  }

  .tf-list {
    gap: var(--lp-space-2);
  }

  .nav-btns {
    justify-content: stretch;
  }

  .nav-btns .el-button {
    flex: 1;
    min-height: 44px;
    margin-left: 0;
  }
}

@media (max-width: 420px) {
  .question-card {
    padding: var(--lp-space-4);
  }

  .sheet-grid {
    grid-template-columns: repeat(4, minmax(44px, 1fr));
  }

  .q-meta {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .tf-list {
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .countdown-warn {
    animation: none;
  }

  .option-item {
    transition: none;
  }
}
</style>
