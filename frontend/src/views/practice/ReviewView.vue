<template>
  <div class="review-container page-container">
    <section class="review-hero">
      <div>
        <span class="section-kicker">间隔重复</span>
        <h2>智能复习</h2>
        <p>按到期时间处理复习卡片，先清空今日任务，再查看全部复习计划。</p>
      </div>
      <el-button type="primary" size="large" :icon="Reading" :disabled="stats.dueToday === 0" @click="startReview">
        开始复习
        <el-badge v-if="stats.overdue > 0" :value="`${stats.overdue}逾期`" type="danger" class="button-badge" />
      </el-button>
    </section>

    <div v-if="targetKnowledgePointName" class="kp-filter-chip">
      <el-tag type="info" effect="plain" closable @close="clearKnowledgePointFilter">
        知识点：{{ targetKnowledgePointName }}
      </el-tag>
    </div>

    <section class="stats-grid">
      <el-card shadow="never" class="stat-card stat-due">
        <span>今日待复习</span>
        <strong>{{ stats.dueToday }}</strong>
      </el-card>
      <el-card shadow="never" class="stat-card stat-reviewed">
        <span>今日已完成</span>
        <strong>{{ stats.reviewedToday }}</strong>
      </el-card>
      <el-card shadow="never" class="stat-card stat-mastered">
        <span>已掌握</span>
        <strong>{{ stats.masteredCards }}</strong>
      </el-card>
      <el-card shadow="never" class="stat-card stat-streak">
        <span>连续复习</span>
        <strong>{{ stats.streakDays }}<small>天</small></strong>
      </el-card>
    </section>

    <el-card shadow="never" v-if="stats.totalCards > 0" class="progress-card">
      <div class="progress-header">
        <span>掌握进度</span>
        <small>{{ stats.totalCards }} 张卡片</small>
      </div>
      <el-progress :percentage="masteredPercent" :format="() => `${stats.masteredCards}/${stats.totalCards}`" />
      <div class="status-row">
        <span><el-tag type="info" size="small">新卡片</el-tag> {{ stats.newCards }}</span>
        <span><el-tag type="warning" size="small">学习中</el-tag> {{ stats.learningCards }}</span>
        <span><el-tag type="success" size="small">已掌握</el-tag> {{ stats.masteredCards }}</span>
        <span><el-tag type="danger" size="small">困难</el-tag> {{ stats.difficultCards }}</span>
      </div>
    </el-card>

    <el-card shadow="never" class="action-card">
      <div class="action-bar">
        <div>
          <el-button size="large" :icon="View" @click="showAllCards = !showAllCards">
            {{ showAllCards ? '收起卡片列表' : '查看全部卡片' }}
          </el-button>
          <el-button size="large" :icon="Download" :loading="syncing" @click="handleSyncWrongQuestions">
            同步错题到复习
          </el-button>
          <el-button
            size="large"
            :icon="MagicStick"
            :loading="aiSuggestionLoading"
            :disabled="stats.totalCards === 0"
            @click="handleAiSuggestion"
          >
            AI 复习建议
          </el-button>
        </div>
        <el-text type="info" size="small"
          >平均简易因子: {{ stats.avgEaseFactor?.toFixed(2) ?? '-' }} | 连续 {{ stats.streakDays }} 天</el-text
        >
      </div>
    </el-card>

    <!-- AI 复习建议区域 -->
    <el-card v-if="aiSuggestionContent" shadow="never" class="ai-card">
      <template #header>
        <div class="card-header">
          <span>AI 复习建议</span>
          <el-button size="small" text @click="aiSuggestionContent = ''">收起</el-button>
        </div>
      </template>
      <div class="ai-suggestion-content">
        <MarkdownRenderer :content="aiSuggestionContent" />
        <div v-if="aiSuggestionLoading" class="streaming-tip">
          <el-icon class="is-loading"><Loading /></el-icon> AI 正在生成建议...
        </div>
      </div>
    </el-card>

    <!-- 复习会话区域（当前待复习卡片） -->
    <el-card v-if="reviewing && currentCard" shadow="never" class="review-session">
      <template #header>
        <div class="card-header">
          <span>复习进度: {{ currentIndex + 1 }} / {{ dueCards.length }}</span>
          <el-tag :type="statusTagType(currentCard.statusLabel)" size="small">{{ currentCard.statusLabel }}</el-tag>
        </div>
      </template>

      <!-- 进度条 -->
      <el-progress
        :percentage="Math.round((currentIndex / dueCards.length) * 100)"
        :show-text="false"
        style="margin-bottom: 16px"
      />

      <!-- 题目信息 -->
      <div class="question-info">
        <div style="display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap">
          <el-tag size="small">{{ currentCard.questionType }}</el-tag>
          <el-tag size="small" type="info">{{ currentCard.courseName || '未知课程' }}</el-tag>
          <el-tag size="small" :type="currentCard.overdue ? 'danger' : 'success'">
            {{ currentCard.overdue ? `逾期 ${currentCard.overdueDays} 天` : '今日到期' }}
          </el-tag>
          <el-tag size="small">间隔 {{ currentCard.intervalDays }} 天</el-tag>
          <el-tag size="small">EF {{ currentCard.easeFactor?.toFixed(2) }}</el-tag>
        </div>
        <div class="question-content">{{ currentCard.questionContent }}</div>
      </div>

      <!-- 答题输入 -->
      <div class="answer-box">
        <el-input
          v-model="userAnswer"
          type="textarea"
          :rows="3"
          placeholder="输入你的答案..."
          :disabled="answerSubmitted"
        />
      </div>

      <!-- 操作按钮 -->
      <div class="session-actions">
        <el-button
          type="primary"
          @click="submitCurrentAnswer"
          :disabled="!userAnswer.trim() || answerSubmitted"
          :loading="submitting"
        >
          提交答案
        </el-button>
        <el-button @click="skipCard" :disabled="answerSubmitted">跳过</el-button>
        <el-button type="danger" plain @click="stopReview">结束复习</el-button>
      </div>

      <!-- 答题结果 -->
      <el-alert
        v-if="answerSubmitted"
        :title="lastCorrect ? '✅ 回答正确！' : '❌ 回答错误'"
        :type="lastCorrect ? 'success' : 'error'"
        :description="
          lastCorrect
            ? `下次复习: ${currentCard.intervalDays} 天后 | 新间隔: ${lastResult?.intervalDays} 天`
            : `间隔已重置为 1 天，请继续加油！`
        "
        show-icon
        :closable="false"
        class="result-alert"
      />

      <div v-if="answerSubmitted" class="next-action">
        <el-button type="primary" @click="nextCard">
          {{ currentIndex < dueCards.length - 1 ? '下一题' : '完成复习' }}
        </el-button>
      </div>
    </el-card>

    <!-- 复习完成 -->
    <el-card v-if="reviewComplete" shadow="never" class="complete-card">
      <h3>今日复习完成！</h3>
      <p>共复习 {{ reviewedCount }} 题，正确 {{ correctCount }} 题</p>
      <el-button type="primary" @click="finishReview">返回</el-button>
    </el-card>

    <!-- 全部卡片列表 -->
    <el-card v-if="showAllCards" shadow="never">
      <template #header>
        <div class="card-header">
          <span>复习计划卡片 ({{ allCards.length }})</span>
          <el-button size="small" @click="loadAllCards">刷新</el-button>
        </div>
      </template>

      <el-table
        :data="allCards"
        stripe
        style="width: 100%"
        v-loading="cardsLoading"
        empty-text="暂无复习卡片，刷题后自动加入"
      >
        <el-table-column label="题目" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.questionContent }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small">{{ row.questionType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="课程" width="120" show-overflow-tooltip prop="courseName" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.statusLabel)">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="间隔" width="80" prop="intervalDays" />
        <el-table-column label="EF" width="70">
          <template #default="{ row }">
            {{ row.easeFactor?.toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column label="下次复习" width="120" prop="nextReviewDate" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button size="small" type="danger" plain @click="handleRemove(row.questionId)">移出</el-button>
            <el-button size="small" @click="handleReset(row.questionId)">重置</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { errorMessage, isAbortError } from '@/utils/errors'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Loading, MagicStick, Reading, View } from '@element-plus/icons-vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { getAiReviewSuggestionStream } from '@/api/review'
import { getToken } from '@/utils/auth'
import {
  getReviewStats,
  getDueReviewCards,
  getAllReviewCards,
  submitReview,
  removeFromReviewPlan,
  resetReviewProgress,
  syncWrongQuestionsToReview,
  type ReviewStatsVO,
  type ReviewScheduleVO,
} from '@/api/review'

const route = useRoute()
const router = useRouter()

function positiveQueryNumber(value: unknown) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined
}

