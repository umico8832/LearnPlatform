<template>
  <div class="admin-shell">
    <a class="admin-skip-link" href="#admin-main">跳到主要内容</a>
    <aside class="admin-shell-sidebar">
      <router-link class="admin-shell-brand" to="/"
        ><span aria-hidden="true">LP</span><strong>管理工作台</strong></router-link
      >
      <nav aria-label="管理导航">
        <router-link
          to="/"
          class="admin-overview-link"
          active-class="admin-overview-parent"
          exact-active-class="is-active"
          >平台总览</router-link
        >
        <div v-for="group in navigation" :key="group.label" class="admin-nav-group">
          <p>{{ group.label }}</p>
          <router-link
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            :class="{ 'is-active': item.to === '/courses' && route.path === '/knowledge-points' }"
            :aria-current="item.to === '/courses' && route.path === '/knowledge-points' ? 'page' : undefined"
          >
            {{ item.label }}
          </router-link>
        </div>
      </nav>
      <a class="admin-shell-legacy" href="/">进入学习端 <span aria-hidden="true">↗</span></a>
    </aside>
    <section class="admin-shell-content">
      <header class="admin-shell-toolbar">
        <span class="admin-location">{{ sectionLabel }}</span>
        <div class="admin-account">
          <span>{{ userStore.userInfo?.nickname || userStore.userInfo?.username || '管理员' }}</span>
          <el-button text @click="logout">退出登录</el-button>
        </div>
      </header>
      <main id="admin-main" ref="main" tabindex="-1"><router-view /></main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const main = ref<HTMLElement | null>(null)
const navigation = [
  {
    label: '内容维护',
    items: [
      { to: '/courses', label: '课程与知识点' },
      { to: '/questions', label: '题目管理' },
      { to: '/exams', label: '试卷管理' },
    ],
  },
  {
    label: '审核与批阅',
    items: [
      { to: '/knowledge', label: '知识快照审核' },
      { to: '/subjective-reviews', label: '主观题批阅' },
      { to: '/submissions', label: '投稿管理' },
      { to: '/ai-variant-reviews', label: 'AI 变式题审查' },
    ],
  },
  {
    label: '平台运营',
    items: [
      { to: '/community', label: '社区管理' },
      { to: '/users', label: '用户管理' },
      { to: '/ai-usage', label: 'AI 调用分析' },
    ],
  },
]
const sectionLabel = computed(
  () =>
    navigation.find((group) =>
      group.items.some(
        (item) => item.to === route.path || (item.to === '/courses' && route.path === '/knowledge-points'),
      ),
    )?.label ?? '平台总览',
)
watch(
  () => route.path,
  async () => {
    await nextTick()
    main.value?.focus({ preventScroll: true })
  },
)
function logout() {
  userStore.clearLoginInfo()
  void router.push({ name: 'AdminLogin' })
}
</script>
