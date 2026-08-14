<template>
  <div class="admin-page subjective-review-page">
    <header class="admin-page-header">
      <div>
        <p class="admin-page-kicker">MANUAL GRADING</p>
        <h2>主观题批阅</h2>
        <p class="admin-page-description">逐项核对评分点后给分；全部主观题完成批阅时，系统自动固化考试总分。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadPending">刷新队列</el-button>
    </header>

    <section class="admin-summary-grid">
      <el-card shadow="never" class="admin-summary-card">
        <span class="admin-summary-icon"
          ><el-icon><EditPen /></el-icon
        ></span>
        <div class="admin-summary-copy">
          <p class="admin-summary-label">待批阅答案</p>
          <div class="admin-summary-value">{{ pending.length }}</div>
          <div class="admin-summary-note">仅展示尚未评分的主观题答案</div>
        </div>
      </el-card>
    </section>

    <el-card shadow="never" class="admin-table-card">
      <el-table v-loading="loading" :data="pending" stripe class="admin-data-table">
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
      <el-empty v-if="!loading && pending.length === 0" description="当前没有待批阅答案" />
    </el-card>

    <el-drawer v-model="drawerVisible" title="按评分点批阅" size="min(760px, 94vw)" destroy-on-close>
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

        <el-form label-position="top" class="rubric-form" @submit.prevent>
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
            <el-button @click="drawerVisible = false">取消</el-button>
            <el-button type="primary" :loading="submitting" @click="submitReview">确认并完成批阅</el-button>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { EditPen, Refresh } from '@element-plus/icons-vue'
import { getPendingSubjectiveReviews, gradeSubjectiveAnswer } from '@/api/exam'
import type { SubjectiveAnswerReviewVO } from '@/api/exam'

const loading = ref(false)
const submitting = ref(false)
const pending = ref<SubjectiveAnswerReviewVO[]>([])
const drawerVisible = ref(false)
const current = ref<SubjectiveAnswerReviewVO | null>(null)
const formScores = reactive<Record<string, { awardedScore: number; comment: string }>>({})
const reviewComment = ref('')

const awardedTotal = computed(() =>
  Object.values(formScores).reduce((sum, point) => sum + Number(point.awardedScore || 0), 0),
)

async function loadPending() {
  loading.value = true
  try {
    const response = await getPendingSubjectiveReviews()
    pending.value = response.data || []
  } catch {
    ElMessage.error('待批阅队列加载失败，请重试')
  } finally {
    loading.value = false
  }
}

function openReview(answer: SubjectiveAnswerReviewVO) {
  current.value = answer
  Object.keys(formScores).forEach((key) => delete formScores[key])
  answer.gradingPoints.forEach((point) => {
    formScores[point.pointKey] = { awardedScore: 0, comment: '' }
  })
  reviewComment.value = ''
  drawerVisible.value = true
}

async function submitReview() {
  if (!current.value || submitting.value) return
  submitting.value = true
  try {
    const response = await gradeSubjectiveAnswer(current.value.answerId, {
      points: current.value.gradingPoints.map((point) => ({
        pointKey: point.pointKey,
        awardedScore: formScores[point.pointKey].awardedScore,
        comment: formScores[point.pointKey].comment || undefined,
      })),
      reviewComment: reviewComment.value || undefined,
    })
    if (response.code !== 0) {
      ElMessage.error(response.message || '批阅提交失败')
      return
    }
    ElMessage.success('批阅已保存，考试成绩已重新计算')
    drawerVisible.value = false
    await loadPending()
  } catch {
    ElMessage.error('批阅提交失败，请检查评分后重试')
  } finally {
    submitting.value = false
  }
}

function formatTime(value: string) {
  return value ? value.replace('T', ' ').substring(0, 19) : '-'
}

onMounted(loadPending)
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

.review-mobile-list {
  display: none;
}

@media (max-width: 640px) {
  .admin-data-table {
    display: none;
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
