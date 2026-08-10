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
      <TutorArrayQueueDequeue v-if="queueDequeueCourseware" :visualization="queueDequeueCourseware" />
      <TutorArrayQueueResize v-if="queueResizeCourseware" :visualization="queueResizeCourseware" />
      <TutorArrayDequeRepresentation v-if="dequeCourseware" :visualization="dequeCourseware" />
      <TutorArrayDequeFrontShiftInsert v-if="dequeFrontShiftCourseware" :visualization="dequeFrontShiftCourseware" />
      <TutorDualArrayDequeRepresentation v-if="dualDequeCourseware" :visualization="dualDequeCourseware" />
      <TutorDualArrayDequeBalance v-if="dualDequeBalanceCourseware" :visualization="dualDequeBalanceCourseware" />
      <TutorRootishArrayStackLayout v-if="rootishLayoutCourseware" :visualization="rootishLayoutCourseware" />
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
          <el-button
            v-if="result.guidanceKnowledgePointId"
            type="primary"
            @click="openGuidance(result.guidanceKnowledgePointId)"
            >{{ result.guidanceType === 'PREREQUISITE' ? '复习前置内容' : '学习下一内容' }}</el-button
          >
        </div>
      </section>
    </template>
  </main>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import TutorArrayStackInsertion from '@/components/TutorArrayStackInsertion.vue'
import TutorArrayStackResize from '@/components/TutorArrayStackResize.vue'
import TutorArrayQueueRepresentation from '@/components/TutorArrayQueueRepresentation.vue'
import TutorArrayQueueEnqueue from '@/components/TutorArrayQueueEnqueue.vue'
import TutorArrayQueueDequeue from '@/components/TutorArrayQueueDequeue.vue'
import TutorArrayQueueResize from '@/components/TutorArrayQueueResize.vue'
import TutorArrayDequeRepresentation from '@/components/TutorArrayDequeRepresentation.vue'
import TutorArrayDequeFrontShiftInsert from '@/components/TutorArrayDequeFrontShiftInsert.vue'
import TutorDualArrayDequeRepresentation from '@/components/TutorDualArrayDequeRepresentation.vue'
import TutorDualArrayDequeBalance from '@/components/TutorDualArrayDequeBalance.vue'
import TutorRootishArrayStackLayout from '@/components/TutorRootishArrayStackLayout.vue'
import {
  isArrayStackInsertionCourseware,
  isArrayStackResizeCourseware,
  isArrayQueueRepresentationCourseware,
  isArrayQueueEnqueueCourseware,
  isArrayQueueDequeueCourseware,
  isArrayQueueResizeCourseware,
  isArrayDequeRepresentationCourseware,
  isArrayDequeFrontShiftInsertCourseware,
  isDualArrayDequeRepresentationCourseware,
  isDualArrayDequeBalanceCourseware,
  isRootishArrayStackLayoutCourseware,
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
const queueDequeueCourseware = computed(() =>
  session.value && isArrayQueueDequeueCourseware(session.value.lesson.visualization)
    ? session.value.lesson.visualization
    : null,
)
const queueResizeCourseware = computed(() =>
  session.value && isArrayQueueResizeCourseware(session.value.lesson.visualization)
    ? session.value.lesson.visualization
    : null,
)
const dequeCourseware = computed(() =>
  session.value && isArrayDequeRepresentationCourseware(session.value.lesson.visualization)
    ? session.value.lesson.visualization
    : null,
)
const dequeFrontShiftCourseware = computed(() =>
  session.value && isArrayDequeFrontShiftInsertCourseware(session.value.lesson.visualization)
    ? session.value.lesson.visualization
    : null,
)
const dualDequeCourseware = computed(() =>
  session.value && isDualArrayDequeRepresentationCourseware(session.value.lesson.visualization)
    ? session.value.lesson.visualization
    : null,
)
const dualDequeBalanceCourseware = computed(() =>
  session.value && isDualArrayDequeBalanceCourseware(session.value.lesson.visualization)
    ? session.value.lesson.visualization
    : null,
)
const rootishLayoutCourseware = computed(() =>
  session.value && isRootishArrayStackLayoutCourseware(session.value.lesson.visualization)
    ? session.value.lesson.visualization
    : null,
)
async function load() {
  loading.value = true
  failed.value = false
  session.value = undefined
  optionId.value = ''
  result.value = undefined
  try {
    session.value = (await startTutorSession(courseId.value, pointId.value)).data
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
function openGuidance(knowledgePointId: number) {
  router.push({
    name: 'TutorSession',
    params: { id: courseId.value },
    query: { knowledgePointId: String(knowledgePointId) },
  })
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
watch(pointId, (value, previous) => {
  if (value !== previous) load()
})
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
