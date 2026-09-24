<template>
  <div class="admin-shell">
    <aside class="admin-shell-sidebar">
      <a class="admin-shell-brand" href="/admin/">
        <span>LP</span>
        <strong>管理系统</strong>
      </a>
      <nav aria-label="管理导航">
        <router-link to="/">平台总览</router-link>
        <router-link to="/courses">课程管理</router-link>
        <router-link to="/questions">题目管理</router-link>
        <router-link to="/knowledge">知识快照审核</router-link>
        <router-link to="/exams">试卷管理</router-link>
        <router-link to="/subjective-reviews">主观题批阅</router-link>
        <router-link to="/community">社区管理</router-link>
        <router-link to="/users">用户管理</router-link>
        <router-link to="/submissions">投稿管理</router-link>
        <router-link to="/ai-variant-reviews">AI 变式题审查</router-link>
        <router-link to="/ai-usage">AI 调用分析</router-link>
      </nav>
      <a class="admin-shell-legacy" href="/">进入学习端</a>
    </aside>
    <section class="admin-shell-content">
      <header class="admin-shell-toolbar">
        <el-button @click="logout">退出登录</el-button>
      </header>
      <main><router-view /></main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

function logout() {
  userStore.clearLoginInfo()
  void router.push({ name: 'AdminLogin' })
}
</script>
