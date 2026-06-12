<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { isAuthenticated } from '@/utils/auth'

// 页面刷新时恢复用户信息
const userStore = useUserStore()
onMounted(() => {
  if (isAuthenticated()) {
    userStore.fetchUserInfo()
  }
})
</script>

<style>
html, body {
  margin: 0;
  padding: 0;
  height: 100%;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
}

#app {
  height: 100%;
}
</style>