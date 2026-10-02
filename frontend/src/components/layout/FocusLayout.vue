<template>
  <div class="focus-layout">
    <a class="skip-link" href="#focus-main" @click.prevent="focusMain">跳到主要内容</a>
    <header class="focus-topbar">
      <div class="focus-topbar-left">
        <button type="button" class="focus-back" @click="handleBack">
          <el-icon :size="16"><ArrowLeft /></el-icon>
          <span>返回</span>
        </button>
        <span class="focus-topbar-separator" aria-hidden="true" />
        <div class="focus-context">
          <strong class="focus-context-title">{{ contextTitle }}</strong>
          <span v-if="contextSub" class="focus-context-sub">{{ contextSub }}</span>
        </div>
      </div>
      <div class="focus-topbar-right">
        <LearningProgressHeader
          v-if="gamification.summary && route.name !== 'ExamTake'"
          :summary="gamification.summary"
          compact
        />
        <slot name="actions" />
      </div>
    </header>

    <main id="focus-main" ref="mainRef" class="focus-main" tabindex="-1">
      <div class="focus-content" :class="{ 'is-narrow': narrow }">
        <router-view v-slot="{ Component }">
          <component :is="Component" :key="route.path" />
        </router-view>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useGamificationStore } from '@/stores/gamification'
import LearningProgressHeader from '@/components/gamification/LearningProgressHeader.vue'

/**
 * Focus Layout：沉浸式学习布局。
 * 用于 Tutor 教学、限时考试、试卷学习、考试结果等页面——
 * 弱化全局导航，让学习内容成为界面中心。
 */
withDefaults(defineProps<{ narrow?: boolean }>(), { narrow: true })

const route = useRoute()
const router = useRouter()
const gamification = useGamificationStore()
const mainRef = ref<HTMLElement>()

const contextTitle = computed(() => (route.meta.focusTitle as string) || (route.meta.title as string) || '学习')
const contextSub = computed(() => (route.meta.focusSubtitle as string) || '')

function focusMain() {
  mainRef.value?.focus({ preventScroll: true })
}

onMounted(() => {
  if (document.activeElement === document.body) focusMain()
})

function isInternalPath(path: unknown): path is string {
  return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//')
}

function fallbackPath() {
  if (route.name === 'TutorSession' && typeof route.params.id === 'string') return `/my-courses/${route.params.id}`
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

watch(
  () => route.path,
  async () => {
    await nextTick()
    focusMain()
  },
  { flush: 'post' },
)
</script>

<style scoped>
.focus-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--lp-bg);
}

.focus-topbar {
  position: sticky;
  top: 0;
  z-index: var(--lp-z-header);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-4);
  height: 56px;
  padding: 0 var(--lp-content-gutter);
  background: var(--lp-surface-glass);
  border-bottom: var(--lp-border-hairline);
  backdrop-filter: blur(12px);
}

.focus-topbar-left {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  min-width: 0;
}

.focus-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 var(--lp-space-2);
  border: 0;
  border-radius: var(--lp-radius-sm);
  background: transparent;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  cursor: pointer;
  transition:
    background-color var(--lp-duration-fast) var(--lp-ease-out),
    color var(--lp-duration-fast) var(--lp-ease-out);
}

.focus-back:hover {
  background: var(--lp-surface-inset);
  color: var(--lp-text);
}

.focus-topbar-separator {
  width: 1px;
  height: 18px;
  background: var(--lp-border);
}

.focus-context {
  display: grid;
  gap: 1px;
  min-width: 0;
  line-height: 1.2;
}

.focus-context-title {
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-semibold);
  color: var(--lp-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.focus-context-sub {
  font-size: var(--lp-text-xs);
  color: var(--lp-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.focus-topbar-right {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  flex-shrink: 0;
}

.focus-main {
  flex: 1;
  min-width: 0;
}

.skip-link {
  position: fixed;
  top: var(--lp-space-2);
  left: var(--lp-space-2);
  z-index: var(--lp-z-modal);
  padding: var(--lp-space-2) var(--lp-space-3);
  color: var(--lp-on-primary);
  background: var(--lp-primary);
  border-radius: var(--lp-radius-control);
  transform: translateY(-150%);
}

.skip-link:focus-visible {
  transform: translateY(0);
  outline: var(--lp-focus-width) solid var(--lp-focus-ring);
  outline-offset: var(--lp-focus-offset);
}

.focus-content {
  width: min(100%, var(--lp-container-max));
  margin: 0 auto;
  padding: var(--lp-space-6) var(--lp-content-gutter) var(--lp-space-12);
}

.focus-content.is-narrow {
  width: min(100%, var(--lp-container-narrow));
}

@media (max-width: 767px) {
  .focus-topbar {
    height: 50px;
    padding: 0 var(--lp-content-gutter);
  }

  .focus-context-sub {
    display: none;
  }

  .focus-content {
    padding: var(--lp-space-4) var(--lp-content-gutter) var(--lp-space-8);
  }
}
</style>
