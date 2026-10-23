<template>
  <main class="lt question-list" data-appearance="dark">
    <LtPageHeader title="题库" description="选一道题开始练习，或收藏后集中练习。">
      <template #lead><LtBackLink to="/practice" label="返回练习" /></template>
      <template #actions><RouterLink class="collection-link" to="/favorites">查看我的收藏</RouterLink></template>
    </LtPageHeader>
    <CatalogSearch />
    <section class="filter-panel" aria-label="题目筛选">
      <div class="filter-fields">
        <div class="filter-field course-filter">
          <label for="catalog-course">课程</label>
          <select
            id="catalog-course"
            v-model="filters.courseId"
            :disabled="coursesLoading || !!coursesError"
            @change="handleFilterChange"
          >
            <option :value="null">{{ coursesLoading ? '正在加载课程…' : '全部课程' }}</option>
            <option v-for="course in courseList" :key="course.id" :value="course.id">{{ course.name }}</option>
          </select>
        </div>
        <div class="filter-field type-filter">
          <label for="catalog-type">题型</label>
          <select id="catalog-type" v-model="filters.questionType" @change="handleFilterChange">
            <option v-for="type in questionTypes" :key="type.value" :value="type.value">{{ type.label }}</option>
          </select>
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
        <LtButton v-if="activeFilterCount" variant="quiet" @click="resetFilters">重置条件</LtButton>
      </div>
      <div v-if="coursesError" class="inline-message" role="alert">
        <span>{{ coursesError }}</span
        ><LtButton variant="quiet" @click="loadCourses">重新加载课程</LtButton>
      </div>
      <div v-if="searchContext" class="search-context">
        <span>{{ searchContext.label }}</span>
        <LtButton variant="quiet" @click="clearSearchContext">清除搜索筛选</LtButton>
      </div>
      <p v-if="searchQueryWarning" class="search-query-warning" role="status">{{ searchQueryWarning }}</p>
    </section>

    <div v-if="favoritesError" class="inline-message" role="alert">
      <span>{{ favoritesError }}重新加载后可继续收藏。</span>
      <LtButton variant="quiet" :disabled="favoritesLoading" @click="loadFavoriteIds">重新加载收藏状态</LtButton>
    </div>
    <p v-if="actionError" class="action-message is-error" role="alert">{{ actionError }}</p>
    <p v-else-if="notice" class="action-message" role="status">{{ notice }}</p>

    <section class="question-results" aria-label="题目列表">
      <div class="result-toolbar">
        <p>{{ resultSummary }}</p>
        <span v-if="favoritesReady">已收藏 {{ favoriteSet.size }} 题</span>
        <span v-else-if="favoritesLoading">正在读取收藏状态…</span>
      </div>
      <div v-if="loading" class="state" role="status" aria-busy="true">正在加载题目…</div>
      <div v-else-if="loadError" class="state" role="alert">
        <p>{{ loadError }}</p>
        <LtButton @click="retryFetch">重试</LtButton>
      </div>
      <div v-else-if="!questions.length" class="state">
        <h2>没有找到符合条件的题目</h2>
        <p>调整课程、题型或难度后再试。</p>
        <LtButton v-if="searchContext" @click="clearSearchContext">清除搜索筛选</LtButton
        ><LtButton v-else @click="resetFilters">查看全部题目</LtButton>
      </div>
      <LtCard v-else radius="xl" padding="none" class="question-collection">
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
          <div class="question-content"><QuestionProse :content="question.content" /></div>
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
              <span class="option-label">{{ option.optionLabel }}.</span
              ><QuestionProse :content="option.content" inline />
            </li>
          </ol>
          <div v-if="question.knowledgePointNames?.length" class="question-tags" aria-label="相关知识点">
            <span v-for="name in question.knowledgePointNames" :key="name">{{ name }}</span>
          </div>
          <footer class="question-actions">
            <LtButton
              variant="quiet"
              :disabled="practiceStartingId !== null"
              @click="startQuestionPractice(question.id)"
              >{{ practiceStartingId === question.id ? '正在准备…' : '练这道题' }}</LtButton
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
                <Star aria-hidden="true" />
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
                <MessageCircle aria-hidden="true" />{{ expandedComments.has(question.id) ? '收起讨论' : '讨论' }}
              </button>
              <button type="button" class="quiet-action" @click="openCorrectionDialog(question)">
                <CircleAlert aria-hidden="true" />纠错
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
      </LtCard>
      <nav v-if="!loading && !loadError && total > 0" class="pagination-wrap" aria-label="题库翻页">
        <label
          >每页
          <select v-model="pageSize" aria-label="每页题数" @change="handleSizeChange">
            <option v-for="size in [10, 20, 50]" :key="size" :value="size">{{ size }}</option>
          </select>
          题</label
        >
        <LtButton :disabled="pageNum <= 1" @click="changePage(-1)">上一页</LtButton
        ><span>第 {{ pageNum }} / {{ pages }} 页</span
        ><LtButton :disabled="pageNum >= pages" @click="changePage(1)">下一页</LtButton>
      </nav>
    </section>

    <LtSheet v-model:open="correctionOpen" title="提交题目纠错">
      <div v-if="correctionQuestion" class="correction-question">
        <span>{{ questionTypeLabel(correctionQuestion.questionType) }}</span>
        <p>{{ correctionQuestion.content }}</p>
      </div>
      <form class="correction-form" @submit.prevent="submitCorrection">
        <label
          >需要核对的内容<select
            v-model="correctionForm.reportType"
            aria-label="需要核对的内容"
            :disabled="correctionSubmitting"
          >
            <option value="CONTENT">题干内容</option>
            <option value="ANSWER">答案</option>
            <option value="ANALYSIS">解析</option>
            <option value="KNOWLEDGE_POINT">知识点关联</option>
            <option value="OTHER">其他问题</option>
          </select></label
        >
        <label
          >问题描述<textarea
            v-model="correctionForm.description"
            rows="4"
            maxlength="1000"
            aria-label="问题描述"
            placeholder="说明疑问所在；如有依据，也可以一起提供。"
            :disabled="correctionSubmitting"
          />
        </label>
        <p v-if="correctionError" class="correction-error" role="alert">{{ correctionError }}</p>
        <div class="sheet-actions">
          <LtButton :disabled="correctionSubmitting" @click="correctionDialogVisible = false">暂时关闭</LtButton
          ><LtButton type="submit" variant="primary" :disabled="correctionSubmitting">{{
            correctionSubmitting ? '正在提交…' : '提交纠错'
          }}</LtButton>
        </div>
      </form></LtSheet
    >
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Star, MessageCircle, CircleAlert } from '@lucide/vue'
import { LtBackLink, LtButton, LtCard, LtPageHeader, LtSheet } from '@/components/living'
import QuestionProse from '@/views/question/QuestionProse.vue'
import CatalogSearch from './CatalogSearch.vue'
import QuestionComment from './CatalogDiscussion.vue'
import { useQuestionCatalog } from './useQuestionCatalog'

