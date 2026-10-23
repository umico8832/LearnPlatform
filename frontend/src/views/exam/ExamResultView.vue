<template>
  <div class="lt exam-result-container" data-appearance="dark">
    <div v-if="resultState === 'loading'" class="state" role="status" aria-busy="true">正在读取考试结果</div>
    <div v-else-if="resultState !== 'ready'" class="state" :role="resultState === 'error' ? 'alert' : 'status'">
      <h1>{{ stateTitle }}</h1>
      <p>{{ stateDescription }}</p>
      <LtButton v-if="resultState === 'error'" @click="loadResult">重试读取</LtButton>
    </div>
    <template v-else-if="result"
      ><section class="result-header" aria-labelledby="result-title">
        <h1 id="result-title" class="result-title">{{ result.examTitle }}</h1>
        <div class="score-main">
          <div class="score-circle" :aria-label="scoreAriaLabel">
            <span class="score-label">{{ scoreLabel }}</span>
            <div>
              <strong class="score-number">{{ displayScore }}</strong
              ><span v-if="result.score != null" class="score-total"> / {{ result.totalScore }}</span>
            </div>
          </div>
          <div v-if="scoreRate != null" class="score-rate" :aria-label="`${rateLabel} ${scoreRate}%`">
            <strong class="rate-value">{{ scoreRate }}%</strong><span class="rate-label">{{ rateLabel }}</span>
          </div>
        </div>
        <p v-if="isPendingReview" class="grading-alert" role="status">
          客观题已判分，主观题正在等待管理员按评分点复核；当前分数为暂定分。
        </p>
        <dl class="score-meta">
          <div>
            <dt>用时</dt>
            <dd>{{ timeUsed }}</dd>
          </div>
          <div>
            <dt>题数</dt>
            <dd>{{ answers.length }} 题</dd>
          </div>
          <div>
            <dt>需复习</dt>
            <dd>{{ wrongAnswers.length }} 题</dd>
          </div>
        </dl>
        <div v-if="isOfficialPaper" class="source-panel">
          <div class="source-heading">
            <strong>官方试卷 · {{ officialPaperTitle }}</strong
            ><span :data-tone="result.sourceVerified ? 'success' : 'warning'">{{
              result.sourceVerified ? '来源已核验' : '来源未核验'
            }}</span>
          </div>
          <p v-if="result.sourceReference">来源：{{ result.sourceReference }}</p>
        </div>
        <section
          v-if="result.submissionReward"
          class="exam-reward"
          data-testid="gamification-exam-reward"
          aria-label="学习记录"
        >
          <strong>学习记录</strong><span>+{{ result.submissionReward.awardedXp }} 经验</span
          ><span v-if="result.submissionReward.leveledUp">升级至 Lv.{{ result.submissionReward.levelAfter }}</span
          ><small v-if="result.submissionReward.newAchievements.length"
            >解锁：{{ result.submissionReward.newAchievements.map((item) => item.name).join('、') }}</small
          >
        </section>
        <div v-if="result.courseId" class="score-actions">
          <LtButton @click="goToCourseOverview">查看课程总览</LtButton>
        </div>
      </section>
      <section class="answers-section" aria-labelledby="answer-detail-title">
        <div class="answers-heading">
          <div>
            <h2 id="answer-detail-title">答题详情</h2>
            <p class="muted">{{ answers.length }} 题 · {{ wrongAnswers.length }} 题需复习</p>
          </div>
          <nav v-if="answers.length > 1" class="answer-navigation" aria-label="答题导航">
            <a
              v-for="(answer, idx) in answers"
              :key="answer.questionId"
              :href="`#answer-${answer.questionId}`"
              :aria-label="`第 ${idx + 1} 题，${answerStatusLabel(answer)}`"
            >
              <span class="navigation-dot" :data-tone="navigationTone(answer)" aria-hidden="true" />{{
                answerLabel(answer, idx)
              }}
            </a>
          </nav>
        </div>
        <article
          v-for="(answer, idx) in answers"
          :id="`answer-${answer.questionId}`"
          :key="answer.questionId"
          class="answer-item"
          tabindex="-1"
        >
          <div class="answer-card">
            <p v-if="answer.sectionTitle" class="muted">{{ answer.sectionTitle }}</p>
            <div class="answer-header">
              <h3 :id="`answer-heading-${answer.questionId}`">题目 {{ answerLabel(answer, idx) }}</h3>
              <span>{{ getTypeLabel(answer.questionType) }}</span
              ><span>满分 {{ answer.fullScore }} 分</span
              ><span class="result-tag" :data-tone="answerStatusType(answer)"
                ><span class="verdict-dot" aria-hidden="true" />{{ answerStatusLabel(answer) }}</span
              ><span>{{ answer.score == null ? '待评分' : `得 ${answer.score} 分` }}</span>
            </div>
            <div class="answer-content"><MarkdownRenderer :content="answer.content" /></div>
          </div>
          <div class="answer-detail">
            <div class="answer-chips">
              <p
                class="answer-chip"
                :data-tone="answer.isCorrect === 1 ? 'success' : answer.isCorrect === 0 ? 'warning' : 'neutral'"
              >
                <span>我的答案</span><strong>{{ answer.userAnswer || '未作答' }}</strong>
              </p>
              <p
                v-if="answer.gradingStatus !== 'PENDING' && answer.isCorrect !== 1"
                class="answer-chip"
                data-tone="success"
              >
                <span>正确答案</span><strong>{{ answer.correctAnswer }}</strong>
              </p>
            </div>
            <div v-if="answer.gradingStatus !== 'PENDING' && answer.analysis" class="analysis">
              <h4>解析</h4>
              <MarkdownRenderer :content="answer.analysis" />
            </div>
            <div v-if="answer.gradingStatus !== 'PENDING' && answer.reviewComment" class="analysis">
              <h4>批阅意见</h4>
              <MarkdownRenderer :content="answer.reviewComment" />
            </div>
          </div>
          <div
            v-if="answer.gradingStatus !== 'PENDING' && answer.isCorrect === 0 && result.courseId"
            class="answer-actions"
          >
            <LtButton :aria-label="`复习错题 ${answerLabel(answer, idx)}`" @click="reviewWrongAnswer(answer.questionId)"
              >复习此错题</LtButton
            >
          </div>
        </article>
      </section>
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getExamResult, type ExamAnswerVO, type ExamRecordVO } from '@/api/exam'
import MarkdownRenderer from '@/views/question/QuestionProse.vue'
import { LtButton } from '@/components/living'
import { errorMessage } from '@/utils/errors'
import { getAuthSessionVersion, isAuthenticated, onAuthSessionChange } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const result = ref<ExamRecordVO | null>(null)
const resultState = ref<'loading' | 'ready' | 'error' | 'empty'>('loading')
const failure = ref('')
let requestVersion = 0
let alive = true

