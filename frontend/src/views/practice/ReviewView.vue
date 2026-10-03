<template>
  <div class="review-container page-container">
    <LpPageHeader title="复习">
      <template #actions>
        <el-button
          type="primary"
          size="large"
          :icon="Reading"
          :loading="startingReview"
          :disabled="statsLoading || !!statsError || stats.dueToday === 0 || reviewSession?.reviewing"
          @click="startReview"
        >
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

    <LpStatePanel v-if="statsLoading" state="loading" loading-label="正在读取复习计划" />
    <LpStatePanel v-else-if="statsError" state="error" :description="statsError" @retry="loadStats" />

    <section v-else class="stats-grid" aria-label="复习概况">
      <LpStat label="今日待复习" :value="stats.dueToday" tone="emphasis" />
      <LpStat label="今日已完成" :value="stats.reviewedToday" />
      <LpStat label="复习卡片" :value="stats.totalCards" />
    </section>

    <section v-if="!statsLoading && !statsError && stats.totalCards > 0" class="progress-panel">
      <LpSectionHeading :title="`已掌握 ${stats.masteredCards} / ${stats.totalCards} 张卡片`" />
      <LpProgress
        :percent="masteredPercent"
        :label="`${stats.masteredCards}/${stats.totalCards}`"
        tone="success"
        show-label
      />
      <p class="status-row">
        新卡 {{ stats.newCards }} · 学习中 {{ stats.learningCards }} · 困难 {{ stats.difficultCards }}
      </p>
    </section>

    <LpEmptyState
      v-else-if="!statsLoading && !statsError"
      title="暂时没有复习卡片"
      description="完成练习或同步错题后，待复习内容会出现在这里。"
    />

    <section class="action-panel" aria-label="复习工具">
      <div class="action-buttons">
        <el-button :icon="View" @click="toggleAllCards">
          {{ showAllCards ? '收起卡片列表' : '查看全部卡片' }}
        </el-button>
        <el-button :icon="Download" :loading="syncing" @click="handleSyncWrongQuestions"> 同步错题到复习 </el-button>
        <el-button
          :icon="MagicStick"
          :loading="aiSuggestionLoading"
          :disabled="statsLoading || !!statsError || stats.totalCards === 0"
          @click="handleAiSuggestion"
        >
          AI 复习建议
        </el-button>
      </div>
    </section>

    <LpStatePanel v-if="aiSuggestionError" state="error" :description="aiSuggestionError" @retry="handleAiSuggestion" />

    <section v-if="aiSuggestionContent || aiSuggestionLoading" class="ai-panel" aria-label="AI 复习建议">
      <LpSectionHeading title="AI 复习建议">
        <template #aside>
          <el-button size="small" text @click="aiSuggestionContent = ''">收起</el-button>
        </template>
      </LpSectionHeading>
      <div v-if="aiSuggestionContent" class="ai-suggestion-content">
        <MarkdownRenderer :content="aiSuggestionContent" />
        <div v-if="aiSuggestionLoading" class="streaming-tip">
          <el-icon class="is-loading"><Loading /></el-icon> AI 正在生成建议...
        </div>
      </div>
    </section>

    <LpStatePanel v-if="sessionError" state="error" :description="sessionError" @retry="startReview" />
    <ReviewSessionPanel ref="reviewSession" :cards="dueCards" @reviewed="loadStats" />

    <!-- 全部卡片列表 -->
    <el-card v-if="showAllCards" shadow="never" class="all-cards-card">
      <template #header>
        <div class="card-header">
          <span>复习计划卡片 ({{ allCards.length }})</span>
          <el-button size="small" @click="loadAllCards">刷新</el-button>
        </div>
      </template>

      <LpStatePanel v-if="cardsLoading" state="loading" loading-label="正在读取复习卡片" />
      <LpStatePanel v-else-if="allCardsError" state="error" :description="allCardsError" @retry="loadAllCards" />
      <LpEmptyState
        v-else-if="allCards.length === 0"
        title="暂无复习卡片"
        description="刷题后自动加入；也可以同步错题到复习计划。"
      />
      <template v-else>
        <p v-if="allCards.some((card) => card.availableForReview === false)" class="card-availability-note">
          缺少判分依据的卡片暂不安排作答，答案配置完成后会恢复。卡片与已有复习进度保留。
        </p>
        <el-table :data="allCards" stripe style="width: 100%">
          <el-table-column label="题目" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.questionContent }}</span>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="80">
            <template #default="{ row }">
              <el-tag size="small">{{ practiceQuestionTypeLabel(row.questionType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="课程" width="120" show-overflow-tooltip prop="courseName" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.availableForReview === false ? 'info' : statusTagType(row.statusLabel)">{{
                row.availableForReview === false ? '待配置答案' : row.statusLabel
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="间隔" width="80" prop="intervalDays" />
          <el-table-column label="下次复习" width="120" prop="nextReviewDate" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button
                size="small"
                type="danger"
                plain
                :loading="removingQuestionIds.has(row.questionId)"
                :disabled="resettingQuestionIds.has(row.questionId)"
                @click="handleRemove(row.questionId)"
              >
                移出
              </el-button>
              <el-button
                size="small"
                :loading="resettingQuestionIds.has(row.questionId)"
                :disabled="removingQuestionIds.has(row.questionId)"
                @click="handleReset(row.questionId)"
              >
                重置
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount, onMounted } from 'vue'
import { errorMessage } from '@/utils/errors'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Loading, MagicStick, Reading, View } from '@element-plus/icons-vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import ReviewSessionPanel from '@/components/review/ReviewSessionPanel.vue'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import {
  getReviewStats,
  getDueReviewCards,
  getAllReviewCards,
  removeFromReviewPlan,
  resetReviewProgress,
  syncWrongQuestionsToReview,
  type ReviewStatsVO,
  type ReviewScheduleVO,
} from '@/api/review'
import { useReviewAiSuggestion } from './useReviewAiSuggestion'
import { reviewStatusTag as statusTagType } from '@/components/review/reviewSessionPresentation'
import { positiveQueryNumber } from './reviewPresentation'
import { practiceQuestionTypeLabel } from './practiceSessionPresentation'

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

