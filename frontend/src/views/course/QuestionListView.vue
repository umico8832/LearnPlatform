<template>
  <div class="question-list page-container">
    <LpPageHeader
      kicker="题库"
      title="题库浏览"
      description="按课程、题型和难度快速定位题目，把值得回看的内容收藏起来。"
    >
      <template #actions>
        <div class="hero-metrics">
          <div class="metric-item">
            <span>当前结果</span>
            <strong>{{ total }}</strong>
          </div>
          <div class="metric-item">
            <span>已收藏</span>
            <strong>{{ favoriteSet.size }}</strong>
          </div>
        </div>
      </template>
    </LpPageHeader>

    <section class="question-workbench">
      <aside class="filter-panel">
        <div class="panel-title">
          <div>
            <span class="section-kicker">筛选</span>
            <h3>缩小题目范围</h3>
          </div>
          <el-button v-if="activeFilterCount > 0" link type="primary" @click="resetFilters">清空</el-button>
        </div>

        <div class="filter-block">
          <span class="filter-label">题型</span>
          <el-radio-group v-model="filters.questionType" class="type-switch" @change="handleFilterChange">
            <el-radio-button v-for="type in questionTypes" :key="type.value" :value="type.value">
              {{ type.shortLabel }}
            </el-radio-button>
          </el-radio-group>
        </div>

        <div class="filter-block">
          <span class="filter-label">所属课程</span>
          <el-select
            v-model="filters.courseId"
            placeholder="全部课程"
            clearable
            filterable
            @change="handleFilterChange"
          >
            <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </div>

        <div class="filter-block">
          <span class="filter-label">难度</span>
          <div class="difficulty-grid">
            <button
              v-for="item in difficultyOptions"
              :key="item.value"
              type="button"
              class="difficulty-chip"
              :class="{ active: filters.difficulty === item.value }"
              @click="selectDifficulty(item.value)"
            >
              <strong>{{ item.value }}</strong>
              <span>{{ item.label }}</span>
            </button>
          </div>
        </div>
      </aside>

      <main class="question-results">
        <div class="result-toolbar">
          <div>
            <h3>题目列表</h3>
            <p>{{ resultSummary }}</p>
          </div>
          <el-tag v-if="activeFilterCount > 0" type="info" effect="plain">{{ activeFilterCount }} 个筛选条件</el-tag>
        </div>

        <div v-loading="loading" class="result-body" element-loading-background="rgba(255,255,255,0.72)">
          <div v-if="questions.length === 0 && !loading" class="empty-state">
            <el-empty description="暂无符合条件的题目">
              <el-button type="primary" @click="resetFilters">查看全部题目</el-button>
            </el-empty>
          </div>

          <article v-for="q in questions" :key="q.id" class="question-card">
            <div class="question-main">
              <div class="question-meta">
                <el-tag size="small" :type="questionTypeTag(q.questionType)">
                  {{ questionTypeLabel(q.questionType) }}
                </el-tag>
                <span>{{ q.courseName || '未关联课程' }}</span>
                <span>{{ difficultyLabel(q.difficulty) }}</span>
                <span>{{ q.score }} 分</span>
              </div>

              <h4>{{ q.content }}</h4>

              <div v-if="q.options && q.options.length > 0" class="question-options">
                <div v-for="opt in q.options" :key="opt.id" class="option-item">
                  <span class="option-label">{{ opt.optionLabel }}</span>
                  <span>{{ opt.content }}</span>
                </div>
              </div>
            </div>

            <div class="question-side">
              <el-tooltip :content="favoriteSet.has(q.id) ? '取消收藏' : '收藏题目'" placement="top">
                <button
                  type="button"
                  class="icon-action favorite-btn"
                  :class="{ active: favoriteSet.has(q.id) }"
                  @click.stop="toggleFavorite(q.id)"
                >
                  <el-icon :size="18">
                    <StarFilled v-if="favoriteSet.has(q.id)" />
                    <Star v-else />
                  </el-icon>
                </button>
              </el-tooltip>
              <button type="button" class="text-action" @click="toggleComment(q.id)">
                <el-icon><ChatLineRound /></el-icon>
                <span>{{ expandedComments.has(q.id) ? '收起讨论' : '讨论' }}</span>
              </button>
              <button type="button" class="text-action" @click="openCorrectionDialog(q)">
                <el-icon><Warning /></el-icon>
                <span>纠错</span>
              </button>
            </div>

            <div class="question-footer">
              <div v-if="q.knowledgePointNames && q.knowledgePointNames.length > 0" class="question-tags">
                <el-tag v-for="name in q.knowledgePointNames" :key="name" size="small" type="info" class="kp-tag">
                  {{ name }}
                </el-tag>
              </div>
              <span v-else class="no-kp">暂无知识点标签</span>
            </div>

            <div v-if="expandedComments.has(q.id)" class="comment-section">
              <QuestionComment :question-id="q.id" />
            </div>
          </article>
        </div>

        <div class="pagination-wrap">
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
      </main>
    </section>

    <el-dialog v-model="correctionDialogVisible" title="提交题目纠错" width="520px" destroy-on-close>
      <div v-if="correctionQuestion" class="correction-question">
        <span>#{{ correctionQuestion.id }} · {{ questionTypeLabel(correctionQuestion.questionType) }}</span>
        <p>{{ correctionQuestion.content }}</p>
      </div>
      <el-form label-width="88px" @submit.prevent>
        <el-form-item label="问题类型">
          <el-select v-model="correctionForm.reportType" style="width: 100%">
            <el-option label="题干内容错误" value="CONTENT" />
            <el-option label="答案有误" value="ANSWER" />
            <el-option label="解析有误" value="ANALYSIS" />
            <el-option label="知识点关联错误" value="KNOWLEDGE_POINT" />
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
            placeholder="请描述你发现的问题，例如正确答案、题干错字或解析不清楚之处"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="correctionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="correctionSubmitting" @click="submitCorrection">提交纠错</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Star, StarFilled, ChatLineRound, Warning } from '@element-plus/icons-vue'
