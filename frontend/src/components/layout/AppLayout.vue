<template>
  <el-container class="app-layout">
    <!-- 移动端遮罩层 -->
    <div
      v-if="isMobile && sidebarOpen"
      class="sidebar-overlay"
      @click="sidebarOpen = false"
    />

    <!-- 侧边栏（桌面端固定 / 移动端抽屉） -->
    <el-aside
      :width="isMobile ? '0px' : '220px'"
      :class="['app-sidebar', { 'mobile-open': isMobile && sidebarOpen }]"
    >
      <div class="sidebar-inner" :style="{ width: '220px' }">
        <div class="logo">
          <h2>AI 题库系统</h2>
        </div>
        <el-menu
          :default-active="activeMenu"
          router
          background-color="#001529"
          text-color="#ffffffb3"
          active-text-color="#409eff"
          @select="handleMenuSelect"
        >
          <el-menu-item index="/">
            <el-icon><HomeFilled /></el-icon>
            <span>首页</span>
          </el-menu-item>
          <el-menu-item index="/courses">
            <el-icon><Reading /></el-icon>
            <span>课程列表</span>
          </el-menu-item>
          <el-menu-item index="/questions">
            <el-icon><EditPen /></el-icon>
            <span>题库</span>
          </el-menu-item>
          <el-menu-item index="/practice">
            <el-icon><Promotion /></el-icon>
            <span>刷题练习</span>
          </el-menu-item>
          <el-menu-item index="/practice/records">
            <el-icon><Clock /></el-icon>
            <span>刷题记录</span>
          </el-menu-item>
          <el-menu-item index="/wrong-questions">
            <el-icon><WarningFilled /></el-icon>
            <span>错题本</span>
          </el-menu-item>
          <el-menu-item index="/favorites">
            <el-icon><StarFilled /></el-icon>
            <span>我的收藏</span>
          </el-menu-item>
          <el-menu-item index="/review">
            <el-icon><Timer /></el-icon>
            <span>智能复习</span>
          </el-menu-item>
          <el-menu-item index="/learning-report">
            <el-icon><DataLine /></el-icon>
            <span>学习报告</span>
          </el-menu-item>
          <el-menu-item index="/learning-path">
            <el-icon><Guide /></el-icon>
            <span>学习路径</span>
          </el-menu-item>
          <el-menu-item index="/knowledge-graph">
            <el-icon><Connection /></el-icon>
            <span>知识图谱</span>
          </el-menu-item>
          <el-menu-item index="/learning-diagnosis">
            <el-icon><TrendCharts /></el-icon>
            <span>学习诊断</span>
          </el-menu-item>
          <el-menu-item index="/exams">
            <el-icon><Trophy /></el-icon>
            <span>考试</span>
          </el-menu-item>
          <el-menu-item index="/ai/review">
            <el-icon><MagicStick /></el-icon>
            <span>AI 复习建议</span>
          </el-menu-item>
          <el-menu-item index="/submit">
            <el-icon><Upload /></el-icon>
            <span>题目投稿</span>
          </el-menu-item>

          <template v-if="isAdmin">
            <el-sub-menu index="admin">
              <template #title>
                <el-icon><Setting /></el-icon>
                <span>后台管理</span>
              </template>
              <el-menu-item index="/admin">
                <el-icon><DataAnalysis /></el-icon>
                <span>平台总览</span>
              </el-menu-item>
              <el-menu-item index="/admin/courses">
                <el-icon><Collection /></el-icon>
                <span>课程管理</span>
              </el-menu-item>
              <el-menu-item index="/admin/knowledge-points">
                <el-icon><Notebook /></el-icon>
                <span>知识点管理</span>
              </el-menu-item>
              <el-menu-item index="/admin/questions">
                <el-icon><EditPen /></el-icon>
                <span>题目管理</span>
              </el-menu-item>
              <el-menu-item index="/admin/exams">
                <el-icon><Trophy /></el-icon>
                <span>试卷管理</span>
              </el-menu-item>
              <el-menu-item index="/admin/users">
                <el-icon><UserFilled /></el-icon>
                <span>用户管理</span>
              </el-menu-item>
              <el-menu-item index="/admin/submissions">
                <el-icon><Upload /></el-icon>
                <span>投稿管理</span>
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>
      </div>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <!-- 顶部导航 -->
      <el-header class="app-header">
        <div class="header-left">
          <el-icon
            v-if="isMobile"
            class="hamburger"
            :size="22"
            @click="sidebarOpen = !sidebarOpen"
          >
            <Fold v-if="sidebarOpen" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb v-if="!isMobile" separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <!-- 全局搜索入口 -->
          <div class="header-search-trigger" @click="openSearch" title="搜索 (⌘K / Ctrl+K)">
            <el-icon :size="18"><Search /></el-icon>
            <span v-if="!isMobile" class="search-trigger-text">搜索</span>
            <kbd v-if="!isMobile" class="search-trigger-kbd">⌘K</kbd>
          </div>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" :src="userInfo?.avatar || undefined">
                {{ userInfo?.nickname?.charAt(0) || 'U' }}
              </el-avatar>
              <span v-if="!isMobile" class="username">{{ userInfo?.nickname || userInfo?.username || '用户' }}</span>
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

      <!-- 页面内容 -->
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <!-- 全局搜索对话框 -->
  <GlobalSearchDialog ref="searchDialogRef" />
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { HomeFilled, Reading, Setting, Collection, Notebook, EditPen, Promotion, Clock, WarningFilled, Trophy, MagicStick, DataAnalysis, StarFilled, UserFilled, Fold, Expand, DataLine, Guide, Connection, TrendCharts, Upload, Search } from '@element-plus/icons-vue'
import GlobalSearchDialog from '@/components/GlobalSearchDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
const userInfo = computed(() => userStore.userInfo)
const isAdmin = computed(() => userStore.userInfo?.role === 'ADMIN')

