<template>
  <el-container class="app-layout">
    <div
      v-if="isMobile && sidebarOpen"
      class="sidebar-overlay"
      @click="sidebarOpen = false"
    />

    <el-aside
      :width="isMobile ? '0px' : '248px'"
      :class="['app-sidebar', { 'mobile-open': isMobile && sidebarOpen }]"
    >
      <div class="sidebar-inner">
        <div class="brand">
          <div class="brand-mark">AI</div>
          <div class="brand-copy">
            <strong>学习工作台</strong>
            <span>题库 · 复习 · 诊断</span>
          </div>
        </div>

        <el-menu
          :default-active="activeMenu"
          router
          class="app-menu"
          @select="handleMenuSelect"
        >
          <template v-for="section in visibleSections" :key="section.label">
            <div class="menu-section-label">{{ section.label }}</div>
            <el-menu-item
              v-for="item in section.items"
              :key="item.path"
              :index="item.path"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </div>
    </el-aside>

    <el-container class="app-content-shell">
      <el-header class="app-header">
        <div class="header-left">
          <el-button
            v-if="isMobile"
            class="hamburger"
            text
            :icon="sidebarOpen ? Fold : Expand"
            @click="sidebarOpen = !sidebarOpen"
          />
          <div class="page-context">
            <h1>{{ pageTitle }}</h1>
            <p v-if="!isMobile">{{ pageDescription }}</p>
          </div>
        </div>

        <div class="header-right">
          <button class="header-search-trigger" type="button" @click="openSearch">
            <el-icon :size="18"><Search /></el-icon>
            <span v-if="!isMobile" class="search-trigger-text">搜索题目、课程、知识点</span>
            <kbd v-if="!isMobile" class="search-trigger-kbd">⌘K</kbd>
          </button>

          <el-dropdown
            v-if="isAdmin"
            trigger="click"
            popper-class="ops-alert-dropdown"
            @visible-change="handleAlertDropdownVisible"
          >
            <button class="header-icon-button" type="button" aria-label="AI 运营提醒">
              <el-badge
                :value="openAlertCount"
                :hidden="openAlertCount === 0"
                :max="99"
                type="danger"
              >
                <el-icon :size="18"><Bell /></el-icon>
              </el-badge>
            </button>
            <template #dropdown>
              <div class="ops-alert-panel">
                <div class="ops-alert-panel-header">
                  <strong>AI 运营提醒</strong>
                  <el-button
                    link
                    type="primary"
                    :loading="alertsLoading"
                    @click.stop="fetchOpenAlerts"
                  >
                    刷新
                  </el-button>
                </div>
                <div v-if="openAlerts.length" class="ops-alert-list">
                  <div
                    v-for="alert in openAlerts"
                    :key="alert.id || alert.type"
                    class="ops-alert-item"
                  >
                    <div>
                      <div class="ops-alert-title">
                        <el-tag
                          size="small"
                          :type="alert.level === 'WARNING' ? 'warning' : 'info'"
                          effect="light"
                        >
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
                <el-empty
                  v-else
                  description="暂无未确认提醒"
                  :image-size="48"
                />
              </div>
            </template>
          </el-dropdown>

          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="34" :src="userInfo?.avatar || undefined">
                {{ avatarText }}
              </el-avatar>
              <span v-if="!isMobile" class="user-copy">
                <strong>{{ userInfo?.nickname || userInfo?.username || '用户' }}</strong>
                <small>{{ isAdmin ? '管理员' : '学习者' }}</small>
              </span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <GlobalSearchDialog ref="searchDialogRef" />
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import type { Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { acknowledgeAiUsageAlert, getAiUsageAlerts, type AiUsageAlert } from '@/api/aiUsage'
import { ElMessage } from 'element-plus'
import {
  Bell,
  HomeFilled,
  Reading,
  Collection,
  Notebook,
  EditPen,
  Promotion,
  Clock,
  WarningFilled,
  Trophy,
  MagicStick,
  DataAnalysis,
  StarFilled,
  UserFilled,
  Fold,
  Expand,
  DataLine,
  Guide,
  Connection,
  TrendCharts,
  Upload,
  Search,
  Monitor,
  Timer,
} from '@element-plus/icons-vue'
import GlobalSearchDialog from '@/components/GlobalSearchDialog.vue'

interface NavItem {
  path: string
  label: string
  icon: Component
  adminOnly?: boolean
}

interface NavSection {
  label: string
  items: NavItem[]
}

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

const navSections: NavSection[] = [
  {
    label: '学习中心',
    items: [
      { path: '/', label: '学习首页', icon: HomeFilled },
      { path: '/courses', label: '课程列表', icon: Reading },
      { path: '/questions', label: '题库浏览', icon: EditPen },
      { path: '/favorites', label: '我的收藏', icon: StarFilled },
    ],
  },
  {
    label: '练习复习',
    items: [
      { path: '/practice', label: '刷题练习', icon: Promotion },
      { path: '/practice/records', label: '刷题记录', icon: Clock },
      { path: '/wrong-questions', label: '错题本', icon: WarningFilled },
      { path: '/review', label: '智能复习', icon: Timer },
    ],
  },
  {
    label: '考试测评',
    items: [
      { path: '/exams', label: '考试测评', icon: Trophy },
      { path: '/learning-report', label: '学习报告', icon: DataLine },
    ],
  },
  {
    label: 'AI 与诊断',
    items: [
      { path: '/learning-diagnosis', label: '学习诊断', icon: TrendCharts },
      { path: '/learning-path', label: '学习路径', icon: Guide },
      { path: '/knowledge-graph', label: '知识图谱', icon: Connection },
      { path: '/ai/review', label: 'AI 复习建议', icon: MagicStick },
    ],
  },
  {
    label: '内容共建',
    items: [
      { path: '/submit', label: '题目投稿', icon: Upload },
    ],
  },
  {
    label: '管理后台',
    items: [
      { path: '/admin', label: '平台总览', icon: DataAnalysis, adminOnly: true },
      { path: '/admin/courses', label: '课程管理', icon: Collection, adminOnly: true },
      { path: '/admin/knowledge-points', label: '知识点管理', icon: Notebook, adminOnly: true },
      { path: '/admin/questions', label: '题目管理', icon: EditPen, adminOnly: true },
      { path: '/admin/exams', label: '试卷管理', icon: Trophy, adminOnly: true },
      { path: '/admin/users', label: '用户管理', icon: UserFilled, adminOnly: true },
      { path: '/admin/submissions', label: '投稿管理', icon: Upload, adminOnly: true },
      { path: '/admin/ai-usage', label: 'AI 调用分析', icon: Monitor, adminOnly: true },
    ],
  },
]

const visibleSections = computed(() =>
  navSections
    .map(section => ({
      ...section,
      items: section.items.filter(item => !item.adminOnly || isAdmin.value),
    }))
    .filter(section => section.items.length > 0),
)

const flatNavItems = computed(() => visibleSections.value.flatMap(section => section.items))
const activeMenu = computed(() => {
  const exact = flatNavItems.value.find(item => item.path === route.path)
  if (exact) return exact.path
  const matched = [...flatNavItems.value]
    .filter(item => item.path !== '/' && route.path.startsWith(item.path))
    .sort((a, b) => b.path.length - a.path.length)[0]
  return matched?.path || '/'
})

const pageTitle = computed(() => (route.meta.title as string) || '学习工作台')
const pageDescriptions: Record<string, string> = {
  '/': '查看今日计划、学习指标和下一步任务。',
  '/courses': '按课程组织知识点，找到适合当前阶段的学习内容。',
  '/questions': '浏览题库并结合课程、知识点、题型快速筛选。',
  '/practice': '选择练习任务，完成即时判分与解析复盘。',
  '/practice/records': '回看练习历史，定位近期薄弱项。',
  '/wrong-questions': '集中处理反复出错的题目，逐步提升掌握度。',
  '/favorites': '沉淀值得反复查看的题目与解析。',
  '/review': '按间隔重复计划安排今天的复习任务。',
  '/exams': '参加模拟考试，检验阶段性学习效果。',
  '/learning-report': '用数据复盘近期学习表现。',
  '/learning-diagnosis': '结合答题记录生成学习诊断与建议。',
  '/learning-path': '查看个性化学习路径和推荐顺序。',
  '/knowledge-graph': '从知识结构视角理解课程关联。',
  '/ai/review': '让 AI 汇总复习重点与补强建议。',
  '/submit': '提交高质量题目，参与题库共建。',
  '/admin': '查看平台运营概况与待处理事项。',
  '/admin/courses': '维护课程基础信息和展示顺序。',
  '/admin/knowledge-points': '管理知识点层级与课程归属。',
  '/admin/questions': '维护题目、答案、解析和知识点关系。',
  '/admin/exams': '创建试卷并管理发布状态。',
  '/admin/users': '管理用户、角色与 AI 日配额。',
  '/admin/submissions': '审核用户投稿并入库。',
  '/admin/ai-usage': '追踪 AI 调用、成本、失败率和异常提醒。',
}
const pageDescription = computed(() => pageDescriptions[activeMenu.value] || '围绕当前任务继续推进学习。')

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
    openAlerts.value = openAlerts.value.filter(alert => alert.id !== id)
    ElMessage.success('已确认 AI 运营提醒')
  } catch (error: any) {
    console.error('Failed to acknowledge AI usage alert', error)
    ElMessage.error(error?.message || '确认提醒失败')
  } finally {
    acknowledgingAlertId.value = null
  }
}

