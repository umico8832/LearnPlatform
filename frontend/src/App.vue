<template>
  <el-config-provider :locale="zhCn">
    <router-view v-slot="{ Component }">
      <Transition name="auth-motion" mode="out-in" appear>
        <component :is="Component" />
      </Transition>
    </router-view>
  </el-config-provider>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import { useUserStore } from '@/stores/user'
import { isAuthenticated } from '@/utils/auth'

// 页面刷新时恢复用户信息
const userStore = useUserStore()
const route = useRoute()
onMounted(() => {
  const isAuthPreview = import.meta.env.DEV && typeof route.query['auth-preview'] === 'string'
  if (isAuthenticated() && !isAuthPreview) {
    userStore.fetchUserInfo()
  }
})
</script>
