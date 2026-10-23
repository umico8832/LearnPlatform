<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft } from '@lucide/vue'
import { LtIconButton } from '@/components/living'
import { useGamificationStore } from '@/stores/gamification'
import { useLivingTheme } from '@/composables/useLivingTheme'
import ShellProgress from './ShellProgress.vue'

/**
 * Focus shell (Phase 28, dark only) for AI teaching, timed exams, exam study and
 * results: no section navigation, only the way back and where you are. A timed exam
 * hides learning facts entirely. Leaving an exam is still guarded by the page itself.
 */
withDefaults(defineProps<{ narrow?: boolean }>(), { narrow: true })

useLivingTheme({ dark: true, legacyBridge: true })

const route = useRoute()
const router = useRouter()
const gamification = useGamificationStore()
const mainRef = ref<HTMLElement>()
const scrolled = ref(false)

const contextTitle = computed(() => (route.meta.focusTitle as string) || (route.meta.title as string) || '学习')
const contextSub = computed(() => (route.meta.focusSubtitle as string) || '')

function focusMain() {
  mainRef.value?.focus({ preventScroll: true })
}

function isInternalPath(path: unknown): path is string {
  return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//')
}

function fallbackPath() {
  if (route.name === 'TutorSession' && typeof route.params.id === 'string')
    return `/my-courses/${route.params.id}/space`
  if (route.name === 'ExamTake' || route.name === 'ExamLearning' || route.name === 'ExamResult') return '/exams'
  return '/my-courses'
}

function handleBack() {
  const back = window.history.state?.back
  if (isInternalPath(back)) {
    router.back()
    return
  }
  void router.push(fallbackPath())
}

function onScroll() {
  scrolled.value = window.scrollY > 4
}

watch(
  () => route.path,
  async () => {
    await nextTick()
    focusMain()
  },
  { flush: 'post' },
)

onMounted(() => {
  if (document.activeElement === document.body) focusMain()
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <div class="focus-shell lt" data-appearance="dark">
    <a class="skip-link" href="#focus-main" @click.prevent="focusMain">跳到主要内容</a>
    <header class="bar" :data-scrolled="scrolled || undefined">
      <div class="bar-inner">
        <LtIconButton class="focus-back" label="返回" @click="handleBack">
          <ChevronLeft aria-hidden="true" />
        </LtIconButton>
        <div class="context">
          <strong class="context-title">{{ contextTitle }}</strong>
          <span v-if="contextSub" class="context-sub">{{ contextSub }}</span>
        </div>
        <div class="actions">
          <ShellProgress
            v-if="gamification.summary && route.name !== 'ExamTake'"
            :summary="gamification.summary"
            compact
          />
          <slot name="actions" />
        </div>
      </div>
    </header>

    <main id="focus-main" ref="mainRef" class="main" tabindex="-1">
      <div class="content" :class="{ 'is-narrow': narrow && route.meta.focusWidth !== 'wide' }">
        <router-view v-slot="{ Component }">
          <component :is="Component" :key="route.path" />
        </router-view>
      </div>
    </main>
  </div>
</template>

<style scoped>
.focus-shell {
  min-height: 100vh;
  background: var(--lt-canvas);
}

.bar {
  position: sticky;
  z-index: 20;
  top: 0;
  box-shadow: 0 0.5px 0 transparent;
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    box-shadow var(--lt-dur-fade) var(--lt-ease-fade);
}

.bar[data-scrolled] {
  background: var(--lt-glass);
  box-shadow: 0 0.5px 0 var(--lt-separator);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  backdrop-filter: saturate(180%) blur(20px);
}

.bar-inner {
  display: flex;
  align-items: center;
  gap: var(--lt-space-3);
  height: 56px;
  padding: 0 var(--lt-space-5);
}

.context {
  display: flex;
  flex: 1;
  align-items: baseline;
  gap: var(--lt-space-3);
  min-width: 0;
}

.context-title {
  overflow: hidden;
  font-size: var(--lt-text-headline);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.context-sub {
  overflow: hidden;
  color: var(--lt-label-3);
  font-size: var(--lt-text-footnote);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: var(--lt-space-2);
}

.main {
  outline: none;
}

/* 内容区仍是旧页面：保留旧外壳的宽度与内边距，迁移到新基线后去掉。 */
.content {
  width: min(100%, var(--lp-container-max));
  margin: 0 auto;
  padding: var(--lp-space-6) var(--lp-content-gutter) var(--lp-space-12);
}

.content.is-narrow {
  width: min(100%, var(--lp-container-narrow));
}

.skip-link {
  position: fixed;
  z-index: 30;
  top: var(--lt-space-2);
  left: var(--lt-space-2);
  padding: var(--lt-space-2) var(--lt-space-4);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-accent-fill);
  color: var(--lt-on-accent);
  font-size: var(--lt-text-subhead);
  transform: translateY(-150%);
}

.skip-link:focus-visible {
  transform: translateY(0);
}
</style>
