<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { getPracticeRecords, type PracticeRecordVO } from '@/api/practice'
import { usePersonalCollection } from './usePersonalCollection'
import {
  answerStatus,
  difficultyLabel,
  practiceQuestionTypes,
  questionTypeLabel,
  recordTime,
} from './practiceLibraryPresentation'

const router = useRouter()
const filter = reactive({ questionType: '', isCorrect: '' as '' | 0 | 1 | undefined })
const { records, total, page, size, state, load, resetPage } = usePersonalCollection<PracticeRecordVO>(
  (pageNum, pageSize) =>
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
const filtered = computed(() => !!filter.questionType || typeof filter.isCorrect === 'number')
const pageSummary = computed(() => {
  const correct = records.value.filter((record) => record.isCorrect === 1).length
  const wrong = records.value.filter((record) => record.isCorrect === 0).length
  const pending = records.value.length - correct - wrong
  const graded = correct + wrong
  return { correct, wrong, pending, rate: graded ? `${Math.round((correct / graded) * 100)}%` : '—' }
})
function resetFilters() {
  filter.questionType = ''
  filter.isCorrect = ''
  void resetPage()
}
</script>

<template>
  <div class="record-page page-container">
    <LpPageHeader title="练习记录" description="回看每一次作答，再决定下一步练习什么。">
      <template #actions><el-button type="primary" @click="router.push('/practice')">开始练习</el-button></template>
    </LpPageHeader>

    <section class="record-panel" aria-label="练习记录">
      <el-form :inline="true" class="record-filters" @submit.prevent>
        <el-form-item label="题型">
          <el-select v-model="filter.questionType" placeholder="全部题型" clearable @change="resetPage">
            <el-option
              v-for="item in practiceQuestionTypes"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="filter.isCorrect" placeholder="全部结果" clearable @change="resetPage">
            <el-option label="正确" :value="1" /><el-option label="需复习" :value="0" />
          </el-select>
        </el-form-item>
        <el-button v-if="filtered" text @click="resetFilters">清除筛选</el-button>
        <span class="total-count">{{
          total === null ? (state === 'error' ? '记录数量暂不可用' : '正在读取记录') : `共 ${total} 条记录`
        }}</span>
      </el-form>

      <div v-if="state === 'ready'" class="page-summary" aria-label="当前页作答统计">
        <span>当前页 {{ records.length }} 条</span>
        <span
          >正确 {{ pageSummary.correct }} · 需复习 {{ pageSummary.wrong
          }}<template v-if="pageSummary.pending"> · 待判分 {{ pageSummary.pending }}</template></span
        >
        <span
          >已判分正确率 <strong>{{ pageSummary.rate }}</strong></span
        >
      </div>
      <LpStatePanel
        :state="state"
        :title="state === 'error' ? '练习记录暂时无法加载' : filtered ? '没有符合筛选条件的记录' : '还没有练习记录'"
        :description="
          state === 'error'
            ? '请重试，已提交的作答会继续保留。'
            : filtered
              ? '可以清除筛选，再查看全部作答。'
              : '完成一道题后，可以在这里回看作答。'
        "
        loading-label="正在加载练习记录"
        @retry="load"
      >
        <template #actions
          ><el-button v-if="filtered" @click="resetFilters">查看全部记录</el-button
          ><el-button v-else @click="router.push('/practice')">开始练习</el-button></template
        >
        <ol class="record-list">
          <li v-for="item in records" :key="item.id" class="record-row">
            <div class="record-meta">
              <span
                class="answer-state"
                :data-result="item.isCorrect === 1 ? 'correct' : item.isCorrect === 0 ? 'wrong' : 'pending'"
                >{{ answerStatus(item.isCorrect) }}</span
              >
              <span>{{ item.courseName || '未关联课程' }}</span>
              <span>{{ questionTypeLabel(item.questionType) }}</span>
              <span>{{ difficultyLabel(item.difficulty) }}</span>
              <time class="record-time">{{ recordTime(item.createTime) }}</time>
            </div>
            <p class="question-content">{{ item.questionContent }}</p>
            <div class="answer-line">
              <span class="answer-label">我的答案</span
              ><span class="answer-content">{{ item.userAnswer || '未填写' }}</span
              ><span class="answer-time">{{ item.answerTime == null ? '耗时未记录' : `${item.answerTime} 秒` }}</span>
            </div>
          </li>
        </ol>
      </LpStatePanel>
      <el-pagination
        v-if="total && total > size"
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="resetPage"
      />
    </section>
  </div>
</template>

<style scoped>
.record-page {
  display: grid;
  gap: var(--lp-space-6);
}
.record-panel {
  padding: var(--lp-space-5);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
}
.record-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--lp-space-3);
  padding-bottom: var(--lp-space-5);
}
.record-filters :deep(.el-form-item) {
  margin: 0;
}
.record-filters .el-select {
  width: 152px;
}
.total-count {
  margin-left: auto;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}
.page-summary {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lp-space-3) var(--lp-space-5);
  padding: var(--lp-space-3) 0;
  border-top: var(--lp-border-hairline);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.page-summary strong {
  color: var(--lp-text);
  font-weight: var(--lp-weight-medium);
}
.record-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.record-row {
  padding: var(--lp-space-5) 0;
  border-top: var(--lp-border-hairline);
}
.record-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--lp-space-3);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
.answer-state {
  padding: var(--lp-space-1) var(--lp-space-2);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-sm);
  color: var(--lp-text-secondary);
}
.answer-state[data-result='correct'] {
  color: var(--lp-success);
  background: var(--lp-success-soft);
}
.answer-state[data-result='wrong'] {
  color: var(--lp-warning);
  background: var(--lp-warning-soft);
}
.record-time {
  margin-left: auto;
}
.question-content {
  margin: var(--lp-space-3) 0;
  line-height: var(--lp-leading-relaxed);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.answer-line {
  display: flex;
  align-items: baseline;
  gap: var(--lp-space-3);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}
.answer-label,
.answer-time {
  color: var(--lp-text-muted);
  flex-shrink: 0;
}
.answer-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  min-width: 0;
}
.answer-time {
  margin-left: auto;
}
.el-pagination {
  padding-top: var(--lp-space-5);
  justify-content: flex-end;
  border-top: var(--lp-border-hairline);
}
@media (max-width: 767px) {
  .record-time {
    margin-left: 0;
  }
  .total-count {
    width: 100%;
    margin-left: 0;
  }
  .answer-line {
    flex-wrap: wrap;
  }
  .answer-time {
    width: 100%;
  }
}
</style>
