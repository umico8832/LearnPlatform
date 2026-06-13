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
        <el-button type="danger" size="small" @click="handleSubmit">提交试卷</el-button>
      </div>
    </div>

    <div v-if="loading" v-loading="true" style="height: 300px"></div>

    <div v-else-if="currentQuestion" class="question-area">
      <el-card shadow="hover">
        <div class="q-meta">
          <el-tag size="small">{{ getTypeLabel(currentQuestion.questionType) }}</el-tag>
          <span class="q-score">分值：{{ currentQuestion.score }} 分</span>
        </div>
        <div class="q-content">{{ currentQuestion.content }}</div>

        <div v-if="currentQuestion.questionType === 'SINGLE_CHOICE'" class="option-list">
          <div v-for="opt in currentQuestion.options" :key="opt.id"
            :class="['option-item', { selected: answers[currentQuestion.questionId] === opt.optionLabel }]"
            @click="answers[currentQuestion.questionId] = opt.optionLabel">
            <span class="opt-label">{{ opt.optionLabel }}</span><span>{{ opt.content }}</span>
          </div>
        </div>

        <div v-else-if="currentQuestion.questionType === 'MULTIPLE_CHOICE'" class="option-list">
          <div v-for="opt in currentQuestion.options" :key="opt.id"
            :class="['option-item', { selected: isMultiSelected(currentQuestion.questionId, opt.optionLabel) }]"
            @click="toggleMulti(currentQuestion.questionId, opt.optionLabel)">
            <el-checkbox :model-value="isMultiSelected(currentQuestion.questionId, opt.optionLabel)" @click.stop />
            <span class="opt-label">{{ opt.optionLabel }}</span><span>{{ opt.content }}</span>
          </div>
        </div>

        <div v-else-if="currentQuestion.questionType === 'TRUE_FALSE'" class="option-list tf-list">
          <div :class="['option-item', { selected: answers[currentQuestion.questionId] === 'TRUE' }]"
            @click="answers[currentQuestion.questionId] = 'TRUE'">✓ 正确</div>
          <div :class="['option-item', { selected: answers[currentQuestion.questionId] === 'FALSE' }]"
            @click="answers[currentQuestion.questionId] = 'FALSE'">✗ 错误</div>
        </div>

        <div v-else>
          <el-input v-model="answers[currentQuestion.questionId]" type="textarea" :rows="3" placeholder="请输入答案" />
        </div>

        <div class="nav-btns">
          <el-button @click="currentIndex--" :disabled="currentIndex === 0">上一题</el-button>
          <el-button v-if="currentIndex < questions.length - 1" type="primary" @click="currentIndex++">下一题</el-button>
          <el-button v-else type="danger" @click="handleSubmit">提交试卷</el-button>
        </div>
      </el-card>

      <div class="answer-sheet">
        <h4>答题卡</h4>
        <div class="sheet-grid">
          <div v-for="(q, idx) in questions" :key="q.questionId"
            :class="['sheet-item', { answered: answers[q.questionId], current: idx === currentIndex }]"
            @click="currentIndex = idx">{{ idx + 1 }}</div>
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
import { submitExam } from '@/api/exam'
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
  s.has(label) ? s.delete(label) : s.add(label)
  answers.value[qId] = Array.from(s).sort().join(',')
}

onMounted(() => {
  recordId.value = Number(route.params.recordId)
  const stored = sessionStorage.getItem('exam_session_' + recordId.value)
  if (stored) {
    const data = JSON.parse(stored)
    questions.value = data.questions || []
    // 初始化倒计时
    const duration = data.duration || 60
    remainSeconds.value = duration * 60
    countdownTimer = setInterval(() => {
      if (remainSeconds.value > 0) {
        remainSeconds.value--
      } else {
        if (countdownTimer) clearInterval(countdownTimer)
        ElMessage.warning('考试时间到，自动提交')
        doSubmit()
      }
    }, 1000)
  } else {
    ElMessage.warning('请从考试列表开始考试')
    router.replace('/exams')
    return
  }
  loading.value = false
})

onBeforeUnmount(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

const handleSubmit = () => {
  ElMessageBox.confirm('确定提交试卷？提交后不可修改', '提交确认', { type: 'warning' })
    .then(() => doSubmit())
    .catch(() => {})
}

const doSubmit = async () => {
  if (submitted.value) return
  submitted.value = true
  const answerList = questions.value.map(q => ({
    questionId: q.questionId,
    userAnswer: answers.value[q.questionId] || ''
  }))
  try {
    const res = await submitExam({ examRecordId: recordId.value, answers: answerList })
    if (res.code === 0 && res.data) {
      sessionStorage.removeItem('exam_session_' + recordId.value)
      sessionStorage.setItem('exam_result_' + res.data.id, JSON.stringify(res.data))
      router.replace({ name: 'ExamResult', params: { recordId: String(res.data.id) } })
    } else {
      ElMessage.error(res.message || '提交失败')
      submitted.value = false
    }
  } catch (e) {
    ElMessage.error('提交失败')
    submitted.value = false
  }
}

const getTypeLabel = (type: string) => {
  const map: Record<string, string> = { SINGLE_CHOICE: '单选', MULTIPLE_CHOICE: '多选', TRUE_FALSE: '判断', FILL_BLANK: '填空', SHORT_ANSWER: '简答' }
  return map[type] || type
}
</script>

<style scoped>
.exam-take-container { padding: 24px; max-width: 900px; margin: 0 auto; }
.take-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; padding: 16px; background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,.08); }
.take-header h3 { margin: 0; font-size: 16px; }
.progress-text { font-weight: 600; font-size: 16px; }
.question-area { display: flex; gap: 20px; }
.question-area > .el-card { flex: 1; }
.q-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.q-score { font-size: 13px; color: #909399; }
.q-content { font-size: 16px; line-height: 1.8; margin-bottom: 20px; white-space: pre-wrap; }
.option-list { display: flex; flex-direction: column; gap: 10px; margin-bottom: 20px; }
.option-item { display: flex; align-items: center; gap: 10px; padding: 12px 16px; border: 2px solid #ebeef5; border-radius: 8px; cursor: pointer; transition: all .2s; }
.option-item:hover { border-color: #c0c4cc; background: #f5f7fa; }
.option-item.selected { border-color: #409eff; background: #ecf5ff; }
.opt-label { font-weight: 700; color: #409eff; min-width: 20px; }
.tf-list { flex-direction: row; gap: 20px; }
.tf-list .option-item { flex: 1; justify-content: center; font-size: 16px; font-weight: 600; }
.nav-btns { display: flex; justify-content: center; gap: 12px; margin-top: 20px; }
.answer-sheet { width: 200px; padding: 16px; background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,.08); height: fit-content; position: sticky; top: 20px; }
.answer-sheet h4 { margin: 0 0 12px; font-size: 14px; }
.sheet-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 6px; }
.sheet-item { display: flex; align-items: center; justify-content: center; width: 32px; height: 32px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 12px; cursor: pointer; }
.sheet-item.answered { background: #409eff; color: #fff; border-color: #409eff; }
.sheet-item.current { border-color: #e6a23c; box-shadow: 0 0 0 2px #e6a23c; }
.countdown { display: inline-flex; align-items: center; gap: 4px; margin-left: 16px; font-size: 16px; font-weight: 600; color: #409eff; }
.countdown-warn { color: #f56c6c; animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0.5; } }
</style>