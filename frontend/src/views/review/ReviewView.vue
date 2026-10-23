<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, ChevronLeft, RotateCcw, X } from '@lucide/vue'
import { LtButton, LtCard, LtIconButton } from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'
import { redoWhen } from '@/utils/redoDate'
import ReviewCardRow from './ReviewCardRow.vue'
import ReviewDueRow from './ReviewDueRow.vue'
import { useReview } from './useReview'

/**
 * 复习页（ADR-0011 新基线，主线版）：今天到期的题排成队列并说明为什么回来，再列出全部卡片的排期。
 * 进入方式与错题页“重做”一致；AI 复习建议、同步错题与页内作答留在旧复习页（`ReviewClassic`）。
 */
defineOptions({ name: 'ReviewView' })
useLivingTheme()

const router = useRouter()
const review = useReview()
const { status, due, cards, stats, upcoming } = review

const CARD_PREVIEW = 12
const showAll = ref(false)
const visibleCards = computed(() => (showAll.value ? cards.value : cards.value.slice(0, CARD_PREVIEW)))

const canGoBack = typeof window !== 'undefined' && typeof window.history.state?.back === 'string'
function goBack() {
  if (canGoBack) router.back()
  else void router.push(review.courseId.value ? `/my-courses/${review.courseId.value}` : '/my-courses')
}

const chips = computed(() => {
  const list: { key: 'courseId' | 'knowledgePointId' | 'questionId'; text: string }[] = []
  if (review.courseId.value) list.push({ key: 'courseId', text: review.courseName.value ?? '当前课程' })
  if (review.knowledgePointId.value)
    list.push({ key: 'knowledgePointId', text: review.knowledgePointName.value || '当前知识点' })
  if (review.questionId.value) list.push({ key: 'questionId', text: '只看这一道题' })
  return list
})

const upcomingText = computed(() => (upcoming.value ? redoWhen(upcoming.value).text : ''))

onMounted(() => review.load())
</script>

<template>
  <main class="page">
    <header class="head">
      <LtIconButton class="back" label="返回" @click="goBack">
        <ChevronLeft aria-hidden="true" />
      </LtIconButton>
      <h1 class="title">复习</h1>
      <p class="lead">到期的题再独立做一次。只有不看提示的作答会更新下次复习的日期。</p>
      <ul v-if="chips.length" class="chips" aria-label="当前范围">
        <li v-for="chip in chips" :key="chip.key" class="chip">
          {{ chip.text }}
          <button
            type="button"
            class="chip-clear"
            :aria-label="`取消“${chip.text}”这个范围`"
            @click="review.clearScope(chip.key)"
          >
            <X :size="14" aria-hidden="true" />
          </button>
        </li>
      </ul>
    </header>

    <LtCard v-if="status === 'loading'" radius="xl" padding="none" class="list" aria-busy="true">
      <div v-for="n in 3" :key="n" class="skeleton-row">
        <span class="skeleton" style="width: 64px; height: 16px" />
        <div>
          <span class="skeleton" style="width: 72%; height: 16px" />
          <span class="skeleton" style="width: 30%; height: 12px; margin-top: 10px" />
        </div>
      </div>
      <span class="sr-only">正在读取复习计划</span>
    </LtCard>

    <div v-else-if="status === 'error'" class="state">
      <h2 class="state-title">复习计划没有加载出来</h2>
      <p class="state-text">请检查网络后重试。</p>
      <LtButton variant="primary" @click="review.load()">
        <template #icon><RotateCcw aria-hidden="true" /></template>
        重试
      </LtButton>
    </div>

    <template v-else>
      <section aria-labelledby="due-title">
        <div class="section-head">
          <h2 id="due-title" class="section-title">
            今天到期<span v-if="due.length" class="sr-only">，</span
            ><span v-if="due.length" class="count">{{ due.length }}</span
            ><span v-if="due.length" class="sr-only">道</span>
          </h2>
          <LtButton v-if="due.length" variant="primary" @click="review.open(due[0])">
            从第一道开始
            <template #trailing><ArrowRight aria-hidden="true" /></template>
          </LtButton>
        </div>

        <LtCard v-if="due.length" radius="xl" padding="none" class="list">
          <ReviewDueRow v-for="card in due" :key="card.id" :card="card" @open="review.open(card)" />
        </LtCard>
        <div v-else class="quiet">
          <p class="quiet-title">{{ review.scoped.value ? '这个范围里今天没有到期的题' : '今天没有到期的题' }}</p>
          <p class="quiet-text">
            <template v-if="upcomingText"
              >下一道{{ upcomingText === '明天' ? '明天' : `在 ${upcomingText} ` }}到期。</template
            >
            <template v-else-if="cards.length === 0">答错的题和独立做过的题会自动加入复习计划。</template>
          </p>
        </div>
      </section>

      <section v-if="cards.length" class="all" aria-labelledby="cards-title">
        <div class="section-head">
          <h2 id="cards-title" class="section-title">
            全部卡片<span class="sr-only">，共</span><span class="count">{{ cards.length }}</span
            ><span class="sr-only">张</span>
          </h2>
        </div>
        <LtCard radius="xl" padding="none" class="list">
          <ReviewCardRow
            v-for="card in visibleCards"
            :key="card.id"
            :card="card"
            :removing="review.removing.value.has(card.questionId)"
            :resetting="review.resetting.value.has(card.questionId)"
            :error="review.rowErrors.value[card.questionId]"
            @remove="review.remove(card)"
            @reset="review.reset(card)"
          />
        </LtCard>
        <button v-if="cards.length > CARD_PREVIEW" type="button" class="more-link" @click="showAll = !showAll">
          {{ showAll ? '收起' : `显示全部 ${cards.length} 张` }}
        </button>
      </section>

      <dl v-if="stats && stats.totalCards" class="totals" aria-label="复习计划统计">
        <div>
          <dt>到期可复习</dt>
          <dd>{{ stats.dueToday }}</dd>
        </div>
        <div>
          <dt>最近评分在今天</dt>
          <dd>{{ stats.reviewedToday }}</dd>
        </div>
        <div>
          <dt>最近评分答错</dt>
          <dd>{{ stats.lastAgainCards }}</dd>
        </div>
        <div>
          <dt>复习卡片</dt>
          <dd>{{ stats.totalCards }}</dd>
        </div>
      </dl>
    </template>

    <footer v-if="status !== 'loading'" class="tools">
      <div>
        <h2 class="tools-title">复习工具</h2>
        <p class="tools-text">AI 复习建议、同步错题，以及在页内直接作答。</p>
      </div>
      <LtButton @click="router.push({ name: 'ReviewClassic' })">
        打开复习工具
        <template #trailing><ArrowRight aria-hidden="true" /></template>
      </LtButton>
    </footer>
  </main>
