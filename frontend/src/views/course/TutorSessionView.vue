<template>
  <section class="lt course-page tutor" data-appearance="dark" aria-label="课程教学">
    <div>
      <LtButton variant="quiet" @click="router.push({ name: 'CourseSpace', params: { id: courseId } })"
        ><template #icon><ChevronLeft aria-hidden="true" /></template>返回课程空间</LtButton
      >
    </div>
    <CoursePageState v-if="loading" state="loading" title="正在读取课程教学" />
    <template v-else-if="failed">
      <CoursePageState
        state="error"
        title="无法开始教学"
        description="请确认已加入课程，并从可学习的知识点进入。"
        retry-label="重新尝试"
        @retry="load()"
        ><div class="course-actions">
          <LtButton @click="load(true)">重新开始教学</LtButton><LtButton @click="router.back()">返回</LtButton>
        </div></CoursePageState
      >
    </template>

    <template v-else-if="session">
      <header class="tutor-heading">
        <h1 class="tutor-title">{{ session.title }}</h1>
        <p class="tutor-summary">{{ session.lesson.summary }}</p>
      </header>

      <details v-if="hasLearningContext" class="lesson-block evidence" aria-labelledby="learning-context-heading">
        <summary id="learning-context-heading">相关学习记录</summary>
        <div class="block-heading">
          <p class="lesson-label">最近相关记录</p>
          <h2>与本节内容相关的记录</h2>
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
      </details>

      <details
        v-if="session.lesson.prerequisite"
        class="lesson-block prerequisite"
        aria-labelledby="prerequisite-heading"
      >
        <summary id="prerequisite-heading">学习前提：{{ session.lesson.prerequisite.title }}</summary>
        <p class="lesson-label">学习前提</p>
        <h2>{{ session.lesson.prerequisite.title }}</h2>
        <p>{{ session.lesson.prerequisite.description }}</p>
      </details>

      <section class="lesson-block steps" aria-labelledby="steps-heading">
        <p class="lesson-label">教学步骤</p>
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

      <section
        ref="checkSection"
        class="lesson-block check"
        aria-labelledby="check-heading"
        tabindex="-1"
        data-testid="tutor-check"
      >
        <p class="lesson-label">理解检查</p>
        <h2 id="check-heading">确认一下理解</h2>
        <p class="check-prompt">{{ session.check.prompt }}</p>
        <fieldset
          class="check-options"
          role="radiogroup"
          aria-labelledby="check-heading"
          :disabled="!!result || submitting"
        >
          <label v-for="option in session.check.options" :key="option.id" class="check-option"
            ><input v-model="optionId" type="radio" name="tutor-check" :value="option.id" /><span>{{
              option.text
            }}</span></label
          >
        </fieldset>
        <LtButton variant="primary" :disabled="!optionId || !!result || submitting" @click="submit">{{
          submitting ? '正在提交…' : '提交检查'
        }}</LtButton>
        <p v-if="checkFailure" class="course-error" role="alert">{{ checkFailure }}</p>

        <transition name="result" mode="out-in">
          <div v-if="result" class="check-result" :class="result.correct ? 'is-correct' : 'is-wrong'">
            <div class="result-heading">
              <span class="result-mark" aria-hidden="true">
                <CircleCheck v-if="result.correct" /><CircleAlert v-else />
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
              <p class="lesson-label">
                {{ result.guidanceType === 'PREREQUISITE' ? '建议先回看' : '建议下一步' }}
              </p>
              <h3>{{ result.guidanceTitle }}</h3>
              <p>{{ result.guidanceDescription }}</p>
              <LtButton
                v-if="result.guidanceKnowledgePointId"
                :variant="result.guidanceType === 'PREREQUISITE' ? 'plain' : 'primary'"
                @click="openGuidance(result.guidanceKnowledgePointId)"
              >
                {{ result.guidanceType === 'PREREQUISITE' ? '复习前置内容' : '学习下一内容' }}
              </LtButton>
            </div>
          </div>
        </transition>
      </section>
      <TutorAgentConversation
        v-if="session.agentAvailable"
        :key="session.sessionKey"
        :course-id="courseId"
        :session-key="session.sessionKey"
        :check-result="result"
        @request-check="focusCheck"
      />
    </template>
    <CoursePageState v-else state="empty" title="暂无教学内容" description="返回课程空间，选择可学习的课节。" />
  </section>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ChevronLeft, CircleCheck, CircleAlert } from '@lucide/vue'
