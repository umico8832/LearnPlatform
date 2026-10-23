<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, BookOpen, Play, RefreshCw, StarOff } from '@lucide/vue'
import { LtButton, LtCard, LtLinkButton, LtSheet } from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'
import { difficultyLabel, questionTypeLabel, recordTime } from './practiceLibraryPresentation'
import { useFavoriteLibrary } from './useFavoriteLibrary'

useLivingTheme({ dark: true })
const router = useRouter()
const removing = ref<number | null>(null)
const removeOrigin = ref<HTMLElement | null>(null)
const lib = useFavoriteLibrary()
const {
  records,
  total,
  page,
  size,
  loading,
  state,
  load,
  resetPage,
  practiceCount,
  startingId,
  removingId,
  actionError,
  notice,
  startPractice,
  remove,
} = lib
const busy = computed(() => startingId.value !== null || removingId.value !== null)
const pages = computed(() => Math.max(1, Math.ceil((total.value ?? 0) / size.value)))
const removeSheetOpen = computed({
  get: () => removing.value !== null,
  set: (open: boolean) => {
    if (!open) removing.value = null
  },
})
function goBack() {
  if (typeof window !== 'undefined' && typeof window.history.state?.back === 'string') router.back()
  else void router.push('/my-courses')
}
function openRemove(questionId: number, event: MouseEvent) {
  removeOrigin.value = event.currentTarget as HTMLElement
  removing.value = questionId
}
async function confirmRemove() {
  if (removing.value === null) return
  const questionId = removing.value
  removing.value = null
  await remove(questionId)
}
function changePage(delta: number) {
  page.value += delta
  void load()
}
function changeSize(event: Event) {
  size.value = Number((event.target as HTMLSelectElement).value)
  void resetPage()
}
</script>

<template>
  <main class="favorite-page lt" data-appearance="dark">
    <header class="head">
      <LtButton class="back" variant="quiet" aria-label="返回" @click="goBack"
        ><template #icon><ArrowLeft aria-hidden="true" /></template>返回</LtButton
      >
      <div class="heading-row">
        <div>
          <h1>我的收藏</h1>
          <p>把值得回看的题留在这里，再按自己的节奏练习。</p>
        </div>
        <LtLinkButton to="/questions"><BookOpen aria-hidden="true" />浏览题库</LtLinkButton>
      </div>
    </header>
    <LtCard as="section" radius="xl" class="practice-card" aria-label="收藏题练习"
      ><div>
        <p class="eyebrow">收藏题目</p>
        <p class="count">
          {{ total === null ? (state === 'error' ? '收藏题目数量暂不可用' : '正在读取题目数量') : `${total} 道收藏题` }}
        </p>
        <p class="muted">从收藏中抽取一组题目。</p>
      </div>
      <div class="practice-controls">
        <label for="favorite-practice-count">题目数</label
        ><input
          id="favorite-practice-count"
          v-model.number="practiceCount"
          min="1"
          max="50"
          :disabled="busy"
          type="number"
        /><LtButton variant="primary" :disabled="loading || !total || busy" @click="startPractice()"
          ><template #icon><Play aria-hidden="true" /></template
          >{{ startingId === 'all' ? '正在准备' : '练习收藏题' }}</LtButton
        >
      </div></LtCard
    >
    <p v-if="actionError" class="notice error" role="alert">{{ actionError }}</p>
    <p v-else-if="notice" class="notice" role="status">{{ notice }}</p>
    <section class="collection" aria-label="收藏题目">
      <div class="section-head">
        <div>
          <h2 id="collection-title">收藏列表</h2>
          <p v-if="total !== null">第 {{ page }} / {{ pages }} 页</p>
        </div>
        <LtButton :disabled="busy || loading" @click="load"
          ><template #icon><RefreshCw aria-hidden="true" /></template>刷新</LtButton
        >
      </div>
      <LtCard v-if="state === 'loading'" radius="xl" padding="none" aria-busy="true"
        ><div v-for="n in 3" :key="n" class="skeleton-row"><span class="skeleton wide" /><span class="skeleton" /></div>
        <span class="sr-only">正在加载收藏题目</span></LtCard
      >
      <div v-else-if="state === 'error'" class="state">
        <h3>收藏暂时无法加载</h3>
        <p>请重试，已收藏的题目会继续保留。</p>
        <LtButton variant="primary" @click="load">重试</LtButton>
      </div>
      <div v-else-if="state === 'empty'" class="state">
        <h3>还没有收藏题目</h3>
        <p>在题库中收藏想要回看的题目，再回来练习。</p>
        <LtLinkButton to="/questions">浏览题库</LtLinkButton>
      </div>
      <LtCard v-else radius="xl" padding="none" class="list"
        ><ol>
          <li v-for="(item, index) in records" :key="item.id" class="question-row">
            <span class="order" aria-hidden="true">{{ (page - 1) * size + index + 1 }}</span>
            <div class="copy">
              <div class="meta">
                <span>{{ item.courseName || '未关联课程' }}</span
                ><span>{{ questionTypeLabel(item.questionType) }}</span
                ><span>{{ difficultyLabel(item.difficulty) }}</span>
              </div>
              <p>{{ item.questionContent }}</p>
              <time>收藏于 {{ recordTime(item.createTime) }}</time>
            </div>
            <div class="actions">
              <LtButton
                size="sm"
                :disabled="busy"
                :aria-label="`练习第 ${(page - 1) * size + index + 1} 道收藏题`"
                @click="startPractice(item.questionId)"
                >{{ startingId === item.questionId ? '正在准备' : '练习' }}</LtButton
              ><LtButton
                size="sm"
                variant="quiet"
                :disabled="busy"
                :aria-label="`取消收藏第 ${(page - 1) * size + index + 1} 道题`"
                @click="openRemove(item.questionId, $event)"
                ><template #icon><StarOff aria-hidden="true" /></template>取消收藏</LtButton
              >
            </div>
          </li>
        </ol></LtCard
      >
    </section>
    <nav v-if="state === 'ready' && pages > 1" class="pager" aria-label="收藏题翻页">
      <label
        >每页
        <select :value="size" :disabled="busy" @change="changeSize">
          <option :value="10">10</option>
          <option :value="20">20</option>
          <option :value="50">50</option>
        </select>
        道</label
      ><LtButton size="sm" :disabled="busy || page <= 1" @click="changePage(-1)">上一页</LtButton
      ><span>第 {{ page }} / {{ pages }} 页</span
      ><LtButton size="sm" :disabled="busy || page >= pages" @click="changePage(1)">下一页</LtButton>
    </nav>
    <LtSheet v-model:open="removeSheetOpen" title="取消收藏" :origin="removeOrigin"
      ><p class="sheet-copy">取消收藏这道题目？题目不会从题库删除。</p>
      <div class="sheet-actions">
        <LtButton @click="removing = null">保留</LtButton
        ><LtButton variant="primary" @click="confirmRemove">取消收藏</LtButton>
      </div></LtSheet
    >
  </main>
