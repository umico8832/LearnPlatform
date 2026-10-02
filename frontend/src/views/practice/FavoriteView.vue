<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useFavoriteLibrary } from './useFavoriteLibrary'
import { difficultyLabel, questionTypeLabel, recordTime } from './practiceLibraryPresentation'

const router = useRouter()
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
} = useFavoriteLibrary()
const busy = computed(() => startingId.value !== null || removingId.value !== null)
</script>

<template>
  <div class="favorite-page page-container">
    <LpPageHeader title="我的收藏" description="把值得再做的题目留在这里，按自己的节奏练习。">
      <template #actions><el-button @click="router.push('/questions')">浏览题库</el-button></template>
    </LpPageHeader>

    <section class="practice-toolbar" aria-label="收藏题练习">
      <div class="collection-count">
        <strong>{{ total === null ? '收藏题目' : `${total} 道收藏题` }}</strong>
        <span>从收藏中抽取一组题目</span>
      </div>
      <div class="practice-controls">
        <label for="favorite-practice-count">题目数</label>
        <el-input-number
          id="favorite-practice-count"
          v-model="practiceCount"
          :min="1"
          :max="50"
          controls-position="right"
          :disabled="busy"
        />
        <el-button
          type="primary"
          :loading="startingId === 'all'"
          :disabled="loading || !total || busy"
          @click="startPractice()"
          >练习收藏题</el-button
        >
      </div>
    </section>

    <p v-if="actionError" class="action-error" role="alert">{{ actionError }}</p>
    <p v-else-if="notice" class="action-notice" role="status">{{ notice }}</p>

    <section class="favorite-list" aria-label="收藏题目">
      <div class="list-heading">
        <h2>最近收藏</h2>
        <el-button text :loading="loading" :disabled="busy" @click="load">刷新列表</el-button>
      </div>
      <LpStatePanel
        :state="state"
        :title="state === 'error' ? '收藏暂时无法加载' : '还没有收藏题目'"
        :description="
          state === 'error' ? '请重试，已收藏的题目会继续保留。' : '在题库中收藏想要回看的题目，再回来练习。'
        "
        loading-label="正在加载收藏题目"
        @retry="load"
      >
        <template #actions><el-button @click="router.push('/questions')">浏览题库</el-button></template>
        <ol class="question-list">
          <li v-for="(item, index) in records" :key="item.id" class="question-row">
            <span class="question-order" aria-hidden="true">{{ (page - 1) * size + index + 1 }}</span>
            <div class="question-copy">
              <div class="question-meta">
                <span>{{ item.courseName || '未关联课程' }}</span
                ><span>{{ questionTypeLabel(item.questionType) }}</span
                ><span>{{ difficultyLabel(item.difficulty) }}</span>
              </div>
              <p class="question-content">{{ item.questionContent }}</p>
              <span class="favorite-time">收藏于 {{ recordTime(item.createTime) }}</span>
            </div>
            <div class="question-actions">
              <el-button
                :loading="startingId === item.questionId"
                :disabled="busy"
                :aria-label="`练习第 ${(page - 1) * size + index + 1} 道收藏题`"
                @click="startPractice(item.questionId)"
                >练习</el-button
              >
              <el-popconfirm
                title="取消收藏这道题目？"
                confirm-button-text="取消收藏"
                cancel-button-text="保留"
                :disabled="busy"
                @confirm="remove(item.questionId)"
              >
                <template #reference
                  ><el-button
                    text
                    :loading="removingId === item.questionId"
                    :disabled="busy"
                    :aria-label="`取消收藏第 ${(page - 1) * size + index + 1} 道题`"
                    >取消收藏</el-button
                  ></template
                >
              </el-popconfirm>
            </div>
          </li>
        </ol>
      </LpStatePanel>
      <el-pagination
        v-if="total && total > size"
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :disabled="busy"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="resetPage"
      />
    </section>
  </div>
</template>

<style scoped>
.favorite-page {
  display: grid;
  gap: var(--lp-space-6);
}
.practice-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-5);
  padding: var(--lp-space-5);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
}
.collection-count {
  display: grid;
  gap: var(--lp-space-2);
}
.collection-count strong {
  font-size: var(--lp-text-lg);
  font-weight: var(--lp-weight-semibold);
}
.collection-count span,
.practice-controls label {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.practice-controls {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
}
.practice-controls .el-input-number {
  width: 112px;
}
.favorite-list {
  padding: 0 var(--lp-space-5);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
}
.list-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--lp-space-4) 0;
}
.list-heading h2 {
  margin: 0;
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-semibold);
}
.question-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.question-row {
  display: flex;
  gap: var(--lp-space-4);
  padding: var(--lp-space-5) 0;
  border-top: var(--lp-border-hairline);
}
.question-order {
  flex: 0 0 24px;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
  font-variant-numeric: tabular-nums;
}
.question-copy {
  min-width: 0;
  flex: 1;
}
.question-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lp-space-3);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
}
.question-content {
  margin: var(--lp-space-3) 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: var(--lp-leading-relaxed);
}
.favorite-time {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
.question-actions {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: var(--lp-space-2);
  align-items: stretch;
}
.question-actions .el-button + .el-button {
  margin-left: 0;
}
.action-error,
.action-notice {
  margin: 0;
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}
.action-error {
  color: var(--lp-danger);
}
.action-notice {
  color: var(--lp-text-secondary);
}
.el-pagination {
  padding: var(--lp-space-5) 0;
  justify-content: flex-end;
  border-top: var(--lp-border-hairline);
}
.lp-state-panel:not([data-state='ready']) {
  padding-bottom: var(--lp-space-5);
}
@media (max-width: 767px) {
  .practice-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .practice-controls {
    flex-wrap: wrap;
  }
  .question-row {
    flex-wrap: wrap;
  }
  .question-actions {
    flex-direction: row;
    margin-left: calc(24px + var(--lp-space-4));
  }
}
</style>
