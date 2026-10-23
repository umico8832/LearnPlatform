<script setup lang="ts">
import { computed } from 'vue'
import type { ErrorPatternSummary } from '@/api/statistics'
const props = defineProps<{ patterns: ErrorPatternSummary }>()
const emit = defineEmits<{
  'similar-question': [questionId: number, questionContent?: string]
  'question-error-analysis': [questionId: number]
}>()
const total = computed(() =>
  Math.max(
    1,
    Object.values(props.patterns.masteryDistribution).reduce((sum, value) => sum + value, 0),
  ),
)
const maxWeek = computed(() => Math.max(1, ...props.patterns.weeklyErrorTrend.map((week) => week.count)))
</script>
<template>
  <section data-appearance="dark" class="lt patterns panel">
    <h2>错因分析</h2>
    <div class="grid">
      <article>
        <h3>错题掌握程度分布</h3>
        <div v-if="Object.keys(patterns.masteryDistribution).length" class="bars">
          <div v-for="(count, label) in patterns.masteryDistribution" :key="label">
            <span>{{ label }}</span
            ><i><b :style="{ width: `${Math.round((count / total) * 100)}%` }" /></i><em>{{ count }} 道</em>
          </div>
        </div>
        <p v-else class="muted">暂无错题数据</p>
        <dl>
          <div>
            <dt>反复出错题目</dt>
            <dd>{{ patterns.repeatedErrorCount }} 道</dd>
          </div>
          <div>
            <dt>近7天新增错题</dt>
            <dd>{{ patterns.recentNewWrongCount }} 道</dd>
          </div>
        </dl>
      </article>
      <article>
        <h3>高频错题课程</h3>
        <ul v-if="patterns.topErrorCourses.length">
          <li v-for="course in patterns.topErrorCourses" :key="course.courseId">
            <span>{{ course.courseName }}</span
            ><strong>{{ course.wrongCount }} 道错题</strong>
          </li>
        </ul>
        <p v-else class="muted">暂无错题数据</p>
      </article>
    </div>
    <div class="grid">
      <article>
        <h3>错题题型分布</h3>
        <div class="bars">
          <div v-for="(count, label) in patterns.questionTypeDistribution" :key="label">
            <span>{{ label }}</span
            ><i><b :style="{ width: `${Math.round((count / total) * 100)}%` }" /></i><em>{{ count }} 道</em>
          </div>
        </div>
      </article>
      <article>
        <h3>错题难度分布</h3>
        <div class="bars">
          <div v-for="(count, label) in patterns.difficultyDistribution" :key="label">
            <span>难度 {{ label }}</span
            ><i><b :style="{ width: `${Math.round((count / total) * 100)}%` }" /></i><em>{{ count }} 道</em>
          </div>
        </div>
      </article>
    </div>
    <article v-if="patterns.weeklyErrorTrend.length">
      <h3>近 4 周错题趋势</h3>
      <div class="chart">
        <div v-for="week in patterns.weeklyErrorTrend" :key="week.label">
          <i :style="{ height: `${(week.count / maxWeek) * 100}%` }" /><span>{{ week.label }}</span
          ><em>{{ week.count }}</em>
        </div>
      </div>
    </article>
    <article v-if="patterns.knowledgePointErrors.length">
      <h3>知识点错因排名</h3>
      <ul class="rows">
        <li v-for="item in patterns.knowledgePointErrors" :key="item.knowledgePointId">
          <div>
            <strong>{{ item.knowledgePointName }}</strong
            ><span>{{ item.courseName }}</span>
          </div>
          <span
            >{{ item.wrongCount }} 次错 · {{ item.totalAttempts }} 次作答 · {{ Math.round(item.correctRate) }}%</span
          >
        </li>
      </ul>
    </article>
    <article v-if="patterns.repeatedErrors.length">
      <h3>反复错题详情</h3>
      <ul class="rows">
        <li v-for="item in patterns.repeatedErrors" :key="item.questionId">
          <div>
            <strong>{{ item.questionContent }}</strong
            ><span>{{ item.questionType }} · {{ item.knowledgePointName || '未标注知识点' }}</span>
          </div>
          <span>{{ item.wrongCount }} 次错</span>
          <div class="actions">
            <button type="button" @click="emit('similar-question', item.questionId, item.questionContent)">
              找相似题</button
            ><button type="button" @click="emit('question-error-analysis', item.questionId)">错因分析</button>
          </div>
        </li>
      </ul>
    </article>
  </section>
</template>
<style scoped>
.panel {
  display: grid;
  gap: var(--lt-space-5);
  padding: var(--lt-space-5);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface-raised);
  box-shadow: var(--lt-shadow-card);
}
h2,
h3 {
  margin: 0;
  font-size: var(--lt-text-title3);
}
h3 {
  font-size: var(--lt-text-footnote);
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--lt-space-6);
}
.grid article {
  min-width: 0;
}
.bars {
  display: grid;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-3);
}
.bars > div {
  display: grid;
  grid-template-columns: calc(var(--lt-space-10) + var(--lt-space-2) + var(--lt-space-1) / 2) minmax(0, 1fr) calc(
      var(--lt-space-8) + var(--lt-space-2) - var(--lt-space-1) / 2
    );
  gap: var(--lt-space-2);
  align-items: center;
  font-size: var(--lt-text-caption);
}
i {
  display: block;
  height: calc(var(--lt-space-2) - var(--lt-space-1) / 4);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  overflow: hidden;
}
i b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--lt-orange);
}
em {
  font-style: normal;
  font-variant-numeric: tabular-nums;
}
dl {
  display: grid;
  gap: var(--lt-space-2);
  margin: var(--lt-space-4) 0 0;
}
dt,
span,
.muted {
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}
dd {
  margin: var(--lt-space-1) 0 0;
}
ul {
  display: grid;
  gap: var(--lt-space-2);
  margin: var(--lt-space-3) 0 0;
  padding: 0;
  list-style: none;
}
ul:not(.rows) li {
  display: flex;
  justify-content: space-between;
  padding-bottom: var(--lt-space-2);
  border-bottom: 1px solid var(--lt-separator);
}
ul:not(.rows) strong {
  color: var(--lt-orange-text);
  font-size: var(--lt-text-caption);
}
.chart {
  display: flex;
  align-items: end;
  gap: var(--lt-space-3);
  height: calc(var(--lt-space-10) + var(--lt-space-7) + var(--lt-space-5));
  margin-top: var(--lt-space-3);
}
.chart > div {
  display: grid;
  flex: 1;
  justify-items: center;
  gap: var(--lt-space-1);
  height: 100%;
}
.chart i {
  width: calc(var(--lt-space-4) + var(--lt-space-1) / 2);
  height: auto;
  background: var(--lt-orange);
}
.rows li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: var(--lt-space-3);
  align-items: center;
  padding: var(--lt-space-3) 0;
  border-top: 1px solid var(--lt-separator);
}
.rows li div:first-child {
  display: grid;
  gap: var(--lt-space-1);
}
button {
  border: 0;
  background: transparent;
  color: var(--lt-accent);
  font: 500 var(--lt-text-caption)/1 var(--lt-font);
  cursor: pointer;
}
.actions {
  display: flex;
  gap: var(--lt-space-2);
}
button:focus-visible {
  outline: 2px solid var(--lt-accent);
  outline-offset: 2px;
}
@media (max-width: 700px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .rows li {
    grid-template-columns: 1fr;
  }
}
</style>
