<template>
  <div class="page-container">
    <h1 class="page-title">欢迎使用 AI 题库与错题复习系统</h1>
    <div class="page-card">
      <p>系统功能开发中...</p>
      <p>后端健康检查状态: <el-tag :type="healthStatus === 'UP' ? 'success' : 'danger'">{{ healthStatus || '检查中...' }}</el-tag></p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/utils/request'

const healthStatus = ref<string>('')

onMounted(async () => {
  try {
    const res = await request.get('/public/health')
    healthStatus.value = res.data.data.status
  } catch {
    healthStatus.value = 'DOWN'
  }
})
</script>