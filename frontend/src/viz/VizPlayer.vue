<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import { Braces, Maximize2 } from '@lucide/vue'
import { LtButton, LtIconButton } from '@/components/living'
import type { SceneSpec } from './engine/types'
import { announceFullPlayerReady, expandInto } from './expandTransition'
import { specToQuery, visibleSpec } from './spec'
import { useVizSession } from './useVizSession'
import VizCaption from './ui/VizCaption.vue'
import VizCheckpoints from './ui/VizCheckpoints.vue'
import VizCodePanel from './ui/VizCodePanel.vue'
import VizParams from './ui/VizParams.vue'
import VizPredict from './ui/VizPredict.vue'
import VizSpecSheet from './ui/VizSpecSheet.vue'
import VizStage from './ui/VizStage.vue'
import VizTransport from './ui/VizTransport.vue'
import VizVarsPanel from './ui/VizVarsPanel.vue'

/**
 * One visualization in two sizes. Embedded: stage, caption and transport inside
 * an explanation, fed with the question's own data, with a link that expands it.
 * Full: adds parameters, code, variables and checkpoints for a page of its own.
 */
const props = withDefaults(
  defineProps<{
    spec: SceneSpec
    size?: 'embedded' | 'full'
    /** Heading shown above an embedded player. */
    title?: string
    /** Full-page link for an embedded player; defaults to this scene's own page, false hides it. */
    expandTo?: RouteLocationRaw | false
    /** Let learners edit the data; defaults to the full size only. */
    editable?: boolean
  }>(),
  { size: 'embedded', title: '', expandTo: undefined, editable: undefined },
)

const emit = defineEmits<{
  'update:spec': [spec: SceneSpec]
  /** The learner started this player for the first time: pressed play or stepped forward. */
  started: []
}>()

const router = useRouter()
const session = useVizSession(() => props.spec)
const root = ref<HTMLElement | null>(null)
const card = ref<HTMLElement | null>(null)
const transport = ref<InstanceType<typeof VizTransport> | null>(null)
const predict = ref<InstanceType<typeof VizPredict> | null>(null)
const specButton = ref<InstanceType<typeof LtIconButton> | null>(null)
const sheetOpen = ref(false)

const full = computed(() => props.size === 'full')
const canEdit = computed(() => props.editable ?? full.value)
const asking = computed(() => session.state.value.asking)
const trace = computed(() => session.trace.value)
const step = computed(() => session.step.value)
const ghost = computed(() => (asking.value !== null ? session.ghost.value : null))
const codeLine = computed(() =>
  asking.value !== null ? (trace.value?.steps[asking.value]?.line ?? null) : (step.value?.line ?? null),
)
const shownSpec = computed(() =>
  session.module.value && session.spec.value ? visibleSpec(session.module.value, session.spec.value) : props.spec,
)
const expandTarget = computed<RouteLocationRaw | undefined>(() => {
  if (full.value || props.expandTo === false) return undefined
  if (props.expandTo) return props.expandTo
  const module = session.module.value
  const spec = session.spec.value
  return module && spec
    ? { name: 'Visualization', params: { scene: spec.scene }, query: specToQuery(module, spec) }
    : undefined
})
const expandHref = computed(() => (expandTarget.value ? router.resolve(expandTarget.value).href : undefined))
const sheetOrigin = computed(() => (specButton.value?.$el as HTMLElement | undefined) ?? null)
const stageLabel = computed(() =>
  step.value ? `${props.title || '数据结构动画'}：${step.value.title}` : props.title || '数据结构动画',
)

function changeSpec(patch: Record<string, unknown>) {
  emit('update:spec', { ...(session.spec.value ?? props.spec), ...patch })
}

function onKeydown(event: KeyboardEvent) {
  if (sheetOpen.value || event.defaultPrevented || event.metaKey || event.ctrlKey || event.altKey) return
  const target = event.target as HTMLElement
  if (target.closest('input, textarea, select, [contenteditable="true"], dialog')) return
  if (asking.value !== null && predict.value?.handleKey(event)) {
    event.preventDefault()
    return
  }
  const player = session.player
  const onControl = target.closest('button, a, [role="radio"], [role="slider"]')
  const inGroup = target.closest('[role="radiogroup"], [role="slider"]')
  if (event.key === ' ' && !onControl) player.toggle()
  else if (event.key === 'ArrowRight' && !inGroup) player.next()
  else if (event.key === 'ArrowLeft' && !inGroup) player.prev()
  else if (event.key === 'Home' && !inGroup) player.restart()
  else return
  event.preventDefault()
}

