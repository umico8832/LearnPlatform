<template>
  <div class="app-layout">
    <div v-if="isMobile && sidebarOpen" class="sidebar-overlay" @click="sidebarOpen = false" />

    <aside class="app-sidebar" :class="{ 'mobile-open': isMobile && sidebarOpen }">
      <div class="sidebar-inner">
        <router-link to="/my-courses" class="brand" aria-label="LearnPlatform 首页">
          <span class="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none">
              <path
                d="M4 19.5V5.5a1.5 1.5 0 0 1 1.5-1.5H18a1.5 1.5 0 0 1 1.5 1.5v11a1.5 1.5 0 0 1-1.5 1.5H7l-3 1.5Z"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linejoin="round"
              />
              <path d="M8 9h8M8 12.5h5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
          </span>
          <span class="brand-copy">
            <strong>LearnPlatform</strong>
            <small>安静的数字教材</small>
          </span>
        </router-link>

        <nav class="nav-primary" aria-label="主导航">
          <router-link to="/my-courses" class="nav-item" :class="{ 'is-active': isActive('/my-courses') }">
            <el-icon :size="17"><Collection /></el-icon>
            <span>我的课程</span>
          </router-link>
          <router-link to="/courses" class="nav-item" :class="{ 'is-active': isActive('/courses') }">
            <el-icon :size="17"><Reading /></el-icon>
            <span>课程库</span>
          </router-link>
        </nav>

        <div class="sidebar-divider" />

        <button type="button" class="sidebar-search" @click="openSearch">
          <el-icon :size="15"><Search /></el-icon>
          <span>搜索内容</span>
          <kbd>⌘K</kbd>
        </button>

        <div class="sidebar-bottom">
          <a v-if="isAdmin" class="admin-entry" href="/admin/" aria-label="进入管理系统">
            <el-icon :size="15"><DataAnalysis /></el-icon>
            <span>进入管理系统</span>
          </a>
          <el-dropdown trigger="click" @command="handleCommand">
            <button type="button" class="user-entry" aria-label="用户菜单">
              <el-avatar :size="30" :src="userInfo?.avatar || undefined" class="user-avatar">
                {{ avatarText }}
              </el-avatar>
              <span class="user-copy">
                <strong>{{ userInfo?.nickname || userInfo?.username || '用户' }}</strong>
                <small>{{ isAdmin ? '管理员' : '学习者' }}</small>
              </span>
              <el-icon :size="13" class="user-chevron"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </aside>

    <div class="app-content-shell">
      <header class="app-header">
        <div class="header-left">
          <button
            v-if="isMobile"
            type="button"
            class="hamburger"
            :aria-label="sidebarOpen ? '关闭导航' : '打开导航'"
            :aria-expanded="sidebarOpen"
            @click="sidebarOpen = !sidebarOpen"
          >
            <el-icon :size="18"><component :is="sidebarOpen ? Fold : Expand" /></el-icon>
          </button>
        </div>
        <div class="header-right">
          <button class="header-search-trigger" type="button" @click="openSearch">
            <el-icon :size="16"><Search /></el-icon>
            <span v-if="!isMobile" class="search-trigger-text">搜索题目、课程、知识点</span>
            <kbd v-if="!isMobile">⌘K</kbd>
          </button>
          <el-dropdown
            v-if="isAdmin"
            trigger="click"
            popper-class="ops-alert-dropdown"
            @visible-change="handleAlertDropdownVisible"
          >
            <button class="header-icon-button" type="button" aria-label="AI 运营提醒">
              <el-badge :value="openAlertCount" :hidden="openAlertCount === 0" :max="99" type="danger">
                <el-icon :size="17"><Bell /></el-icon>
              </el-badge>
            </button>
            <template #dropdown>
              <div class="ops-alert-panel">
                <div class="ops-alert-panel-header">
                  <strong>AI 运营提醒</strong>
                  <el-button link type="primary" :loading="alertsLoading" @click.stop="fetchOpenAlerts">
                    刷新
                  </el-button>
                </div>
                <div v-if="openAlerts.length" class="ops-alert-list">
                  <div v-for="alert in openAlerts" :key="alert.id || alert.type" class="ops-alert-item">
                    <div>
                      <div class="ops-alert-title">
                        <el-tag size="small" :type="alert.level === 'WARNING' ? 'warning' : 'info'" effect="light">
                          {{ alert.level === 'WARNING' ? '告警' : '提示' }}
                        </el-tag>
                        <span>{{ alert.type }}</span>
                      </div>
                      <p>{{ alert.message }}</p>
                      <small v-if="alert.periodStart && alert.periodEnd">
                        {{ alert.periodStart }} 至 {{ alert.periodEnd }}
                      </small>
                    </div>
                    <el-button
                      v-if="alert.id"
                      size="small"
                      text
                      type="primary"
                      :loading="acknowledgingAlertId === alert.id"
                      @click.stop="handleAcknowledgeOpenAlert(alert.id)"
                    >
                      确认
                    </el-button>
                  </div>
                </div>
                <el-empty v-else description="暂无未确认提醒" :image-size="48" />
              </div>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" :key="route.fullPath" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>

  <GlobalSearchDialog ref="searchDialogRef" />
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { errorMessage } from '@/utils/errors'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { acknowledgeAiUsageAlert, getAiUsageAlerts, type AiUsageAlert } from '@/api/aiUsage'
import { ElMessage } from 'element-plus'
import { ArrowDown, Bell, Collection, DataAnalysis, Expand, Fold, Reading, Search } from '@element-plus/icons-vue'
import GlobalSearchDialog from '@/components/GlobalSearchDialog.vue'
import { useResponsiveSidebar } from './useResponsiveSidebar'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const userInfo = computed(() => userStore.userInfo)
const isAdmin = computed(() => userStore.userInfo?.role === 'ADMIN')
const avatarText = computed(() => userInfo.value?.nickname?.charAt(0) || userInfo.value?.username?.charAt(0) || 'U')
const openAlerts = ref<AiUsageAlert[]>([])
const alertsLoading = ref(false)
const acknowledgingAlertId = ref<number | null>(null)
const openAlertCount = computed(() => openAlerts.value.length)
const { isMobile, sidebarOpen } = useResponsiveSidebar()

