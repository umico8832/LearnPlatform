<script setup lang="ts">
import { computed, nextTick, onActivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, ChevronLeft, CircleAlert, CircleCheck, RotateCcw } from '@lucide/vue'
import { LtButton, LtCard, LtIconButton } from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'
import { blurIn, prefersReducedMotion } from '@/motion'
import { VizPlayer } from '@/viz'
import type { Misconception, UnderstandingOption } from '@/api/understanding'
import ChoiceList from './ChoiceList.vue'
import FlowStep from './FlowStep.vue'
import QuestionProse from './QuestionProse.vue'
import SourceBadge from './SourceBadge.vue'
import { useUnderstandingFlow } from './useUnderstandingFlow'

/**
 * 题目理解页（ADR-0011 新基线）：一道题从错到懂。作答 → 选错时讲清所选选项的误区，
 * 用本题数据播放并停在出错一步先预测 → 正解 → 无提示变式。判分与答案都由服务端开放。
 */
defineOptions({ name: 'QuestionUnderstandingView' })
useLivingTheme()

const route = useRoute()
const router = useRouter()
const questionId = computed(() => Number(route.params.id))
const flow = useUnderstandingFlow(() => questionId.value)
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

function optionOf(options: UnderstandingOption[] | undefined, label: string | null | undefined) {
  return options?.find((option) => option.label === label) ?? null
}

const chosenOption = computed(() => optionOf(question.value?.options, attempt.result.value?.answer))
const correctOption = computed(() => optionOf(question.value?.options, correctLabel.value))
const variantCorrectOption = computed(() =>
  optionOf(variant.result.value?.options, variantAttempt.result.value?.correctAnswer),
)

const position = computed(() => question.value?.position ?? null)
const crumbs = computed(() => {
  const place = position.value
  if (!place) return []
  return place.chapterName && place.chapterName !== place.knowledgePointName
    ? [place.courseName, place.chapterName]
    : [place.courseName]
})

const verdict = computed(() => {
  const result = attempt.result.value
  if (!result) return ''
  return result.correct ? '回答正确。' : '回答错误。'
})

type StepName = 'answer' | 'misconception' | 'demo' | 'solution' | 'variant' | 'done'

/** The step waiting for the learner; its margin label is emphasised. */
const current = computed<StepName>(() => {
  if (!answered.value) return 'answer'
  if (wrong.value && solution.status.value !== 'done') return attempt.result.value?.viz ? 'demo' : 'misconception'
  if (!showVariantStep.value) return 'solution'
  return variantAttempt.result.value ? 'done' : 'variant'
})

const misconceptionStep = ref<InstanceType<typeof FlowStep> | null>(null)
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
    window.scrollTo({ top: window.scrollY + by, behavior: prefersReducedMotion() ? 'auto' : 'smooth' })
  }
  el.querySelector<HTMLElement>('[data-focus]')?.focus({ preventScroll: true })
}

watch(
  () => attempt.result.value,
  (result) => result && !result.correct && present(() => stepEl(misconceptionStep.value), 14),
)
watch(
  () => solution.result.value,
  (result) => result && present(() => stepEl(solutionStep.value)),
)
watch(
  () => variantAttempt.result.value,
  (result) => result && present(() => variantResultEl.value, 6),
)

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

onMounted(() => {
  if (Number.isInteger(questionId.value) && questionId.value > 0) void flow.load()
  else loadStatus.value = 'missing'
})
</script>

