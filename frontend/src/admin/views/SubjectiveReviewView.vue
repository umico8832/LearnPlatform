<template>
  <div class="admin-page subjective-review-page">
    <header class="admin-page-header">
      <div>
        <h1>主观题批阅</h1>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadPending">刷新队列</el-button>
    </header>

    <section v-if="loaded && !loading && !loadError" class="admin-summary-grid" aria-label="批阅队列统计">
      <el-card shadow="never" class="admin-summary-card">
        <span class="admin-summary-icon"
          ><el-icon><EditPen /></el-icon
        ></span>
        <div class="admin-summary-copy">
          <p class="admin-summary-label">待批阅答案</p>
          <div class="admin-summary-value">{{ pending.length }}</div>
        </div>
      </el-card>
    </section>

    <el-card shadow="never" class="admin-table-card">
      <LpStatePanel v-if="loading" state="loading" loading-label="正在读取待批阅队列" />
      <LpStatePanel
        v-else-if="loadError"
        state="error"
        title="待批阅队列暂时无法读取"
        :description="loadError"
        @retry="loadPending"
      />
      <template v-else-if="pending.length">
        <el-table :data="pending" stripe class="admin-data-table">
          <el-table-column prop="examTitle" label="试卷" min-width="240" />
          <el-table-column prop="displayNumber" label="题号" width="100" />
          <el-table-column prop="userId" label="用户 ID" width="100" />
          <el-table-column label="满分" width="80">
            <template #default="{ row }">{{ (row as SubjectiveAnswerReviewVO).fullScore }} 分</template>
          </el-table-column>
          <el-table-column label="提交时间" width="180">
            <template #default="{ row }">{{ formatTime((row as SubjectiveAnswerReviewVO).submittedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link :icon="EditPen" @click="openReview(row as SubjectiveAnswerReviewVO)">
                开始批阅
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="review-mobile-list" aria-label="待批阅答案">
          <article v-for="answer in pending" :key="answer.answerId" class="review-mobile-card">
            <div>
              <strong>{{ answer.displayNumber }} · {{ answer.examTitle }}</strong>
              <p>用户 {{ answer.userId }} · 满分 {{ answer.fullScore }} 分 · {{ formatTime(answer.submittedAt) }}</p>
            </div>
            <el-button type="primary" :icon="EditPen" @click="openReview(answer)">开始批阅</el-button>
          </article>
        </div>
      </template>
      <LpEmptyState v-else-if="loaded" title="当前没有待批阅答案" description="新提交的主观题会在这里出现。" />
    </el-card>

    <el-drawer
      v-model="drawerVisible"
      title="按评分点批阅"
      size="min(760px, 94vw)"
      destroy-on-close
      :close-on-click-modal="!submitting"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
    >
      <template v-if="current">
        <section class="review-context" aria-labelledby="review-question-title">
          <div class="review-context-heading">
            <el-tag type="warning" size="small">{{ current.displayNumber }}</el-tag>
            <strong>{{ current.examTitle }}</strong>
            <span>满分 {{ current.fullScore }} 分</span>
          </div>
          <h3 id="review-question-title">题目</h3>
          <pre>{{ current.content }}</pre>
          <h3>考生答案</h3>
          <pre class="student-answer">{{ current.userAnswer || '未作答' }}</pre>
        </section>

        <el-form label-position="top" class="rubric-form" :disabled="submitting" @submit.prevent>
          <fieldset v-for="point in current.gradingPoints" :key="point.pointKey" class="rubric-point">
            <legend>{{ point.title }}（{{ point.maxScore }} 分）</legend>
            <p>{{ point.description }}</p>
            <el-alert :title="`参考：${point.referenceAnswer}`" type="info" :closable="false" />
            <div class="point-fields">
              <el-form-item label="本项得分" required>
                <el-input-number
                  v-model="formScores[point.pointKey].awardedScore"
                  :min="0"
                  :max="point.maxScore"
                  :step="1"
                />
              </el-form-item>
              <el-form-item label="本项评语">
                <el-input v-model="formScores[point.pointKey].comment" maxlength="500" show-word-limit />
              </el-form-item>
            </div>
          </fieldset>
          <el-form-item label="总体批阅意见">
            <el-input v-model="reviewComment" type="textarea" :rows="3" maxlength="1000" show-word-limit />
          </el-form-item>
        </el-form>

        <div class="drawer-actions">
          <span>合计 {{ awardedTotal }} / {{ current.fullScore }} 分</span>
          <div>
            <el-button :disabled="submitting" @click="drawerVisible = false">取消</el-button>
            <el-button type="primary" :loading="submitting" @click="submitReview">确认并完成批阅</el-button>
            <p v-if="submitError" class="review-submit-error" role="alert">
              {{ submitError }} <button type="button" :disabled="submitting" @click="submitReview">重试</button>
            </p>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { EditPen, Refresh } from '@element-plus/icons-vue'
import LpEmptyState from '@/components/ui/LpEmptyState.vue'
import LpStatePanel from '@/components/ui/LpStatePanel.vue'
import { getPendingSubjectiveReviews, gradeSubjectiveAnswer } from '@/api/exam'
import type { SubjectiveAnswerReviewVO } from '@/api/exam'