const targetCourseId = computed(() => positiveQueryNumber(route.query.courseId))
const targetQuestionId = computed(() => positiveQueryNumber(route.query.questionId))
const targetKnowledgePointId = computed(() => positiveQueryNumber(route.query.knowledgePointId))
const targetKnowledgePointName = computed(() =>
  typeof route.query.knowledgePointName === 'string' ? route.query.knowledgePointName : '',
)

async function clearKnowledgePointFilter() {
  const query = { ...route.query }
  delete query.knowledgePointId
  delete query.knowledgePointName
  await router.replace({ query })
  await loadDueCards()
}

// 统计数据
const stats = ref<ReviewStatsVO>({
  totalCards: 0,
  dueToday: 0,
  overdue: 0,
  reviewedToday: 0,
  newCards: 0,
  learningCards: 0,
  masteredCards: 0,
  difficultCards: 0,
  streakDays: 0,
  avgEaseFactor: 2.5,
})

// 复习会话
const reviewing = ref(false)

function finishReview() {
  reviewComplete.value = false
  reviewing.value = false
}
const dueCards = ref<ReviewScheduleVO[]>([])
const currentIndex = ref(0)
const userAnswer = ref('')
const answerSubmitted = ref(false)
const submitting = ref(false)
const lastResult = ref<ReviewScheduleVO | null>(null)
const lastCorrect = ref(false)
const reviewedCount = ref(0)
const correctCount = ref(0)
const reviewComplete = ref(false)

