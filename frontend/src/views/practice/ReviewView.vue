<template>
  <div class="review-container page-container">
    <LpPageHeader
      kicker="间隔重复"
      title="复习"
      description="按到期时间处理复习卡片，先清空今日任务，再查看全部复习计划。"
    >
      <template #actions>
        <el-button type="primary" size="large" :icon="Reading" :disabled="stats.dueToday === 0" @click="startReview">
          开始复习
          <el-badge v-if="stats.overdue > 0" :value="`${stats.overdue}逾期`" type="danger" class="button-badge" />
        </el-button>
      </template>
    </LpPageHeader>

    <div v-if="targetKnowledgePointName" class="kp-filter-chip">
      <el-tag type="info" effect="plain" closable @close="clearKnowledgePointFilter">
        知识点：{{ targetKnowledgePointName }}
      </el-tag>
    </div>

    <section class="stats-grid">
      <LpStat label="今日待复习" :value="stats.dueToday" tone="emphasis" />
      <LpStat label="今日已完成" :value="stats.reviewedToday" />
      <LpStat label="已掌握" :value="stats.masteredCards" tone="warning" />
      <LpStat label="连续复习" :value="`${stats.streakDays} 天`" tone="danger" />
    </section>

    <section v-if="stats.totalCards > 0" class="progress-panel">
      <LpSectionHeading
        kicker="掌握进度"
        :title="`已掌握 ${stats.masteredCards} / ${stats.totalCards} 张卡片`"
        description="新卡片、学习中与困难卡片会随着复习持续流转。"
      />
      <LpProgress
        :percent="masteredPercent"
        :label="`${stats.masteredCards}/${stats.totalCards}`"
        tone="success"
        show-label
      />
      <div class="status-row">
        <span><el-tag type="info" size="small">新卡片</el-tag> {{ stats.newCards }}</span>
        <span><el-tag type="warning" size="small">学习中</el-tag> {{ stats.learningCards }}</span>
        <span><el-tag type="success" size="small">已掌握</el-tag> {{ stats.masteredCards }}</span>
        <span><el-tag type="danger" size="small">困难</el-tag> {{ stats.difficultCards }}</span>
      </div>
    </section>

    <section class="action-panel">
      <div class="action-bar">
        <div class="action-buttons">
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
        <el-text type="info" size="small">
          平均简易因子: {{ stats.avgEaseFactor?.toFixed(2) ?? '-' }} | 连续 {{ stats.streakDays }} 天
        </el-text>
      </div>
    </section>

    <!-- AI 复习建议区域 -->
    <section v-if="aiSuggestionContent" class="ai-panel">
      <LpSectionHeading kicker="AI 辅助" title="AI 复习建议">
        <template #aside>
          <el-button size="small" text @click="aiSuggestionContent = ''">收起</el-button>
        </template>
      </LpSectionHeading>
      <div class="ai-suggestion-content">
        <MarkdownRenderer :content="aiSuggestionContent" />
        <div v-if="aiSuggestionLoading" class="streaming-tip">
          <el-icon class="is-loading"><Loading /></el-icon> AI 正在生成建议...
        </div>
      </div>
    </section>

    <!-- 复习会话区域（当前待复习卡片） -->
    <el-card v-if="reviewing && currentCard" shadow="never" class="review-session">
      <template #header>
        <div class="card-header">
          <span>复习进度: {{ currentIndex + 1 }} / {{ dueCards.length }}</span>
          <el-tag :type="statusTagType(currentCard.statusLabel)" size="small">{{ currentCard.statusLabel }}</el-tag>
        </div>
      </template>

      <!-- 进度条 -->
      <LpProgress :percent="Math.round((currentIndex / dueCards.length) * 100)" />

      <!-- 题目信息 -->
      <div class="question-info">
        <div class="question-tags">
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
        :title="lastCorrect ? '回答正确！' : '回答错误'"
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
    <div v-if="reviewComplete" class="complete-card">
      <div class="complete-icon" aria-hidden="true">✓</div>
      <h3>今日复习完成！</h3>
      <p>共复习 {{ reviewedCount }} 题，正确 {{ correctCount }} 题</p>
      <el-button type="primary" @click="finishReview">返回</el-button>
    </div>

    <!-- 全部卡片列表 -->
    <el-card v-if="showAllCards" shadow="never" class="all-cards-card">
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
import { consumeReviewSuggestionStream } from './reviewSuggestionStream'
import { positiveQueryNumber, reviewStatusTag as statusTagType } from './reviewPresentation'

const route = useRoute()
const router = useRouter()

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
    await consumeReviewSuggestionStream(response, {
      onContent: (content) => (aiSuggestionContent.value += content),
      onError: (message) => ElMessage.error(message),
    })
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
  gap: var(--lp-space-6);
}

.kp-filter-chip {
  margin-top: calc(-1 * var(--lp-space-2));
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--lp-space-3);
}

.progress-panel,
.action-panel,
.ai-panel {
  display: grid;
  gap: var(--lp-space-4);
  padding: var(--lp-space-5);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.status-row {
  display: flex;
  gap: var(--lp-space-4);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  flex-wrap: wrap;
}

.status-row span {
  display: inline-flex;
  align-items: center;
  gap: var(--lp-space-2);
}

.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-4);
  flex-wrap: wrap;
}

.action-buttons {
  display: flex;
  gap: var(--lp-space-3);
  flex-wrap: wrap;
}

.ai-suggestion-content {
  line-height: var(--lp-leading-relaxed);
  font-size: var(--lp-text-base);
}

.streaming-tip {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  color: var(--lp-text-muted);
  margin-top: var(--lp-space-2);
  font-size: var(--lp-text-sm);
}

.review-session {
  border: var(--lp-border-hairline);
  border-left: 3px solid var(--lp-primary);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--lp-space-3);
}

.card-header span {
  font-weight: var(--lp-weight-semibold);
  color: var(--lp-text);
}

.question-info {
  margin-top: var(--lp-space-4);
}

.question-tags {
  display: flex;
  gap: var(--lp-space-2);
  margin-bottom: var(--lp-space-3);
  flex-wrap: wrap;
}

.question-content {
  font-size: var(--lp-text-lg);
  line-height: var(--lp-leading-relaxed);
  padding: var(--lp-space-4);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-md);
  white-space: pre-wrap;
  color: var(--lp-text);
}

.answer-box,
.result-alert,
.next-action {
  margin-top: var(--lp-space-4);
}

.session-actions {
  display: flex;
  gap: var(--lp-space-3);
  flex-wrap: wrap;
  margin-top: var(--lp-space-3);
}

.complete-card {
  text-align: center;
  padding: var(--lp-space-8) var(--lp-space-6);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.complete-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  margin: 0 auto var(--lp-space-4);
  border-radius: var(--lp-radius-full);
  background: var(--lp-success-soft);
  color: var(--lp-success);
  font-size: var(--lp-text-4xl);
  font-weight: var(--lp-weight-bold);
}

.complete-card h3 {
  margin: 0 0 var(--lp-space-2);
  color: var(--lp-text);
  font-size: var(--lp-text-3xl);
}

.complete-card p {
  margin: 0 0 var(--lp-space-4);
  color: var(--lp-text-secondary);
}

.all-cards-card {
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

@media (max-width: 900px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .progress-panel,
  .action-panel,
  .ai-panel {
    padding: var(--lp-space-4);
  }

  .action-bar,
  .card-header {
    align-items: stretch;
    flex-direction: column;
  }

  .action-bar .el-button,
  .action-buttons {
    width: 100%;
  }

  .action-buttons {
    flex-direction: column;
  }
}
</style>
