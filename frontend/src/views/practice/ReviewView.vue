<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Brain, Download, RefreshCw, Sparkles } from '@lucide/vue'
import { LtButton, LtCard, LtSheet } from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'
import QuestionProse from '@/views/question/QuestionProse.vue'
import { errorMessage } from '@/utils/errors'
import { getAuthSessionVersion, onAuthSessionChange } from '@/utils/auth'
import {
  getAllReviewCards,
  getDueReviewCards,
  getReviewStats,
  removeFromReviewPlan,
  resetReviewProgress,
  syncWrongQuestionsToReview,
  type ReviewScheduleVO,
  type ReviewStatsVO,
} from '@/api/review'
import { practiceQuestionTypeLabel } from './practiceSessionPresentation'
import { positiveQueryNumber } from './reviewPresentation'
import { useReviewAiSuggestion } from './useReviewAiSuggestion'
import ReviewClassicSession from './ReviewClassicSession.vue'

useLivingTheme({ dark: true })
const route = useRoute()
const router = useRouter()
const targetCourseId = computed(() => positiveQueryNumber(route.query.courseId))
const targetQuestionId = computed(() => positiveQueryNumber(route.query.questionId))
const targetKnowledgePointId = computed(() => positiveQueryNumber(route.query.knowledgePointId))
const targetKnowledgePointName = computed(() =>
  typeof route.query.knowledgePointName === 'string' ? route.query.knowledgePointName : '',
)
const stats = ref<ReviewStatsVO | null>(null)
const dueCards = ref<ReviewScheduleVO[]>([])
const allCards = ref<ReviewScheduleVO[]>([])
const statsLoading = ref(true)
const cardsLoading = ref(false)
const startingReview = ref(false)
const syncing = ref(false)
const showAllCards = ref(false)
const statsError = ref('')
const sessionError = ref('')
const allCardsError = ref('')
const notice = ref('')
const reviewSession = ref<InstanceType<typeof ReviewClassicSession> | null>(null)
const removing = ref(new Set<number>())
const resetting = ref(new Set<number>())
const action = ref<{ kind: 'remove' | 'reset'; card: ReviewScheduleVO } | null>(null)
let generation = 0
let alive = true
const ai = useReviewAiSuggestion(() => alive)
const reviewPercent = computed(() =>
  stats.value?.totalCards ? Math.round(((stats.value.reviewCards || 0) / stats.value.totalCards) * 100) : 0,
)
const actionOpen = computed({
  get: () => action.value !== null,
  set: (open: boolean) => {
    if (!open) action.value = null
  },
})
const canStart = computed(
  () =>
    !!stats.value &&
    stats.value.dueToday > 0 &&
    !statsLoading.value &&
    !statsError.value &&
    !reviewSession.value?.reviewing,
)
function current() {
  const version = generation
  const session = getAuthSessionVersion()
  return () => alive && version === generation && session === getAuthSessionVersion()
}
async function loadStats() {
  const valid = current()
  statsLoading.value = true
  statsError.value = ''
  try {
    const response = await getReviewStats({ errorDisplay: 'inline' })
    if (valid()) stats.value = response.data
  } catch {
    if (valid()) statsError.value = '复习计划暂时无法加载，请重试'
  } finally {
    if (valid()) statsLoading.value = false
  }
}
async function loadDueCards() {
  const valid = current()
  sessionError.value = ''
  try {
    const response = await getDueReviewCards(
      targetCourseId.value,
      30,
      targetQuestionId.value,
      targetKnowledgePointId.value,
      { errorDisplay: 'inline' },
    )
    if (valid()) dueCards.value = response.data
  } catch {
    if (valid()) sessionError.value = '待复习题目暂时无法加载，请重试'
  }
}
async function loadAllCards() {
  const valid = current()
  cardsLoading.value = true
  allCardsError.value = ''
  try {
    const response = await getAllReviewCards(undefined, { errorDisplay: 'inline' })
    if (valid()) allCards.value = response.data
  } catch {
    if (valid()) allCardsError.value = '复习卡片暂时无法加载，请重试'
  } finally {
    if (valid()) cardsLoading.value = false
  }
}
async function startReview() {
  if (startingReview.value || reviewSession.value?.reviewing) return
  const valid = current()
  startingReview.value = true
  notice.value = ''
  await loadDueCards()
  if (!valid()) return
  if (!sessionError.value && dueCards.value.length) reviewSession.value?.start()
  else if (!sessionError.value) notice.value = '当前没有待复习的题目'
  startingReview.value = false
}
async function toggleCards() {
  showAllCards.value = !showAllCards.value
  if (showAllCards.value) await loadAllCards()
}
async function sync() {
  if (syncing.value) return
  const valid = current()
  syncing.value = true
  notice.value = ''
  try {
    const response = await syncWrongQuestionsToReview({ errorDisplay: 'inline' })
    if (!valid()) return
    notice.value = response.data.syncedCount
      ? `已同步 ${response.data.syncedCount} 道错题到复习计划`
      : '暂无新的错题需要同步'
    await loadStats()
    if (showAllCards.value) await loadAllCards()
  } catch (cause) {
    if (valid()) notice.value = errorMessage(cause, '同步失败')
  } finally {
    if (valid()) syncing.value = false
  }
}
async function confirmAction() {
  const item = action.value
  if (!item) return
  const id = item.card.questionId
  if (removing.value.has(id) || resetting.value.has(id)) return
  const valid = current()
  action.value = null
  const set = item.kind === 'remove' ? removing : resetting
  set.value = new Set(set.value).add(id)
  notice.value = ''
  try {
    if (item.kind === 'remove') await removeFromReviewPlan(id, { errorDisplay: 'inline' })
    else await resetReviewProgress(id, { errorDisplay: 'inline' })
    if (!valid()) return
    notice.value = item.kind === 'remove' ? '已移出复习计划' : '已重置复习进度'
    await loadAllCards()
    await loadStats()
  } catch (cause) {
    if (valid()) notice.value = errorMessage(cause, '操作失败')
  } finally {
    if (valid()) {
      const next = new Set(set.value)
      next.delete(id)
      set.value = next
    }
  }
}
async function clearKnowledgePointFilter() {
  const query = { ...route.query }
  delete query.knowledgePointId
  delete query.knowledgePointName
  await router.replace({ query })
  await loadDueCards()
}
function goBack() {
  if (typeof window !== 'undefined' && typeof window.history.state?.back === 'string') router.back()
  else void router.push('/review')
}
onMounted(async () => {
  await loadStats()
  if (targetCourseId.value || targetQuestionId.value) await startReview()
})
const off = onAuthSessionChange(() => {
  generation++
  ai.clear()
  stats.value = null
  dueCards.value = []
  allCards.value = []
  notice.value = ''
  sessionError.value = ''
  allCardsError.value = ''
  statsError.value = ''
  startingReview.value = false
  syncing.value = false
  removing.value = new Set()
  resetting.value = new Set()
  action.value = null
})
onBeforeUnmount(() => {
  alive = false
  generation++
  ai.cancel()
  off()
})
</script>
<template>
  <main class="page lt" data-appearance="dark">
    <header class="head">
      <LtButton class="back" variant="quiet" aria-label="返回" @click="goBack"
        ><template #icon><ArrowLeft aria-hidden="true" /></template>返回</LtButton
      >
      <div class="title-row">
        <div>
          <h1>复习工具</h1>
          <p>在这里同步错题、获得复习建议，并完成今天到期的题。</p>
        </div>
        <LtButton data-testid="review-start" variant="primary" :disabled="!canStart" @click="startReview"
          ><template #icon><Brain aria-hidden="true" /></template
          >{{ startingReview ? '正在准备' : '开始复习' }}</LtButton
        >
      </div>
      <button v-if="targetKnowledgePointName" type="button" class="chip" @click="clearKnowledgePointFilter">
        知识点：{{ targetKnowledgePointName }} ×
      </button>
    </header>
    <LtCard v-if="statsLoading" radius="xl" class="loading" aria-busy="true"
      ><span class="skeleton wide" /><span class="skeleton" /><span class="sr-only">正在读取复习计划</span></LtCard
    >
    <div v-else-if="statsError" class="state">
      <h2>复习计划没有加载出来</h2>
      <p>{{ statsError }}</p>
      <LtButton variant="primary" @click="loadStats"
        ><template #icon><RefreshCw aria-hidden="true" /></template>重试</LtButton
      >
    </div>
    <template v-else
      ><section v-if="stats" class="overview" aria-label="复习概况">
        <LtCard
          v-for="item in [
            { label: '到期可复习', value: stats.dueToday },
            { label: '最近评分在今天', value: stats.reviewedToday },
            { label: '复习卡片', value: stats.totalCards },
          ]"
          :key="item.label"
          radius="lg"
          ><p>{{ item.label }}</p>
          <strong>{{ item.value }}</strong></LtCard
        >
      </section>
      <LtCard v-if="stats && stats.totalCards" radius="xl" class="progress"
        ><div class="section-title">
          <h2>间隔复习 {{ stats.reviewCards }} / {{ stats.totalCards }} 张卡片</h2>
          <span>{{ reviewPercent }}%</span>
        </div>
        <div class="track"><span :style="{ width: `${reviewPercent}%` }" /></div>
        <p>
          待首次 FSRS 评分 {{ stats.pendingRatingCards }} · 初学排期 {{ stats.learningCards }} · 重学排期
          {{ stats.relearningCards }} · 最近评分答错 {{ stats.lastAgainCards }} · 最近自评困难 {{ stats.lastHardCards }}
        </p></LtCard
      >
      <div v-else class="state">
        <h2>复习计划仍为空</h2>
        <p>完成练习或同步错题后，待复习内容会出现在这里。</p>
      </div></template
    >
    <section class="tools" aria-label="复习工具">
      <LtButton data-testid="review-toggle-cards" @click="toggleCards">{{
        showAllCards ? '收起卡片列表' : '查看全部卡片'
      }}</LtButton
      ><LtButton :disabled="syncing" @click="sync"
        ><template #icon><Download aria-hidden="true" /></template
        >{{ syncing ? '正在同步' : '同步错题到复习' }}</LtButton
      ><LtButton :disabled="!stats?.totalCards || ai.loading.value" @click="ai.request"
        ><template #icon><Sparkles aria-hidden="true" /></template
        >{{ ai.loading.value ? '正在生成' : 'AI 复习建议' }}</LtButton
      >
    </section>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <div v-if="ai.error.value" class="state">
      <h2>AI 复习建议暂时无法加载</h2>
      <p>{{ ai.error.value }}</p>
      <LtButton @click="ai.request">重试</LtButton>
    </div>
    <LtCard v-if="ai.content.value || ai.loading.value" radius="xl" class="suggestion"
      ><div class="section-title">
        <h2>AI 复习建议</h2>
        <LtButton size="sm" variant="quiet" @click="ai.clear">收起</LtButton>
      </div>
      <QuestionProse v-if="ai.content.value" :content="ai.content.value" />
      <p v-if="ai.loading.value" role="status">AI 正在生成建议…</p></LtCard
    >
    <div v-if="sessionError" class="state">
      <h2>待复习题目暂时无法加载</h2>
      <p>{{ sessionError }}</p>
      <LtButton @click="startReview">重试</LtButton>
    </div>
    <ReviewClassicSession ref="reviewSession" :cards="dueCards" @reviewed="loadStats" />
    <section v-if="showAllCards" class="cards" aria-labelledby="cards-title">
      <div class="section-title">
        <h2 id="cards-title">
          {{
            cardsLoading
              ? '正在加载复习卡片'
              : allCardsError
                ? '复习卡片暂时无法加载'
                : `复习计划卡片 (${allCards.length})`
          }}
        </h2>
        <LtButton size="sm" :disabled="cardsLoading" @click="loadAllCards"
          ><template #icon><RefreshCw aria-hidden="true" /></template>刷新</LtButton
        >
      </div>
      <LtCard v-if="cardsLoading" radius="xl" class="loading"
        ><span class="skeleton wide" /><span class="skeleton"
      /></LtCard>
      <div v-else-if="allCardsError" class="state">
        <h2>复习卡片暂时无法加载</h2>
        <p>{{ allCardsError }}</p>
        <LtButton @click="loadAllCards">重试</LtButton>
      </div>
      <div v-else-if="!allCards.length" class="state">
        <h2>暂无复习卡片</h2>
        <p>刷题后自动加入；也可以同步错题到复习计划。</p>
      </div>
      <LtCard v-else radius="xl" padding="none" class="card-list"
        ><p v-if="allCards.some((card) => card.availableForReview === false)" class="availability">
          缺少判分依据的卡片暂不安排作答，答案配置完成后会恢复。卡片与已有复习进度保留。
        </p>
        <ol>
          <li v-for="card in allCards" :key="card.id">
            <div>
              <p>{{ card.questionContent }}</p>
              <span
                >{{ card.courseName }} · {{ practiceQuestionTypeLabel(card.questionType) }} ·
                {{ card.availableForReview === false ? '待配置答案' : card.statusLabel }} · 间隔
                {{ card.intervalDays }} 天 · 下次 {{ card.nextReviewDate }}</span
              >
            </div>
            <div class="row-actions">
              <LtButton
                size="sm"
                variant="quiet"
                :disabled="resetting.has(card.questionId) || removing.has(card.questionId)"
                data-testid="review-card-remove"
                @click="action = { kind: 'remove', card }"
                >{{ removing.has(card.questionId) ? '正在移出' : '移出' }}</LtButton
              ><LtButton
                size="sm"
                :disabled="resetting.has(card.questionId) || removing.has(card.questionId)"
                data-testid="review-card-reset"
                @click="action = { kind: 'reset', card }"
                >{{ resetting.has(card.questionId) ? '正在重置' : '重置' }}</LtButton
              >
            </div>
          </li>
        </ol></LtCard
      >
    </section>
    <LtSheet v-model:open="actionOpen" :title="action?.kind === 'remove' ? '移出复习计划' : '重置复习进度'"
      ><p class="sheet-copy">
        {{ action?.kind === 'remove' ? '确定将这道题移出复习计划？' : '确定重置这道题的复习进度？' }}
      </p>
      <div class="sheet-actions">
        <LtButton @click="action = null">取消</LtButton
        ><LtButton data-testid="review-confirm-action" variant="primary" @click="confirmAction">确认</LtButton>
      </div></LtSheet
    >
  </main>
