<template>
  <main class="tutor" v-loading="loading">
    <template v-if="failed">
      <section class="state-panel">
        <el-result icon="error" title="无法开始教学" sub-title="请确认已将课程加入课程库，并从已审查知识点进入。">
          <template #extra><el-button @click="router.back()">返回</el-button></template>
        </el-result>
      </section>
    </template>

    <template v-else-if="session">
      <header class="tutor-heading">
        <LpKicker>AI 教学 · 已审查内容</LpKicker>
        <h1 class="tutor-title">{{ session.title }}</h1>
        <p class="tutor-summary">{{ session.lesson.summary }}</p>
      </header>

      <section v-if="hasLearningContext" class="lesson-block evidence" aria-labelledby="learning-context-heading">
        <div class="block-heading">
          <LpKicker>最近相关记录</LpKicker>
          <h2 id="learning-context-heading">与本节内容相关的记录</h2>
          <p>只统计真实作答、错题与复习记录，不推断掌握程度。</p>
        </div>
        <div class="evidence-grid">
          <div v-if="session.learningContext.paperAnswerCount" class="evidence-item">
            <strong>{{ session.learningContext.paperAnswerCount }}</strong
            ><span>次真题学习作答</span>
            <small v-if="session.learningContext.paperIncorrectCount">
              其中 {{ session.learningContext.paperIncorrectCount }} 次答错
            </small>
          </div>
          <div v-if="session.learningContext.paperAiAssistanceCount" class="evidence-item">
            <strong>{{ session.learningContext.paperAiAssistanceCount }}</strong
            ><span>次试卷 AI 辅导</span>
          </div>
          <div v-if="session.learningContext.unresolvedWrongCount" class="evidence-item">
            <strong>{{ session.learningContext.unresolvedWrongCount }}</strong
            ><span>道未掌握错题</span>
          </div>
          <div v-if="session.learningContext.dueReviewCount" class="evidence-item">
            <strong>{{ session.learningContext.dueReviewCount }}</strong
            ><span>道到期复习</span>
          </div>
          <div v-if="session.learningContext.reviewAnswerCount" class="evidence-item">
            <strong>{{ session.learningContext.reviewAnswerCount }}</strong
            ><span>次复习作答</span>
          </div>
        </div>
        <small v-if="session.learningContext.latestEvidenceAt" class="evidence-time">
          最近相关记录：{{ formatEvidenceTime(session.learningContext.latestEvidenceAt) }}
        </small>
      </section>

      <section
        v-if="session.lesson.prerequisite"
        class="lesson-block prerequisite"
        aria-labelledby="prerequisite-heading"
      >
        <LpKicker tone="warning">学习前提</LpKicker>
        <h2 id="prerequisite-heading">{{ session.lesson.prerequisite.title }}</h2>
        <p>{{ session.lesson.prerequisite.description }}</p>
      </section>

      <section class="lesson-block steps" aria-labelledby="steps-heading">
        <LpKicker>教学步骤</LpKicker>
        <h2 id="steps-heading">按步骤理解</h2>
        <ol class="steps-list">
          <li v-for="(step, index) in session.lesson.steps" :key="step" :style="{ '--step-index': index }">
            <span class="step-number" aria-hidden="true">{{ index + 1 }}</span>
            <span class="step-text">{{ step }}</span>
          </li>
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
      <TutorSequentialListStorage v-if="sequentialStorageCourseware" :visualization="sequentialStorageCourseware" />
      <TutorLinkedListReversal v-if="linkedListReversalCourseware" :visualization="linkedListReversalCourseware" />
      <TutorFactorialCallStack v-if="factorialCallStackCourseware" :visualization="factorialCallStackCourseware" />

      <section class="lesson-block check" aria-labelledby="check-heading">
        <LpKicker>理解检查</LpKicker>
        <h2 id="check-heading">确认一下理解</h2>
        <p class="check-prompt">{{ session.check.prompt }}</p>
        <el-radio-group v-model="optionId" :disabled="!!result" class="check-options">
          <el-radio v-for="option in session.check.options" :key="option.id" :value="option.id" class="check-option">
            {{ option.text }}
          </el-radio>
        </el-radio-group>
        <el-button type="primary" :disabled="!optionId || !!result" :loading="submitting" @click="submit">
          提交检查
        </el-button>

        <transition name="result" mode="out-in">
          <div v-if="result" class="check-result" :class="result.correct ? 'is-correct' : 'is-wrong'">
            <div class="result-heading">
              <span class="result-mark" aria-hidden="true">
                <el-icon :size="18">
                  <CircleCheckFilled v-if="result.correct" />
                  <WarningFilled v-else />
                </el-icon>
              </span>
              <strong>{{ result.correct ? '回答正确' : '需要再想一步' }}</strong>
            </div>
            <p class="result-explanation">{{ result.explanation }}</p>

            <div
              v-if="result.guidanceTitle"
              class="guidance"
              :class="result.guidanceType === 'PREREQUISITE' ? 'is-prerequisite' : 'is-next'"
              aria-live="polite"
            >
              <LpKicker :tone="result.guidanceType === 'PREREQUISITE' ? 'warning' : 'success'">
                {{ result.guidanceType === 'PREREQUISITE' ? '建议先回看' : '建议下一步' }}
              </LpKicker>
              <h3>{{ result.guidanceTitle }}</h3>
              <p>{{ result.guidanceDescription }}</p>
              <el-button
                v-if="result.guidanceKnowledgePointId"
                :type="result.guidanceType === 'PREREQUISITE' ? 'default' : 'primary'"
                @click="openGuidance(result.guidanceKnowledgePointId)"
              >
                {{ result.guidanceType === 'PREREQUISITE' ? '复习前置内容' : '学习下一内容' }}
              </el-button>
            </div>
          </div>
        </transition>
      </section>
    </template>
  </main>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { CircleCheckFilled, WarningFilled } from '@element-plus/icons-vue'
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
import TutorSequentialListStorage from '@/components/TutorSequentialListStorage.vue'
import TutorLinkedListReversal from '@/components/TutorLinkedListReversal.vue'
import TutorFactorialCallStack from '@/components/TutorFactorialCallStack.vue'
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
  isSequentialListStorageCourseware,
  isLinkedListReversalCourseware,
  isFactorialCallStackCourseware,
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
const sequentialStorageCourseware = computed(() =>
  session.value && isSequentialListStorageCourseware(session.value.lesson.visualization)
    ? session.value.lesson.visualization
    : null,
)
const linkedListReversalCourseware = computed(() =>
  session.value && isLinkedListReversalCourseware(session.value.lesson.visualization)
    ? session.value.lesson.visualization
    : null,
)
const factorialCallStackCourseware = computed(() =>
  session.value && isFactorialCallStackCourseware(session.value.lesson.visualization)
    ? session.value.lesson.visualization
    : null,
)
const hasLearningContext = computed(() => {
  const context = session.value?.learningContext
  return (
    !!context &&
    [
      context.paperAnswerCount,
      context.paperAiAssistanceCount,
      context.unresolvedWrongCount,
      context.dueReviewCount,
      context.reviewAnswerCount,
    ].some((count) => count > 0)
  )
})
function formatEvidenceTime(value: string) {
  return value.replace('T', ' ').slice(0, 16)
}
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
  gap: var(--lp-space-6);
  min-height: 60vh;
}