// 卡片列表
const showAllCards = ref(false)
const allCards = ref<ReviewScheduleVO[]>([])
const cardsLoading = ref(false)
const syncing = ref(false)

// AI 复习建议
const aiSuggestionLoading = ref(false)
const aiSuggestionContent = ref('')

const currentCard = computed(() => dueCards.value[currentIndex.value] || null)

const masteredPercent = computed(() => {
  if (stats.value.totalCards === 0) return 0
  return Math.round((stats.value.masteredCards / stats.value.totalCards) * 100)
})

function statusTagType(label: string) {
  switch (label) {
    case '新卡片':
      return 'info'
    case '学习中':
      return 'warning'
    case '已掌握':
      return 'success'
    case '困难':
      return 'danger'
    default:
      return 'info'
  }
}

async function loadStats() {
  try {
    const { data } = await getReviewStats()
    stats.value = data
  } catch {
    // ignore
  }
}

async function loadDueCards() {
  try {
    const { data } = await getDueReviewCards(
      targetCourseId.value,
      30,
      targetQuestionId.value,
      targetKnowledgePointId.value,
    )
    dueCards.value = data
  } catch {
    ElMessage.error('获取待复习题目失败')
  }
}

async function loadAllCards() {
  cardsLoading.value = true
  try {
    const { data } = await getAllReviewCards()
    allCards.value = data
  } catch {
    ElMessage.error('获取复习卡片失败')
  } finally {
    cardsLoading.value = false
  }
}

async function startReview() {
  await loadDueCards()
  if (dueCards.value.length === 0) {
    ElMessage.info('没有待复习的题目')
    return
  }
  reviewing.value = true
  reviewComplete.value = false
  currentIndex.value = 0
  reviewedCount.value = 0
  correctCount.value = 0
  userAnswer.value = ''
  answerSubmitted.value = false
  lastResult.value = null
  lastCorrect.value = false
}

async function submitCurrentAnswer() {
  if (!currentCard.value || !userAnswer.value.trim()) return
  submitting.value = true
  try {
    const { data } = await submitReview({
      questionId: currentCard.value.questionId,
      userAnswer: userAnswer.value.trim(),
    })
    lastResult.value = data
    // Determine correctness by checking if repetitions increased (SM-2: quality>=3 increments repetitions)
    const prevReps = currentCard.value?.repetitions || 0
    lastCorrect.value = (data?.repetitions ?? 0) > prevReps || (data?.intervalDays ?? 0) > 1
    answerSubmitted.value = true
    reviewedCount.value++
    if (lastCorrect.value) {
      correctCount.value++
    }
    await loadStats()
  } catch (e) {
    ElMessage.error(errorMessage(e, '提交失败'))
  } finally {
    submitting.value = false
  }
}

function nextCard() {
  if (currentIndex.value < dueCards.value.length - 1) {
    currentIndex.value++
    userAnswer.value = ''
    answerSubmitted.value = false
    lastResult.value = null
    lastCorrect.value = false
  } else {
    reviewing.value = false
    reviewComplete.value = true
  }
}

function skipCard() {
  nextCard()
}

function stopReview() {
  reviewing.value = false
  ElMessage.info(`已结束复习，本次复习 ${reviewedCount.value} 题`)
}

async function handleRemove(questionId: number) {
  await ElMessageBox.confirm('确定将该题目移出复习计划？', '确认')
  try {
    await removeFromReviewPlan(questionId)
    ElMessage.success('已移出')
    await loadAllCards()
    await loadStats()
  } catch (e) {
    ElMessage.error(errorMessage(e, '操作失败'))
  }
}

