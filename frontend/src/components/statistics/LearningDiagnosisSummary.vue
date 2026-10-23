<script setup lang="ts">
import { computed } from 'vue'
import type { LearningDiagnosis } from '@/api/statistics'
import QuestionProse from '@/views/question/QuestionProse.vue'
const props = defineProps<{
  data: LearningDiagnosis
  aiAdviceLoading: boolean
  aiAdviceStreaming: boolean
  aiAdviceContent: string
  aiAdviceError: string
}>()
const emit = defineEmits<{ 'generate-ai-advice': []; 'stop-ai-advice': [] }>()
const adviceLines = computed(() =>
  props.data.dailyAdvice
    .split('\n')
    .map((line) => line.trim().replace(/^(?:📚|⚠️?|⏰|🔥|📈|💡|✅)\s*/u, ''))
    .filter(Boolean),
)
const max = computed(() => Math.max(...props.data.learningHabit.weeklyTrend.map((day) => day.total), 1))
function height(value: number) {
  return `${Math.max(0, value / max.value) * 100}%`
}
</script>
<template>
  <section data-appearance="dark" class="lt diagnosis-summary">
    <article class="panel">
      <header>
        <h2>今日建议</h2>
        <button type="button" :disabled="aiAdviceLoading || aiAdviceStreaming" @click="emit('generate-ai-advice')">
          {{ aiAdviceLoading ? '生成中…' : '获取 AI 建议' }}
        </button>
      </header>
      <p v-for="line in adviceLines" :key="line" class="reading">{{ line }}</p>
    </article>
    <article v-if="aiAdviceContent || aiAdviceLoading || aiAdviceError" class="panel">
      <header>
        <h2>AI 学习建议</h2>
        <button v-if="aiAdviceStreaming" type="button" @click="emit('stop-ai-advice')">停止</button
        ><span v-else-if="aiAdviceContent" class="tag">AI 生成</span>
      </header>
      <p v-if="aiAdviceLoading && !aiAdviceContent" role="status" class="muted">正在生成建议…</p>
      <QuestionProse v-else :content="aiAdviceContent" />
      <p v-if="aiAdviceError" class="notice" role="alert">{{ aiAdviceError }}</p>
    </article>
    <dl class="metrics">
      <div>
        <dt>已判分作答</dt>
        <dd>{{ data.totalPractice }}</dd>
      </div>
      <div>
        <dt>作答正确率</dt>
        <dd>{{ data.overallCorrectRate }}%</dd>
      </div>
      <div>
        <dt>近30天活跃</dt>
        <dd>{{ data.activeDaysLast30 }}天</dd>
      </div>
      <div>
        <dt>连续刷题</dt>
        <dd>{{ data.streakDays }}天</dd>
      </div>
    </dl>
    <article v-if="data.weakPoints.length" class="panel">
      <h2>下一步关注的知识点</h2>
      <p class="muted">诊断基于平台已判分的练习记录；记录较少时仅展示事实，不判断掌握程度。</p>
      <ul class="rows">
        <li v-for="item in data.weakPoints" :key="item.knowledgePointId">
          <div>
            <strong>{{ item.knowledgePointName }}</strong
            ><span class="tag" :data-type="item.totalAttempts ? 'info' : undefined">{{ item.courseName }}</span
            ><span
              class="tag"
              :data-type="
                item.masteryStatus === 'NOT_STARTED' || item.masteryStatus === 'INSUFFICIENT_DATA'
                  ? 'info'
                  : item.masteryStatus === 'WEAK'
                    ? 'warning'
                    : undefined
              "
              >{{
                item.masteryStatus === 'INSUFFICIENT_DATA'
                  ? '记录较少'
                  : item.masteryStatus === 'NOT_STARTED'
                    ? '未开始'
                    : item.masteryStatus === 'WEAK'
                      ? '薄弱'
                      : '需复习'
              }}</span
            >
            <p>{{ item.diagnosis }} · 作答次数 {{ item.totalAttempts }} · 累计错次 {{ item.wrongCount }}</p>
          </div>
          <div v-if="item.totalAttempts" class="progress">
            <span :style="{ width: `${Math.round(item.correctRate)}%` }" /><small
              >{{ Math.round(item.correctRate) }}%</small
            >
          </div>
          <span v-else class="muted">暂无记录</span>
        </li>
      </ul>
    </article>
    <article class="panel">
      <h2>学习节奏</h2>
      <div class="habit">
        <dl>
          <div>
            <dt>日均作答</dt>
            <dd>{{ data.learningHabit.avgDailyPractice }} 次</dd>
          </div>
          <div>
            <dt>偏好题型</dt>
            <dd>{{ data.learningHabit.preferredQuestionType }}</dd>
          </div>
          <div>
            <dt>偏好课程</dt>
            <dd>{{ data.learningHabit.preferredCourse }}</dd>
          </div>
          <div>
            <dt>学习频次</dt>
            <dd class="frequency-description">{{ data.learningHabit.frequencyDescription }}</dd>
          </div>
        </dl>
        <div>
          <h3>近 7 天刷题趋势</h3>
          <div class="chart">
            <div v-for="day in data.learningHabit.weeklyTrend" :key="day.date">
              <div class="bars">
                <i :style="{ height: height(day.correct) }" /><b :style="{ height: height(day.wrong) }" />
              </div>
              <small>{{ day.date.slice(5) }}</small
              ><em>{{ day.total }}</em>
            </div>
          </div>
        </div>
      </div>
    </article>
  </section>