import QuestionComment from '@/components/QuestionComment.vue'
import { useQuestionCatalog } from './useQuestionCatalog'

const {
  questions,
  loading,
  pageNum,
  pageSize,
  total,
  filters,
  questionTypes,
  difficultyOptions,
  courseList,
  favoriteSet,
  expandedComments,
  correctionDialogVisible,
  correctionSubmitting,
  correctionQuestion,
  correctionForm,
  activeFilterCount,
  resultSummary,
  toggleComment,
  questionTypeLabel,
  questionTypeTag,
  difficultyLabel,
  handleFilterChange,
  handleSizeChange,
  selectDifficulty,
  resetFilters,
  fetchQuestions,
  toggleFavorite,
  openCorrectionDialog,
  submitCorrection,
} = useQuestionCatalog()
</script>

<style scoped>
.question-list {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-5);
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(2, 118px);
  gap: var(--lp-space-2);
  align-items: stretch;
}

.metric-item {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 72px;
  padding: var(--lp-space-3) var(--lp-space-4);
  background: var(--lp-surface-soft);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
}

.metric-item span {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
}

.metric-item strong {
  margin-top: 4px;
  color: var(--lp-text);
  font-size: var(--lp-text-2xl);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.question-workbench {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.filter-panel,
.question-results {
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  box-shadow: var(--lp-shadow-sm);
}

.filter-panel {
  position: sticky;
  top: 76px;
  padding: 18px;
}

.panel-title,
.result-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.panel-title h3,
.result-toolbar h3 {
  margin: 4px 0 0;
  color: var(--lp-text);
  font-size: 18px;
}

.filter-block {
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin-top: 18px;
}

.filter-label {
  color: var(--lp-text-muted);
  font-size: 13px;
  font-weight: 700;
}

.type-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.type-switch :deep(.el-radio-button__inner) {
  width: 100%;
  border: 1px solid var(--lp-border);
  border-radius: 7px !important;
  box-shadow: none !important;
}

.type-switch :deep(.el-radio-button:first-child .el-radio-button__inner),
.type-switch :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 7px !important;
}

.difficulty-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 6px;
}

