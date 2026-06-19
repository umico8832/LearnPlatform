<template>
  <div class="review-container">
    <h2 style="margin-bottom: 20px;">🧠 智能复习</h2>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card stat-due">
          <div class="stat-value">{{ stats.dueToday }}</div>
          <div class="stat-label">今日待复习</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card stat-reviewed">
          <div class="stat-value">{{ stats.reviewedToday }}</div>
          <div class="stat-label">今日已完成</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card stat-mastered">
          <div class="stat-value">{{ stats.masteredCards }}</div>
          <div class="stat-label">已掌握</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card stat-streak">
          <div class="stat-value">{{ stats.streakDays }}<span style="font-size:14px">天</span></div>
          <div class="stat-label">连续复习</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 学习进度条 -->
    <el-card shadow="hover" v-if="stats.totalCards > 0" style="margin-bottom: 20px;">
      <div style="display:flex; justify-content:space-between; margin-bottom:8px;">
        <span style="font-weight:600;">掌握进度</span>
        <span style="color:#909399; font-size:13px;">{{ stats.totalCards }} 张卡片</span>
      </div>
      <el-progress :percentage="masteredPercent" :format="() => `${stats.masteredCards}/${stats.totalCards}`" />
      <div style="display:flex; gap:20px; margin-top:12px; font-size:13px; color:#606266;">
        <span><el-tag type="info" size="small">新卡片</el-tag> {{ stats.newCards }}</span>
        <span><el-tag type="warning" size="small">学习中</el-tag> {{ stats.learningCards }}</span>
        <span><el-tag type="success" size="small">已掌握</el-tag> {{ stats.masteredCards }}</span>
        <span><el-tag type="danger" size="small">困难</el-tag> {{ stats.difficultCards }}</span>
      </div>
    </el-card>

    <!-- 操作区 -->
    <el-card shadow="hover" style="margin-bottom: 20px;">
      <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap: wrap; gap:12px;">
        <div>
          <el-button type="primary" size="large" :disabled="stats.dueToday === 0" @click="startReview">
            📖 开始复习
            <el-badge v-if="stats.overdue > 0" :value="`${stats.overdue}逾期`" type="danger" style="margin-left:8px" />
          </el-button>
          <el-button size="large" @click="showAllCards = !showAllCards">
            {{ showAllCards ? '收起卡片列表' : '查看全部卡片' }}
          </el-button>
          <el-button size="large" :loading="syncing" @click="handleSyncWrongQuestions">
            📥 同步错题到复习
          </el-button>
          <el-button size="large" :loading="aiSuggestionLoading" :disabled="stats.totalCards === 0" @click="handleAiSuggestion">
            🤖 AI 复习建议
          </el-button>
        </div>
        <el-text type="info" size="small">平均简易因子: {{ stats.avgEaseFactor?.toFixed(2) ?? '-' }} | 连续 {{ stats.streakDays }} 天</el-text>
      </div>
    </el-card>

    <!-- AI 复习建议区域 -->
    <el-card v-if="aiSuggestionContent" shadow="hover" style="margin-bottom: 20px;">
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <span>🤖 AI 复习建议</span>
          <el-button size="small" text @click="aiSuggestionContent = ''">收起</el-button>
        </div>
      </template>
      <div class="ai-suggestion-content">
        <MarkdownRenderer :content="aiSuggestionContent" />
        <div v-if="aiSuggestionLoading" style="color:#909399; margin-top:8px; font-size:13px;">
          <el-icon class="is-loading"><Loading /></el-icon> AI 正在生成建议...
        </div>
      </div>
    </el-card>

    <!-- 复习会话区域（当前待复习卡片） -->
    <el-card v-if="reviewing && currentCard" shadow="hover" style="margin-bottom: 20px;" class="review-session">
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <span>复习进度: {{ currentIndex + 1 }} / {{ dueCards.length }}</span>
          <el-tag :type="statusTagType(currentCard.statusLabel)" size="small">{{ currentCard.statusLabel }}</el-tag>
        </div>
      </template>

      <!-- 进度条 -->
      <el-progress :percentage="Math.round(((currentIndex) / dueCards.length) * 100)" :show-text="false" style="margin-bottom: 16px" />

      <!-- 题目信息 -->
      <div class="question-info">
        <div style="display:flex; gap:8px; margin-bottom:12px; flex-wrap:wrap;">
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
      <div style="margin-top: 16px;">
        <el-input
          v-model="userAnswer"
          type="textarea"
          :rows="3"
          placeholder="输入你的答案..."
          :disabled="answerSubmitted"
        />
      </div>

      <!-- 操作按钮 -->
      <div style="margin-top: 12px; display:flex; gap:12px; flex-wrap:wrap;">
        <el-button type="primary" @click="submitCurrentAnswer" :disabled="!userAnswer.trim() || answerSubmitted" :loading="submitting">
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
      :description="lastCorrect
        ? `下次复习: ${currentCard.intervalDays} 天后 | 新间隔: ${lastResult?.intervalDays} 天`
        : `间隔已重置为 1 天，请继续加油！`"
      show-icon
      :closable="false"
      style="margin-top: 16px;"
    />

      <div v-if="answerSubmitted" style="margin-top: 12px;">
        <el-button type="primary" @click="nextCard">
          {{ currentIndex < dueCards.length - 1 ? '下一题 →' : '完成复习 🎉' }}
        </el-button>
      </div>
    </el-card>

    <!-- 复习完成 -->
    <el-card v-if="reviewComplete" shadow="hover" style="margin-bottom: 20px; text-align:center;">
      <div style="font-size:48px; margin-bottom:16px;">🎉</div>
      <h3>今日复习完成！</h3>
      <p style="color:#606266; margin:12px 0;">共复习 {{ reviewedCount }} 题，正确 {{ correctCount }} 题</p>
      <el-button type="primary" @click="reviewComplete = false; reviewing = false;">返回</el-button>
    </el-card>

    <!-- 全部卡片列表 -->
    <el-card v-if="showAllCards" shadow="hover">
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <span>复习计划卡片 ({{ allCards.length }})</span>
          <el-button size="small" @click="loadAllCards">刷新</el-button>
        </div>
      </template>

      <el-table :data="allCards" stripe style="width: 100%" v-loading="cardsLoading" empty-text="暂无复习卡片，刷题后自动加入">
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
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