// 全局搜索
const searchDialogRef = ref<InstanceType<typeof GlobalSearchDialog>>()
function openSearch() {
  searchDialogRef.value?.open()
}

// 响应式断点
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
  // 移动端点击菜单项后自动关闭侧边栏
  if (isMobile.value) {
    sidebarOpen.value = false
  }
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
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
  height: 100vh;
  position: relative;
}

/* 侧边栏 */
.app-sidebar {
  background-color: #001529;
  overflow: hidden;
  transition: width 0.3s ease;
}

.sidebar-inner {
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
}

/* 自定义滚动条 */
.sidebar-inner::-webkit-scrollbar {
  width: 4px;
}
.sidebar-inner::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  flex-shrink: 0;
}

.logo h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.app-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  padding: 0 20px;
  height: 60px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

/* 搜索触发按钮 */
.header-search-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 6px;
  background: #f0f2f5;
  border: 1px solid #e4e7ed;
  cursor: pointer;
  transition: all 0.2s;
  color: #909399;
  font-size: 13px;
}

.header-search-trigger:hover {
  border-color: #c0c4cc;
  color: #606266;
  background: #e8e8e8;
}

.search-trigger-text {
  margin-left: 2px;
}

.search-trigger-kbd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 1px 5px;
  font-size: 11px;
  color: #b0b3b8;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  font-family: inherit;
  margin-left: 4px;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  gap: 8px;
}

.username {
  font-size: 14px;
  color: #333;
}

.app-main {
  background-color: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}

/* 移动端侧边栏覆盖 */
@media (max-width: 767px) {
  .app-sidebar {
    position: fixed !important;
    top: 0;
    left: 0;
    z-index: 2000;
    height: 100vh;
    width: 0 !important;
    transition: width 0.3s ease;
  }

  .app-sidebar.mobile-open {
    width: 220px !important;
  }

  .app-sidebar.mobile-open .sidebar-inner {
    width: 220px;
  }

  .sidebar-overlay {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.45);
    z-index: 1999;
    animation: fadeIn 0.25s ease;
  }

  @keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
  }

  .hamburger {
    cursor: pointer;
    color: #303133;
  }

  .app-main {
    padding: 12px;
  }

  .app-header {
    padding: 0 12px;
  }

  .header-search-trigger {
    padding: 6px 8px;
  }
}
</style>