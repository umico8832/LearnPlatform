<template>
  <div class="admin-shell">
    <aside class="admin-shell-sidebar">
      <a class="admin-shell-brand" href="/admin-app/">
        <span>LP</span>
        <strong>管理系统</strong>
      </a>
      <nav aria-label="管理导航">
        <router-link to="/">平台总览</router-link>
        <router-link to="/courses">课程管理</router-link>
        <router-link to="/questions">题目管理</router-link>
        <router-link to="/exams">试卷管理</router-link>
        <router-link to="/subjective-reviews">主观题批阅</router-link>
        <router-link to="/users">用户管理</router-link>
        <router-link to="/submissions">投稿管理</router-link>
        <router-link to="/ai-usage">AI 调用分析</router-link>
      </nav>
      <a class="admin-shell-legacy" href="/admin">旧版管理入口</a>
    </aside>
    <section class="admin-shell-content">
      <header>
        <div>
          <h1>{{ route.meta.title }}</h1>
          <p>按明确权限和可追溯业务事实执行内容运营。</p>
        </div>
        <el-button @click="logout">退出登录</el-button>
      </header>
      <main><router-view /></main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

function logout() {
  userStore.clearLoginInfo()
  void router.push({ name: 'AdminLogin' })
}
</script>