const dueCards = ref<ReviewScheduleVO[]>([])
const statsLoading = ref(false)
const statsError = ref('')
const startingReview = ref(false)
const sessionError = ref('')
const reviewSession = ref<InstanceType<typeof ReviewSessionPanel>>()

// 卡片列表
const showAllCards = ref(false)
const allCards = ref<ReviewScheduleVO[]>([])
const cardsLoading = ref(false)
const allCardsError = ref('')
const syncing = ref(false)
const removingQuestionIds = ref(new Set<number>())
const resettingQuestionIds = ref(new Set<number>())

// AI 复习建议
const aiSuggestion = useReviewAiSuggestion(() => alive)
const { loading: aiSuggestionLoading, content: aiSuggestionContent, error: aiSuggestionError } = aiSuggestion

let generation = 0
let alive = true

const masteredPercent = computed(() => {
  if (stats.value.totalCards === 0) return 0
  return Math.round((stats.value.masteredCards / stats.value.totalCards) * 100)
})

async function loadStats() {
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  statsLoading.value = true
  statsError.value = ''
  try {
    const { data } = await getReviewStats({ errorDisplay: 'inline' })
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) stats.value = data
  } catch {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) {
      statsError.value = '复习计划暂时无法加载，请重试'
    }
  } finally {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) statsLoading.value = false
  }
}

async function loadDueCards() {
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  sessionError.value = ''
  try {
    const { data } = await getDueReviewCards(
      targetCourseId.value,
      30,
      targetQuestionId.value,
      targetKnowledgePointId.value,
      { errorDisplay: 'inline' },
    )
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) dueCards.value = data
  } catch {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) {
      sessionError.value = '待复习题目暂时无法加载，请重试'
    }
  }
}

async function loadAllCards() {
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  cardsLoading.value = true
  allCardsError.value = ''
  try {
    const { data } = await getAllReviewCards(undefined, { errorDisplay: 'inline' })
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) allCards.value = data
  } catch {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion())
      allCardsError.value = '复习卡片暂时无法加载，请重试'
  } finally {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) cardsLoading.value = false
  }
}