.state-panel {
  padding: var(--lp-space-6);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
}

/* ---------------- Heading ---------------- */
.tutor-heading {
  padding: var(--lp-space-2) 0 var(--lp-space-1);
}

.tutor-title {
  margin-top: var(--lp-space-2);
  font-family: var(--lp-font-display);
  font-size: var(--lp-text-4xl);
  font-weight: var(--lp-weight-bold);
  line-height: var(--lp-leading-display);
  color: var(--lp-text);
}

.tutor-summary {
  margin: var(--lp-space-3) 0 0;
  max-width: 640px;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-md);
  line-height: var(--lp-leading-relaxed);
}

/* ---------------- Lesson blocks ---------------- */
.lesson-block {
  padding: var(--lp-space-6);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  box-shadow: var(--lp-shadow-xs);
  animation: block-in var(--lp-duration-slow) var(--lp-ease-out) both;
}

@keyframes block-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.block-heading h2,
.prerequisite h2,
.steps h2,
.check h2 {
  margin-top: var(--lp-space-1);
  font-size: var(--lp-text-xl);
  font-weight: var(--lp-weight-bold);
  color: var(--lp-text);
}

.block-heading p,
.prerequisite p,
.steps p,
.check p {
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-body);
}

.block-heading > p {
  margin: var(--lp-space-1) 0 0;
  font-size: var(--lp-text-sm);
}

