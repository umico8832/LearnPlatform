<template>
  <el-config-provider :locale="zhCn">
    <router-view v-slot="{ Component }">
      <Transition name="auth-motion" mode="out-in" appear>
        <component :is="Component" />
      </Transition>
    </router-view>
    <GamificationFeedbackHost v-if="route.name !== 'ExamTake'" :scheduler="gamification.celebrations" />
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
import GamificationFeedbackHost from '@/components/gamification/GamificationFeedbackHost.vue'

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