async function handleSyncWrongQuestions() {
  syncing.value = true
  try {
    const { data } = await syncWrongQuestionsToReview()
    const count = data.syncedCount
    if (count > 0) {
      ElMessage.success(`已同步 ${count} 道错题到复习计划`)
      await loadStats()
      if (showAllCards.value) {
        await loadAllCards()
      }
    } else {
      ElMessage.info('暂无新的错题需要同步（已在复习计划中的会跳过）')
    }
  } catch (e) {
    ElMessage.error(errorMessage(e, '同步失败'))
  } finally {
    syncing.value = false
  }
}

async function handleReset(questionId: number) {
  await ElMessageBox.confirm('确定重置该题目的复习进度？', '确认')
  try {
    await resetReviewProgress(questionId)
    ElMessage.success('已重置')
    await loadAllCards()
  } catch (e) {
    ElMessage.error(errorMessage(e, '操作失败'))
  }
}

async function handleAiSuggestion() {
  aiSuggestionLoading.value = true
  aiSuggestionContent.value = ''
  const token = getToken()
  if (!token) {
    ElMessage.error('请先登录')
    aiSuggestionLoading.value = false
    return
  }
  try {
    const response = await getAiReviewSuggestionStream(token)
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('无法读取响应流')
    }
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('event:')) {
          // event type line
        } else if (line.startsWith('data:')) {
          const jsonStr = line.slice(5).trim()
          if (!jsonStr) continue
          try {
            const data = JSON.parse(jsonStr)
            // Check if this is a done or error event by looking at the previous event line
            if (data.source === 'ai') {
              // done event - handled by the loop ending
            } else if (data.message) {
              // error event
              ElMessage.error(data.message)
            } else if (data.content) {
              aiSuggestionContent.value += data.content
            }
          } catch {
            // skip non-JSON lines
          }
        }
      }
    }
  } catch (e) {
    if (!isAbortError(e)) {
      ElMessage.error(errorMessage(e, 'AI 复习建议获取失败'))
    }
  } finally {
    aiSuggestionLoading.value = false
  }
}

onMounted(async () => {
  await loadStats()
  if (targetCourseId.value || targetQuestionId.value) {
    await startReview()
  }
})
</script>

<style scoped>
.review-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.kp-filter-chip {
  margin-top: 14px;
}

.review-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 22px;
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  box-shadow: var(--lp-shadow-sm);
}

.review-hero h2 {
  margin: 4px 0 8px;
  font-size: 24px;
  color: var(--lp-text);
}

.review-hero p {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: 14px;
}

.section-kicker {
  color: var(--lp-primary);
  font-size: 12px;
  font-weight: 800;
}

.button-badge {
  margin-left: 8px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  min-height: 94px;
}

.stat-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.stat-card span {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.stat-card strong {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-card small {
  font-size: 13px;
  margin-left: 2px;
}

.stat-due strong {
  color: var(--lp-primary);
}
.stat-reviewed strong {
  color: var(--lp-success);
}
.stat-mastered strong {
  color: var(--lp-warning);
}
.stat-streak strong {
  color: var(--lp-danger);
}

.progress-card,
.action-card,
.ai-card,
.review-session {
  margin: 0;
}

.progress-header,
.card-header,
.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.progress-header {
  margin-bottom: 8px;
}

.progress-header span,
.card-header span {
  font-weight: 700;
  color: var(--lp-text);
}

.progress-header small {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.status-row {
  display: flex;
  gap: 18px;
  margin-top: 12px;
  color: var(--lp-text-secondary);
  font-size: 13px;
  flex-wrap: wrap;
}

.action-bar {
  flex-wrap: wrap;
}

.action-bar > div {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.review-session {
  border-left: 4px solid var(--lp-primary);
}

.question-info {
  margin-bottom: 8px;
}

.question-content {
  font-size: 16px;
  line-height: 1.6;
  padding: 12px;
  background: var(--lp-surface-soft);
  border-radius: 8px;
  white-space: pre-wrap;
  color: var(--lp-text);
}

.answer-box,
.result-alert,
.next-action {
  margin-top: 16px;
}

.session-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.complete-card {
  text-align: center;
}

.complete-card h3 {
  margin: 0 0 10px;
  color: var(--lp-text);
}

.complete-card p {
  margin: 0 0 14px;
  color: var(--lp-text-secondary);
}

.ai-suggestion-content {
  line-height: 1.8;
  font-size: 14px;
}

.streaming-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--lp-text-muted);
  margin-top: 8px;
  font-size: 13px;
}

@media (max-width: 900px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .review-hero,
  .progress-header,
  .card-header,
  .action-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .review-hero {
    padding: 16px;
  }

  .review-hero .el-button,
  .action-bar .el-button {
    width: 100%;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
