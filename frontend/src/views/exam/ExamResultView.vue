<template>
  <div class="exam-result-container page-container">
    <div v-loading="loading" class="result-loading-shell">
      <template v-if="result">
        <section class="result-header" aria-labelledby="result-title">
          <el-card shadow="never" class="score-card">
            <span class="section-kicker">考试复盘</span>
            <h2 id="result-title">{{ result.examTitle }}</h2>

            <div class="score-main">
              <div class="score-circle" aria-label="考试得分">
                <span class="score-number">{{ result.score ?? 0 }}</span>
                <span class="score-total">/ {{ result.totalScore }}</span>
              </div>
              <div class="score-rate">
                <span class="rate-value">{{ scoreRate }}%</span>
                <span class="rate-label">得分率</span>
              </div>
            </div>

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
                <dt class="meta-label">错题</dt>
                <dd class="meta-value">{{ wrongAnswers.length }} 题</dd>
              </div>
            </dl>

            <div v-if="isOfficialPaper" class="source-panel">
              <div class="source-heading">
                <el-tag :type="result.sourceVerified ? 'success' : 'warning'" size="small">
                  {{ result.sourceVerified ? '来源已核验' : '来源未核验' }}
                </el-tag>
                <strong>{{ officialPaperTitle }}</strong>
              </div>
              <p v-if="result.sourceReference">来源：{{ result.sourceReference }}</p>
            </div>

            <div class="score-actions">
              <el-button @click="router.push({ name: 'ExamList', query: { tab: 'records' } })">
                返回考试列表
              </el-button>
              <el-button
                v-if="result.courseId"
                type="primary"
                @click="goToCourseOverview"
              >
                返回课程总览
              </el-button>
            </div>
          </el-card>
        </section>

        <section class="answers-section" aria-labelledby="answer-detail-title">
          <div class="answers-heading">
            <div>
              <span class="section-kicker">逐题核对</span>
              <h2 id="answer-detail-title">答题详情</h2>
            </div>
            <span class="answers-summary">{{ answers.length }} 题 · {{ wrongAnswers.length }} 题需复习</span>
          </div>

          <article v-for="(answer, idx) in answers" :key="answer.questionId" class="answer-item">
            <el-card shadow="never">
              <div v-if="answer.sectionTitle" class="answer-section-title">{{ answer.sectionTitle }}</div>
              <div class="answer-header">
                <span class="q-index">{{ answer.displayNumber || `${idx + 1}.` }}</span>
                <el-tag size="small">{{ getTypeLabel(answer.questionType) }}</el-tag>
                <span class="q-score-tag">满分 {{ answer.fullScore }} 分</span>
                <el-tag :type="answer.isCorrect === 1 ? 'success' : 'danger'" size="small" class="result-tag">
                  {{ answer.isCorrect === 1 ? '正确' : '错误' }}
                </el-tag>
                <span class="earned-score">得 {{ answer.score }} 分</span>
              </div>

              <div class="answer-content">{{ answer.content }}</div>
              <dl class="answer-detail">
                <div class="detail-row">
                  <dt class="detail-label">我的答案</dt>
                  <dd :class="['detail-value', answer.isCorrect === 1 ? 'correct' : 'wrong']">
                    {{ answer.userAnswer || '未作答' }}
                  </dd>
                </div>
                <div v-if="answer.isCorrect !== 1" class="detail-row">
                  <dt class="detail-label">正确答案</dt>
                  <dd class="detail-value correct">{{ answer.correctAnswer }}</dd>
                </div>
                <div v-if="answer.analysis" class="detail-row">
                  <dt class="detail-label">解析</dt>
                  <dd class="detail-value analysis">{{ answer.analysis }}</dd>
                </div>
              </dl>

              <div v-if="answer.isCorrect !== 1 && result.courseId" class="answer-actions">
                <el-button type="primary" plain @click="reviewWrongAnswer(answer.questionId)">
                  复习此错题
                </el-button>
              </div>
            </el-card>
          </article>
        </section>
      </template>

      <el-empty v-else-if="!loading" description="考试结果不存在">
        <el-button type="primary" @click="router.push({ name: 'ExamList' })">返回考试列表</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getExamResult } from '@/api/exam'
import type { ExamRecordVO } from '@/api/exam'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const result = ref<ExamRecordVO | null>(null)

const scoreRate = computed(() => {
  if (!result.value || result.value.score == null || !result.value.totalScore) return 0
  return Math.round((result.value.score / result.value.totalScore) * 100)
})