<template>
  <div class="page">
    <p class="sr-only" aria-live="polite">{{ verdict }}</p>

    <header class="head">
      <div class="head-rail">
        <LtIconButton label="返回" @click="goBack">
          <ChevronLeft aria-hidden="true" />
        </LtIconButton>
      </div>
      <div v-if="position" class="head-title">
        <nav class="crumbs" aria-label="所在位置">
          <template v-for="(crumb, i) in crumbs" :key="crumb">
            <span v-if="i > 0" aria-hidden="true">›</span>
            <RouterLink v-if="i === 0" :to="courseLink">{{ crumb }}</RouterLink>
            <span v-else>{{ crumb }}</span>
          </template>
        </nav>
        <h1 class="title">{{ position.knowledgePointName }}</h1>
      </div>
      <div v-else-if="loadStatus === 'loading'" class="head-title" aria-hidden="true">
        <span class="skeleton" style="width: 140px; height: 12px" />
        <span class="skeleton" style="width: 220px; height: 26px; margin-top: 8px" />
      </div>
    </header>

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
          <LtButton @click="goBack">返回</LtButton>
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
          <LtCard as="article" radius="xl" padding="none" class="question-card" aria-labelledby="question-heading">
            <div class="card-head">
              <SourceBadge :source="question.source" />
              <span class="card-type">单选题</span>
            </div>
            <h2 id="question-heading" class="sr-only">题目</h2>
            <QuestionProse class="stem" :content="question.stem" />
            <ChoiceList
              v-model="selected"
              class="choices"
              name="question"
              label="选项"
              :options="question.options"
              :locked="answered"
              :chosen="attempt.result.value?.answer"
              :correct="correctLabel"
              :active="!answered"
              @submit="flow.submit()"
            />
            <footer class="card-foot">
              <template v-if="!answered">
                <span class="foot-note" :data-tone="attempt.error.value ? 'error' : undefined">
                  {{ attempt.error.value || '按 A–D 选择，Enter 提交' }}
                </span>
                <LtButton
                  variant="primary"
                  size="lg"
                  :disabled="!selected || attempt.status.value === 'pending'"
                  @click="flow.submit()"
                >
                  {{ attempt.status.value === 'pending' ? '正在判分…' : '提交' }}
                </LtButton>
              </template>
              <p v-else class="verdict" :data-tone="wrong ? 'orange' : 'green'">
                <CircleAlert v-if="wrong" :size="18" aria-hidden="true" />
                <CircleCheck v-else :size="18" aria-hidden="true" />
                <span v-if="wrong">不对。先看看这个选项背后的想法。</span>
                <span v-else>答对了。下面对一遍完整思路。</span>
              </p>
            </footer>
          </LtCard>
        </FlowStep>

        <!-- 误区：只针对学习者选的那个选项 -->
        <FlowStep
          v-if="wrong"
          ref="misconceptionStep"
          label="误区"
          tone="orange"
          :current="current === 'misconception'"
        >
          <p class="pick" data-tone="orange">
            <span class="pick-label">你选了</span>
            <span class="pick-key">{{ attempt.result.value?.answer }}</span>
            <QuestionProse v-if="chosenOption" class="pick-text" :content="chosenOption.content" inline />
          </p>
          <template v-if="misconception">
            <h2 class="step-title" tabindex="-1" data-focus>{{ misconception.title }}</h2>
            <QuestionProse class="reading" :content="misconception.explanation" />
            <p class="byline">
              讲解 · {{ misconception.source.kind === 'AI_GENERATED' ? 'AI 生成' : '人工整理' }} · 已审核
            </p>
          </template>
          <template v-else>
            <h2 class="step-title" tabindex="-1" data-focus>这个选项还没有单独的讲解</h2>
            <p class="reading muted">先看正解，对照一下自己是在哪一步想岔的。</p>
          </template>
          <div v-if="!attempt.result.value?.viz && solution.status.value !== 'done'" class="next">
            <LtButton
              variant="primary"
              size="lg"
              :disabled="solution.status.value === 'pending'"
              @click="flow.openSolution()"
            >
              {{ solution.status.value === 'pending' ? '正在打开…' : '看正解' }}
              <template #trailing><ArrowRight aria-hidden="true" /></template>
            </LtButton>
            <span v-if="solution.error.value" class="next-note" data-tone="error">{{ solution.error.value }}</span>
          </div>
        </FlowStep>

        <!-- 演示：用本题数据播放，停在出错的那一步 -->
        <FlowStep v-if="wrong && attempt.result.value?.viz" label="演示" tone="purple" :current="current === 'demo'">
          <h2 class="sr-only">用本题数据演示</h2>
          <p class="lead">用这道题自己的数据走一遍。播放到容易出错的那一步会停下来，先预测，再看结果。</p>
          <VizPlayer :spec="attempt.result.value.viz.spec" :title="attempt.result.value.viz.title" />
          <div v-if="solution.status.value !== 'done'" class="next">
            <LtButton
              variant="primary"
              size="lg"
              :disabled="solution.status.value === 'pending'"
              @click="flow.openSolution()"
            >
              {{ solution.status.value === 'pending' ? '正在打开…' : '看正解' }}
              <template #trailing><ArrowRight aria-hidden="true" /></template>
            </LtButton>
            <span class="next-note" :data-tone="solution.error.value ? 'error' : undefined">
              {{ solution.error.value || '不必先看完演示，随时可以看正解。' }}
            </span>
          </div>
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
            <p class="pick" data-tone="green">
              <span class="pick-label">正确答案</span>
              <span class="pick-key">{{ solution.result.value.correctAnswer }}</span>
              <QuestionProse v-if="correctOption" class="pick-text" :content="correctOption.content" inline />
            </p>
            <h2 class="step-title" tabindex="-1" data-focus>
              {{ wrong ? '正确的思路' : '对一遍思路' }}
            </h2>
            <QuestionProse class="reading" :content="solution.result.value.explanation" />
            <VizPlayer v-if="solutionViz" class="step-viz" :spec="solutionViz.spec" :title="solutionViz.title" />
          </template>
          <p v-else-if="solution.status.value === 'pending'" class="reading muted">正在打开正解…</p>
          <div v-else-if="solution.status.value === 'error'" class="next">
            <span class="next-note" data-tone="error">{{ solution.error.value }}</span>
            <LtButton @click="flow.openSolution()">重试</LtButton>
          </div>
        </FlowStep>

        <!-- 变式：保留概念，只改一个条件，无提示独立作答 -->
        <FlowStep
          v-if="showVariantStep"
          label="变式"
          :tone="variantAttempt.result.value ? (variantAttempt.result.value.correct ? 'green' : 'orange') : 'accent'"
          :current="current === 'variant'"
        >
          <h2 class="step-title">换一个条件，再做一次</h2>
          <p class="lead">同一个概念，只改了一个条件。这次没有提示，自己做出来。</p>

          <LtCard
            v-if="variant.result.value"
            as="article"
            radius="xl"
            padding="none"
            class="question-card"
            aria-labelledby="variant-heading"
          >
            <div class="card-head">
              <SourceBadge :source="variant.result.value.source" />
              <span class="card-type">变式 · 单选题</span>
            </div>
            <h3 id="variant-heading" class="sr-only">变式题</h3>
            <p class="changed">
              <span class="changed-label">和原题相比</span>
              <span>{{ variant.result.value.changedCondition }}</span>
            </p>
            <QuestionProse class="stem" :content="variant.result.value.stem" />
            <ChoiceList
              v-model="variantSelected"
              class="choices"
              name="variant"
              label="变式题选项"
              :options="variant.result.value.options"
              :locked="Boolean(variantAttempt.result.value)"
              :chosen="variantSelected"
              :correct="variantAttempt.result.value?.correctAnswer ?? null"
              :active="!variantAttempt.result.value"
              @submit="flow.submitVariant()"
            />
            <footer class="card-foot">
              <template v-if="!variantAttempt.result.value">
                <span class="foot-note" :data-tone="variantAttempt.error.value ? 'error' : undefined">
                  {{ variantAttempt.error.value || '按 A–D 选择，Enter 提交' }}
                </span>
                <LtButton
                  variant="primary"
                  size="lg"
                  :disabled="!variantSelected || variantAttempt.status.value === 'pending'"
                  @click="flow.submitVariant()"
                >
                  {{ variantAttempt.status.value === 'pending' ? '正在判分…' : '提交' }}
                </LtButton>
              </template>
              <p v-else class="verdict" :data-tone="variantAttempt.result.value.correct ? 'green' : 'orange'">
                <CircleCheck v-if="variantAttempt.result.value.correct" :size="18" aria-hidden="true" />
                <CircleAlert v-else :size="18" aria-hidden="true" />
                <span v-if="variantAttempt.result.value.correct">独立做对了。</span>
                <span v-else> 这次还不对，正确答案是 {{ variantAttempt.result.value.correctAnswer }}。 </span>
              </p>
            </footer>
          </LtCard>
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
            <p v-if="variantCorrectOption && !variantAttempt.result.value.correct" class="pick" data-tone="green">
              <span class="pick-label">正确答案</span>
              <span class="pick-key">{{ variantAttempt.result.value.correctAnswer }}</span>
              <QuestionProse class="pick-text" :content="variantCorrectOption.content" inline />
            </p>
            <QuestionProse class="reading" :content="variantAttempt.result.value.explanation" />
            <VizPlayer
              v-if="variantAttempt.result.value.viz"
              class="step-viz"
              :spec="variantAttempt.result.value.viz.spec"
              :title="variantAttempt.result.value.viz.title"
            />
            <div class="next">
              <LtButton @click="router.push(courseLink)">回到课程</LtButton>
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