async function toggleAllCards() {
  showAllCards.value = !showAllCards.value
  if (showAllCards.value) await loadAllCards()
}

async function startReview() {
  if (startingReview.value || reviewSession.value?.reviewing) return
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  startingReview.value = true
  sessionError.value = ''
  try {
    await loadDueCards()
    if (!alive || requestGeneration !== generation || session !== getAuthSessionVersion()) return
    if (sessionError.value) return
    if (dueCards.value.length === 0) {
      ElMessage.info('没有待复习的题目')
      return
    }
    reviewSession.value?.start()
  } finally {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) startingReview.value = false
  }
}

async function handleRemove(questionId: number) {
  if (removingQuestionIds.value.has(questionId) || resettingQuestionIds.value.has(questionId)) return
  try {
    await ElMessageBox.confirm('确定将该题目移出复习计划？', '确认')
  } catch {
    return
  }
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  removingQuestionIds.value = new Set(removingQuestionIds.value).add(questionId)
  try {
    await removeFromReviewPlan(questionId, { errorDisplay: 'inline' })
    if (!alive || requestGeneration !== generation || session !== getAuthSessionVersion()) return
    ElMessage.success('已移出')
    await loadAllCards()
    await loadStats()
  } catch (e) {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion())
      ElMessage.error(errorMessage(e, '操作失败'))
  } finally {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) {
      const next = new Set(removingQuestionIds.value)
      next.delete(questionId)
      removingQuestionIds.value = next
    }
  }
}

async function handleSyncWrongQuestions() {
  if (syncing.value) return
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  syncing.value = true
  try {
    const { data } = await syncWrongQuestionsToReview({ errorDisplay: 'inline' })
    if (!alive || requestGeneration !== generation || session !== getAuthSessionVersion()) return
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
    if (alive && requestGeneration === generation && session === getAuthSessionVersion())
      ElMessage.error(errorMessage(e, '同步失败'))
  } finally {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) syncing.value = false
  }
}

async function handleReset(questionId: number) {
  if (resettingQuestionIds.value.has(questionId) || removingQuestionIds.value.has(questionId)) return
  try {
    await ElMessageBox.confirm('确定重置该题目的复习进度？', '确认')
  } catch {
    return
  }
  const requestGeneration = generation
  const session = getAuthSessionVersion()
  resettingQuestionIds.value = new Set(resettingQuestionIds.value).add(questionId)
  try {
    await resetReviewProgress(questionId, { errorDisplay: 'inline' })
    if (!alive || requestGeneration !== generation || session !== getAuthSessionVersion()) return
    ElMessage.success('已重置')
    await loadAllCards()
  } catch (e) {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion())
      ElMessage.error(errorMessage(e, '操作失败'))
  } finally {
    if (alive && requestGeneration === generation && session === getAuthSessionVersion()) {
      const next = new Set(resettingQuestionIds.value)
      next.delete(questionId)
      resettingQuestionIds.value = next
    }
  }
}

function handleAiSuggestion() {
  return aiSuggestion.request()
}

onMounted(async () => {
  await loadStats()
  if (targetCourseId.value || targetQuestionId.value) {
    await startReview()
  }
})

const unsubscribeSession = onAuthSessionChange(() => {
  generation++
  aiSuggestion.clear()
  stats.value = { ...stats.value, totalCards: 0, dueToday: 0, reviewedToday: 0 }
  dueCards.value = []
  allCards.value = []
  allCardsError.value = ''
  statsError.value = ''
  sessionError.value = ''
  startingReview.value = false
  syncing.value = false
  removingQuestionIds.value = new Set()
  resettingQuestionIds.value = new Set()
})
onBeforeUnmount(() => {
  alive = false
  generation++
  aiSuggestion.cancel()
  unsubscribeSession()
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

.all-cards-card {
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

.card-availability-note {
  margin: 0 0 var(--lp-space-4);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-relaxed);
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