/** 一级入口高亮：我的课程 与 课程库 精确匹配其子路由前缀。 */
function isActive(prefix: string) {
  if (prefix === '/my-courses') {
    return route.path === '/my-courses' || route.path.startsWith('/my-courses/')
  }
  return route.path === '/courses' || route.path.startsWith('/courses/')
}

const searchDialogRef = ref<InstanceType<typeof GlobalSearchDialog>>()
function openSearch() {
  searchDialogRef.value?.open()
}

async function fetchOpenAlerts() {
  if (!isAdmin.value || alertsLoading.value) return
  alertsLoading.value = true
  try {
    const response = await getAiUsageAlerts(20)
    openAlerts.value = response.data || []
  } catch (error) {
    console.error('Failed to fetch AI usage alerts', error)
  } finally {
    alertsLoading.value = false
  }
}

function handleAlertDropdownVisible(visible: boolean) {
  if (visible) {
    fetchOpenAlerts()
  }
}

async function handleAcknowledgeOpenAlert(id: number) {
  acknowledgingAlertId.value = id
  try {
    await acknowledgeAiUsageAlert(id)
    openAlerts.value = openAlerts.value.filter((alert) => alert.id !== id)
    ElMessage.success('已确认 AI 运营提醒')
  } catch (error) {
    console.error('Failed to acknowledge AI usage alert', error)
    ElMessage.error(errorMessage(error, '确认提醒失败'))
  } finally {
    acknowledgingAlertId.value = null
  }
}

onMounted(() => {
  fetchOpenAlerts()
})

watch(isAdmin, (value) => {
  if (value) {
    fetchOpenAlerts()
  } else {
    openAlerts.value = []
  }
})