.head,
.state-row {
  display: grid;
  grid-template-columns: var(--rail) minmax(0, 1fr);
  column-gap: var(--rail-gap);
}

.head {
  align-items: end;
  padding: var(--lt-space-7) 0;
}

.head-rail {
  margin-left: -8px;
}

.head-title {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.crumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.crumbs a {
  color: var(--lt-label-2);
  text-decoration: none;
}

.crumbs a:hover {
  color: var(--lt-accent);
}

.title {
  margin: 4px 0 0;
  font-size: var(--lt-text-title2);
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

/* ------------------------------------------------------------ question */

.answer-step {
  --rail-offset: 20px;
}

.question-card {
  padding: 22px 28px 24px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-4);
  margin-bottom: var(--lt-space-4);
}

.card-type {
  flex: none;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.choices {
  margin-top: var(--lt-space-5);
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-4);
  min-height: var(--lt-control-lg);
  margin-top: var(--lt-space-5);
}

.foot-note {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.verdict {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: var(--lt-text-body);
  font-weight: 600;
}

.verdict[data-tone='orange'] {
  color: var(--lt-orange-text);
}

.verdict[data-tone='green'] {
  color: var(--lt-green-text);
}

/* --------------------------------------------------- explanation steps */

.pick {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  margin: 2px 0 0;
  padding: 4px 14px 4px 4px;
  border-radius: var(--lt-radius-pill);
  font-size: var(--lt-text-footnote);
  line-height: 1.4;
}

.pick[data-tone='orange'] {
  background: var(--lt-orange-soft);
}

.pick[data-tone='green'] {
  background: var(--lt-green-soft);
}

.pick-key {
  display: grid;
  flex: none;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  color: var(--lt-on-accent);
  font: 600 var(--lt-text-caption) / 1 var(--lt-font-rounded);
}

.pick[data-tone='orange'] .pick-key {
  background: var(--lt-orange);
}

.pick[data-tone='green'] .pick-key {
  background: var(--lt-green);
}

.pick-label {
  flex: none;
  padding-left: 8px;
  font-weight: 600;
}

.pick[data-tone='orange'] .pick-label {
  color: var(--lt-orange-text);
}

.pick[data-tone='green'] .pick-label {
  color: var(--lt-green-text);
}

.pick-text {
  min-width: 0;
  font-variant-numeric: tabular-nums;
}

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

.byline {
  margin: var(--lt-space-3) 0 0;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
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

:is(.next-note, .foot-note)[data-tone='error'] {
  color: var(--lt-red-text);
}

.step-viz {
  margin-top: var(--lt-space-6);
}

/* -------------------------------------------------------------- variant */

.changed {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 0 0 var(--lt-space-4);
  padding: 10px 14px;
  border-radius: var(--lt-radius-sm);
  background: var(--lt-fill-3);
  box-shadow: inset 0 0 0 0.5px var(--lt-separator);
  font-size: var(--lt-text-subhead);
  line-height: 1.5;
}

.changed-label {
  flex: none;
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
  font-weight: 600;
}

.variant-result {
  margin-top: var(--lt-space-7);
}

.sub-title {
  margin: 0 0 var(--lt-space-3);
  font-size: var(--lt-text-headline);
}

.variant-result .pick {
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

@media (prefers-reduced-motion: reduce) {
  .skeleton {
    animation: none;
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