import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
const loading = ref(true)
const loaded = ref(false)
const loadError = ref('')
const submitting = ref(false)
const submitError = ref('')
const pending = ref<SubjectiveAnswerReviewVO[]>([])
const drawerVisible = ref(false)
const current = ref<SubjectiveAnswerReviewVO | null>(null)
const formScores = reactive<Record<string, { awardedScore: number; comment: string }>>({})
const reviewComment = ref('')
let alive = true
let queueVersion = 0
let reviewVersion = 0
const queueCurrent = (version: number, session: number) =>
  alive && version === queueVersion && session === getAuthSessionVersion()
const reviewCurrent = (version: number, session: number, answerId: number) =>
  alive &&
  drawerVisible.value &&
  version === reviewVersion &&
  session === getAuthSessionVersion() &&
  current.value?.answerId === answerId
const awardedTotal = computed(() =>
  Object.values(formScores).reduce((sum, point) => sum + Number(point.awardedScore || 0), 0),
)

async function loadPending() {
  const version = ++queueVersion
  const session = getAuthSessionVersion()
  loading.value = true
  loadError.value = ''
  try {
    const response = await getPendingSubjectiveReviews({ errorDisplay: 'inline' })
    if (!queueCurrent(version, session)) return
    pending.value = response.data || []
    loaded.value = true
  } catch {
    if (queueCurrent(version, session)) {
      loaded.value = true
      loadError.value = '待批阅队列加载失败，请重试'
    }
  } finally {
    if (queueCurrent(version, session)) loading.value = false
  }
}
function invalidateReview() {
  reviewVersion++
  submitting.value = false
}
function openReview(answer: SubjectiveAnswerReviewVO) {
  if (submitting.value) return
  invalidateReview()
  current.value = answer
  Object.keys(formScores).forEach((key) => delete formScores[key])
  answer.gradingPoints.forEach((point) => {
    formScores[point.pointKey] = { awardedScore: 0, comment: '' }
  })
  reviewComment.value = ''
  submitError.value = ''
  drawerVisible.value = true
}
async function submitReview() {
  if (!current.value || submitting.value) return
  const version = reviewVersion
  const session = getAuthSessionVersion()
  const answer = current.value
  submitting.value = true
  submitError.value = ''
  try {
    const response = await gradeSubjectiveAnswer(
      answer.answerId,
      {
        points: answer.gradingPoints.map((point) => ({
          pointKey: point.pointKey,
          awardedScore: formScores[point.pointKey].awardedScore,
          comment: formScores[point.pointKey].comment || undefined,
        })),
        reviewComment: reviewComment.value || undefined,
      },
      { errorDisplay: 'inline' },
    )
    if (!reviewCurrent(version, session, answer.answerId)) return
    if (response.code !== 0) {
      submitError.value = response.message || '批阅提交失败，请重试'
      return
    }
    ElMessage.success('批阅已保存，考试成绩已重新计算')
    drawerVisible.value = false
    await loadPending()
  } catch {
    if (reviewCurrent(version, session, answer.answerId)) submitError.value = '批阅提交失败，请检查评分后重试'
  } finally {
    if (reviewCurrent(version, session, answer.answerId)) submitting.value = false
  }
}
watch(
  drawerVisible,
  (visible) => {
    if (!visible) invalidateReview()
  },
  { flush: 'sync' },
)

function formatTime(value: string) {
  return value ? value.replace('T', ' ').substring(0, 19) : '-'
}

const unsubscribeSession = onAuthSessionChange(() => {
  queueVersion++
  invalidateReview()
  pending.value = []
  current.value = null
  drawerVisible.value = false
  submitError.value = ''
  loadError.value = ''
  loading.value = false
  loaded.value = false
})
onMounted(() => void loadPending())
onUnmounted(() => {
  alive = false
  queueVersion++
  invalidateReview()
  unsubscribeSession()
})
</script>

<style scoped>
.subjective-review-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.review-context,
.rubric-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.review-context-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: var(--lp-text-secondary);
}

.review-context-heading strong {
  color: var(--lp-text);
}

.review-context h3 {
  margin: 8px 0 0;
  font-size: 15px;
}

.review-context pre {
  margin: 0;
  padding: 16px;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  font: inherit;
  line-height: 1.7;
  background: var(--lp-surface-soft);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
}

.student-answer {
  min-height: 120px;
}

.rubric-form {
  margin-top: 24px;
}

.rubric-point {
  margin: 0;
  padding: 16px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
}

.rubric-point legend {
  padding: 0 6px;
  color: var(--lp-text);
  font-weight: 700;
}

.rubric-point p {
  margin: 0 0 12px;
  color: var(--lp-text-secondary);
  line-height: 1.6;
}

.point-fields {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.drawer-actions {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 24px;
  padding: 16px 0;
  background: var(--lp-surface);
  border-top: 1px solid var(--lp-border);
  font-weight: 700;
}

.review-submit-error {
  margin: 0;
  color: var(--lp-danger);
}

.review-mobile-list {
  display: none;
}

@media (max-width: 640px) {
  .admin-data-table {
    display: none;
  }

  .review-submit-error {
    margin: 0;
    color: var(--lp-danger);
  }

  .review-mobile-list {
    display: grid;
    gap: 12px;
  }

  .review-mobile-card {
    display: grid;
    gap: 12px;
    padding: 16px;
    border: 1px solid var(--lp-border);
    border-radius: var(--lp-radius);
    background: var(--lp-surface);
  }

  .review-mobile-card p {
    margin: 6px 0 0;
    color: var(--lp-text-secondary);
    line-height: 1.6;
  }

  .point-fields {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .drawer-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .drawer-actions > div {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
  }
}
</style>