const hasValidRecordId = computed(() => {
  const recordId = Number(route.params.recordId)
  return Number.isInteger(recordId) && recordId > 0
})
const stateTitle = computed(() => {
  if (resultState.value === 'error') return '暂时无法读取考试结果'
  if (!hasValidRecordId.value) return '考试记录无效'
  if (resultState.value === 'empty') return '考试结果不存在'
  return ''
})
const stateDescription = computed(() => {
  if (resultState.value === 'error') return failure.value || '请检查网络后重试。'
  if (!hasValidRecordId.value) return '请从考试记录中重新打开结果。'
  if (resultState.value === 'empty') return '该考试记录可能已失效或被删除。'
  return ''
})
const answers = computed(() => result.value?.answers || [])
const wrongAnswers = computed(() =>
  answers.value.filter((answer) => answer.gradingStatus !== 'PENDING' && answer.isCorrect === 0),
)
function answerStatusLabel(answer: ExamAnswerVO) {
  if (answer.gradingStatus === 'PENDING') return '待人工批阅'
  if (answer.gradingStatus === 'UNANSWERED') return '未作答'
  return answer.isCorrect === 1 ? '正确' : '错误'
}
function navigationTone(answer: ExamAnswerVO) {
  if (answer.gradingStatus === 'PENDING' || answer.gradingStatus === 'UNANSWERED' || answer.isCorrect == null)
    return 'neutral'
  return answer.isCorrect === 1 ? 'success' : 'warning'
}
function answerStatusType(answer: ExamAnswerVO) {
  if (answer.gradingStatus === 'PENDING') return 'warning'
  if (answer.gradingStatus === 'UNANSWERED') return 'info'
  return answer.isCorrect === 1 ? 'success' : 'warning'
}
const isPendingReview = computed(() => result.value?.status === 3)
const isOfficialPaper = computed(() => result.value?.paperType === 'OFFICIAL_EXAM')
const scoreLabel = computed(() =>
  result.value?.score == null ? '考试得分' : isPendingReview.value ? '暂定得分' : '考试得分',
)
const displayScore = computed(() => (result.value?.score == null ? '待评分' : result.value.score))
const scoreRate = computed<number | null>(() => {
  if (
    !result.value ||
    result.value.score == null ||
    !Number.isFinite(result.value.totalScore) ||
    result.value.totalScore <= 0
  )
    return null
  return Math.round((result.value.score / result.value.totalScore) * 100)
})
const rateLabel = computed(() => (isPendingReview.value ? '暂定得分率' : '得分率'))
const scoreAriaLabel = computed(() => {
  if (!result.value || result.value.score == null) return '考试得分待评分'
  const rate = scoreRate.value == null ? '' : `，${rateLabel.value} ${scoreRate.value}%`
  return `${scoreLabel.value} ${result.value.score} / ${result.value.totalScore}${rate}`
})
const officialPaperTitle = computed(() => {
  if (!result.value) return ''
  const metadata = [result.value.examYear, result.value.examName].filter(Boolean)
  return metadata.length > 0 ? metadata.join(' · ') : '官方考试试卷'
})

