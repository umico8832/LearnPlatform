<script setup lang="ts">
import LtPageHeader from '@/components/living/LtPageHeader.vue'
import { computed, onScopeDispose, ref } from 'vue'
import { FilePlus2, RotateCcw } from '@lucide/vue'
import { getMySubmissions, type QuestionSubmissionVO } from '@/api/submission'
import { onAuthSessionChange } from '@/utils/auth'
import LtBackLink from '@/components/living/LtBackLink.vue'
import LtButton from '@/components/living/LtButton.vue'
import QuestionSubmissionEditor from './QuestionSubmissionEditor.vue'
import QuestionSubmissionDetail from './QuestionSubmissionDetail.vue'
import { usePersonalCollection } from './usePersonalCollection'
import { questionTypeLabel, recordTime } from './practiceLibraryPresentation'
import { submissionStatus } from './questionSubmissionForm'

const statusFilter = ref(-1)
const { records, total, page, size, state, load, resetPage } = usePersonalCollection<QuestionSubmissionVO>(
  (pageNum, pageSize) =>
    getMySubmissions(
      { pageNum, pageSize, status: statusFilter.value < 0 ? undefined : statusFilter.value },
      { errorDisplay: 'inline' },
    ),
)
const statusOptions = computed(() => [
  { value: -1, label: '全部' },
  ...[0, 1, 2, 3].map((value) => ({ value, label: submissionStatus(value) })),
])
const showEditor = ref(false)
const showDetail = ref(false)
const currentDetail = ref<QuestionSubmissionVO | null>(null)
const notice = ref('')
function viewDetail(row: QuestionSubmissionVO) {
  currentDetail.value = row
  showDetail.value = true
}
function submitted() {
  notice.value = '投稿已提交，等待审核。可以在这里查看后续反馈。'
  clearFilter()
}
function clearFilter() {
  statusFilter.value = -1
  void resetPage()
}
function selectStatus(event: Event) {
  statusFilter.value = Number((event.target as HTMLSelectElement).value)
  void resetPage()
}
function previous() {
  if (page.value > 1) {
    page.value--
    void load()
  }
}
function next() {
  if (total.value && page.value * size.value < total.value) {
    page.value++
    void load()
  }
}
onScopeDispose(
  onAuthSessionChange(() => {
    showEditor.value = false
    showDetail.value = false
    currentDetail.value = null
    notice.value = ''
  }),
)
</script>

<template>
  <div class="lt submission-page" data-appearance="dark">
    <div class="submission-wrap">
      <LtPageHeader title="题目投稿" description="分享一道好题，查看它的审核与入库进度。">
        <template #lead><LtBackLink to="/community" label="返回社区" /></template>
        <template #actions>
          <div role="group" aria-label="题目投稿操作">
            <LtButton variant="primary" @click="showEditor = true"
              ><template #icon><FilePlus2 aria-hidden="true" /></template>投稿新题目</LtButton
            >
          </div>
        </template>
      </LtPageHeader>
      <p v-if="notice" class="notice" role="status">{{ notice }}</p>
      <section class="submission-list" aria-label="我的题目投稿">
        <div class="submission-toolbar">
          <label
            >审核状态<select aria-label="按审核状态筛选" :value="statusFilter" @change="selectStatus">
              <option v-for="option in statusOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select></label
          >
          <span v-if="total !== null">共 {{ total }} 篇投稿</span>
        </div>
        <div v-if="state === 'loading'" class="state" aria-busy="true">
          <span class="skeleton" /><span class="skeleton" /><span class="sr-only" role="status">正在加载投稿记录</span>
        </div>
        <div v-else-if="state === 'error'" class="state" role="alert">
          <h2>投稿记录暂时无法加载</h2>
          <p>请重试，已提交的题目会继续保留。</p>
          <LtButton @click="load"
            ><template #icon><RotateCcw aria-hidden="true" /></template>重新加载</LtButton
          >
        </div>
        <div v-else-if="state === 'empty'" class="state">
          <h2>{{ statusFilter >= 0 ? '没有这个状态的投稿' : '还没有投稿记录' }}</h2>
          <p>
            {{
              statusFilter >= 0
                ? '可以切换到全部，查看其他投稿。'
                : '准备好题干、选项和参考答案，就可以分享你的第一道题。'
            }}
          </p>
          <LtButton v-if="statusFilter >= 0" @click="clearFilter">查看全部投稿</LtButton
          ><LtButton v-else variant="primary" @click="showEditor = true">投稿新题目</LtButton>
        </div>
        <ol v-else class="submission-rows">
          <li v-for="row in records" :key="row.id">
            <div class="submission-meta">
              <span class="status" :data-status="row.status">{{ submissionStatus(row.status) }}</span
              ><span>{{ row.courseName }}</span
              ><span>{{ questionTypeLabel(row.questionType) }}</span
              ><time>{{ recordTime(row.createTime) }}</time>
            </div>
            <div class="submission-content">
              <p>{{ row.content }}</p>
              <LtButton size="sm" @click="viewDetail(row)">查看详情</LtButton>
            </div>
            <p v-if="row.reviewComment" class="review"><strong>审核反馈</strong>{{ row.reviewComment }}</p>
            <p v-else-if="row.status === 1" class="review">已通过审核，等待入库。</p>
          </li>
        </ol>
        <nav v-if="total && total > size" class="pager" aria-label="投稿分页">
          <LtButton size="sm" :disabled="page <= 1" @click="previous">上一页</LtButton><span>第 {{ page }} 页</span
          ><LtButton size="sm" :disabled="page * size >= total" @click="next">下一页</LtButton>
        </nav>
      </section>
    </div>
    <QuestionSubmissionEditor v-model="showEditor" @submitted="submitted" /><QuestionSubmissionDetail
      v-model="showDetail"
      :detail="currentDetail"
    />
  </div>
