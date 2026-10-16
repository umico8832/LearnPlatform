<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronLeft, RotateCcw, X } from '@lucide/vue'
import { LtButton, LtCard, LtIconButton, LtSegmented } from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'
import { useWrongQuestions, type WrongScope } from './useWrongQuestions'
import WrongRow from './WrongRow.vue'

/**
 * 错题页（ADR-0011 新基线）：每道错题先说清为什么错（上次所选错项的误区），
 * 再说什么时候重做（复习卡片的到期日），统计放在最后。课程、知识点、题目范围来自路由。
 */
defineOptions({ name: 'WrongQuestionsView' })
useLivingTheme()

const router = useRouter()
const wrong = useWrongQuestions()
const { scope, page, pages, status, records, stats, scoped } = wrong

const SCOPES = computed<{ value: WrongScope; label: string; hint?: string }[]>(() => [
  { value: 'due', label: '今天该重做', hint: stats.value ? String(stats.value.dueCount) : undefined },
  { value: 'all', label: '全部' },
  { value: 'mastered', label: '已掌握' },
])

const canGoBack = typeof window !== 'undefined' && typeof window.history.state?.back === 'string'
function goBack() {
  if (canGoBack) router.back()
  else void router.push(wrong.courseId.value ? `/my-courses/${wrong.courseId.value}` : '/my-courses')
}

const courseName = computed(() => records.value.find((item) => item.courseId === wrong.courseId.value)?.courseName)

const chips = computed(() => {
  const list: { key: 'courseId' | 'knowledgePointId' | 'questionId'; text: string }[] = []
  if (wrong.courseId.value) list.push({ key: 'courseId', text: courseName.value ?? '当前课程' })
  if (wrong.knowledgePointId.value)
    list.push({ key: 'knowledgePointId', text: wrong.knowledgePointName.value || '当前知识点' })
  if (wrong.questionId.value) list.push({ key: 'questionId', text: '只看这一道题' })
  return list
})

const pending = computed(() => (stats.value ? stats.value.unmastered + stats.value.partial : null))

onMounted(() => wrong.load())
</script>

<template>
  <main class="page">
    <header class="head">
      <LtIconButton class="back" label="返回" @click="goBack">
        <ChevronLeft aria-hidden="true" />
      </LtIconButton>
      <h1 class="title">错题</h1>
      <p class="lead">每道题先说清你为什么选错，再告诉你什么时候重做。只有不看提示的独立作答会计入复习安排。</p>
      <ul v-if="chips.length" class="chips" aria-label="当前范围">
        <li v-for="chip in chips" :key="chip.key" class="chip">
          {{ chip.text }}
          <button
            type="button"
            class="chip-clear"
            :aria-label="`取消“${chip.text}”这个范围`"
            @click="wrong.clearScope(chip.key)"
          >
            <X :size="14" aria-hidden="true" />
          </button>
        </li>
      </ul>
    </header>

    <div class="toolbar">
      <LtSegmented v-model="scope" :options="SCOPES" label="显示哪些错题" />
      <div class="toolbar-end">
        <span v-if="wrong.practiceError.value" class="toolbar-note" role="alert">{{ wrong.practiceError.value }}</span>
        <LtButton
          variant="primary"
          :disabled="wrong.practicePending.value || status !== 'ready' || records.length === 0"
          @click="wrong.startPractice()"
        >
          {{ wrong.practicePending.value ? '正在准备…' : '练习当前范围' }}
        </LtButton>
      </div>
    </div>

    <LtCard v-if="status === 'loading'" radius="xl" padding="none" class="list" aria-busy="true">
      <div v-for="n in 3" :key="n" class="skeleton-row">
        <span class="skeleton" style="width: 56px; height: 18px" />
        <div>
          <span class="skeleton" style="width: 72%; height: 16px" />
          <span class="skeleton" style="width: 40%; height: 14px; margin-top: 16px" />
          <span class="skeleton" style="width: 90%; height: 14px; margin-top: 10px" />
        </div>
      </div>
      <span class="sr-only">正在读取错题</span>
    </LtCard>

    <div v-else-if="status === 'error'" class="state">
      <h2 class="state-title">错题没有加载出来</h2>
      <p class="state-text">请检查网络后重试。</p>
      <LtButton variant="primary" @click="wrong.loadList()">
        <template #icon><RotateCcw aria-hidden="true" /></template>
        重试
      </LtButton>
    </div>

    <div v-else-if="records.length === 0" class="state">
      <template v-if="scope === 'due'">
        <h2 class="state-title">今天没有要重做的题</h2>
        <p class="state-text">到期的错题会排在这里。想多练几道，可以看看全部错题。</p>
        <LtButton @click="scope = 'all'">看全部错题</LtButton>
      </template>
      <template v-else-if="scoped || scope !== 'all'">
        <h2 class="state-title">这个范围里没有错题</h2>
        <p class="state-text">换个范围看看，或者回到全部错题。</p>
        <LtButton @click="router.replace({ query: {} }).then(() => (scope = 'all'))">看全部错题</LtButton>
      </template>
      <template v-else>
        <h2 class="state-title">还没有错题</h2>
        <p class="state-text">答错的题会自动来到这里，并告诉你为什么错、什么时候重做。</p>
        <LtButton @click="router.push('/my-courses')">去我的课程</LtButton>
      </template>
    </div>

    <LtCard v-else as="section" radius="xl" padding="none" class="list" aria-label="错题列表">
      <WrongRow
        v-for="item in records"
        :key="item.id"
        :item="item"
        :updating="wrong.updating.value.has(item.id)"
        :removing="wrong.removing.value.has(item.id)"
        :redoing="wrong.redoing.value === item.id"
        :error="wrong.rowErrors.value[item.id]"
        @redo="wrong.redo(item)"
        @mastery="(level) => wrong.setMastery(item, level)"
        @remove="wrong.remove(item)"
      />
    </LtCard>

    <nav v-if="status === 'ready' && pages > 1" class="pager" aria-label="翻页">
      <LtButton size="sm" :disabled="page <= 1" @click="page -= 1">上一页</LtButton>
      <span class="pager-text">第 {{ page }} / {{ pages }} 页</span>
      <LtButton size="sm" :disabled="page >= pages" @click="page += 1">下一页</LtButton>
    </nav>

    <dl v-if="stats" class="totals" aria-label="错题本统计">
      <div>
        <dt>{{ scoped ? '这个范围共' : '错题本共' }}</dt>
        <dd>{{ stats.total }}</dd>
      </div>
      <div>
        <dt>待处理</dt>
        <dd>{{ pending }}</dd>
      </div>
      <div>
        <dt>已掌握</dt>
        <dd>{{ stats.mastered }}</dd>
      </div>
    </dl>
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

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-4);
  margin-bottom: var(--lt-space-5);
}

.toolbar-end {
  display: flex;
  align-items: center;
  gap: var(--lt-space-4);
}

.toolbar-note {
  color: var(--lt-red-text);
  font-size: var(--lt-text-footnote);
}

.skeleton-row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  column-gap: var(--lt-space-6);
  padding: var(--lt-space-6) var(--lt-space-7);
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

@media (prefers-reduced-motion: reduce) {
  .skeleton {
    animation: none;
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

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--lt-space-4);
  margin-top: var(--lt-space-6);
}

.pager-text {
  color: var(--lt-label-2);
  font: 500 var(--lt-text-footnote) / 1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
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

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