</template>

<style scoped>
.favorite-page {
  max-width: calc(var(--lt-space-10) * 15);
  margin: 0 auto;
  padding: 0 var(--lt-space-7) var(--lt-space-10);
}
.head {
  padding: var(--lt-space-7) 0 var(--lt-space-6);
}
.back {
  margin-left: calc(-1 * var(--lt-space-2));
}
.heading-row,
.practice-card,
.section-head,
.question-row,
.actions,
.pager,
.sheet-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-4);
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
.heading-row p,
.muted,
.section-head p,
time,
.meta,
.notice {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.heading-row p {
  margin-top: var(--lt-space-2);
}
.practice-card {
  margin-bottom: var(--lt-space-5);
}
.eyebrow {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}
.count {
  margin-top: var(--lt-space-1);
  font: 600 var(--lt-text-title2)/1.2 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}
.muted {
  margin-top: var(--lt-space-1);
}
.practice-controls {
  display: flex;
  align-items: center;
  gap: var(--lt-space-3);
}
.practice-controls label,
.pager label {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.practice-controls input,
select {
  height: var(--lt-control-md);
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font: inherit;
  padding: 0 var(--lt-space-3);
}
.practice-controls input {
  width: calc(var(--lt-space-10) + var(--lt-space-2));
}
.notice {
  margin: calc(-1 * var(--lt-space-3)) 0 var(--lt-space-5);
}
.error {
  color: var(--lt-red-text);
}
.section-head {
  margin: 0 var(--lt-space-2) var(--lt-space-3);
}
.section-head h2 {
  font-size: var(--lt-text-title3);
}
.section-head p {
  margin-top: var(--lt-space-1);
}
.list {
  overflow: hidden;
}
.list ol {
  margin: 0;
  padding: 0;
  list-style: none;
}
.question-row {
  align-items: flex-start;
  padding: var(--lt-space-5) var(--lt-space-6);
}
.question-row + .question-row {
  border-top: thin solid var(--lt-separator);
}
.order {
  color: var(--lt-label-3);
  font: 500 var(--lt-text-footnote)/1 var(--lt-font-rounded);
}
.copy {
  min-width: 0;
  flex: 1;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-2);
}
.copy p {
  margin: var(--lt-space-2) 0;
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.actions {
  flex: none;
  align-items: flex-start;
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
.state p,
.sheet-copy {
  color: var(--lt-label-2);
}
.pager {
  justify-content: center;
  margin-top: var(--lt-space-6);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
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
.sheet-actions {
  justify-content: flex-end;
  margin-top: var(--lt-space-5);
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