function isCurrent(version: number, authSession: number, recordId: number) {
  return (
    alive &&
    version === requestVersion &&
    authSession === getAuthSessionVersion() &&
    recordId === Number(route.params.recordId)
  )
}

async function loadResult() {
  const recordId = Number(route.params.recordId)
  const version = ++requestVersion
  const authSession = getAuthSessionVersion()
  result.value = null
  failure.value = ''
  if (!Number.isInteger(recordId) || recordId <= 0) {
    resultState.value = 'empty'
    failure.value = '考试记录编号无效。'
    return
  }
  resultState.value = 'loading'
  try {
    const response = await getExamResult(recordId, { errorDisplay: 'inline' })
    if (!isCurrent(version, authSession, recordId)) return
    if (response.code === 0 && response.data) {
      result.value = response.data
      resultState.value = 'ready'
    } else if (response.code === 0) {
      resultState.value = 'empty'
    } else {
      failure.value = response.message || '请稍后重试。'
      resultState.value = 'error'
    }
  } catch (error) {
    if (!isCurrent(version, authSession, recordId)) return
    failure.value = errorMessage(error, '请稍后重试。')
    resultState.value = 'error'
  }
}

const timeUsed = computed(() => {
  if (!result.value?.startTime || !result.value.endTime) return '-'
  const start = new Date(result.value.startTime).getTime()
  const end = new Date(result.value.endTime).getTime()
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return '-'
  const diff = Math.floor((end - start) / 1000)
  const minutes = Math.floor(diff / 60)
  const seconds = diff % 60
  return minutes > 0 ? `${minutes} 分 ${seconds} 秒` : `${seconds} 秒`
})

function getTypeLabel(type: string) {
  const labels: Record<string, string> = {
    SINGLE_CHOICE: '单选',
    MULTIPLE_CHOICE: '多选',
    TRUE_FALSE: '判断',
    FILL_BLANK: '填空',
    SHORT_ANSWER: '简答',
  }
  return labels[type] || type
}
function answerLabel(answer: ExamAnswerVO, index: number) {
  return answer.displayNumber || `${index + 1}.`
}
function goToCourseOverview() {
  if (result.value?.courseId) router.push({ name: 'CourseOverview', params: { id: String(result.value.courseId) } })
}
function reviewWrongAnswer(questionId: number) {
  if (!result.value?.courseId) return
  router.push({
    name: 'WrongQuestions',
    query: { courseId: String(result.value.courseId), questionId: String(questionId) },
  })
}

