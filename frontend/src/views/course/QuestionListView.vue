<template>
  <div class="question-list page-container">
    <LpPageHeader title="题库" description="选一道题开始练习，或收藏后集中练习。">
      <template #actions><RouterLink class="collection-link" to="/favorites">查看我的收藏</RouterLink></template>
    </LpPageHeader>

    <section class="filter-panel" aria-label="题目筛选">
      <div class="filter-fields">
        <div class="filter-field course-filter">
          <label for="catalog-course">课程</label>
          <el-select
            id="catalog-course"
            v-model="filters.courseId"
            placeholder="全部课程"
            clearable
            filterable
            :loading="coursesLoading"
            :disabled="coursesLoading || !!coursesError"
            @change="handleFilterChange"
          >
            <el-option v-for="course in courseList" :key="course.id" :label="course.name" :value="course.id" />
          </el-select>
        </div>
        <div class="filter-field type-filter">
          <label for="catalog-type">题型</label>
          <el-select
            id="catalog-type"
            v-model="filters.questionType"
            placeholder="全部题型"
            @change="handleFilterChange"
          >
            <el-option v-for="type in questionTypes" :key="type.value" :label="type.label" :value="type.value" />
          </el-select>
        </div>
        <fieldset class="difficulty-filter">
          <legend>难度</legend>
          <div class="difficulty-options">
            <button
              v-for="item in difficultyOptions"
              :key="item.value"
              type="button"
              class="difficulty-chip"
              :aria-pressed="filters.difficulty === item.value"
              @click="selectDifficulty(item.value)"
            >
              <span>{{ item.value }}</span> {{ item.label }}
            </button>
          </div>
        </fieldset>
        <el-button v-if="activeFilterCount" text @click="resetFilters">重置条件</el-button>
      </div>
      <div v-if="coursesError" class="inline-message" role="alert">
        <span>{{ coursesError }}</span
        ><el-button text @click="loadCourses">重新加载课程</el-button>
      </div>
      <div v-if="searchContext" class="search-context">
        <span>{{ searchContext.label }}</span>
        <el-button text @click="clearSearchContext">清除搜索筛选</el-button>
      </div>
      <p v-if="searchQueryWarning" class="search-query-warning" role="status">{{ searchQueryWarning }}</p>
    </section>

    <div v-if="favoritesError" class="inline-message" role="alert">
      <span>{{ favoritesError }}重新加载后可继续收藏。</span>
      <el-button text :loading="favoritesLoading" @click="loadFavoriteIds">重新加载收藏状态</el-button>
    </div>
    <p v-if="actionError" class="action-message is-error" role="alert">{{ actionError }}</p>
    <p v-else-if="notice" class="action-message" role="status">{{ notice }}</p>

    <section class="question-results" aria-label="题目列表">
      <div class="result-toolbar">
        <p>{{ resultSummary }}</p>
        <span v-if="favoritesReady">已收藏 {{ favoriteSet.size }} 题</span>
        <span v-else-if="favoritesLoading">正在读取收藏状态…</span>
      </div>
      <LpStatePanel v-if="loading" state="loading" loading-label="正在加载题目" />
      <LpStatePanel v-else-if="loadError" state="error" :description="loadError" @retry="retryFetch" />
      <LpStatePanel
        v-else-if="!questions.length"
        state="empty"
        title="没有找到符合条件的题目"
        description="调整课程、题型或难度后再试。"
      >
        <template #actions>
          <el-button v-if="searchContext" @click="clearSearchContext">清除搜索筛选</el-button>
          <el-button v-else @click="resetFilters">查看全部题目</el-button>
        </template>
      </LpStatePanel>
      <div v-else class="question-collection">
        <article
          v-for="(question, index) in questions"
          :key="question.id"
          class="question-card"
          :aria-labelledby="`question-title-${question.id}`"
        >
          <header class="question-meta">
            <h2 :id="`question-title-${question.id}`">第 {{ (pageNum - 1) * pageSize + index + 1 }} 题</h2>
            <span>{{ questionTypeLabel(question.questionType) }}</span>
            <span>{{ question.courseName || '未关联课程' }}</span>
            <span>{{ difficultyLabel(question.difficulty) }}</span>
            <span>{{ question.score }} 分</span>
          </header>
          <div class="question-content"><MarkdownRenderer :content="question.content" /></div>
          <ol
            v-if="question.options?.length"
            class="question-options"
            :class="{
              'has-long-options': question.options.some(
                (option) => option.content.length > 45 || option.content.includes('\n'),
              ),
            }"
          >
            <li v-for="option in question.options" :key="option.id">
              <span class="option-label">{{ option.optionLabel }}.</span><span>{{ option.content }}</span>
            </li>
          </ol>
          <div v-if="question.knowledgePointNames?.length" class="question-tags" aria-label="相关知识点">
            <span v-for="name in question.knowledgePointNames" :key="name">{{ name }}</span>
          </div>
          <footer class="question-actions">
            <el-button
              type="primary"
              text
              :icon="ArrowRight"
              :loading="practiceStartingId === question.id"
              :disabled="practiceStartingId !== null"
              @click="startQuestionPractice(question.id)"
              >练这道题</el-button
            >
            <div class="secondary-actions">
              <button
                type="button"
                class="quiet-action favorite-btn"
                :aria-label="`${favoriteSet.has(question.id) ? '取消收藏' : '收藏题目'}：第 ${(pageNum - 1) * pageSize + index + 1} 题`"
                :aria-pressed="favoritesReady ? favoriteSet.has(question.id) : undefined"
                :aria-busy="favoritePending.has(question.id)"
                :disabled="!favoritesReady || favoritesLoading || favoritePending.has(question.id)"
                @click="toggleFavorite(question.id)"
              >
                <el-icon aria-hidden="true"
                  ><StarFilled v-if="favoritesReady && favoriteSet.has(question.id)" /><Star v-else
                /></el-icon>
                {{
                  favoritePending.has(question.id)
                    ? '正在保存'
                    : favoritesReady && favoriteSet.has(question.id)
                      ? '已收藏'
                      : '收藏'
                }}
              </button>
              <button
                type="button"
                class="quiet-action"
                :aria-expanded="expandedComments.has(question.id)"
                :aria-controls="`question-discussion-${question.id}`"
                @click="toggleComment(question.id)"
              >
                <el-icon aria-hidden="true"><ChatLineRound /></el-icon
                >{{ expandedComments.has(question.id) ? '收起讨论' : '讨论' }}
              </button>
              <button type="button" class="quiet-action" @click="openCorrectionDialog(question)">
                <el-icon aria-hidden="true"><Warning /></el-icon>纠错
              </button>
            </div>
          </footer>
          <div
            v-if="expandedComments.has(question.id)"
            :id="`question-discussion-${question.id}`"
            class="comment-section"
          >
            <QuestionComment :question-id="question.id" />
          </div>
        </article>
      </div>
      <div v-if="!loading && !loadError && total > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="fetchQuestions"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog
      v-model="correctionDialogVisible"
      title="提交题目纠错"
      width="560px"
      class="catalog-correction-dialog"
      :show-close="!correctionSubmitting"
      :close-on-click-modal="false"
      :close-on-press-escape="!correctionSubmitting"
    >
      <div v-if="correctionQuestion" class="correction-question">
        <span>{{ questionTypeLabel(correctionQuestion.questionType) }}</span>
        <p>{{ correctionQuestion.content }}</p>
      </div>
      <el-form label-position="top" :disabled="correctionSubmitting" @submit.prevent="submitCorrection">
        <el-form-item label="需要核对的内容">
          <el-select v-model="correctionForm.reportType" aria-label="需要核对的内容">
            <el-option label="题干内容" value="CONTENT" /><el-option label="答案" value="ANSWER" />
            <el-option label="解析" value="ANALYSIS" /><el-option label="知识点关联" value="KNOWLEDGE_POINT" />
            <el-option label="其他问题" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题描述">
          <el-input
            v-model="correctionForm.description"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            aria-label="问题描述"
            placeholder="说明疑问所在；如有依据，也可以一起提供。"
          />
        </el-form-item>
      </el-form>
      <p v-if="correctionError" class="correction-error" role="alert">{{ correctionError }}</p>
      <template #footer>
        <el-button :disabled="correctionSubmitting" @click="correctionDialogVisible = false">暂时关闭</el-button>
        <el-button type="primary" :loading="correctionSubmitting" @click="submitCorrection">提交纠错</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ArrowRight, Star, StarFilled, ChatLineRound, Warning } from '@element-plus/icons-vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import QuestionComment from '@/components/QuestionComment.vue'
