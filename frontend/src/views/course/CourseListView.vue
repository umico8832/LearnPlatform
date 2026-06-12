<template>
  <div class="course-list">
    <div class="page-header">
      <h2>课程列表</h2>
    </div>
    <el-row :gutter="20">
      <el-col v-for="course in courses" :key="course.id" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card class="course-card" shadow="hover" @click="goToDetail(course.id)">
          <div class="course-cover">
            <el-icon :size="48" color="#409eff"><Reading /></el-icon>
          </div>
          <h3>{{ course.name }}</h3>
          <p class="course-desc">{{ course.description || '暂无描述' }}</p>
        </el-card>
      </el-col>
      <el-col v-if="courses.length === 0 && !loading" :span="24">
        <el-empty description="暂无课程" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Reading } from '@element-plus/icons-vue'
import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

interface CourseVO {
  id: number
  name: string
  description: string
  coverImage: string | null
  sortOrder: number
  status: number
}

const router = useRouter()
const courses = ref<CourseVO[]>([])
const loading = ref(false)

async function fetchCourses() {
  loading.value = true
  try {
    const res = await request.get<ApiResponse<CourseVO[]>>('/courses/list')
    courses.value = res.data.data
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

function goToDetail(id: number) {
  router.push(`/courses/${id}`)
}

onMounted(() => {
  fetchCourses()
})
</script>

<style scoped>
.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.course-card {
  margin-bottom: 20px;
  cursor: pointer;
  transition: transform 0.2s;
}

.course-card:hover {
  transform: translateY(-4px);
}

.course-cover {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100px;
  background: #f0f7ff;
  border-radius: 4px;
  margin-bottom: 12px;
}

.course-card h3 {
  margin: 0 0 8px;
  font-size: 16px;
  color: #303133;
}

.course-desc {
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>