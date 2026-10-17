<script setup lang="ts">
import { computed, nextTick, onActivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { RotateCcw } from '@lucide/vue'
import { LtButton, LtCard } from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'
import { blurIn } from '@/motion'
import { VizPlayer } from '@/viz'
import type { Misconception, UnderstandingOption } from '@/api/understanding'
import { useUserStore } from '@/stores/user'
import { redoWhen } from '@/utils/redoDate'
import AnswerCard from './AnswerCard.vue'
import AskPanel from './AskPanel.vue'
import ContentByline from './ContentByline.vue'
import FlowStep from './FlowStep.vue'
import QuestionHead from './QuestionHead.vue'
import HintLadder from './HintLadder.vue'
import OptionChip from './OptionChip.vue'
import QuestionProse from './QuestionProse.vue'
import TutorNext from './TutorNext.vue'
import { useQuestionAsk } from './useQuestionAsk'
import { useUnderstandingFlow } from './useUnderstandingFlow'
import { liveTransport, previewTransport } from './understandingTransport'

/**
 * 题目理解页（ADR-0011 新基线）：一道题从错到懂。作答 → 选错时讲清所选选项的误区，
 * 按导师建议逐级提示，用本题数据播放并停在出错一步先预测 → 正解 → 变式。
 * 判分、答案、提示级别与正解门禁都由服务端决定，页面只跟随导师的下一步建议。
 */
defineOptions({ name: 'QuestionUnderstandingView' })
useLivingTheme()

const route = useRoute()
const router = useRouter()
const questionId = computed(() => Number(route.params.id))
/** An admin's preview of content still waiting for review: the same steps, nothing recorded. */
const previewing = computed(() => route.query.preview === '1')
const previewApi = previewTransport(() => questionId.value)
const flow = useUnderstandingFlow(
  () => questionId.value,
  () => (previewing.value ? previewApi : liveTransport),
)
const isAdmin = computed(() => useUserStore().userInfo?.role === 'ADMIN')
const {
  loadStatus,
  loadError,
  question,
  selected,
  attempt,
  answered,
  wrong,
  solution,
  solutionViz,
  variant,
  variantMissing,
  variantSelected,
  variantAttempt,
  tutor,
} = flow

const canGoBack = typeof window !== 'undefined' && typeof window.history.state?.back === 'string'
const courseLink = computed(() => (question.value ? `/my-courses/${question.value.position.courseId}` : '/my-courses'))

function goBack() {
  if (canGoBack) router.back()
  else void router.push(courseLink.value)
}

const misconception = computed<Misconception | null>(() => attempt.result.value?.misconception ?? null)
const correctLabel = computed(() =>
  attempt.result.value?.correct ? attempt.result.value.answer : (solution.result.value?.correctAnswer ?? null),
)
const showVariantStep = computed(() => solution.status.value === 'done' && !variantMissing.value)

const hints = computed(() => tutor.hints.value)
/** The misconception's player follows the hint ladder, as the tutor orders them. */
const showDemo = computed(() => wrong.value && !!attempt.result.value?.viz && tutor.hintsSettled.value)
/** Free-form questions about this attempt; previews record nothing, so they never ask. */
const ask = useQuestionAsk(() => (previewing.value ? null : (attempt.result.value?.attemptId ?? null)))
const showAsk = computed(() => answered.value && ask.status.value === 'ready')
const solved = computed(() => !wrong.value || solution.status.value === 'done')
const aided = computed(() => (tutor.state.value?.hintsUsed ?? 0) > 0 || ask.used.value)

/** Which step carries the tutor's next suggestion; none once the solution is open. */
const nextHost = computed<'misconception' | 'hints' | 'demo' | null>(() => {
  if (!wrong.value || solution.status.value === 'done') return null
  if (showDemo.value) return 'demo'
  return hints.value.length > 0 ? 'hints' : 'misconception'
})

const nextProps = computed(() => {
  const action = tutor.action.value
  const hinting = action?.type === 'HINT'
  return {
    action,
    reason: tutor.next.value?.reason ?? '',
    pending: hinting ? tutor.hintPending.value : solution.status.value === 'pending',
    error: (hinting ? tutor.hintError.value : solution.error.value) || tutor.nextError.value,
    failed: !tutor.next.value && !!tutor.nextError.value,
  }
})

function optionOf(options: UnderstandingOption[] | undefined, label: string | null | undefined) {
  return options?.find((option) => option.label === label) ?? null
}

const chosenOption = computed(() => optionOf(question.value?.options, attempt.result.value?.answer))
const correctOption = computed(() => optionOf(question.value?.options, correctLabel.value))
const variantCorrectOption = computed(() =>
  optionOf(variant.result.value?.options, variantAttempt.result.value?.correctAnswer),
)

const position = computed(() => question.value?.position ?? null)

const verdict = computed(() => {
  const result = attempt.result.value
  if (!result) return ''
  return result.correct ? '回答正确。' : '回答错误。'
})

const answerVerdict = computed(() => {
  const result = attempt.result.value
  if (!result) return null
  return result.correct
    ? { correct: true, text: '答对了。下面对一遍完整思路。' }
    : { correct: false, text: '不对。先看看这个选项背后的想法。' }
})

const variantVerdict = computed(() => {
  const result = variantAttempt.result.value
  if (!result) return null
  return result.correct
    ? { correct: true, text: result.assistance === 'AIDED' ? '这次做对了。' : '独立做对了。' }
    : { correct: false, text: `这次还不对，正确答案是 ${result.correctAnswer}。` }
})

/** How the attempt counted towards review and when the question comes back; nothing in a preview. */
const wrapUp = computed(() => {
  const result = variantAttempt.result.value
  const review = result?.review ?? attempt.result.value?.review
  const parts: string[] = []
  if (result) {
    parts.push(
      result.assistance === 'AIDED' ? '原题用过提示，这次变式不计入复习安排。' : '这次独立作答已计入复习安排。',
    )
  }
  if (review?.nextReviewDate) {
    const when = redoWhen(review.nextReviewDate)
    parts.push(`这道题${when.tone === 'due' ? (when.text === '今天' ? '今天' : '尽快') : when.text}再做一次。`)
  }
  return previewing.value ? '' : parts.join('')
})

const variantLead = computed(() =>
  aided.value
    ? '同一个概念，只改了一个条件。你在原题用过提示或追问，这次当作练习，结果不计入复习安排。'
    : '同一个概念，只改了一个条件。这次没有提示，自己做出来；结果会计入复习安排。',
)

type StepName = 'answer' | 'misconception' | 'hints' | 'demo' | 'solution' | 'variant' | 'done'

/** The step waiting for the learner; its margin label is emphasised. */
const current = computed<StepName>(() => {
  if (!answered.value) return 'answer'
  if (nextHost.value) return nextHost.value
  if (!showVariantStep.value) return 'solution'
  return variantAttempt.result.value ? 'done' : 'variant'
})

const misconceptionStep = ref<InstanceType<typeof FlowStep> | null>(null)
const hintStep = ref<InstanceType<typeof FlowStep> | null>(null)
const demoStep = ref<InstanceType<typeof FlowStep> | null>(null)
const solutionStep = ref<InstanceType<typeof FlowStep> | null>(null)
const variantResultEl = ref<HTMLElement | null>(null)

/**
 * Bring a newly opened step in: it rises into place and gets focus. When it
 * would start low, scroll only until it sits a little above the middle, so
 * what led to it (e.g. the chosen option) stays in view above it.
 */
async function present(target: () => HTMLElement | null, distance = 10) {
  await nextTick()
  const el = target()
  if (!el) return
  blurIn(el, distance)
  const top = el.getBoundingClientRect().top
  if (top > window.innerHeight * 0.55) {
    const by = top - window.innerHeight * 0.38
    window.scrollTo({ top: window.scrollY + by, behavior: 'smooth' })
  }
  el.querySelector<HTMLElement>('[data-focus]')?.focus({ preventScroll: true })
}

watch(
  () => attempt.result.value,
  (result) => result && !result.correct && present(() => stepEl(misconceptionStep.value), 14),
)
watch(
  () => hints.value.length,
  (count) =>
    count > 0 && present(() => stepEl(hintStep.value)?.querySelector<HTMLElement>('.hint:last-of-type') ?? null),
)
// After a hint ladder the player opens below it; without one it opens together with the misconception.
watch(showDemo, (shown) => shown && hints.value.length > 0 && present(() => stepEl(demoStep.value)))
watch(
  () => solution.result.value,
  (result) => result && present(() => stepEl(solutionStep.value)),
)
watch(
  () => variantAttempt.result.value,
  (result) => result && present(() => variantResultEl.value, 6),
)

function showDemoStep() {
  void present(() => stepEl(demoStep.value))
}

function stepEl(component: unknown): HTMLElement | null {
  return ((component as { $el?: HTMLElement } | null)?.$el as HTMLElement | undefined) ?? null
}

/** The tab names the knowledge point; reapplied when the page returns from the cache, after the router's own title. */
function applyTitle() {
  const name = question.value?.position.knowledgePointName
  if (name) document.title = `${name} · 一道题 · LearnPlatform`
}

watch(() => question.value?.position.knowledgePointName, applyTitle)
onActivated(applyTitle)

function start() {
  if (Number.isInteger(questionId.value) && questionId.value > 0) void flow.load()
  else loadStatus.value = 'missing'
}

watch(previewing, start)
onMounted(start)
</script>

<template>
  <div class="page">
    <p class="sr-only" aria-live="polite">{{ verdict }}</p>

    <QuestionHead
      :position="position"
      :loading="loadStatus === 'loading'"
      :previewing="previewing"
      :course-link="courseLink"
      @back="goBack"
    />

    <main class="flow">
      <!-- 加载、缺失与失败 -->
      <div v-if="loadStatus === 'loading'" class="state-row" aria-busy="true">
        <LtCard as="div" radius="xl" padding="none" class="question-card">
          <span class="skeleton" style="width: 180px; height: 14px" />
          <span class="skeleton" style="width: 100%; height: 18px; margin-top: 22px" />
          <span class="skeleton" style="width: 70%; height: 18px; margin-top: 12px" />
          <span v-for="n in 4" :key="n" class="skeleton" style="width: 100%; height: 52px; margin-top: 12px" />
          <span class="sr-only">正在加载题目</span>
        </LtCard>
      </div>

      <div v-else-if="loadStatus === 'missing'" class="state-row">
        <div class="state">
          <h2 class="state-title">这道题还没有理解内容</h2>
          <p class="state-text">它可能还在审核，或者你暂时没有访问权限。</p>
          <div class="state-actions">
            <LtButton
              v-if="isAdmin && !previewing"
              variant="primary"
              @click="router.replace({ query: { preview: '1' } })"
            >
              预览待审核内容
            </LtButton>
            <LtButton @click="goBack">返回</LtButton>
          </div>
        </div>
      </div>

      <div v-else-if="loadStatus === 'error'" class="state-row">
        <div class="state">
          <h2 class="state-title">题目没有加载出来</h2>
          <p class="state-text">{{ loadError }}</p>
          <LtButton variant="primary" @click="flow.load()">
            <template #icon><RotateCcw aria-hidden="true" /></template>
            重试
          </LtButton>
        </div>
      </div>

      <template v-else-if="question">
        <!-- 作答 -->
        <FlowStep
          label="作答"
          :tone="answered ? (wrong ? 'orange' : 'green') : 'accent'"
          :current="current === 'answer'"
          class="answer-step"
        >
          <AnswerCard
            v-model="selected"
            :source="question.source"
            kind="单选题"
            heading="题目"
            heading-id="question-heading"
            :level="2"
            :stem="question.stem"
            :options="question.options"
            name="question"
            label="选项"
            :chosen="attempt.result.value?.answer"
            :correct="correctLabel"
            :pending="attempt.status.value === 'pending'"
            :error="attempt.error.value"
            :verdict="answerVerdict"
            @submit="flow.submit()"
          />
        </FlowStep>

        <!-- 误区：只针对学习者选的那个选项 -->
        <FlowStep
          v-if="wrong"
          ref="misconceptionStep"
          label="误区"
          tone="orange"
          :current="current === 'misconception'"
        >
          <OptionChip
            tone="orange"
            label="你选了"
            :option-key="attempt.result.value?.answer"
            :content="chosenOption?.content"
          />
          <template v-if="misconception">
            <h2 class="step-title" tabindex="-1" data-focus>{{ misconception.title }}</h2>
            <QuestionProse class="reading" :content="misconception.explanation" />
            <ContentByline what="讲解" :source="misconception.source" />
          </template>
          <template v-else>
            <h2 class="step-title" tabindex="-1" data-focus>这个选项还没有单独的讲解</h2>
            <p class="reading muted">先看正解，对照一下自己是在哪一步想岔的。</p>
          </template>
          <TutorNext
            v-if="nextHost === 'misconception'"
            v-bind="nextProps"
            @hint="tutor.takeHint()"
            @reveal="flow.openSolution()"
            @retry="tutor.refresh()"
          />
        </FlowStep>

        <!-- 提示：服务端逐级给出，不判断选项、不泄露答案 -->
        <FlowStep
          v-if="wrong && hints.length > 0"
          ref="hintStep"
          label="提示"
          tone="accent"
          :current="current === 'hints'"
        >
          <HintLadder :hints="hints" />
          <TutorNext
            v-if="nextHost === 'hints'"
            v-bind="nextProps"
            @hint="tutor.takeHint()"
            @reveal="flow.openSolution()"
            @retry="tutor.refresh()"
          />
        </FlowStep>

        <!-- 演示：用本题数据播放，停在出错的那一步 -->
        <FlowStep
          v-if="showDemo && attempt.result.value?.viz"
          ref="demoStep"
          label="演示"
          tone="purple"
          :current="current === 'demo'"
        >
          <h2 class="sr-only">用本题数据演示</h2>
          <p class="lead">用这道题自己的数据走一遍。播放到容易出错的那一步会停下来，先预测，再看结果。</p>
          <VizPlayer
            :spec="attempt.result.value.viz.spec"
            :title="attempt.result.value.viz.title"
            @started="tutor.vizStarted('MISCONCEPTION')"
          />
          <TutorNext
            v-if="nextHost === 'demo'"
            v-bind="nextProps"
            @hint="tutor.takeHint()"
            @reveal="flow.openSolution()"
            @retry="tutor.refresh()"
          />
        </FlowStep>

        <!-- 追问：用自己的话问这道题；答对或揭示正解之前不给答案 -->
        <FlowStep v-if="showAsk && !solved" label="追问" tone="accent">
          <AskPanel :ask="ask" :solved="solved" @hint="tutor.takeHint()" @demo="showDemoStep()" />
        </FlowStep>

        <!-- 正解 -->
        <FlowStep
          v-if="solution.status.value !== 'idle' && (!wrong || solution.status.value === 'done')"
          ref="solutionStep"
          label="正解"
          :tone="solution.status.value === 'done' ? 'green' : 'muted'"
          :current="current === 'solution'"
        >
          <template v-if="solution.result.value">
            <OptionChip
              tone="green"
              label="正确答案"
              :option-key="solution.result.value.correctAnswer"
              :content="correctOption?.content"
            />
            <h2 class="step-title" tabindex="-1" data-focus>
              {{ wrong ? '正确的思路' : '对一遍思路' }}
            </h2>
            <QuestionProse class="reading" :content="solution.result.value.explanation" />
            <ContentByline what="讲解" :source="solution.result.value.explanationSource" />
            <VizPlayer
              v-if="solutionViz"
              class="step-viz"
              :spec="solutionViz.spec"
              :title="solutionViz.title"
              @started="tutor.vizStarted('SOLUTION')"
            />
            <div v-if="variantMissing" class="next">
              <LtButton @click="router.push(courseLink)">回到课程</LtButton>
              <span v-if="wrapUp" class="next-note">{{ wrapUp }}</span>
            </div>
          </template>
          <p v-else-if="solution.status.value === 'pending'" class="reading muted">正在打开正解…</p>
          <div v-else-if="solution.status.value === 'error'" class="next">
            <span class="next-note" data-tone="error">{{ solution.error.value }}</span>
            <LtButton @click="flow.openSolution()">重试</LtButton>
          </div>
        </FlowStep>

        <FlowStep v-if="showAsk && solved" label="追问" tone="accent">
          <AskPanel :ask="ask" :solved="solved" @hint="tutor.takeHint()" @demo="showDemoStep()" />
        </FlowStep>

        <!-- 变式：保留概念，只改一个条件，无提示独立作答 -->
        <FlowStep
          v-if="showVariantStep"
          label="变式"
          :tone="variantAttempt.result.value ? (variantAttempt.result.value.correct ? 'green' : 'orange') : 'accent'"
          :current="current === 'variant'"
        >
          <h2 class="step-title">换一个条件，再做一次</h2>
          <p class="lead">{{ variantLead }}</p>

          <AnswerCard
            v-if="variant.result.value"
            v-model="variantSelected"
            :source="variant.result.value.source"
            kind="变式 · 单选题"
            heading="变式题"
            heading-id="variant-heading"
            :level="3"
            :changed="variant.result.value.changedCondition"
            :stem="variant.result.value.stem"
            :options="variant.result.value.options"
            name="variant"
            label="变式题选项"
            :chosen="variantSelected"
            :correct="variantAttempt.result.value?.correctAnswer ?? null"
            :pending="variantAttempt.status.value === 'pending'"
            :error="variantAttempt.error.value"
            :verdict="variantVerdict"
            @submit="flow.submitVariant()"
          />
          <p v-else-if="variant.status.value === 'pending' || variant.status.value === 'idle'" class="reading muted">
            正在准备变式题…
          </p>
          <div v-else-if="variant.status.value === 'error'" class="next">
            <span class="next-note" data-tone="error">{{ variant.error.value }}</span>
            <LtButton @click="flow.loadVariant()">重试</LtButton>
          </div>

          <div v-if="variantAttempt.result.value" ref="variantResultEl" class="variant-result">
            <h3 class="sub-title" tabindex="-1" data-focus>
              {{ variantAttempt.result.value.correct ? '为什么这次是对的' : '这道变式的思路' }}
            </h3>
            <OptionChip
              v-if="variantCorrectOption && !variantAttempt.result.value.correct"
              class="variant-pick"
              tone="green"
              label="正确答案"
              :option-key="variantAttempt.result.value.correctAnswer"
              :content="variantCorrectOption.content"
            />
            <QuestionProse class="reading" :content="variantAttempt.result.value.explanation" />
            <ContentByline what="讲解" :source="variantAttempt.result.value.explanationSource" />
            <VizPlayer
              v-if="variantAttempt.result.value.viz"
              class="step-viz"
              :spec="variantAttempt.result.value.viz.spec"
              :title="variantAttempt.result.value.viz.title"
            />
            <div class="next">
              <LtButton @click="router.push(courseLink)">回到课程</LtButton>
              <span v-if="wrapUp" class="next-note">{{ wrapUp }}</span>
            </div>
          </div>
        </FlowStep>
      </template>
    </main>
  </div>
</template>

<style scoped>
.page {
  --rail: 88px;
  --rail-gap: var(--lt-space-7);

  max-width: 960px;
  margin: 0 auto;
  padding: 0 var(--lt-space-7) 120px;
}

.state-row {
  display: grid;
  grid-template-columns: var(--rail) minmax(0, 1fr);
  column-gap: var(--rail-gap);
}

.state-row > * {
  grid-column: 2;
}

.state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--lt-space-3);
  padding: var(--lt-space-8) 0;
}