</template>
<style scoped>
.diagnosis-summary {
  display: grid;
  gap: var(--lt-space-5);
}
.panel {
  padding: var(--lt-space-5);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface-raised);
  box-shadow: var(--lt-shadow-card);
}
header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-3);
}
h2,
h3 {
  margin: 0;
  font-size: var(--lt-text-title3);
}
h3 {
  font-size: var(--lt-text-footnote);
}
button {
  min-height: var(--lt-control-sm);
  padding: 0 var(--lt-space-3);
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-accent-fill);
  color: var(--lt-on-accent);
  font: 500 var(--lt-text-caption)/1 var(--lt-font);
  cursor: pointer;
}
button:focus-visible {
  outline: 2px solid var(--lt-accent);
  outline-offset: 2px;
}
.reading {
  margin: var(--lt-space-3) 0 0;
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}
.muted {
  color: var(--lt-label-2);
}
.notice {
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-md);
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
.tag {
  display: inline-flex;
  padding: calc(var(--lt-space-1) * 0.75) var(--lt-space-2);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}
.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--lt-space-3);
  margin: 0;
}
.metrics div {
  padding: var(--lt-space-4);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-fill-2);
}
dt {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}
dd {
  margin: var(--lt-space-1) 0 0;
  font-variant-numeric: tabular-nums;
}
.metrics dd {
  font: 600 var(--lt-text-title2)/1 var(--lt-font-rounded);
}
.rows {
  display: grid;
  gap: var(--lt-space-3);
  margin: var(--lt-space-4) 0 0;
  padding: 0;
  list-style: none;
}
.rows li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) calc(var(--lt-space-10) * 2 + var(--lt-space-5) + var(--lt-space-4));
  gap: var(--lt-space-4);
  align-items: center;
  padding-top: var(--lt-space-3);
  border-top: 1px solid var(--lt-separator);
}
.rows p {
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.rows .tag {
  margin-left: var(--lt-space-2);
}
.progress {
  display: flex;
  align-items: center;
  gap: var(--lt-space-2);
}
.progress:before {
  content: '';
  position: absolute;
}
.progress span {
  height: calc(var(--lt-space-2) - var(--lt-space-1) / 4);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-accent);
  min-width: calc(var(--lt-space-1) / 2);
}
.progress {
  background: var(--lt-fill-2);
  border-radius: var(--lt-radius-pill);
  overflow: hidden;
}
.progress small {
  padding-right: var(--lt-space-2);
  margin-left: auto;
}
.habit {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--lt-space-6);
  margin-top: var(--lt-space-4);
}
.habit dl {
  display: grid;
  gap: var(--lt-space-3);
  margin: 0;
}
.chart {
  display: flex;
  align-items: end;
  gap: var(--lt-space-2);
  height: calc(var(--lt-space-10) * 2 - var(--lt-space-1));
  margin-top: var(--lt-space-3);
}
.chart > div {
  display: grid;
  flex: 1;
  justify-items: center;
  gap: var(--lt-space-1);
  height: 100%;
  font-style: normal;
}
.bars {
  display: flex;
  align-items: end;
  gap: calc(var(--lt-space-1) / 2);
  height: 100%;
  width: 100%;
}
.bars i,
.bars b {
  flex: 1;
  min-height: calc(var(--lt-space-1) / 2);
  border-radius: var(--lt-radius-sm) var(--lt-radius-sm) 0 0;
}
.bars i {
  background: var(--lt-green);
}
.bars b {
  background: var(--lt-orange);
}
.chart small {
  color: var(--lt-label-3);
}
.chart em {
  font-style: normal;
  font-size: var(--lt-text-caption);
}
@media (max-width: 700px) {
  .metrics,
  .habit {
    grid-template-columns: 1fr 1fr;
  }
  .rows li {
    grid-template-columns: 1fr;
  }
}
</style>
