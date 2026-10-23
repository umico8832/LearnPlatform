<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Play, RefreshCw } from '@lucide/vue'
import { LtBackLink, LtButton, LtCard, LtLinkButton } from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'
import { getPracticeRecords, type PracticeRecordVO } from '@/api/practice'
import { usePersonalCollection } from './usePersonalCollection'
import {
  answerStatus,
  difficultyLabel,
  practiceQuestionTypes,
  questionTypeLabel,
  recordTime,
} from './practiceLibraryPresentation'
useLivingTheme({ dark: true })
const router = useRouter()
const filter = reactive({ questionType: '', isCorrect: '' as '' | 0 | 1 | undefined })
const collection = usePersonalCollection<PracticeRecordVO>((pageNum, pageSize) =>
  getPracticeRecords(
    {
      pageNum,
      pageSize,
      questionType: filter.questionType || undefined,
      isCorrect: typeof filter.isCorrect === 'number' ? filter.isCorrect : undefined,
    },
    { errorDisplay: 'inline' },
  ),
)
const { records, total, page, size, state, loading, load, resetPage } = collection
const filtered = computed(() => !!filter.questionType || typeof filter.isCorrect === 'number')
const pages = computed(() => Math.max(1, Math.ceil((total.value ?? 0) / size.value)))
const pageSummary = computed(() => {
  const correct = records.value.filter((x) => x.isCorrect === 1).length
  const wrong = records.value.filter((x) => x.isCorrect === 0).length
  const pending = records.value.length - correct - wrong
  return {
    correct,
    wrong,
    pending,
    rate: correct + wrong ? `${Math.round((correct / (correct + wrong)) * 100)}%` : '—',
  }
})
function resetFilters() {
  filter.questionType = ''
  filter.isCorrect = ''
  void resetPage()
}
function changeCorrectness(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  filter.isCorrect = value === '' ? '' : (Number(value) as 0 | 1)
  void resetPage()
}
function changePage(delta: number) {
  page.value += delta
  void load()
}
function changeSize(event: Event) {
  size.value = Number((event.target as HTMLSelectElement).value)
  void resetPage()
}
function goBack(event: MouseEvent) {
  const previous = typeof window === 'undefined' ? undefined : window.history.state?.back
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
  if (typeof previous === 'string' && previous.startsWith('/') && !previous.startsWith('//')) {
    event.preventDefault()
    router.back()
  }
}
</script>
<template>
  <main class="record-page lt" data-appearance="dark">
    <header class="head">
      <LtBackLink to="/practice" label="返回练习" @click.capture="goBack" />
      <div class="heading-row">
        <div>
          <h1>练习记录</h1>
          <p>回看每一次作答，再决定下一步练习什么。</p>
        </div>
        <LtLinkButton to="/practice" variant="primary"><Play aria-hidden="true" />开始练习</LtLinkButton>
      </div>
    </header>
    <LtCard as="section" radius="xl" class="filters" aria-label="练习记录筛选"
      ><label
        >题型<select v-model="filter.questionType" @change="resetPage">
          <option value="">全部题型</option>
          <option v-for="item in practiceQuestionTypes" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select></label
      ><label
        >结果<select :value="filter.isCorrect" @change="changeCorrectness">
          <option value="">全部结果</option>
          <option :value="1">正确</option>
          <option :value="0">需复习</option>
        </select></label
      ><LtButton v-if="filtered" size="sm" variant="quiet" @click="resetFilters">清除筛选</LtButton
      ><span class="total">{{
        total === null ? (state === 'error' ? '记录数量暂不可用' : '正在读取记录') : `共 ${total} 条记录`
      }}</span></LtCard
    >
    <div v-if="state === 'ready'" class="summary" aria-label="当前页作答统计">
      <span>当前页 {{ records.length }} 条</span
      ><span
        >正确 {{ pageSummary.correct }} · 需复习 {{ pageSummary.wrong
        }}<template v-if="pageSummary.pending"> · 待判分 {{ pageSummary.pending }}</template></span
      ><span
        >已判分正确率 <strong>{{ pageSummary.rate }}</strong></span
      >
    </div>
    <section aria-labelledby="records-title">
      <div class="section-head">
        <h2 id="records-title">作答记录</h2>
        <LtButton :disabled="loading" @click="load"
          ><template #icon><RefreshCw aria-hidden="true" /></template>刷新</LtButton
        >
      </div>
      <LtCard v-if="state === 'loading'" radius="xl" padding="none" aria-busy="true"
        ><div v-for="n in 3" :key="n" class="skeleton-row"><span class="skeleton wide" /><span class="skeleton" /></div>
        <span class="sr-only">正在加载练习记录</span></LtCard
      >
      <div v-else-if="state === 'error'" class="state">
        <h3>练习记录暂时无法加载</h3>
        <p>请重试，已提交的作答会继续保留。</p>
        <LtButton variant="primary" @click="load">重试</LtButton>
      </div>
      <div v-else-if="state === 'empty'" class="state">
        <h3>{{ filtered ? '没有符合筛选条件的记录' : '还没有练习记录' }}</h3>
        <p>{{ filtered ? '可以清除筛选，再查看全部作答。' : '完成一道题后，可以在这里回看作答。' }}</p>
        <LtButton v-if="filtered" @click="resetFilters">查看全部记录</LtButton
        ><LtLinkButton v-else to="/practice">开始练习</LtLinkButton>
      </div>
      <LtCard v-else radius="xl" padding="none" class="list"
        ><ol>
          <li v-for="item in records" :key="item.id" class="record-row">
            <div class="meta">
              <span
                class="answer-state"
                :data-result="item.isCorrect === 1 ? 'correct' : item.isCorrect === 0 ? 'wrong' : 'pending'"
                >{{ answerStatus(item.isCorrect) }}</span
              ><span>{{ item.courseName || '未关联课程' }}</span
              ><span>{{ questionTypeLabel(item.questionType) }}</span
              ><span>{{ difficultyLabel(item.difficulty) }}</span
              ><time>{{ recordTime(item.createTime) }}</time>
            </div>
            <p>{{ item.questionContent }}</p>
            <div class="answer">
              <span>我的答案</span><span class="answer-copy">{{ item.userAnswer || '未填写' }}</span
              ><span>{{ item.answerTime == null ? '耗时未记录' : `${item.answerTime} 秒` }}</span>
            </div>
          </li>
        </ol></LtCard
      >
    </section>
    <nav v-if="state === 'ready' && pages > 1" class="pager" aria-label="练习记录翻页">
      <label
        >每页
        <select :value="size" @change="changeSize">
          <option :value="10">10</option>
          <option :value="20">20</option>
          <option :value="50">50</option>
        </select>
        条</label
      ><LtButton size="sm" :disabled="page <= 1" @click="changePage(-1)">上一页</LtButton
      ><span>第 {{ page }} / {{ pages }} 页</span
      ><LtButton size="sm" :disabled="page >= pages" @click="changePage(1)">下一页</LtButton>
    </nav>
  </main>