const MOBILE_BREAKPOINT = 768
const isMobile = ref(false)
const sidebarOpen = ref(false)

function checkMobile() {
  isMobile.value = window.innerWidth < MOBILE_BREAKPOINT
  if (!isMobile.value) {
    sidebarOpen.value = false
  }
}

function handleMenuSelect() {
  if (isMobile.value) {
    sidebarOpen.value = false
  }
}

onMounted(() => {
  checkMobile()
  fetchOpenAlerts()
  window.addEventListener('resize', checkMobile)
})

watch(isAdmin, (value) => {
  if (value) {
    fetchOpenAlerts()
  } else {
    openAlerts.value = []
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', checkMobile)
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
  min-height: 100vh;
  background: var(--lp-bg);
  color: var(--lp-text);
}

.app-sidebar {
  background: #101820;
  overflow: hidden;
  transition: width 0.25s ease;
}

.sidebar-inner {
  width: 248px;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  overflow-x: hidden;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
}

.sidebar-inner::-webkit-scrollbar {
  width: 4px;
}

.sidebar-inner::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.22);
  border-radius: 999px;
}

.brand {
  min-height: 76px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #f0c75e;
  color: #14213d;
  font-size: 15px;
  font-weight: 800;
}

.brand-copy {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.brand-copy strong {
  font-size: 16px;
  line-height: 1.15;
}

.brand-copy span {
  color: rgba(255, 255, 255, 0.58);
  font-size: 12px;
}

.app-menu {
  flex: 1;
  padding: 12px 10px 18px;
  border-right: 0;
  background: transparent;
}

.menu-section-label {
  margin: 14px 10px 6px;
  color: rgba(255, 255, 255, 0.42);
  font-size: 12px;
  font-weight: 700;
}

.app-menu :deep(.el-menu-item) {
  height: 40px;
  margin: 2px 0;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.74);
  line-height: 40px;
}