const answers = computed(() => result.value?.answers || [])
const wrongAnswers = computed(() => answers.value.filter(answer => answer.isCorrect !== 1))
const isOfficialPaper = computed(() => result.value?.paperType === 'OFFICIAL_EXAM')
const officialPaperTitle = computed(() => {
  if (!result.value) return ''
  const metadata = [result.value.examYear, result.value.examName].filter(Boolean)
  return metadata.length > 0 ? metadata.join(' · ') : '官方考试试卷'
})

const timeUsed = computed(() => {
  if (!result.value?.startTime || !result.value.endTime) return '-'
  const start = new Date(result.value.startTime).getTime()
  const end = new Date(result.value.endTime).getTime()
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return '-'
  const diff = Math.floor((end - start) / 1000)
  const minutes = Math.floor(diff / 60)
  const seconds = diff % 60
  if (minutes > 0) return `${minutes} 分 ${seconds} 秒`
  return `${seconds} 秒`
})

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

const goToCourseOverview = () => {
  if (!result.value?.courseId) return
  router.push({ name: 'CourseOverview', params: { id: String(result.value.courseId) } })
}

const reviewWrongAnswer = (questionId: number) => {
  if (!result.value?.courseId) return
  router.push({
    name: 'WrongQuestions',
    query: { courseId: String(result.value.courseId), questionId: String(questionId) },
  })
}

onMounted(async () => {
  const recordId = Number(route.params.recordId)
  if (!Number.isInteger(recordId) || recordId <= 0) {
    ElMessage.error('考试记录无效')
    loading.value = false
    return
  }

  try {
    const response = await getExamResult(recordId)
    if (response.code === 0 && response.data) {
      result.value = response.data
    } else {
      ElMessage.error(response.message || '获取考试结果失败')
    }
  } catch {
    ElMessage.error('获取考试结果失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.exam-result-container {
  max-width: 960px;
  margin: 0 auto;
}

.result-loading-shell {
  min-height: 320px;
}

.result-header {
  margin-bottom: 24px;
}

.score-card {
  text-align: center;
}

.section-kicker {
  color: var(--lp-primary);
  font-size: 12px;
  font-weight: 800;
}

.score-card h2,
.answers-heading h2 {
  margin: 4px 0 0;
  color: var(--lp-text);
  font-size: 22px;
  line-height: 1.4;
}

.score-main {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 40px;
  margin: 28px 0 24px;
}

.score-circle,
.score-rate {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.score-number {
  color: var(--lp-primary);
  font-size: 48px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.score-total,
.rate-label {
  margin-top: 4px;
  color: var(--lp-text-muted);
  font-size: 14px;
}

.rate-value {
  color: var(--lp-success);
  font-size: 36px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.score-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin: 0;
  padding: 16px 0;
  border-top: 1px solid var(--lp-border);
  border-bottom: 1px solid var(--lp-border);
}

.meta-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.meta-label {
  color: var(--lp-text-muted);
  font-size: 12px;
}

.meta-value {
  margin: 0;
  color: var(--lp-text);
  font-size: 14px;
  font-weight: 600;
}

.source-panel {
  margin-top: 16px;
  padding: 12px 16px;
  color: var(--lp-text-secondary);
  text-align: left;
  background: var(--lp-surface-soft);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
}

.source-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.source-heading strong {
  color: var(--lp-text);
}

.source-panel p {
  margin: 8px 0 0;
  overflow-wrap: anywhere;
  font-size: 13px;
  line-height: 1.6;
}

.score-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 20px;
}

.answers-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.answers-summary {
  color: var(--lp-text-secondary);
  font-size: 13px;
}

.answer-item {
  margin-bottom: 12px;
}

.answer-section-title {
  margin-bottom: 8px;
  color: var(--lp-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.answer-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.q-index {
  color: var(--lp-text);
  font-size: 16px;
  font-weight: 700;
}

.q-score-tag {
  color: var(--lp-text-muted);
  font-size: 12px;
}

.result-tag {
  margin-left: auto;
}

.earned-score {
  color: var(--lp-text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.answer-content {
  margin-bottom: 16px;
  color: var(--lp-text);
  font-size: 15px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.answer-detail {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 16px;
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius);
}

.detail-row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 8px;
}

.detail-label {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.detail-value {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.detail-value.correct { color: var(--lp-success); }
.detail-value.wrong { color: var(--lp-danger); }
.detail-value.analysis { color: var(--lp-text-secondary); }

.answer-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 640px) {
  .exam-result-container {
    padding: 16px;
  }

  .score-main {
    gap: 24px;
  }

  .score-number { font-size: 40px; }
  .rate-value { font-size: 30px; }

  .score-actions,
  .answers-heading {
    align-items: stretch;
    flex-direction: column;
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
    gap: 2px;
  }
}

@media (max-width: 420px) {
  .score-meta {
    grid-template-columns: 1fr;
  }
}
</style>
