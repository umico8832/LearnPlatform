<template>
  <main class="tutor page-container" v-loading="loading">
    <el-result
      v-if="failed"
      icon="error"
      title="无法开始教学"
      sub-title="请确认已将课程加入课程库，并从已审查知识点进入。"
      ><template #extra><el-button @click="router.back()">返回目录</el-button></template></el-result
    >
    <template v-else-if="session">
      <section class="hero">
        <el-button text :icon="ArrowLeft" @click="router.back()">返回目录</el-button><span>已审查教学内容</span>
        <h2>{{ session.title }}</h2>
        <p>{{ session.lesson.summary }}</p>
      </section>
      <section v-if="session.lesson.prerequisite" class="path-card prerequisite" aria-labelledby="prerequisite-heading">
        <span class="path-kicker">学习前提</span>
        <h3 id="prerequisite-heading">{{ session.lesson.prerequisite.title }}</h3>
        <p>{{ session.lesson.prerequisite.description }}</p>
      </section>
      <section class="card">
        <h3>按步骤理解</h3>
        <ol>
          <li v-for="step in session.lesson.steps" :key="step">{{ step }}</li>
        </ol>
      </section>
      <TutorArrayStackInsertion v-if="courseware" :visualization="courseware" />
      <TutorArrayStackResize v-if="resizeCourseware" :visualization="resizeCourseware" />
      <TutorArrayQueueRepresentation v-if="queueCourseware" :visualization="queueCourseware" />
      <TutorArrayQueueEnqueue v-if="queueEnqueueCourseware" :visualization="queueEnqueueCourseware" />
      <section class="card check">
        <h3>理解检查</h3>
        <p>{{ session.check.prompt }}</p>
        <el-radio-group v-model="optionId" :disabled="!!result"
          ><el-radio v-for="option in session.check.options" :key="option.id" :value="option.id" border>{{
            option.text
          }}</el-radio></el-radio-group
        ><el-button type="primary" :disabled="!optionId || !!result" :loading="submitting" @click="submit"
          >提交检查</el-button
        ><el-alert
          v-if="result"
          :type="result.correct ? 'success' : 'warning'"
          :closable="false"
          :title="result.correct ? '回答正确' : '需要再想一步'"
          :description="result.explanation"
        />
        <div
          v-if="result?.guidanceTitle"
          class="path-result"
          :class="result.guidanceType === 'PREREQUISITE' ? 'is-prerequisite' : 'is-next'"
          aria-live="polite"
        >
          <span class="path-kicker">{{ result.guidanceType === 'PREREQUISITE' ? '建议先回看' : '建议下一步' }}</span>
          <h4>{{ result.guidanceTitle }}</h4>
          <p>{{ result.guidanceDescription }}</p>
        </div>
      </section>
    </template>
  </main>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import TutorArrayStackInsertion from '@/components/TutorArrayStackInsertion.vue'
import TutorArrayStackResize from '@/components/TutorArrayStackResize.vue'
import TutorArrayQueueRepresentation from '@/components/TutorArrayQueueRepresentation.vue'
import TutorArrayQueueEnqueue from '@/components/TutorArrayQueueEnqueue.vue'
import {
  isArrayStackInsertionCourseware,
  isArrayStackResizeCourseware,
  isArrayQueueRepresentationCourseware,
  isArrayQueueEnqueueCourseware,
  startTutorSession,
  submitTutorCheck,
  type TutorCheckResultVO,
  type TutorSessionVO,
} from '@/api/course'
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const failed = ref(false)
const submitting = ref(false)
const session = ref<TutorSessionVO>()
const optionId = ref('')
const result = ref<TutorCheckResultVO>()
const courseId = computed(() => Number(route.params.id))
const pointId = computed(() => Number(route.query.knowledgePointId))
const courseware = computed(() =>
  session.value && isArrayStackInsertionCourseware(session.value.lesson.visualization)
    ? session.value.lesson.visualization
    : null,
)
const resizeCourseware = computed(() =>
  session.value && isArrayStackResizeCourseware(session.value.lesson.visualization)
    ? session.value.lesson.visualization
    : null,
)
const queueCourseware = computed(() =>
  session.value && isArrayQueueRepresentationCourseware(session.value.lesson.visualization)
    ? session.value.lesson.visualization
    : null,
)
const queueEnqueueCourseware = computed(() =>
  session.value && isArrayQueueEnqueueCourseware(session.value.lesson.visualization)
    ? session.value.lesson.visualization
    : null,
)
async function load() {
  loading.value = true
  try {
    session.value = (await startTutorSession(courseId.value, pointId.value)).data
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
async function submit() {
  if (!session.value) return
  submitting.value = true
  try {
    result.value = (await submitTutorCheck(courseId.value, session.value.sessionKey, optionId.value)).data
  } finally {
    submitting.value = false
  }
}
onMounted(load)
</script>
<style scoped>
.tutor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.hero,
.card,
.path-card {
  padding: 22px;
  background: var(--lp-surface);
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius);
  box-shadow: var(--lp-shadow-sm);
}
.hero span,
.path-kicker {
  display: block;
  margin-top: 12px;
  color: var(--lp-primary);
  font-size: 12px;
  font-weight: 700;
}
.hero h2,
.card h3,
.path-card h3,
.path-result h4 {
  margin: 4px 0;
  color: var(--lp-text);
}
.hero p,
.card p,
.card li,
.path-card p,
.path-result p {
  color: var(--lp-text-secondary);
  line-height: 1.7;
}
.card ol {
  padding-left: 24px;
}
.prerequisite {
  border-left: 3px solid var(--lp-primary);
}
.path-card .el-tag,
.path-result .el-tag {
  margin-top: 10px;
}
.check {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 14px;
}
.check .el-radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.check .el-alert,
.path-result {
  width: 100%;
}
.path-result {
  padding: 16px;
  border: 1px solid var(--lp-border);
  border-radius: 8px;
  background: var(--lp-surface-soft);
}
.path-result .path-kicker {
  margin-top: 0;
}
.path-result h4 {
  font-size: 16px;
}
.is-prerequisite {
  border-left: 3px solid var(--el-color-warning);
}
.is-next {
  border-left: 3px solid var(--el-color-success);
}
@media (max-width: 767px) {
  .hero,
  .card,
  .path-card {
    padding: 16px;
  }
  .check .el-radio-group {
    flex-direction: column;
  }
  .check .el-radio {
    margin-right: 0;
  }
}
</style>