import { useQuestionCatalog } from './useQuestionCatalog'

const {
  questions,
  loading,
  loadError,
  pageNum,
  pageSize,
  total,
  filters,
  questionTypes,
  difficultyOptions,
  courseList,
  coursesLoading,
  coursesError,
  loadCourses,
  favoriteSet,
  favoritePending,
  favoritesLoading,
  favoritesReady,
  favoritesError,
  loadFavoriteIds,
  actionError,
  notice,
  practiceStartingId,
  startQuestionPractice,
  expandedComments,
  correctionDialogVisible,
  correctionSubmitting,
  correctionQuestion,
  correctionForm,
  correctionError,
  activeFilterCount,
  resultSummary,
  searchContext,
  searchQueryWarning,
  toggleComment,
  questionTypeLabel,
  difficultyLabel,
  handleFilterChange,
  handleSizeChange,
  selectDifficulty,
  resetFilters,
  retryFetch,
  clearSearchContext,
  fetchQuestions,
  toggleFavorite,
  openCorrectionDialog,
  submitCorrection,
} = useQuestionCatalog()
</script>

<style scoped>
.question-list {
  display: grid;
  gap: var(--lp-space-5);
}
.collection-link {
  color: var(--lp-primary);
  font-size: var(--lp-text-sm);
  text-underline-offset: 4px;
}
.filter-panel {
  padding: var(--lp-space-5);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
}
.filter-fields {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: var(--lp-space-4);
}
.filter-field {
  display: grid;
  gap: var(--lp-space-2);
}
.filter-field label,
.difficulty-filter legend {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-medium);
}
.course-filter {
  flex: 1 1 220px;
}
.type-filter {
  flex: 0 1 160px;
}
.difficulty-filter {
  margin: 0;
  padding: 0;
  border: 0;
  min-width: 0;
}
.difficulty-filter legend {
  margin-bottom: var(--lp-space-2);
  padding: 0;
}
.difficulty-options {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lp-space-1);
}
.difficulty-chip {
  min-height: 34px;
  padding: var(--lp-space-2) var(--lp-space-3);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-sm);
  background: var(--lp-surface);
  color: var(--lp-text-secondary);
  cursor: pointer;
  font: inherit;
  font-size: var(--lp-text-sm);
}
.difficulty-chip span {
  font-variant-numeric: tabular-nums;
  margin-right: var(--lp-space-1);
}
.difficulty-chip[aria-pressed='true'] {
  color: var(--lp-primary);
  border-color: var(--lp-primary);
  background: var(--lp-primary-soft);
}
.search-context,
.inline-message {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--lp-space-3);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.search-context {
  margin-top: var(--lp-space-3);
  border-top: var(--lp-border-hairline);
  padding-top: var(--lp-space-2);
}
.inline-message {
  padding: var(--lp-space-3) var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface-subtle);
}
.filter-panel .inline-message {
  margin-top: var(--lp-space-3);
}
.action-message,
.search-query-warning {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.action-message.is-error,
.correction-error {
  color: var(--lp-danger);
}
.result-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-3);
  margin-bottom: var(--lp-space-3);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}
