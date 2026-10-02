<template>
  <div class="exam-result-container">
    <LpStatePanel
      :state="resultState"
      :title="stateTitle"
      :description="stateDescription"
      loading-label="正在读取考试结果"
      retry-label="重试读取"
      :retrying="resultState === 'loading'"
      @retry="loadResult"
    >
      <template #actions>
        <el-button type="primary" @click="router.push({ name: 'ExamList' })">返回考试列表</el-button>
      </template>

      <template v-if="result">
        <section class="result-header" aria-labelledby="result-title">
          <article class="score-card">
            <h1 id="result-title" class="result-title">{{ result.examTitle }}</h1>

            <div class="score-main" aria-live="polite">
              <div class="score-circle" :aria-label="scoreAriaLabel">
                <span class="score-label">{{ scoreLabel }}</span>
                <span class="score-number">{{ displayScore }}</span>
                <span v-if="result.score != null" class="score-total">/ {{ result.totalScore }}</span>
              </div>
              <div v-if="scoreRate != null" class="score-rate" :aria-label="`${rateLabel} ${scoreRate}%`">
                <span class="rate-value">{{ scoreRate }}%</span>
                <span class="rate-label">{{ rateLabel }}</span>
              </div>
            </div>

            <el-alert
              v-if="isPendingReview"
              title="客观题已判分，主观题正在等待管理员按评分点复核；当前分数为暂定分。"
              type="warning"
              :closable="false"
              show-icon
              class="grading-alert"
            />

            <dl class="score-meta">
              <div class="meta-item">
                <dt class="meta-label">用时</dt>
                <dd class="meta-value">{{ timeUsed }}</dd>
              </div>
              <div class="meta-item">
                <dt class="meta-label">题数</dt>
                <dd class="meta-value">{{ answers.length }} 题</dd>
              </div>
              <div class="meta-item">
                <dt class="meta-label">需复习</dt>
                <dd class="meta-value">{{ wrongAnswers.length }} 题</dd>
              </div>
            </dl>

            <div v-if="isOfficialPaper" class="source-panel">
              <div class="source-heading">
                <strong>官方试卷 · {{ officialPaperTitle }}</strong>
                <el-tag :type="result.sourceVerified ? 'success' : 'warning'" size="small">
                  {{ result.sourceVerified ? '来源已核验' : '来源未核验' }}
                </el-tag>
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

            <div class="score-actions">
              <el-button @click="router.push({ name: 'ExamList', query: { tab: 'records' } })">返回考试列表</el-button>
              <el-button v-if="result.courseId" type="primary" @click="goToCourseOverview">返回课程总览</el-button>
            </div>
          </article>
        </section>

        <section class="answers-section" aria-labelledby="answer-detail-title">
          <div class="answers-heading">
            <div>
              <h2 id="answer-detail-title" class="answers-title">答题详情</h2>
              <p class="answers-summary">{{ answers.length }} 题 · {{ wrongAnswers.length }} 题需复习</p>
            </div>
            <nav v-if="answers.length > 1" class="answer-navigation" aria-label="答题导航">
              <a v-for="(answer, idx) in answers" :key="answer.questionId" :href="`#answer-${answer.questionId}`">
                {{ answerLabel(answer, idx) }}
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
              <div v-if="answer.sectionTitle" class="answer-section-title">{{ answer.sectionTitle }}</div>
              <div class="answer-header">
                <h3 :id="`answer-heading-${answer.questionId}`" class="q-index">题目 {{ answerLabel(answer, idx) }}</h3>
                <el-tag size="small">{{ getTypeLabel(answer.questionType) }}</el-tag>
                <span class="q-score-tag">满分 {{ answer.fullScore }} 分</span>
                <el-tag
                  :type="answer.gradingStatus === 'PENDING' ? 'warning' : answer.isCorrect === 1 ? 'success' : 'danger'"
                  size="small"
                  class="result-tag"
                >
                  {{ answer.gradingStatus === 'PENDING' ? '待人工批阅' : answer.isCorrect === 1 ? '正确' : '错误' }}
                </el-tag>
                <span class="earned-score">{{ answer.score == null ? '待评分' : `得 ${answer.score} 分` }}</span>
              </div>

              <div class="answer-content"><MarkdownRenderer :content="answer.content" /></div>
              <dl class="answer-detail">
                <div class="detail-row">
                  <dt class="detail-label">我的答案</dt>
                  <dd
                    :class="[
                      'detail-value',
                      answer.isCorrect === 1 ? 'correct' : answer.gradingStatus === 'PENDING' ? '' : 'wrong',
                    ]"
                  >
                    {{ answer.userAnswer || '未作答' }}
                  </dd>
                </div>
                <div v-if="answer.gradingStatus !== 'PENDING' && answer.isCorrect !== 1" class="detail-row">
                  <dt class="detail-label">正确答案</dt>
                  <dd class="detail-value correct">{{ answer.correctAnswer }}</dd>
                </div>
                <div v-if="answer.gradingStatus !== 'PENDING' && answer.analysis" class="detail-row">
                  <dt class="detail-label">解析</dt>
                  <dd class="detail-value analysis"><MarkdownRenderer :content="answer.analysis" /></dd>
                </div>
                <div v-if="answer.gradingStatus !== 'PENDING' && answer.reviewComment" class="detail-row">
                  <dt class="detail-label">批阅意见</dt>
                  <dd class="detail-value analysis"><MarkdownRenderer :content="answer.reviewComment" /></dd>
                </div>
              </dl>

              <div
                v-if="answer.gradingStatus !== 'PENDING' && answer.isCorrect === 0 && result.courseId"
                class="answer-actions"
              >
                <el-button
                  type="primary"
                  plain
                  :aria-label="`复习错题 ${answerLabel(answer, idx)}`"
                  @click="reviewWrongAnswer(answer.questionId)"
                >
                  复习此错题
                </el-button>
              </div>
            </div>
          </article>
        </section>
      </template>
    </LpStatePanel>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getExamResult, type ExamAnswerVO, type ExamRecordVO } from '@/api/exam'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import LpStatePanel from '@/components/ui/LpStatePanel.vue'
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
  width: min(100%, 960px);
  margin: 0 auto;
  padding: var(--lp-space-4) 0;
}

