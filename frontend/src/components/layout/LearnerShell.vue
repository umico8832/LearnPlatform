<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Search } from '@lucide/vue'
import { useUserStore } from '@/stores/user'
import { useGamificationStore } from '@/stores/gamification'
import { useLivingTheme } from '@/composables/useLivingTheme'
import GlobalSearchDialog from '@/components/GlobalSearchDialog.vue'
import OpsAlertsSheet from './OpsAlertsSheet.vue'
import ShellNav from './ShellNav.vue'
import ShellProgress from './ShellProgress.vue'
import ShellUserMenu from './ShellUserMenu.vue'
import { useOpsAlerts } from './useOpsAlerts'

/**
 * Learner shell (Phase 28, dark only): one bar for every learner page with the
 * sections, today's learning facts, search and the account menu. Redesigned pages
 * (`meta.living`) share the content frame; pages not yet redesigned keep
 * their old padding and sit on the dark bridge until they are migrated.
 */
useLivingTheme({ dark: true, legacyBridge: true })

const route = useRoute()
const userStore = useUserStore()
const gamification = useGamificationStore()
const isAdmin = computed(() => userStore.userInfo?.role === 'ADMIN')
const ops = useOpsAlerts(isAdmin)
const alertsOpen = ref(false)
const mainRef = ref<HTMLElement>()
const searchDialog = ref<InstanceType<typeof GlobalSearchDialog>>()
const scrolled = ref(false)

const legacy = computed(() => !route.meta.living)

function focusMain() {
  mainRef.value?.focus({ preventScroll: true })
}

function onScroll() {
  scrolled.value = window.scrollY > 4
}

function finishPageLeave(_element: Element, done: () => void) {
  // 立即移除旧页，避免两个页面同时撑高文档并破坏历史滚动位置。
  done()
}

function openAlerts() {
  alertsOpen.value = true
  void ops.refresh()
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
  <div class="learner-shell lt" data-appearance="dark">
    <a class="skip-link" href="#app-main" @click.prevent="focusMain">跳到主要内容</a>
    <header class="bar" :data-scrolled="scrolled || undefined">
      <div class="bar-inner">
        <RouterLink to="/my-courses" class="brand" aria-label="LearnPlatform 我的课程">LearnPlatform</RouterLink>
        <ShellNav />
        <div class="actions">
          <ShellProgress v-if="gamification.summary" class="progress" :summary="gamification.summary" />
          <button type="button" class="search" aria-label="搜索题目、课程、知识点" @click="searchDialog?.open()">
            <Search :size="15" aria-hidden="true" />
            <span class="search-text">搜索</span>
            <kbd class="search-key">⌘K</kbd>
          </button>
          <ShellUserMenu :alert-count="ops.count.value" @alerts="openAlerts" />
        </div>
      </div>
    </header>

    <main id="app-main" ref="mainRef" class="main" :data-legacy="legacy || undefined" tabindex="-1">
      <router-view v-slot="{ Component, route: current }">
        <transition name="lp-content" @leave="finishPageLeave">
          <component :is="Component" :key="current.meta.living ? current.path : current.fullPath" />
        </transition>
      </router-view>
    </main>

    <GlobalSearchDialog ref="searchDialog" />
    <OpsAlertsSheet
      v-if="isAdmin"
      v-model:open="alertsOpen"
      :alerts="ops.alerts.value"
      :loading="ops.loading.value"
      :error="ops.error.value"
      :acknowledge-errors="ops.acknowledgeErrors.value"
      :is-acknowledging="ops.isAcknowledging"
      @refresh="ops.refresh()"
      @acknowledge="ops.acknowledge"
    />
  </div>
</template>

<style scoped>
.learner-shell {
  --shell-bar-height: 60px;

  min-height: 100vh;
  background: var(--lt-canvas);
}

.bar {
  position: sticky;
  z-index: 20;
  top: 0;
  background: transparent;
  box-shadow: 0 0.5px 0 transparent;
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    box-shadow var(--lt-dur-fade) var(--lt-ease-fade);
}

/* 贴在顶部时属于画布；内容滚到下面才变成磨砂玻璃并出现分隔线。 */
.bar[data-scrolled] {
  background: var(--lt-glass);
  box-shadow: 0 0.5px 0 var(--lt-separator);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  backdrop-filter: saturate(180%) blur(20px);
}

.bar-inner {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: var(--lt-space-6);
  max-width: 1280px;
  height: var(--shell-bar-height);
  margin: 0 auto;
  padding: 0 var(--lt-space-7);
}

.brand {
  justify-self: start;
  color: var(--lt-label);
  font-size: var(--lt-text-headline);
  font-weight: 600;
  letter-spacing: -0.02em;
  text-decoration: none;
}

.actions {
  display: flex;
  justify-self: end;
  align-items: center;
  gap: var(--lt-space-2);
}

.search {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: var(--lt-control-md);
  padding: 0 6px 0 12px;
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-3);
  color: var(--lt-label-2);
  font: inherit;
  font-size: var(--lt-text-footnote);
  cursor: pointer;
  transition:
    background-color var(--lt-dur-fade) var(--lt-ease-fade),
    color var(--lt-dur-fade) var(--lt-ease-fade);
}

.search:hover {
  background: var(--lt-fill-2);
  color: var(--lt-label);
}

.search-text {
  margin-right: var(--lt-space-4);
}

.search-key {
  padding: 1px 6px;
  border-radius: 6px;
  background: var(--lt-fill-2);
  color: var(--lt-label-3);
  font-family: var(--lt-font-rounded);
  font-size: var(--lt-text-caption);
}

.main {
  outline: none;
}

.main:not([data-legacy]) {
  max-width: 1280px;
  margin: 0 auto;
  padding: var(--lt-space-7) var(--lt-space-7) var(--lt-space-10);
}

/* 尚未重做的页面：保留旧外壳的内边距与内容宽度，迁移到新基线后去掉。 */
.main[data-legacy] {
  max-width: 1280px;
  margin: 0 auto;
  padding: var(--lp-space-6) var(--lp-content-gutter) var(--lp-space-12);
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

/* 学员端按桌面设计（≥1280）。更窄时只保证不撑出横向滚动：导航可横向滑动，进度与搜索文字收起。 */
@media (max-width: 1100px) {
  .bar-inner {
    grid-template-columns: auto minmax(0, 1fr) auto;
    gap: var(--lt-space-3);
  }

  .progress {
    display: none;
  }
}

@media (max-width: 720px) {
  .bar-inner {
    padding-inline: var(--lt-space-4);
  }
  .main:not([data-legacy]) {
    padding-inline: var(--lt-space-4);
  }

  .search-text,
  .search-key {
    display: none;
  }

  .search {
    padding: 0 8px;
  }
}
</style>