// 统计数据
const stats = ref<ReviewStatsVO>({
  totalCards: 0, dueToday: 0, overdue: 0, reviewedToday: 0,
  newCards: 0, learningCards: 0, masteredCards: 0, difficultCards: 0,
  streakDays: 0, avgEaseFactor: 2.5,
})

// 复习会话
const reviewing = ref(false)
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
    case '新卡片': return 'info'
    case '学习中': return 'warning'
    case '已掌握': return 'success'
    case '困难': return 'danger'
    default: return 'info'
  }
}

async function loadStats() {
  try {
    const { data } = await getReviewStats()
    stats.value = data
  } catch (e) {
    // ignore
  }
}

async function loadDueCards() {
  try {
    const { data } = await getDueReviewCards(undefined, 30)
    dueCards.value = data
  } catch (e) {
    ElMessage.error('获取待复习题目失败')
  }
}

async function loadAllCards() {
  cardsLoading.value = true
  try {
    const { data } = await getAllReviewCards()
    allCards.value = data
  } catch (e) {
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
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
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
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
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
  } catch (e: any) {
    ElMessage.error(e?.message || '同步失败')
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
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
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
  } catch (e: any) {
    if (e.name !== 'AbortError') {
      ElMessage.error(e?.message || 'AI 复习建议获取失败')
    }
  } finally {
    aiSuggestionLoading.value = false
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.review-container {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}
.stats-row {
  margin-bottom: 20px;
}
.stat-card {
  text-align: center;
  padding: 8px 0;
}
.stat-value {
  font-size: 32px;
  font-weight: 700;
  line-height: 1.2;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
.stat-due .stat-value { color: #409eff; }
.stat-reviewed .stat-value { color: #67c23a; }
.stat-mastered .stat-value { color: #e6a23c; }
.stat-streak .stat-value { color: #f56c6c; }

.review-session {
  border-left: 4px solid #409eff;
}
.question-info {
  margin-bottom: 8px;
}
.question-content {
  font-size: 16px;
  line-height: 1.6;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  white-space: pre-wrap;
}
.ai-suggestion-content {
  line-height: 1.8;
  font-size: 14px;
}
</style>