.app-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}

.app-menu :deep(.el-menu-item.is-active) {
  background: #e9f4ff;
  color: #0f5ea8;
  font-weight: 700;
}

.app-content-shell {
  min-width: 0;
}

.app-header {
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 0 28px;
  background: rgba(255, 255, 255, 0.9);
  border-bottom: 1px solid var(--lp-border);
  backdrop-filter: blur(16px);
}

.header-left,
.header-right,
.user-info {
  display: flex;
  align-items: center;
}

.header-left {
  gap: 12px;
  min-width: 0;
}

.header-right {
  gap: 14px;
  flex-shrink: 0;
}

.hamburger {
  color: var(--lp-text);
}

.page-context {
  min-width: 0;
}

.page-context h1 {
  margin: 0;
  color: var(--lp-text);
  font-size: 18px;
  font-weight: 800;
  line-height: 1.25;
}

.page-context p {
  margin: 3px 0 0;
  color: var(--lp-text-muted);
  font-size: 13px;
  line-height: 1.35;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-search-trigger {
  min-width: 256px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 10px 0 13px;
  border: 1px solid var(--lp-border);
  border-radius: 8px;
  background: #f8fafc;
  color: var(--lp-text-muted);
  cursor: pointer;
  font-size: 13px;
  transition: border-color 0.18s ease, background-color 0.18s ease, box-shadow 0.18s ease;
}

.header-search-trigger:hover {
  background: #fff;
  border-color: #9bb7d0;
  box-shadow: 0 8px 22px rgba(34, 53, 74, 0.08);
}

.search-trigger-text {
  flex: 1;
  text-align: left;
}

.search-trigger-kbd {
  padding: 2px 6px;
  border: 1px solid #d6dee8;
  border-radius: 5px;
  background: #fff;
  color: #6b7c8f;
  font-size: 11px;
  font-family: inherit;
}

.header-icon-button {
  width: 38px;
  height: 38px;
  display: inline-grid;
  place-items: center;
  border: 1px solid var(--lp-border);
  border-radius: 8px;
  background: #fff;
  color: var(--lp-text-secondary);
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, color 0.18s ease;
}

.header-icon-button:hover {
  border-color: #d19a2c;
  color: #9b6a09;
  box-shadow: 0 8px 22px rgba(157, 111, 24, 0.1);
}

.ops-alert-panel {
  width: min(360px, calc(100vw - 24px));
  padding: 12px;
}

.ops-alert-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 2px 2px 10px;
  border-bottom: 1px solid var(--lp-border);
}

