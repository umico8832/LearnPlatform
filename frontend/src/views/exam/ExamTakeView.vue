<template>
  <div class="exam-take-container">
    <div class="take-header">
      <div class="header-left">
        <h3>考试进行中</h3>
      </div>
      <div class="header-center">
        <span class="progress-text">{{ currentIndex + 1 }} / {{ questions.length }}</span>
        <span :class="['countdown', { 'countdown-warn': remainSeconds < 300 }]">
          <el-icon><Timer /></el-icon> {{ countdownText }}
        </span>
      </div>
      <div class="header-right">
        <el-button type="danger" size="small" :loading="submitted" :disabled="submitted" @click="handleSubmit">
          提交试卷
        </el-button>
      </div>
    </div>

    <div v-if="loading" v-loading="true" style="height: 300px"></div>

    <div v-else-if="currentQuestion" class="question-area">
      <el-card shadow="hover">
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
          <el-button v-if="currentIndex < questions.length - 1" type="primary" @click="currentIndex++"
            >下一题</el-button
          >
          <el-button v-else type="danger" :loading="submitted" :disabled="submitted" @click="handleSubmit">
            提交试卷
          </el-button>
        </div>
      </el-card>

      <div class="answer-sheet">
        <h4>答题卡</h4>
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
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Timer } from '@element-plus/icons-vue'
import { getExamSession, getPaperDetail, submitExam } from '@/api/exam'
import type { ExamQuestionItem } from '@/api/exam'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const questions = ref<ExamQuestionItem[]>([])
const currentIndex = ref(0)
const answers = ref<Record<number, string>>({})
const multiAnswers = ref<Record<number, Set<string>>>({})
const submitted = ref(false)
const recordId = ref(0)
const remainSeconds = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null
let deadlineMs = 0
let serverOffsetMs = 0

const currentQuestion = computed(() => questions.value[currentIndex.value] || null)

const countdownText = computed(() => {
  const m = Math.floor(remainSeconds.value / 60)
  const s = remainSeconds.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
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

const updateRemainingTime = () => {
  const nextSeconds = Math.max(0, Math.ceil((deadlineMs - (Date.now() + serverOffsetMs)) / 1000))
  remainSeconds.value = nextSeconds
  if (nextSeconds > 0 || submitted.value) return

  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  if (questions.value.length === 0) return
  submitted.value = true
  ElMessage.warning('考试时间已结束，已返回考试列表')
  void router.replace({ name: 'ExamList', query: { tab: 'records' } })
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

    const parsedDeadline = Date.parse(session.deadline || '')
    const parsedServerTime = Date.parse(session.serverTime || '')
    if (!Number.isFinite(parsedDeadline) || !Number.isFinite(parsedServerTime)) {
      ElMessage.error('考试时间信息无效，请返回列表重试')
      await router.replace({ name: 'ExamList', query: { tab: 'records' } })
      return
    }

    deadlineMs = parsedDeadline
    // Use the request start as a conservative lower bound. The server timestamp is
    // captured while handling this request, so subtracting the receive time would
    // incorrectly add response latency back to the remaining exam time.
    serverOffsetMs = parsedServerTime - sessionRequestStartedAt

    const paperRes = await getPaperDetail(session.examPaperId)
    if (paperRes.code !== 0 || !paperRes.data) {
      ElMessage.error(paperRes.message || '获取试卷详情失败')
      await router.replace({ name: 'ExamList', query: { tab: 'records' } })
      return
    }

    questions.value = paperRes.data.questions || []
    updateRemainingTime()
    if (remainSeconds.value > 0) countdownTimer = setInterval(updateRemainingTime, 1000)
  } catch {
    ElMessage.error('恢复考试失败')
    await router.replace({ name: 'ExamList', query: { tab: 'records' } })
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<style scoped>
.exam-take-container {
  padding: 24px;
  max-width: 900px;
  margin: 0 auto;
}
.take-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  padding: 16px;
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  box-shadow: var(--lp-shadow-sm);
}
.take-header h3 {
  margin: 0;
  color: var(--lp-text);
  font-size: 16px;
}
.progress-text {
  font-weight: 600;
  font-size: 16px;
}
.question-area {
  display: flex;
  gap: 20px;
}
.question-area > .el-card {
  flex: 1;
}
.q-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.q-section {
  margin-bottom: 8px;
  color: var(--lp-text-secondary);
  font-size: 13px;
  font-weight: 600;
}
.q-number {
  color: var(--lp-text);
  font-size: 15px;
}
.q-score {
  font-size: 13px;
  color: var(--lp-text-muted);
}
.q-content {
  color: var(--lp-text);
  font-size: 16px;
  line-height: 1.8;
  margin-bottom: 20px;
  white-space: pre-wrap;
}
.option-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 20px;
}
.option-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 48px;
  padding: 12px 16px;
  color: var(--lp-text);
  text-align: left;
  font: inherit;
  background: var(--lp-surface);
  border: 2px solid var(--lp-border);
  border-radius: var(--lp-radius);
  cursor: pointer;
  transition:
    background-color 0.2s,
    border-color 0.2s;
}
.option-item:hover {
  border-color: var(--lp-border-strong);
  background: var(--lp-surface-soft);
}
.option-item:focus-visible {
  outline: 3px solid var(--lp-primary-soft);
  outline-offset: 2px;
  border-color: var(--lp-primary);
}
.option-item.selected {
  border-color: var(--lp-primary);
  background: var(--lp-primary-soft);
}
.opt-label {
  font-weight: 700;
  color: var(--lp-primary);
  min-width: 20px;
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
  border-radius: 4px;
}
.option-item.selected .multi-check {
  color: var(--lp-surface);
  background: var(--lp-primary);
  border-color: var(--lp-primary);
}
.tf-list {
  flex-direction: row;
  gap: 20px;
}
.tf-list .option-item {
  flex: 1;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
}
.nav-btns {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
}
.answer-sheet {
  width: 200px;
  padding: 16px;
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  box-shadow: var(--lp-shadow-sm);
  height: fit-content;
  position: sticky;
  top: 20px;
}
.answer-sheet h4 {
  margin: 0 0 12px;
  font-size: 14px;
}
.sheet-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 6px;
}
.sheet-item {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  height: 44px;
  padding: 0 4px;
  overflow: hidden;
  color: var(--lp-text);
  background: var(--lp-surface);
  border: 1px solid var(--lp-border-strong);
  border-radius: 4px;
  font: inherit;
  font-size: 11px;
  white-space: nowrap;
  text-overflow: ellipsis;
  cursor: pointer;
}
.sheet-item:hover {
  background: var(--lp-surface-soft);
}
.sheet-item:focus-visible {
  outline: 3px solid var(--lp-primary-soft);
  outline-offset: 2px;
}
.sheet-item.answered {
  background: var(--lp-primary);
  color: var(--lp-surface);
  border-color: var(--lp-primary);
}
.sheet-item.current {
  border-color: var(--lp-warning);
  box-shadow: 0 0 0 2px var(--lp-warning);
}
.countdown {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 16px;
  font-size: 16px;
  font-weight: 600;
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
    padding: 16px;
  }
  .take-header {
    align-items: stretch;
    flex-direction: column;
  }
  .header-center {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  .countdown {
    margin-left: 0;
  }
  .header-right .el-button {
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
  .sheet-item {
    min-width: 44px;
    height: 44px;
  }
  .tf-list {
    gap: 10px;
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