function changePage(delta: number) {
  pageNum.value += delta
  void fetchQuestions()
}
const pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const correctionOpen = computed({
  get: () => correctionDialogVisible.value,
  set: (value) => {
    if (!correctionSubmitting.value) correctionDialogVisible.value = value
  },
})
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
  gap: var(--lt-space-5);
}
.collection-link {
  color: var(--lt-accent);
  text-underline-offset: var(--lt-space-1);
}
.filter-panel {
  padding: var(--lt-space-5);
  background: var(--lt-surface);
  border-radius: var(--lt-radius-xl);
}
.filter-fields {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: var(--lt-space-5);
}
.filter-field,
.correction-form,
.correction-form label {
  display: grid;
  gap: var(--lt-space-2);
}
.filter-field label,
legend {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
select,
textarea {
  box-sizing: border-box;
  border: var(--lt-space-1) solid transparent;
  border-radius: var(--lt-radius-sm);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  padding: var(--lt-space-2) var(--lt-space-3);
  font: inherit;
}
select option {
  background: var(--lt-surface);
  color: var(--lt-label);
}
textarea {
  width: 100%;
  resize: vertical;
  background: var(--lt-well);
}
fieldset {
  margin: 0;
  padding: 0;
  border: 0;
}
legend {
  margin-bottom: var(--lt-space-2);
}
.difficulty-options,
.secondary-actions,
.sheet-actions {
  display: flex;
  gap: var(--lt-space-2);
  flex-wrap: wrap;
}
.difficulty-chip {
  min-height: var(--lt-control-lg);
  border: 0;
  border-radius: var(--lt-radius-pill);
  padding: var(--lt-space-2) var(--lt-space-3);
  background: var(--lt-fill-2);
  color: var(--lt-label-2);
  cursor: pointer;
  font: inherit;
}
.difficulty-chip[aria-pressed='true'] {
  background: var(--lt-accent-soft);
  color: var(--lt-label);
}
.inline-message,
.search-context {
  display: flex;
  align-items: center;
  gap: var(--lt-space-3);
  flex-wrap: wrap;
  margin-top: var(--lt-space-3);
}
.action-message,
.search-query-warning {
  margin: 0;
  color: var(--lt-label-2);
}
.is-error,
.correction-error {
  color: var(--lt-red-text);
}
.result-toolbar {
  display: flex;
  justify-content: space-between;
  gap: var(--lt-space-4);
  margin-bottom: var(--lt-space-3);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.result-toolbar p {
  margin: 0;
}
.question-card {
  padding: var(--lt-space-7);
}
.question-card + .question-card {
  border-top: thin solid var(--lt-separator);
}
.question-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--lt-space-3);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.question-meta h2 {
  margin: 0;
  font-size: var(--lt-text-body);
}
.question-content {
  margin: var(--lt-space-5) 0;
}
.question-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lt-space-3) var(--lt-space-6);
  margin: 0;
  padding: 0;
  list-style: none;
}
.question-options.has-long-options {
  grid-template-columns: 1fr;
}
.question-options li {
  display: flex;
  gap: var(--lt-space-2);
  color: var(--lt-label-2);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.option-label {
  font-family: var(--lt-font-rounded);
}
.question-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-2);
  margin-top: var(--lt-space-4);
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}
.question-tags span {
  padding: var(--lt-space-1) var(--lt-space-2);
  background: var(--lt-fill-2);
  border-radius: var(--lt-radius-sm);
}
.question-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-5);
}
.quiet-action {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-2);
  min-height: var(--lt-control-lg);
  border: 0;
  border-radius: var(--lt-radius-pill);
  padding: var(--lt-space-2);
  background: transparent;
  color: var(--lt-label-2);
  cursor: pointer;
  font: inherit;
  font-size: var(--lt-text-footnote);
}
.quiet-action svg {
  width: var(--lt-space-4);
  height: var(--lt-space-4);
}
.quiet-action[aria-pressed='true'] {
  color: var(--lt-accent);
}
.quiet-action:hover {
  background: var(--lt-fill-2);
}
.quiet-action:disabled {
  color: var(--lt-label-3);
  cursor: not-allowed;
}
.comment-section {
  margin-top: var(--lt-space-5);
  padding-top: var(--lt-space-5);
  border-top: thin solid var(--lt-separator);
}
.pagination-wrap {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--lt-space-4);
  margin-top: var(--lt-space-5);
  color: var(--lt-label-2);
}
.state {
  padding: var(--lt-space-8);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface);
  color: var(--lt-label-2);
}
.state h2 {
  color: var(--lt-label);
  font-size: var(--lt-text-title3);
}
.correction-question {
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-md);
  background: var(--lt-well);
  max-height: calc(var(--lt-space-10) * 3);
  overflow: auto;
  margin-bottom: var(--lt-space-5);
}
.correction-question p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.correction-form {
  gap: var(--lt-space-5);
}
.sheet-actions {
  justify-content: flex-end;
}
</style>