.ops-alert-panel-header strong {
  color: var(--lp-text);
  font-size: 14px;
}

.ops-alert-list {
  max-height: 360px;
  overflow-y: auto;
  padding-top: 8px;
}

.ops-alert-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  padding: 10px 2px;
  border-bottom: 1px solid #eef1f5;
}

.ops-alert-item:last-child {
  border-bottom: 0;
}

.ops-alert-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--lp-text);
  font-size: 13px;
  font-weight: 700;
}

.ops-alert-item p {
  margin: 6px 0 5px;
  color: var(--lp-text-secondary);
  font-size: 13px;
  line-height: 1.45;
}

.ops-alert-item small {
  color: var(--lp-text-muted);
  font-size: 12px;
}

.user-info {
  gap: 9px;
  cursor: pointer;
}

.user-copy {
  display: grid;
  gap: 2px;
  color: var(--lp-text);
  line-height: 1.1;
}

.user-copy strong {
  font-size: 14px;
  font-weight: 700;
}

.user-copy small {
  color: var(--lp-text-muted);
  font-size: 12px;
}

.app-main {
  min-width: 0;
  padding: 24px;
  overflow-y: auto;
  background:
    linear-gradient(180deg, rgba(241, 246, 249, 0.86), rgba(246, 248, 251, 1) 320px),
    var(--lp-bg);
}

@media (max-width: 767px) {
  .app-sidebar {
    position: fixed !important;
    top: 0;
    left: 0;
    z-index: 2000;
    height: 100vh;
    width: 0 !important;
  }

  .app-sidebar.mobile-open {
    width: 248px !important;
  }

  .sidebar-overlay {
    position: fixed;
    inset: 0;
    z-index: 1999;
    background: rgba(9, 21, 34, 0.48);
  }

  .app-header {
    height: 58px;
    padding: 0 12px;
  }

  .page-context h1 {
    font-size: 16px;
  }

  .header-right {
    gap: 10px;
  }

  .header-search-trigger {
    min-width: 40px;
    width: 40px;
    height: 36px;
    justify-content: center;
    padding: 0;
  }

  .header-icon-button {
    width: 36px;
    height: 36px;
  }

  .app-main {
    padding: 12px;
  }
}
</style>