function handleCommand(command: string) {
  if (command === 'logout') {
    userStore.clearLoginInfo()
    router.push('/login')
  } else if (command === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped>
.app-layout {
  display: grid;
  grid-template-columns: var(--lp-sidebar-width) minmax(0, 1fr);
  min-height: 100vh;
  background: var(--lp-bg);
  color: var(--lp-text);
}

/* ---------------- Sidebar ---------------- */
.app-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  background: var(--lp-surface-subtle);
  border-right: var(--lp-border-hairline);
}

.sidebar-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: var(--lp-space-4) var(--lp-space-3);
  overflow-y: auto;
  overflow-x: hidden;
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  padding: var(--lp-space-2) var(--lp-space-2) var(--lp-space-4);
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border-radius: var(--lp-radius-md);
  background: var(--lp-primary);
  color: var(--lp-paper-0);
}

.brand-copy {
  display: grid;
  gap: 1px;
  min-width: 0;
  line-height: 1.15;
}

.brand-copy strong {
  color: var(--lp-text);
  font-size: var(--lp-text-md);
  font-weight: var(--lp-weight-heavy);
  letter-spacing: var(--lp-tracking-tight);
  white-space: nowrap;
}

.brand-copy small {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  white-space: nowrap;
}

.nav-primary {
  display: grid;
  gap: 2px;
  margin-top: var(--lp-space-2);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  height: 38px;
  padding: 0 var(--lp-space-3);
  border-radius: var(--lp-radius-sm);
  color: var(--lp-ink-600);
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-medium);
  transition:
    background-color var(--lp-duration-fast) var(--lp-ease-out),
    color var(--lp-duration-fast) var(--lp-ease-out);
}

.nav-item:hover {
  background: var(--lp-surface-inset);
  color: var(--lp-text);
}

.nav-item.is-active {
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
  font-weight: var(--lp-weight-semibold);
}

.sidebar-divider {
  height: 1px;
  margin: var(--lp-space-4) var(--lp-space-2);
  background: var(--lp-border);
}

.sidebar-search {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  height: 36px;
  margin: 0 var(--lp-space-1);
  padding: 0 var(--lp-space-3);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-sm);
  background: var(--lp-surface);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
  cursor: pointer;
  transition:
    border-color var(--lp-duration-fast) var(--lp-ease-out),
    color var(--lp-duration-fast) var(--lp-ease-out);
}

.sidebar-search:hover {
  border-color: var(--lp-border-strong);
  color: var(--lp-text-secondary);
}

.sidebar-search span {
  flex: 1;
  text-align: left;
}

.sidebar-search kbd,
.header-search-trigger kbd {
  padding: 1px 5px;
  border: 1px solid var(--lp-border);
  border-radius: var(--lp-radius-xs);
  background: var(--lp-surface-subtle);
  color: var(--lp-text-muted);
  font-size: 11px;
  font-family: inherit;
}

.sidebar-bottom {
  display: grid;
  gap: var(--lp-space-2);
  margin-top: auto;
  padding-top: var(--lp-space-4);
}

.admin-entry {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  height: 34px;
  padding: 0 var(--lp-space-3);
  border-radius: var(--lp-radius-sm);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  transition: background-color var(--lp-duration-fast) var(--lp-ease-out);
}

.admin-entry:hover {
  background: var(--lp-surface-inset);
  color: var(--lp-text);
}

.user-entry {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  width: 100%;
  padding: var(--lp-space-2) var(--lp-space-2);
  border: 0;
  border-radius: var(--lp-radius-sm);
  background: transparent;
  color: var(--lp-text);
  cursor: pointer;
  text-align: left;
  transition: background-color var(--lp-duration-fast) var(--lp-ease-out);
}

.user-entry:hover {
  background: var(--lp-surface-inset);
}

.user-avatar {
  flex: 0 0 auto;
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
  font-weight: var(--lp-weight-bold);
}

.user-copy {
  display: grid;
  gap: 1px;
  min-width: 0;
  flex: 1;
  line-height: 1.15;
}