/** Expand into the full page; the stage card morphs across with a view transition. */
function expand(event: MouseEvent) {
  const target = expandTarget.value
  if (!target || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
  event.preventDefault()
  // The full page plays its own copy; this one waits where it was, e.g. in a page kept alive behind it.
  session.player.pause()
  expandInto(card.value, () => router.push(target))
}

const started = watch(
  () => session.state.value.playing || session.state.value.index > 0,
  (moving) => {
    if (!moving) return
    started.stop()
    emit('started')
  },
)

watch(
  () => full.value && session.status.value === 'ready',
  (ready) => ready && announceFullPlayerReady(),
  { flush: 'post' },
)

onMounted(() => {
  if (full.value) window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <section
    ref="root"
    class="lt viz"
    :data-size="size"
    :aria-label="title || '数据结构动画'"
    :tabindex="full ? undefined : -1"
    @keydown="full ? undefined : onKeydown($event)"
  >
    <div class="viz-layout">
      <div ref="card" class="viz-card">
        <header class="viz-head">
          <h3 v-if="!full && title" class="viz-title">{{ title }}</h3>
          <VizParams
            v-if="canEdit && session.module.value && session.spec.value"
            :module="session.module.value"
            :spec="session.spec.value"
            @change="changeSpec"
          />
          <span class="viz-head-spacer" />
          <LtIconButton
            v-if="trace"
            ref="specButton"
            label="AI 场景描述"
            variant="quiet"
            size="sm"
            @click="sheetOpen = true"
          >
            <Braces aria-hidden="true" />
          </LtIconButton>
          <a v-if="!full && expandHref" class="viz-expand" :href="expandHref" @click="expand">
            <Maximize2 :size="14" aria-hidden="true" />展开
          </a>
        </header>

        <template v-if="session.status.value === 'ready' && trace && step">
          <VizStage class="viz-stage" :frame="step.frame" :ghost="ghost" :label="stageLabel" />
          <div class="viz-dock">
            <VizPredict
              v-if="asking !== null"
              ref="predict"
              :session="session"
              :index="asking"
              :origin="() => transport?.diamondRect(asking!) ?? null"
            />
            <VizCaption v-else :step="step" :index="session.state.value.index" :total="trace.steps.length" />
          </div>
          <VizTransport ref="transport" class="viz-transport" :session="session" :compact="!full" />
        </template>
        <div v-else class="viz-placeholder" :data-status="session.status.value">
          <template v-if="session.status.value === 'error'">
            <p>{{ session.error.value }}</p>
            <LtButton @click="session.retry()">重试</LtButton>
          </template>
          <p v-else>正在准备动画…</p>
        </div>
      </div>

      <aside v-if="full && trace" class="viz-side" aria-label="代码、变量与检查点">
        <VizCodePanel :code="trace.code" :line="codeLine" :pending="asking !== null" />
        <VizVarsPanel v-if="step" :vars="step.vars" />
        <VizCheckpoints :session="session" />
      </aside>
    </div>

    <VizSpecSheet v-if="trace" v-model:open="sheetOpen" :spec="shownSpec" :trace="trace" :origin="sheetOrigin" />
  </section>
</template>

<style scoped>
.viz {
  min-width: 0;
}

/* 嵌入时点击任意位置让键盘快捷键作用于这一个播放器；不显示焦点框。 */
.viz[data-size='embedded']:focus {
  outline: none;
}

.viz-layout {
  display: grid;
  gap: var(--lt-space-5);
  height: 100%;
}

.viz[data-size='full'] {
  height: 100%;
}

.viz[data-size='full'] .viz-layout {
  grid-template-columns: minmax(0, 1fr) 400px;
}

.viz-card {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto auto;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface);
  box-shadow: var(--lt-shadow-card);
}

.viz[data-size='full'] .viz-card {
  view-transition-name: viz-stage;
}

.viz-head {
  display: flex;
  align-items: center;
  gap: var(--lt-space-3);
  min-height: 48px;
  padding: 10px 12px 0 16px;
}

.viz-title {
  margin: 0;
  font-size: var(--lt-text-subhead);
}

.viz-head-spacer {
  flex: 1;
}

.viz-expand {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: var(--lt-control-sm);
  padding: 0 12px;
  border-radius: var(--lt-radius-pill);
  color: var(--lt-accent);
  font-size: var(--lt-text-footnote);
  font-weight: 500;
  transition: background-color var(--lt-dur-fade) var(--lt-ease-fade);
}

.viz-stage {
  margin: 10px 12px 0;
}

.viz[data-size='embedded'] .viz-stage {
  height: 380px;
}

.viz-dock {
  min-height: 118px;
  padding: 16px 24px 6px;
}

.viz-transport {
  padding: 6px 18px 16px;
}

.viz-placeholder {
  display: grid;
  grid-row: 2 / -1;
  place-content: center;
  justify-items: center;
  gap: var(--lt-space-3);
  min-height: 300px;
  color: var(--lt-label-2);
}

.viz-placeholder p {
  margin: 0;
}

.viz-side {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-4);
  min-height: 0;
  overflow: auto;
  padding-bottom: 2px;
}

@media (hover: hover) and (pointer: fine) {
  .viz-expand:hover {
    background: var(--lt-accent-soft);
  }
}
</style>

<style>
::view-transition-group(viz-stage) {
  animation-duration: var(--lt-dur-layout);
  animation-timing-function: var(--lt-ease-layout);
}
</style>