</template>

<style scoped>
.page {
  max-width: 1040px;
  margin: 0 auto;
  padding: 0 var(--lt-space-7) 120px;
}

.head {
  padding: var(--lt-space-7) 0 var(--lt-space-6);
}

.back {
  margin-left: -8px;
}

.title {
  margin: var(--lt-space-4) 0 0;
  font-size: var(--lt-text-title1);
}

.lead {
  max-width: 60ch;
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-body);
  line-height: 1.6;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-2);
  margin: var(--lt-space-4) 0 0;
  padding: 0;
  list-style: none;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 6px 4px 12px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill);
  font-size: var(--lt-text-footnote);
}

.chip-clear {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: none;
  color: var(--lt-label-2);
  cursor: pointer;
}

.chip-clear:hover {
  background: var(--lt-fill-2);
  color: var(--lt-label);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-4);
  min-height: var(--lt-control-md);
  margin: 0 var(--lt-space-2) var(--lt-space-3);
}

.section-title {
  display: flex;
  align-items: baseline;
  gap: var(--lt-space-2);
  margin: 0;
  font-size: var(--lt-text-title3);
}

.count {
  color: var(--lt-label-3);
  font: 500 var(--lt-text-body) / 1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.list {
  overflow: hidden;
}

.all {
  margin-top: var(--lt-space-8);
}

.quiet {
  margin: 0 var(--lt-space-2);
  padding: var(--lt-space-5) 0 var(--lt-space-2);
}

.quiet-title {
  margin: 0;
  font-size: var(--lt-text-reading);
  font-weight: 500;
}

.quiet-text {
  margin: var(--lt-space-1) 0 0;
  color: var(--lt-label-2);
}

.more-link {
  display: block;
  margin: var(--lt-space-3) auto 0;
  padding: 0;
  border: 0;
  background: none;
  color: var(--lt-accent);
  font: inherit;
  font-size: var(--lt-text-footnote);
  cursor: pointer;
}

.more-link:hover {
  text-decoration: underline;
}

.totals {
  display: flex;
  gap: var(--lt-space-8);
  margin: var(--lt-space-9) 0 0;
  padding-top: var(--lt-space-5);
  border-top: 1px solid var(--lt-separator);
}

.totals dt {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.totals dd {
  margin: 2px 0 0;
  font: 600 var(--lt-text-title3) / 1.2 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-6);
  margin-top: var(--lt-space-8);
  padding-top: var(--lt-space-5);
  border-top: 1px solid var(--lt-separator);
}

.tools-title {
  margin: 0;
  font-size: var(--lt-text-headline);
}

.tools-text {
  margin: 2px 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}

.skeleton-row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  column-gap: var(--lt-space-6);
  padding: var(--lt-space-5) var(--lt-space-7);
}

.skeleton-row + .skeleton-row {
  border-top: 1px solid var(--lt-separator);
}

.skeleton {
  display: block;
  border-radius: 8px;
  background: var(--lt-fill-2);
  animation: pending 1.4s ease-in-out infinite alternate;
}

@keyframes pending {
  to {
    opacity: 0.45;
  }
}

.state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--lt-space-3);
  padding: var(--lt-space-8) 0;
}

.state-title {
  margin: 0;
  font-size: var(--lt-text-title3);
}

.state-text {
  margin: 0 0 var(--lt-space-2);
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
