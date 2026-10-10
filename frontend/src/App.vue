<template>
  <el-config-provider :locale="zhCn">
    <router-view v-slot="{ Component, route: current }">
      <!-- 题目理解页展开可视化再返回时保留作答进度，只缓存最近一道题。 -->
      <KeepAlive :include="KEEP_ALIVE" :max="1">
        <component :is="Component" :key="KEEP_ALIVE_ROUTES.has(current.name) ? current.path : undefined" />
      </KeepAlive>
    </router-view>
  </el-config-provider>
</template>

<script setup lang="ts">
import { onMounted, onScopeDispose, watch } from 'vue'
import { useRoute } from 'vue-router'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import { useUserStore } from '@/stores/user'
import { isAuthenticated } from '@/utils/auth'
import { getToken, onAuthSessionChange } from '@/utils/auth'
import { useGamificationStore } from '@/stores/gamification'

const KEEP_ALIVE = ['QuestionUnderstandingView']
const KEEP_ALIVE_ROUTES = new Set<unknown>(['QuestionUnderstanding'])

// 页面刷新时恢复用户信息
const userStore = useUserStore()
const route = useRoute()
const gamification = useGamificationStore()
onMounted(() => {
  const isAuthPreview = import.meta.env.DEV && typeof route.query['auth-preview'] === 'string'
  if (isAuthenticated() && !isAuthPreview) {
    userStore.fetchUserInfo()
  }
})
watch(
  () => route.name,
  (name, previous) => {
    const exam = name === 'ExamTake'
    gamification.setExamMode(exam)
    if (!exam && previous === 'ExamTake' && getToken()) void gamification.load()
  },
  { immediate: true, flush: 'sync' },
)
onScopeDispose(
  onAuthSessionChange(() => {
    if (getToken() && route.name !== 'ExamTake') void gamification.load()
  }),
)
onMounted(() => {
  if (getToken() && route.name !== 'ExamTake') void gamification.load()
})
</script>