</template>
<style scoped>
.page {
  max-width: calc(var(--lt-space-10) * 14);
  margin: 0 auto;
  padding: 0 var(--lt-space-7) var(--lt-space-10);
}
.head {
  padding: var(--lt-space-7) 0 var(--lt-space-6);
}
.back {
  margin-left: calc(-1 * var(--lt-space-2));
}
.title-row,
.tools,
.section-title,
.row-actions,
.sheet-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-4);
}
h1,
h2,
p {
  margin: 0;
}
h1 {
  font-size: var(--lt-text-title1);
}
.title-row p,
.progress p,
.card-list span,
.notice,
.state p {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.title-row p {
  margin-top: var(--lt-space-2);
}
.chip {
  margin-top: var(--lt-space-3);
  padding: var(--lt-space-1) var(--lt-space-2);
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label-2);
  font: inherit;
  font-size: var(--lt-text-footnote);
  cursor: pointer;
}
.overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--lt-space-3);
}
.overview p {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.overview strong {
  display: block;
  margin-top: var(--lt-space-2);
  font: 600 var(--lt-text-title1)/1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}
.progress,
.suggestion {
  display: grid;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-4);
}
.section-title h2 {
  font-size: var(--lt-text-title3);
}
.section-title span {
  color: var(--lt-label-2);
  font: 500 var(--lt-text-footnote)/1 var(--lt-font-rounded);
}
.track {
  overflow: hidden;
  height: var(--lt-space-2);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
}
.track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--lt-green);
}
.tools {
  justify-content: flex-start;
  flex-wrap: wrap;
  margin-top: var(--lt-space-5);
}
.notice {
  margin: var(--lt-space-3) var(--lt-space-2);
  color: var(--lt-green-text);
}
.state {
  display: grid;
  justify-items: start;
  gap: var(--lt-space-3);
  padding: var(--lt-space-8) var(--lt-space-2);
}
.state h2 {
  font-size: var(--lt-text-title3);
}
.cards {
  margin-top: var(--lt-space-7);
}
.card-list {
  overflow: hidden;
  margin-top: var(--lt-space-3);
}
.availability {
  padding: var(--lt-space-4) var(--lt-space-6);
  color: var(--lt-orange-text);
  background: var(--lt-orange-soft);
}
ol {
  margin: 0;
  padding: 0;
  list-style: none;
}
li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-4);
  padding: var(--lt-space-4) var(--lt-space-6);
}
li + li {
  border-top: thin solid var(--lt-separator);
}
li p {
  font-size: var(--lt-text-body);
}
li span {
  display: block;
  margin-top: var(--lt-space-1);
}
.row-actions {
  flex: none;
}
.loading {
  display: grid;
  gap: var(--lt-space-3);
}
.skeleton {
  display: block;
  height: var(--lt-space-3);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
}
.wide {
  width: 70%;
}
.sheet-actions {
  justify-content: flex-end;
  margin-top: var(--lt-space-5);
}
.sheet-copy {
  color: var(--lt-label-2);
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