</template>

<style scoped>
.notice,
.submission-toolbar > span,
.review {
  color: var(--lt-label-2);
}
.notice {
  margin: 0 0 var(--lt-space-4);
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-md);
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
.submission-list {
  padding: var(--lt-space-5);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface-raised);
  box-shadow: var(--lt-shadow-card);
}
.submission-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--lt-space-4);
  margin-bottom: var(--lt-space-5);
}
label {
  display: grid;
  gap: var(--lt-space-1);
  font-size: var(--lt-text-caption);
  color: var(--lt-label-2);
}
select {
  min-height: var(--lt-control-md);
  padding: 0 var(--lt-space-4);
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font: inherit;
}
.submission-rows {
  list-style: none;
  margin: 0;
  padding: 0;
}
.submission-rows > li {
  padding: var(--lt-space-5) 0;
  border-top: 1px solid var(--lt-separator);
}
.submission-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-2);
  align-items: center;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}
.status {
  padding: calc(var(--lt-space-1) * 0.75) calc(var(--lt-space-2) + var(--lt-space-1) / 4);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label-2);
}
.status[data-status='2'] {
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
.status[data-status='3'] {
  background: var(--lt-green-soft);
  color: var(--lt-green-text);
}
time {
  margin-left: auto;
}
.submission-content {
  display: flex;
  align-items: start;
  gap: var(--lt-space-5);
  margin-top: var(--lt-space-3);
}
.submission-content p {
  flex: 1;
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}
.review {
  margin: var(--lt-space-3) 0 0;
  white-space: pre-wrap;
  line-height: var(--lt-leading-ui);
}
.review strong {
  margin-right: var(--lt-space-2);
  color: var(--lt-label);
}
.state {
  display: grid;
  justify-items: start;
  gap: var(--lt-space-3);
  min-height: calc(var(--lt-space-10) * 2 + var(--lt-space-5) + var(--lt-space-4));
  padding: var(--lt-space-5);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-fill-2);
}
.state h2,
.state p {
  margin: 0;
}
.state p {
  color: var(--lt-label-2);
}
.skeleton {
  width: 100%;
  height: var(--lt-space-7);
  border-radius: var(--lt-radius-sm);
  background: var(--lt-fill-hover);
}
.pager {
  display: flex;
  justify-content: end;
  align-items: center;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-5);
  padding-top: var(--lt-space-4);
  border-top: 1px solid var(--lt-separator);
  font-variant-numeric: tabular-nums;
}
@media (max-width: 700px) {
  .submission-content {
    align-items: stretch;
    flex-direction: column;
  }
  time {
    margin-left: 0;
  }
}
</style>