.result-loading-shell {
  min-height: 320px;
}

.result-header {
  margin-bottom: var(--lp-space-6);
}

.score-card {
  padding: var(--lp-space-8) var(--lp-space-6) var(--lp-space-6);
  text-align: center;
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.result-title,
.answers-title {
  margin: var(--lp-space-1) 0 0;
  color: var(--lp-text);
  font-size: var(--lp-text-2xl);
  line-height: var(--lp-leading-snug);
}

.score-main {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--lp-space-10);
  margin: var(--lp-space-8) 0 var(--lp-space-6);
}

.score-circle,
.score-rate {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.score-number {
  color: var(--lp-primary);
  font-size: var(--lp-text-5xl);
  font-weight: var(--lp-weight-bold);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.score-total,
.rate-label {
  margin-top: var(--lp-space-1);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-base);
}

.rate-value {
  color: var(--lp-success);
  font-size: var(--lp-text-4xl);
  font-weight: var(--lp-weight-bold);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.grading-alert {
  margin: 0 auto var(--lp-space-4);
  text-align: left;
}

.score-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--lp-space-4);
  margin: 0;
  padding: var(--lp-space-4) 0;
  border-top: var(--lp-border-hairline);
  border-bottom: var(--lp-border-hairline);
}

.meta-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--lp-space-1);
}

.meta-label {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}

.meta-value {
  margin: 0;
  color: var(--lp-text);
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-semibold);
}

.source-panel {
  margin-top: var(--lp-space-4);
  padding: var(--lp-space-3) var(--lp-space-4);
  color: var(--lp-text-secondary);
  text-align: left;
  background: var(--lp-surface-soft);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
}