</template>
<style scoped>
.record-page {
  max-width: calc(var(--lt-space-10) * 15);
  margin: 0 auto;
  padding: 0 var(--lt-space-7) var(--lt-space-10);
}
.head {
  padding: var(--lt-space-7) 0 var(--lt-space-6);
}
.heading-row,
.filters,
.section-head,
.meta,
.answer,
.summary,
.pager {
  display: flex;
  align-items: center;
  gap: var(--lt-space-3);
}
.heading-row,
.section-head {
  justify-content: space-between;
}
.heading-row p,
.total,
.summary,
.meta,
.answer,
.state p {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
h1,
h2,
h3,
p {
  margin: 0;
}
h1 {
  font-size: var(--lt-text-title1);
}
.heading-row p {
  margin-top: var(--lt-space-2);
}
.filters {
  flex-wrap: wrap;
  margin-bottom: var(--lt-space-4);
}
.filters label,
.pager label {
  display: flex;
  align-items: center;
  gap: var(--lt-space-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
select {
  height: var(--lt-control-md);
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  padding: 0 var(--lt-space-3);
  font: inherit;
}
.total {
  margin-left: auto;
}
.summary {
  flex-wrap: wrap;
  margin: 0 var(--lt-space-2) var(--lt-space-5);
}
.summary strong {
  color: var(--lt-label);
}
.section-head {
  margin: 0 var(--lt-space-2) var(--lt-space-3);
}
.section-head h2 {
  font-size: var(--lt-text-title3);
}
.list {
  overflow: hidden;
}
.list ol {
  margin: 0;
  padding: 0;
  list-style: none;
}
.record-row {
  padding: var(--lt-space-5) var(--lt-space-6);
}
.record-row + .record-row {
  border-top: thin solid var(--lt-separator);
}
.meta {
  flex-wrap: wrap;
}
.meta time {
  margin-left: auto;
}
.answer-state {
  padding: var(--lt-space-1) var(--lt-space-2);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill);
  color: var(--lt-label-2);
}
.answer-state[data-result='correct'] {
  background: var(--lt-green-soft);
  color: var(--lt-green-text);
}
.answer-state[data-result='wrong'] {
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
.record-row > p {
  margin: var(--lt-space-3) 0;
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.answer > span:first-child,
.answer > span:last-child {
  color: var(--lt-label-3);
  flex: none;
}
.answer-copy {
  min-width: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.answer > span:last-child {
  margin-left: auto;
}
.pager {
  justify-content: center;
  margin-top: var(--lt-space-6);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.state {
  display: grid;
  justify-items: start;
  gap: var(--lt-space-3);
  padding: var(--lt-space-8) var(--lt-space-2);
}
.state h3 {
  font-size: var(--lt-text-title3);
}
.skeleton-row {
  display: grid;
  gap: var(--lt-space-3);
  padding: var(--lt-space-5) var(--lt-space-6);
}
.skeleton-row + .skeleton-row {
  border-top: thin solid var(--lt-separator);
}
.skeleton {
  display: block;
  height: var(--lt-space-3);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  animation: pulse var(--lt-dur-fade) var(--lt-ease-fade) infinite alternate;
}
.wide {
  width: 70%;
}
.sr-only {
  position: absolute;
  width: var(--lt-space-1);
  height: var(--lt-space-1);
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
@keyframes pulse {
  to {
    opacity: 0.45;
  }
}
</style>