.difficulty-chip {
  min-width: 0;
  padding: 9px 4px;
  color: var(--lp-text-secondary);
  background: var(--lp-surface-soft);
  border: 1px solid var(--lp-border);
  border-radius: 7px;
  cursor: pointer;
}

.difficulty-chip strong,
.difficulty-chip span {
  display: block;
}

.difficulty-chip strong {
  color: var(--lp-text);
  font-size: 15px;
}

.difficulty-chip span {
  margin-top: 2px;
  font-size: 12px;
}

.difficulty-chip.active,
.difficulty-chip:hover {
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
  border-color: var(--lp-primary);
}

.difficulty-chip.active strong,
.difficulty-chip:hover strong {
  color: var(--lp-primary);
}

.question-results {
  min-width: 0;
  padding: 18px;
}

.result-toolbar {
  margin-bottom: 14px;
}

.result-toolbar p {
  margin: 5px 0 0;
  color: var(--lp-text-muted);
  font-size: 13px;
}

.result-body {
  min-height: 260px;
}

.question-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  padding: 18px;
  margin-bottom: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.question-card:hover {
  border-color: var(--lp-border-strong);
  box-shadow: var(--lp-shadow-md);
}

.question-main {
  min-width: 0;
}

.question-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--lp-text-muted);
  font-size: 13px;
}

.question-meta span:not(.el-tag) {
  padding-right: 8px;
  border-right: 1px solid var(--lp-border);
}

.question-meta span:last-child {
  border-right: 0;
}

.question-card h4 {
  margin: 12px 0;
  color: var(--lp-text);
  font-size: 16px;
  line-height: 1.75;
}

.question-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.option-item {
  display: flex;
  gap: 8px;
  min-width: 0;
  padding: 9px 10px;
  color: var(--lp-text-secondary);
  background: var(--lp-surface-soft);
  border: 1px solid var(--lp-border);
  border-radius: 7px;
  font-size: 14px;
  line-height: 1.55;
}

.option-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
  border-radius: 50%;
  font-size: 12px;
  font-weight: 800;
}

.question-side {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.icon-action,
.text-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  border: 1px solid var(--lp-border);
  border-radius: 7px;
  cursor: pointer;
}

.icon-action {
  width: 34px;
  color: var(--lp-text-muted);
  background: var(--lp-surface);
}

.icon-action.active {
  color: var(--lp-accent);
  border-color: #efd99b;
  background: #fff8e7;
}

.text-action {
  gap: 5px;
  padding: 0 10px;
  color: var(--lp-text-secondary);
  background: var(--lp-surface);
  white-space: nowrap;
}

.icon-action:hover,
.text-action:hover {
  color: var(--lp-primary);
  border-color: var(--lp-primary);
}

.correction-question {
  margin-bottom: 14px;
  padding: 12px;
  background: var(--lp-surface-soft);
  border: 1px solid var(--lp-border);
  border-radius: 7px;
}

.correction-question span {
  color: var(--lp-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.correction-question p {
  margin: 6px 0 0;
  color: var(--lp-text);
  font-size: 14px;
  line-height: 1.7;
}

.question-footer {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 2px;
}

.question-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.kp-tag {
  font-size: 12px;
}

.no-kp {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.comment-section {
  grid-column: 1 / -1;
  padding-top: 12px;
  border-top: 1px solid var(--lp-border);
}

.empty-state {
  padding: 44px 0;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
}

@media (max-width: 1080px) {
  .question-workbench {
    grid-template-columns: 1fr;
  }

  .filter-panel {
    position: static;
  }
}

@media (max-width: 720px) {
  .panel-title,
  .result-toolbar {
    flex-direction: column;
  }

  .hero-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    width: 100%;
  }

  .question-results,
  .filter-panel {
    padding: 14px;
  }

  .type-switch {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .question-card {
    grid-template-columns: 1fr;
    padding: 14px;
  }

  .question-side {
    justify-content: space-between;
  }

  .question-options {
    grid-template-columns: 1fr;
  }

  .pagination-wrap {
    justify-content: center;
  }
}

@media (max-width: 420px) {
  .type-switch {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .difficulty-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