watch(() => route.params.recordId, loadResult, { immediate: true })
const unsubscribeAuth = onAuthSessionChange(() => {
  requestVersion++
  result.value = null
  failure.value = ''
  if (isAuthenticated()) void loadResult()
  else resultState.value = 'empty'
})
onBeforeUnmount(() => {
  alive = false
  requestVersion++
  unsubscribeAuth()
})
</script>
<style scoped>
.exam-result-container {
  display: grid;
  gap: var(--lt-space-8);
  color: var(--lt-label);
}
h1 {
  margin: 0;
  font-size: var(--lt-text-title1);
  line-height: var(--lt-leading-tight);
  overflow-wrap: anywhere;
}
h2 {
  margin: 0;
  font-size: var(--lt-text-title2);
}
h3,
h4 {
  margin: 0;
  font-size: var(--lt-text-body);
}
p {
  margin: 0;
  line-height: var(--lt-leading-reading);
}
.muted,
dt {
  color: var(--lt-label-2);
}
.result-header {
  padding: var(--lt-space-6);
  background: var(--lt-surface);
  border-radius: var(--lt-radius-xl);
  box-shadow: var(--lt-shadow-card);
  display: grid;
  gap: var(--lt-space-5);
}
.score-main {
  display: flex;
  align-items: center;
  gap: var(--lt-space-9);
}
.score-label,
.rate-label {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.score-circle,
.score-rate {
  display: grid;
  gap: var(--lt-space-2);
}
.score-number {
  font: 600 var(--lt-text-display)/var(--lt-leading-tight) var(--lt-font-rounded);
}
.score-total {
  color: var(--lt-label-2);
  font-family: var(--lt-font-rounded);
}
.rate-value {
  font: 600 var(--lt-text-title2)/var(--lt-leading-tight) var(--lt-font-rounded);
}
.grading-alert {
  padding: var(--lt-space-4);
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
  border-radius: var(--lt-radius-md);
}
.score-meta {
  display: flex;
  gap: var(--lt-space-8);
  margin: 0;
}
dd {
  margin: var(--lt-space-2) 0 0;
  font-family: var(--lt-font-rounded);
}
.source-panel {
  display: grid;
  gap: var(--lt-space-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.source-heading,
.exam-reward {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--lt-space-3);
}
[data-tone='success'] {
  color: var(--lt-green-text);
}
[data-tone='warning'] {
  color: var(--lt-orange-text);
}
.exam-reward {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.answers-section {
  display: grid;
  gap: var(--lt-space-6);
}
.answers-heading {
  display: grid;
  gap: var(--lt-space-4);
}
.answer-navigation {
  display: flex;
  gap: var(--lt-space-2);
  flex-wrap: wrap;
}
.answer-navigation a {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--lt-space-2);
  padding: var(--lt-space-2);
  min-width: var(--lt-control-md);
  background: var(--lt-fill-2);
  border-radius: var(--lt-radius-sm);
  color: var(--lt-label);
  text-decoration: none;
}
.navigation-dot {
  flex: none;
  width: var(--lt-space-2);
  height: var(--lt-space-2);
  border-radius: 50%;
  background: var(--lt-label-3);
}
.navigation-dot[data-tone='success'] {
  background: var(--lt-green);
}
.navigation-dot[data-tone='warning'] {
  background: var(--lt-orange);
}
.answer-item {
  display: grid;
  gap: var(--lt-space-4);
  scroll-margin-top: var(--lt-space-10);
  min-width: 0;
}
.answer-card {
  padding: var(--lt-space-6);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-card);
}
.answer-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--lt-space-3);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.answer-header h3 {
  color: var(--lt-label);
}
.result-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-2);
}
.verdict-dot {
  width: var(--lt-space-2);
  height: var(--lt-space-2);
  border-radius: var(--lt-radius-pill);
  background: currentColor;
}
.answer-content {
  margin-top: var(--lt-space-5);
}
.answer-detail {
  display: grid;
  gap: var(--lt-space-5);
}
.answer-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-3);
}
.answer-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-2);
  padding: var(--lt-space-1) var(--lt-space-2) var(--lt-space-1) var(--lt-space-3);
  border-radius: var(--lt-radius-pill);
  font-size: var(--lt-text-footnote);
  max-width: 100%;
  background: var(--lt-fill-2);
}
.answer-chip strong {
  min-width: var(--lt-space-6);
  padding: var(--lt-space-1) var(--lt-space-2);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill);
  color: var(--lt-label);
  font-family: var(--lt-font-rounded);
  overflow-wrap: anywhere;
}
.answer-chip[data-tone='success'] {
  background: var(--lt-green-soft);
}
.answer-chip[data-tone='warning'] {
  background: var(--lt-orange-soft);
}
.answer-chip[data-tone='success'] strong {
  background: var(--lt-green);
  color: var(--lt-on-tint);
}
.answer-chip[data-tone='warning'] strong {
  background: var(--lt-orange);
  color: var(--lt-on-tint);
}
.analysis {
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}
.analysis h4 {
  margin-bottom: var(--lt-space-2);
}
.state {
  padding: var(--lt-space-8);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface);
  text-align: center;
}
.state p {
  margin-block: var(--lt-space-4);
  color: var(--lt-label-2);
}
</style>
