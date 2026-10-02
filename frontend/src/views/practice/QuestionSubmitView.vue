<script setup lang="ts">
import { computed, onScopeDispose, ref } from 'vue'
import { getMySubmissions, type QuestionSubmissionVO } from '@/api/submission'
import { onAuthSessionChange } from '@/utils/auth'
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
  <div class="submission-page page-container">
    <LpPageHeader title="题目投稿" description="分享一道好题，查看它的审核与入库进度。"
      ><template #actions
        ><el-button type="primary" @click="showEditor = true">投稿新题目</el-button></template
      ></LpPageHeader
    >
    <p v-if="notice" class="submission-notice" role="status">{{ notice }}</p>
    <section class="submission-list" aria-label="我的题目投稿">
      <div class="submission-toolbar">
        <el-radio-group v-model="statusFilter" aria-label="按审核状态筛选" @change="resetPage"
          ><el-radio-button v-for="option in statusOptions" :key="option.value" :value="option.value">{{
            option.label
          }}</el-radio-button></el-radio-group
        ><span v-if="total !== null">共 {{ total }} 篇投稿</span>
      </div>
      <LpStatePanel
        :state="state"
        :title="
          state === 'error' ? '投稿记录暂时无法加载' : statusFilter >= 0 ? '没有这个状态的投稿' : '还没有投稿记录'
        "
        :description="
          state === 'error'
            ? '请重试，已提交的题目会继续保留。'
            : statusFilter >= 0
              ? '可以切换到全部，查看其他投稿。'
              : '准备好题干、选项和参考答案，就可以分享你的第一道题。'
        "
        loading-label="正在加载投稿记录"
        @retry="load"
      >
        <template #actions
          ><el-button v-if="statusFilter >= 0" @click="clearFilter">查看全部投稿</el-button
          ><el-button v-else @click="showEditor = true">投稿新题目</el-button></template
        >
        <ol class="submission-rows">
          <li v-for="row in records" :key="row.id">
            <div class="submission-meta">
              <span class="submission-status" :data-status="row.status">{{ submissionStatus(row.status) }}</span
              ><span>{{ row.courseName }}</span
              ><span>{{ questionTypeLabel(row.questionType) }}</span
              ><time>{{ recordTime(row.createTime) }}</time>
            </div>
            <div class="submission-content">
              <p>{{ row.content }}</p>
              <el-button @click="viewDetail(row)">查看详情</el-button>
            </div>
            <p v-if="row.reviewComment" class="review-comment"><span>审核反馈</span>{{ row.reviewComment }}</p>
            <p v-else-if="row.status === 1" class="review-comment">已通过审核，等待入库。</p>
          </li>
        </ol>
      </LpStatePanel>
      <el-pagination
        v-if="total && total > size"
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </section>
    <QuestionSubmissionEditor v-model="showEditor" @submitted="submitted" />
    <QuestionSubmissionDetail v-model="showDetail" :detail="currentDetail" />
  </div>
</template>

<style scoped>
.submission-page {
  display: grid;
  gap: var(--lp-space-6);
}
.submission-notice {
  margin: 0;
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-body);
}
.submission-list {
  padding: var(--lp-space-5);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
}
.submission-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-4);
  margin-bottom: var(--lp-space-5);
}
.submission-toolbar > span {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}
.submission-rows {
  list-style: none;
  margin: 0;
  padding: 0;
}
.submission-rows > li {
  padding: var(--lp-space-5) 0;
  border-top: var(--lp-border-hairline);
}
.submission-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--lp-space-3);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
.submission-status {
  color: var(--lp-text-secondary);
  padding: var(--lp-space-1) var(--lp-space-2);
  border-radius: var(--lp-radius-sm);
  background: var(--lp-surface-soft);
}
.submission-status[data-status='3'] {
  color: var(--lp-success);
  background: var(--lp-success-soft);
}
.submission-meta time {
  margin-left: auto;
}
.submission-content {
  display: flex;
  align-items: baseline;
  gap: var(--lp-space-5);
  margin-top: var(--lp-space-3);
}
.submission-content p {
  flex: 1;
  min-width: 0;
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: var(--lp-leading-relaxed);
}
.review-comment {
  margin: var(--lp-space-3) 0 0;
  padding-left: var(--lp-space-3);
  border-left: 2px solid var(--lp-border);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.review-comment span {
  margin-right: var(--lp-space-3);
  color: var(--lp-text-muted);
}
.el-pagination {
  justify-content: flex-end;
  padding-top: var(--lp-space-5);
  border-top: var(--lp-border-hairline);
}
@media (max-width: 767px) {
  .submission-toolbar {
    flex-wrap: wrap;
  }
  .submission-content {
    flex-direction: column;
    gap: var(--lp-space-3);
  }
  .submission-meta time {
    margin-left: 0;
  }
}
</style>