.state-title {
  margin: 0;
  font-size: var(--lt-text-title3);
}

.state-text {
  margin: 0 0 var(--lt-space-2);
  color: var(--lt-label-2);
}

.state-actions {
  display: flex;
  gap: var(--lt-space-3);
}

/* ------------------------------------------------------------ question */

.answer-step {
  --rail-offset: 20px;
}

.question-card {
  padding: 22px 28px 24px;
}

/* --------------------------------------------------- explanation steps */

.step-title {
  margin: var(--lt-space-4) 0 var(--lt-space-3);
  font-size: var(--lt-text-title2);
  text-wrap: balance;
}

/* 标题只为读屏和键盘定位而聚焦，不画焦点框。 */
[data-focus]:focus {
  outline: none;
}

.step-title:first-child {
  margin-top: 0;
}

.reading.muted,
.muted {
  color: var(--lt-label-2);
}

p.reading {
  margin: 0;
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}

.lead {
  margin: 4px 0 var(--lt-space-4);
  color: var(--lt-label-2);
  font-size: var(--lt-text-body);
  line-height: 1.6;
}

.step-title + .lead {
  margin-top: calc(-1 * var(--lt-space-1));
}

.next {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--lt-space-4);
  margin-top: var(--lt-space-6);
}

.next-note {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.next-note[data-tone='error'] {
  color: var(--lt-red-text);
}

.step-viz {
  margin-top: var(--lt-space-6);
}

/* -------------------------------------------------------------- variant */

.variant-result {
  margin-top: var(--lt-space-7);
}

.sub-title {
  margin: 0 0 var(--lt-space-3);
  font-size: var(--lt-text-headline);
}

.variant-pick {
  margin-bottom: var(--lt-space-3);
}

/* -------------------------------------------------------------- loading */

.skeleton {
  display: block;
  border-radius: 8px;
  background: var(--lt-fill-2);
  animation: pending 1.4s ease-in-out infinite alternate;
}

@keyframes pending {
  to {
    opacity: 0.45;
  }
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