import { LtButton } from '@/components/living'
import CoursePageState from './CoursePageState.vue'
import './course-baseline.css'
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
import TutorAgentConversation from '@/components/course/TutorAgentConversation.vue'
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
} from '@/api/course'
import { useTutorSessionCheck } from '@/composables/useTutorSessionCheck'
const route = useRoute()
const router = useRouter()
const checkSection = ref<HTMLElement>()
const courseId = computed(() => Number(route.params.id))
const pointId = computed(() => Number(route.query.knowledgePointId))
const { loading, failed, submitting, session, optionId, result, checkFailure, load, submit, dispose } =
  useTutorSessionCheck(courseId, pointId)
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
function openGuidance(knowledgePointId: number) {
  router.push({
    name: 'TutorSession',
    params: { id: courseId.value },
    query: { knowledgePointId: String(knowledgePointId) },
  })
}
async function focusCheck() {
  await nextTick()
  checkSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  checkSection.value?.focus({ preventScroll: true })
}
onMounted(load)
watch([courseId, pointId], (value, previous) => {
  if (value[0] !== previous[0] || value[1] !== previous[1]) void load()
})
onBeforeUnmount(() => {
  dispose()
})
</script>
<style scoped>
.tutor {
  max-width: none;
  padding: 0;
  gap: var(--lt-space-7);
}
.tutor-heading {
  max-width: calc(var(--lt-space-10) * 10);
}
.tutor-title {
  margin: 0;
  font-size: var(--lt-text-title1);
}
.tutor-summary {
  margin: var(--lt-space-4) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}
.lesson-block {
  min-width: 0;
}
.lesson-block > summary {
  padding: var(--lt-space-4) 0;
  color: var(--lt-label-2);
  cursor: pointer;
}
.lesson-label {
  margin: 0 0 var(--lt-space-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.block-heading h2,
.prerequisite h2,
.steps h2,
.check h2 {
  margin: 0;
  font-size: var(--lt-text-title3);
}
.prerequisite p {
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
}
.evidence-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--lt-space-3);
  margin: var(--lt-space-4) 0;
}
.evidence-item {
  display: grid;
  align-content: start;
  gap: var(--lt-space-2);
  padding: var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
}
.evidence-item strong {
  font-family: var(--lt-font-rounded);
  font-size: var(--lt-text-title2);
  font-variant-numeric: tabular-nums;
}
.evidence-item span,
.evidence-item small,
.evidence-time {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
.steps {
  max-width: calc(var(--lt-space-10) * 10);
}
.steps-list {
  display: grid;
  margin: var(--lt-space-5) 0 0;
  padding: 0;
  list-style: none;
}
.steps-list li {
  position: relative;
  display: flex;
  align-items: start;
  gap: var(--lt-space-4);
  padding-bottom: var(--lt-space-6);
}
.steps-list li:not(:last-child)::after {
  content: '';
  position: absolute;
  top: var(--lt-space-7);
  bottom: 0;
  left: var(--lt-space-4);
  border-left: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
}
.step-number {
  display: grid;
  place-items: center;
  width: var(--lt-space-7);
  height: var(--lt-space-7);
  flex: none;
  background: var(--lt-fill-2);
  border-radius: var(--lt-radius-pill);
  font-family: var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}
.step-text {
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}
.check {
  display: grid;
  justify-items: start;
  gap: var(--lt-space-4);
  padding: var(--lt-space-6);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-card);
}
.check p {
  margin: 0;
}
.check-prompt {
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}
.check-options {
  display: grid;
  gap: var(--lt-space-2);
  width: 100%;
  padding: 0;
  margin: 0;
  border: 0;
}
.check-option {
  display: flex;
  align-items: baseline;
  gap: var(--lt-space-3);
  padding: var(--lt-space-4);
  border-radius: var(--lt-radius-md);
  background: var(--lt-fill-3);
  cursor: pointer;
  line-height: var(--lt-leading-reading);
}
.check-option:hover {
  background: var(--lt-fill-2);
}
.check-option:has(:checked) {
  background: var(--lt-accent-soft);
  box-shadow: inset 0 0 0 calc(var(--lt-space-1) / 4) var(--lt-accent);
}
.check-option input {
  accent-color: var(--lt-accent-fill);
  flex: none;
}
.check-options:disabled .check-option {
  cursor: default;
}
.check-result {
  width: 100%;
  padding: var(--lt-space-5);
  border-radius: var(--lt-radius-lg);
  background: var(--lt-well);
}
.result-heading {
  display: flex;
  align-items: center;
  gap: var(--lt-space-2);
}
.result-mark {
  display: flex;
}
.result-mark svg {
  width: var(--lt-space-5);
  height: var(--lt-space-5);
}
.is-correct .result-mark {
  color: var(--lt-green-text);
}
.is-wrong .result-mark {
  color: var(--lt-orange-text);
}
.check-result .result-explanation {
  margin-top: var(--lt-space-3);
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}
.guidance {
  margin-top: var(--lt-space-5);
  padding-top: var(--lt-space-5);
  border-top: calc(var(--lt-space-1) / 4) solid var(--lt-separator);
}
.guidance h3 {
  margin: var(--lt-space-2) 0;
  font-size: var(--lt-text-headline);
}
.guidance p {
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
}
.guidance .lt-button {
  margin-top: var(--lt-space-4);
}
.result-enter-active,
.result-leave-active {
  transition:
    opacity var(--lt-dur-fade) var(--lt-ease-fade),
    transform var(--lt-dur-snappy) var(--lt-ease-snappy);
}
.result-enter-from {
  opacity: 0;
  transform: translateY(var(--lt-space-2));
}
.result-leave-to {
  opacity: 0;
}
</style>