.source-heading {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  flex-wrap: wrap;
}

.source-heading strong {
  color: var(--lp-text);
}

.source-panel p {
  margin: var(--lp-space-2) 0 0;
  overflow-wrap: anywhere;
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}

.score-actions {
  display: flex;
  justify-content: center;
  gap: var(--lp-space-3);
  flex-wrap: wrap;
  margin-top: var(--lp-space-5);
}
.exam-reward {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-4);
  padding: var(--lp-space-3);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.exam-reward strong {
  color: var(--lp-text);
}
.exam-reward small {
  flex-basis: 100%;
  color: var(--lp-text-secondary);
}

.answers-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--lp-space-4);
  margin-bottom: var(--lp-space-4);
}

.answers-heading > div:first-child {
  flex: 0 0 auto;
  min-width: max-content;
}

.answers-summary {
  margin: var(--lp-space-2) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}

.answer-navigation {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--lp-space-2);
}

.answer-navigation a {
  padding: var(--lp-space-1) var(--lp-space-2);
  color: var(--lp-text-secondary);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-sm);
  font-size: var(--lp-text-xs);
  text-decoration: none;
}

.answer-navigation a:hover {
  color: var(--lp-text);
  background: var(--lp-surface-inset);
}

.answer-navigation a:focus-visible {
  outline: var(--lp-focus-width) solid var(--lp-focus-ring);
  outline-offset: var(--lp-focus-offset);
  box-shadow: var(--lp-shadow-focus);
}

.answer-item {
  margin-bottom: var(--lp-space-3);
  scroll-margin-top: calc(var(--lp-header-height) + var(--lp-space-4));
}

.answer-card {
  padding: var(--lp-space-5);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.answer-section-title {
  margin-bottom: var(--lp-space-2);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-bold);
}

.answer-header {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  flex-wrap: wrap;
  margin-bottom: var(--lp-space-3);
}

.q-index {
  margin: 0;
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
  font-weight: var(--lp-weight-bold);
}

.q-score-tag {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}

.result-tag {
  margin-left: auto;
}

.earned-score {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
}

.answer-content {
  margin-bottom: var(--lp-space-4);
  color: var(--lp-text);
  font-size: var(--lp-text-md);
  line-height: var(--lp-leading-relaxed);
  white-space: pre-wrap;
}

.answer-detail {
  display: grid;
  gap: var(--lp-space-3);
  margin: 0;
  padding: var(--lp-space-4);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-md);
}

.detail-row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: var(--lp-space-2);
}

.detail-label {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}

.detail-value {
  margin: 0;
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-body);
  overflow-wrap: anywhere;
}

.detail-value.correct {
  color: var(--lp-success);
}

.detail-value.wrong {
  color: var(--lp-danger);
}

.detail-value.analysis {
  color: var(--lp-text-secondary);
}

.answer-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--lp-space-4);
}

@media (max-width: 640px) {
  .exam-result-container {
    padding: 0;
  }

  .score-card {
    padding: var(--lp-space-6) var(--lp-space-4) var(--lp-space-5);
  }

  .score-main {
    gap: var(--lp-space-6);
  }

  .score-number {
    font-size: var(--lp-text-4xl);
  }

  .rate-value {
    font-size: var(--lp-text-3xl);
  }

  .score-actions,
  .answers-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .answer-navigation {
    justify-content: flex-start;
  }

  .score-actions .el-button,
  .answer-actions .el-button {
    width: 100%;
    min-height: 44px;
    margin-left: 0;
  }

  .answer-header {
    align-items: flex-start;
  }

  .result-tag {
    margin-left: 0;
  }

  .detail-row {
    grid-template-columns: 1fr;
    gap: var(--lp-space-1);
  }
}

@media (max-width: 420px) {
  .score-meta {
    grid-template-columns: 1fr;
  }
}
</style>
