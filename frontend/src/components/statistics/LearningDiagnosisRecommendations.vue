<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { CourseMastery, RecommendedQuestion } from '@/api/statistics'
defineProps<{
  courseMasteries: CourseMastery[]
  recommendations: RecommendedQuestion[]
  startingPractice: boolean
  practiceError: string
}>()
const emit = defineEmits<{
  'start-recommend-practice': []
  'similar-question': [questionId: number, questionContent?: string]
}>()
</script>
<template>
  <section data-appearance="dark" class="lt recommendations">
    <article v-if="courseMasteries.length" class="panel">
      <h2>课程学习情况</h2>
      <ul class="rows">
        <li v-for="course in courseMasteries" :key="course.courseId">
          <strong>{{ course.courseName }}</strong
          ><span v-if="course.totalAttempts"
            >{{ course.totalAttempts }} 次作答 · {{ course.wrongCount }} 道错题 · 正确率
            {{ Math.round(course.correctRate) }}%</span
          ><span v-else>暂无记录</span
          ><em :data-weak="course.weakPointCount > 0">{{ course.weakPointCount }} 个薄弱知识点</em>
        </li>
      </ul>
    </article>
    <article class="panel">
      <header>
        <h2>下一步练习</h2>
        <button
          v-if="recommendations.length"
          type="button"
          :disabled="startingPractice"
          @click="emit('start-recommend-practice')"
        >
          {{ startingPractice ? '准备中…' : '开始练习' }}
        </button>
      </header>
      <ul v-if="recommendations.length" class="rows">
        <li v-for="item in recommendations" :key="item.questionId">
          <div>
            <strong>{{ item.questionContent }}</strong
            ><span
              >{{ item.reasonDescription }} · {{ item.questionType
              }}<template v-if="item.courseName"> · {{ item.courseName }}</template></span
            >
          </div>
          <button type="button" @click="emit('similar-question', item.questionId, item.questionContent)">
            找相似题
          </button>
        </li>
      </ul>
      <p v-else class="muted">暂时没有推荐题目。<RouterLink to="/courses">浏览课程</RouterLink></p>
      <p v-if="practiceError" class="notice" role="alert">{{ practiceError }}</p>
    </article>
  </section>
</template>
<style scoped>
.recommendations {
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
  justify-content: space-between;
  align-items: center;
  gap: var(--lt-space-3);
}
h2 {
  margin: 0;
  font-size: var(--lt-text-title3);
}
.rows {
  display: grid;
  gap: var(--lt-space-3);
  margin: var(--lt-space-4) 0 0;
  padding: 0;
  list-style: none;
}
.rows li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--lt-space-4);
  padding-top: var(--lt-space-3);
  border-top: 1px solid var(--lt-separator);
}
.rows li div {
  display: grid;
  gap: var(--lt-space-1);
}
span,
.muted {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
em {
  padding: calc(var(--lt-space-1) * 0.75) var(--lt-space-2);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-green-soft);
  color: var(--lt-green-text);
  font-style: normal;
  font-size: var(--lt-text-caption);
}
em[data-weak='true'] {
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
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
button:disabled {
  background: var(--lt-fill);
  color: var(--lt-label-3);
}
button:focus-visible,
a:focus-visible {
  outline: 2px solid var(--lt-accent);
  outline-offset: 2px;
}
.rows button {
  background: transparent;
  color: var(--lt-accent);
}
a {
  color: var(--lt-accent);
}
.notice {
  margin: var(--lt-space-3) 0 0;
  padding: var(--lt-space-3);
  border-radius: var(--lt-radius-md);
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}
@media (max-width: 700px) {
  .rows li {
    align-items: start;
    flex-direction: column;
  }
}
</style>