.prerequisite {
  border-left: 3px solid var(--lp-warning);
}

.prerequisite h2 {
  font-size: var(--lp-text-lg);
}

.prerequisite p {
  margin: var(--lp-space-1) 0 0;
  font-size: var(--lp-text-base);
}

/* ---------------- Evidence ---------------- */
.evidence-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--lp-space-2);
  margin-top: var(--lp-space-4);
}

.evidence-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--lp-space-3) var(--lp-space-4);
  background: var(--lp-surface-soft);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
}

.evidence-item strong {
  color: var(--lp-primary);
  font-size: var(--lp-text-2xl);
  font-weight: var(--lp-weight-heavy);
  font-variant-numeric: tabular-nums;
}

.evidence-item span {
  color: var(--lp-text);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
}

.evidence-item small,
.evidence-time {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
}

.evidence-time {
  display: block;
  margin-top: var(--lp-space-3);
}

/* ---------------- Steps ---------------- */
.steps-list {
  display: grid;
  gap: var(--lp-space-3);
  margin: var(--lp-space-4) 0 0;
  padding: 0;
  list-style: none;
  counter-reset: steps;
}

.steps-list li {
  display: flex;
  gap: var(--lp-space-3);
  align-items: flex-start;
}

.step-number {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  border-radius: var(--lp-radius-full);
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-bold);
}

.step-text {
  padding-top: 3px;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-md);
  line-height: var(--lp-leading-relaxed);
}

/* ---------------- Check ---------------- */
.check {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--lp-space-3);
}

.check-prompt {
  margin: 0;
  font-size: var(--lp-text-lg);
  line-height: var(--lp-leading-relaxed);
  color: var(--lp-text);
}

.check-options {
  display: grid;
  gap: var(--lp-space-2);
  width: 100%;
}

.check-option {
  width: 100%;
  min-height: 44px;
  padding: var(--lp-space-3) var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface-soft);
  transition:
    border-color var(--lp-duration-fast) var(--lp-ease-out),
    background-color var(--lp-duration-fast) var(--lp-ease-out);
}

.check-option:hover {
  border-color: var(--lp-border-strong);
  background: var(--lp-surface);
}

.check-option.is-checked {
  border-color: var(--lp-primary);
  background: var(--lp-primary-soft);
}

/* ---------------- Result ---------------- */
.check-result {
  width: 100%;
  padding: var(--lp-space-5);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface-soft);
  border: var(--lp-border-hairline);
}

.check-result.is-correct {
  border-left: 3px solid var(--lp-success);
}

.check-result.is-wrong {
  border-left: 3px solid var(--lp-warning);
}

.result-heading {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
}

.result-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: var(--lp-radius-full);
}

.is-correct .result-mark {
  background: var(--lp-success-soft);
  color: var(--lp-success);
}

.is-wrong .result-mark {
  background: var(--lp-warning-soft);
  color: var(--lp-warning);
}

.result-explanation {
  margin: var(--lp-space-2) 0 0;
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-body);
}

.guidance {
  margin-top: var(--lp-space-4);
  padding: var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
  background: var(--lp-surface);
}

.guidance h3 {
  margin-top: var(--lp-space-1);
  font-size: var(--lp-text-lg);
  color: var(--lp-text);
}

.guidance p {
  margin: var(--lp-space-1) 0 var(--lp-space-3);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-base);
}

.guidance.is-prerequisite {
  border-left: 3px solid var(--lp-warning);
}

.guidance.is-next {
  border-left: 3px solid var(--lp-success);
}

/* ---------------- Transitions ---------------- */
.result-enter-active,
.result-leave-active {
  transition:
    opacity var(--lp-duration-normal) var(--lp-ease-out),
    transform var(--lp-duration-normal) var(--lp-ease-out);
}

.result-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.result-leave-to {
  opacity: 0;
}

@media (max-width: 767px) {
  .lesson-block {
    padding: var(--lp-space-4);
  }
  .tutor-title {
    font-size: var(--lp-text-3xl);
  }
  .check-options {
    flex-direction: column;
  }
}
</style>