.user-copy strong {
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-copy small {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}

.user-chevron {
  color: var(--lp-text-muted);
}

/* ---------------- Header ---------------- */
.app-content-shell {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.app-header {
  position: sticky;
  top: 0;
  z-index: var(--lp-z-header);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-4);
  height: var(--lp-header-height);
  padding: 0 var(--lp-content-gutter);
  background: rgba(253, 253, 251, 0.86);
  border-bottom: var(--lp-border-hairline);
  backdrop-filter: blur(12px);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
}

.header-left {
  gap: var(--lp-space-3);
  min-width: 0;
}

.header-right {
  gap: var(--lp-space-3);
  flex-shrink: 0;
}

.hamburger {
  width: 36px;
  height: 36px;
  display: inline-grid;
  place-items: center;
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-sm);
  background: var(--lp-surface);
  color: var(--lp-text);
  cursor: pointer;
}

.header-search-trigger {
  min-width: 236px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  gap: var(--lp-space-2);
  padding: 0 var(--lp-space-3);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-sm);
  background: var(--lp-surface);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
  cursor: pointer;
  transition:
    border-color var(--lp-duration-fast) var(--lp-ease-out),
    box-shadow var(--lp-duration-fast) var(--lp-ease-out);
}

.header-search-trigger:hover {
  border-color: var(--lp-border-strong);
  box-shadow: var(--lp-shadow-xs);
}

.search-trigger-text {
  flex: 1;
  text-align: left;
}

.header-icon-button {
  width: 34px;
  height: 34px;
  display: inline-grid;
  place-items: center;
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-sm);
  background: var(--lp-surface);
  color: var(--lp-text-secondary);
  cursor: pointer;
  transition:
    border-color var(--lp-duration-fast) var(--lp-ease-out),
    color var(--lp-duration-fast) var(--lp-ease-out);
}

.header-icon-button:hover {
  border-color: var(--lp-border-strong);
  color: var(--lp-primary);
}

.ops-alert-panel {
  width: min(360px, calc(100vw - 24px));
  padding: var(--lp-space-3);
}

.ops-alert-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lp-space-3);
  padding: 2px 2px var(--lp-space-3);
  border-bottom: var(--lp-border-hairline);
}

.ops-alert-panel-header strong {
  color: var(--lp-text);
  font-size: var(--lp-text-base);
}

.ops-alert-list {
  max-height: 360px;
  overflow-y: auto;
  padding-top: var(--lp-space-2);
}

.ops-alert-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--lp-space-3);
  padding: var(--lp-space-3) 2px;
  border-bottom: var(--lp-border-hairline);
}

.ops-alert-item:last-child {
  border-bottom: 0;
}

.ops-alert-title {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  color: var(--lp-text);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-bold);
}

.ops-alert-item p {
  margin: 6px 0 5px;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: 1.45;
}

.ops-alert-item small {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}

/* ---------------- Main ---------------- */
.app-main {
  flex: 1;
  min-width: 0;
  padding: var(--lp-space-6) var(--lp-content-gutter) var(--lp-space-12);
}

/* 页面切换过渡：克制淡入 */
.page-fade-enter-active,
.page-fade-leave-active {
  transition:
    opacity var(--lp-duration-normal) var(--lp-ease-out),
    transform var(--lp-duration-normal) var(--lp-ease-out);
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.page-fade-leave-to {
  opacity: 0;
}

/* ---------------- Mobile ---------------- */
@media (max-width: 767px) {
  .app-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .app-sidebar {
    position: fixed;
    top: 0;
    left: 0;
    z-index: var(--lp-z-modal);
    width: 260px;
    transform: translateX(-100%);
    transition: transform var(--lp-duration-normal) var(--lp-ease-out);
    box-shadow: var(--lp-shadow-lg);
  }

  .app-sidebar.mobile-open {
    transform: translateX(0);
  }

  .sidebar-overlay {
    position: fixed;
    inset: 0;
    z-index: calc(var(--lp-z-modal) - 1);
    background: rgba(29, 29, 27, 0.42);
  }

  .app-header {
    height: 54px;
    padding: 0 var(--lp-content-gutter);
  }

  .header-search-trigger {
    min-width: 36px;
    width: 36px;
    justify-content: center;
    padding: 0;
  }

  .header-search-trigger span,
  .header-search-trigger kbd {
    display: none;
  }

  .app-main {
    padding: var(--lp-space-4) var(--lp-content-gutter) var(--lp-space-10);
  }
}
</style>