.result-toolbar p {
  margin: 0;
}
.question-collection {
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
  overflow: clip;
}
.question-card {
  padding: var(--lp-space-6);
}
.question-card + .question-card {
  border-top: var(--lp-border-hairline);
}
.question-meta {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: var(--lp-space-3);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}
.question-meta h2 {
  margin: 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
}
.question-meta > span + span::before {
  content: '·';
  margin-right: var(--lp-space-3);
  color: var(--lp-border-strong);
}
.question-content {
  margin: var(--lp-space-4) 0;
  max-width: 86ch;
  overflow-wrap: anywhere;
}
.question-content :deep(.markdown-body) {
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-relaxed);
}
.question-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lp-space-2) var(--lp-space-5);
  margin: 0;
  padding: 0;
  list-style: none;
  max-width: 90ch;
}
.question-options.has-long-options {
  grid-template-columns: 1fr;
}
.question-options li {
  display: flex;
  gap: var(--lp-space-2);
  padding: var(--lp-space-2) 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.option-label {
  color: var(--lp-text-muted);
  flex: 0 0 20px;
}
.question-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lp-space-2);
  margin-top: var(--lp-space-4);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
.question-tags span {
  padding: 2px var(--lp-space-2);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-sm);
}
.question-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--lp-space-3);
  margin-top: var(--lp-space-4);
}
.question-actions > .el-button {
  margin-left: calc(-1 * var(--lp-space-3));
}
.secondary-actions {
  display: flex;
  gap: var(--lp-space-4);
}
.quiet-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--lp-space-1);
  min-height: 36px;
  padding: var(--lp-space-1);
  border: 0;
  background: transparent;
  color: var(--lp-text-secondary);
  cursor: pointer;
  font: inherit;
  font-size: var(--lp-text-sm);
}
.quiet-action[aria-pressed='true'],
.quiet-action:hover:not(:disabled) {
  color: var(--lp-primary);
}
.quiet-action:disabled {
  cursor: default;
  opacity: 0.6;
}
.comment-section {
  margin-top: var(--lp-space-4);
  padding-top: var(--lp-space-4);
  border-top: var(--lp-border-hairline);
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--lp-space-5);
}
.correction-question {
  max-height: 180px;
  overflow: auto;
  margin-bottom: var(--lp-space-4);
  padding: var(--lp-space-3);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-sm);
}
.correction-question span {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
.correction-question p {
  margin: var(--lp-space-2) 0 0;
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-body);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.correction-error {
  margin: 0;
  line-height: var(--lp-leading-body);
  font-size: var(--lp-text-sm);
}
@media (max-width: 767px) {
  .filter-field {
    flex: 1 1 100%;
  }
  .question-card {
    padding: var(--lp-space-4);
  }
  .question-options {
    grid-template-columns: 1fr;
  }
  .secondary-actions {
    gap: var(--lp-space-2);
  }
  .pagination-wrap {
    justify-content: center;
  }
}
</style>
