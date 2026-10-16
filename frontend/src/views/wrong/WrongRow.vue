<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowRight } from '@lucide/vue'
import { LtButton, LtSegmented } from '@/components/living'
import type { WrongQuestionVO } from '@/api/wrongQuestion'
import ContentByline from '../question/ContentByline.vue'
import OptionChip from '../question/OptionChip.vue'
import QuestionProse from '../question/QuestionProse.vue'
import { isOptionKey, MASTERY_OPTIONS, redoWhen } from './wrongPresentation'

/**
 * One wrong question: when to redo it, why it went wrong (the misconception
 * of the option chosen last time), and the way back into it. Bookkeeping —
 * mastery and removal — stays small at the bottom.
 */
const props = defineProps<{
  item: WrongQuestionVO
  updating: boolean
  removing: boolean
  redoing: boolean
  error?: string
}>()

const emit = defineEmits<{ redo: []; mastery: [level: number]; remove: [] }>()

const when = computed(() => redoWhen(props.item.nextReviewDate))
const expanded = ref(false)
const whyText = ref<HTMLElement | null>(null)
/** Offer "展开" only when the two-line clamp actually hides text. */
const clamped = ref(false)
let observer: ResizeObserver | null = null

function measure() {
  const el = whyText.value
  if (el && !expanded.value) clamped.value = el.scrollHeight > el.clientHeight + 1
}

onMounted(() => {
  measure()
  if (whyText.value && typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(measure)
    observer.observe(whyText.value)
  }
})
onBeforeUnmount(() => observer?.disconnect())
const options = computed(() => MASTERY_OPTIONS.map((option) => ({ ...option, disabled: props.updating })))
const confirming = ref(false)
const mastery = computed({
  get: () => String(props.item.masteryLevel),
  set: (value: string) => emit('mastery', Number(value)),
})
const headingId = computed(() => `wrong-${props.item.id}`)
</script>

<template>
  <article class="row" :aria-labelledby="headingId">
    <p class="when" :data-tone="when.tone">
      <span class="when-label">{{ when.tone === 'none' ? '' : '重做' }}</span>
      <span class="when-text">{{ when.text }}</span>
    </p>

    <div class="main">
      <h3 :id="headingId" class="stem">{{ item.questionContent }}</h3>

      <div class="why">
        <OptionChip
          v-if="isOptionKey(item.lastWrongAnswer)"
          tone="orange"
          label="你选了"
          :option-key="item.lastWrongAnswer"
        />
        <p v-else-if="item.lastWrongAnswer" class="last-answer">
          上次作答：<span>{{ item.lastWrongAnswer }}</span>
        </p>

        <template v-if="item.misconception">
          <h4 class="why-title">{{ item.misconception.title }}</h4>
          <div ref="whyText" class="why-text" :data-expanded="expanded || undefined">
            <QuestionProse :content="item.misconception.explanation" />
          </div>
          <button
            v-if="clamped || expanded"
            type="button"
            class="link"
            :aria-expanded="expanded"
            @click="expanded = !expanded"
          >
            {{ expanded ? '收起' : '展开讲解' }}
          </button>
          <ContentByline what="讲解" :source="item.misconception.source" />
        </template>
        <p v-else class="why-none">这个选项还没有单独讲解，重做时对照正解看看。</p>
      </div>

      <div class="meta">
        <span class="count">答错 {{ item.wrongCount }} 次</span>
        <LtSegmented v-model="mastery" :options="options" label="掌握程度" size="sm" />
        <template v-if="confirming">
          <span class="confirm">从错题本移出这道题？</span>
          <LtButton size="sm" :disabled="removing" @click="emit('remove')">
            {{ removing ? '正在移出…' : '移出' }}
          </LtButton>
          <LtButton size="sm" variant="quiet" :disabled="removing" @click="confirming = false">取消</LtButton>
        </template>
        <button v-else type="button" class="link" @click="confirming = true">移出</button>
      </div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </div>

    <div class="action">
      <LtButton :disabled="redoing" @click="emit('redo')">
        {{ redoing ? '正在打开…' : '重做' }}
        <template #trailing><ArrowRight aria-hidden="true" /></template>
      </LtButton>
    </div>
  </article>
</template>

<style scoped>
.row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) auto;
  column-gap: var(--lt-space-6);
  padding: var(--lt-space-6) var(--lt-space-7);
}

.row + .row {
  border-top: 1px solid var(--lt-separator);
}

.when {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 2px 0 0;
}

.when-label {
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption2);
}

.when-text {
  color: var(--lt-label-2);
  font: 600 var(--lt-text-subhead) / 1.3 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.when[data-tone='due'] .when-text {
  color: var(--lt-orange-text);
}

.when[data-tone='none'] .when-text {
  color: var(--lt-label-3);
  font-weight: 500;
}

.main {
  min-width: 0;
}

.stem {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  font-size: var(--lt-text-body);
  font-weight: 500;
  line-height: 1.55;
}

.why {
  margin-top: var(--lt-space-4);
}

.last-answer {
  margin: 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}

.last-answer span {
  color: var(--lt-orange-text);
  font-weight: 600;
}

.why-title {
  margin: var(--lt-space-3) 0 var(--lt-space-1);
  font-size: var(--lt-text-headline);
}

.why-text {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  color: var(--lt-label-2);
  font-size: var(--lt-text-body);
  line-height: 1.6;
}

.why-text[data-expanded] {
  display: block;
}

.why-none {
  margin: var(--lt-space-3) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-body);
}

.link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--lt-accent);
  font: inherit;
  font-size: var(--lt-text-footnote);
  cursor: pointer;
}

.link:hover {
  text-decoration: underline;
}

.why .link {
  margin-top: var(--lt-space-1);
}

.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--lt-space-4);
  margin-top: var(--lt-space-5);
  color: var(--lt-label-3);
  font-size: var(--lt-text-footnote);
}

.count {
  font-variant-numeric: tabular-nums;
}

.meta .link {
  color: var(--lt-label-2);
}

.confirm {
  color: var(--lt-label);
}

.error {
  margin: var(--lt-space-3) 0 0;
  color: var(--lt-red-text);
  font-size: var(--lt-text-footnote);
}

.action {
  padding-top: 2px;
}
</style>
