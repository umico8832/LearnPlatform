<template>
  <div class="review-container">
    <div class="page-header">
      <h2>AI 复习建议</h2>
    </div>

    <el-card shadow="hover" class="input-card">
      <el-form :inline="true">
        <el-form-item label="针对课程">
          <el-select v-model="courseId" placeholder="全部课程（可选）" clearable style="width: 240px">
            <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="generate" :loading="loading">
            <el-icon><MagicStick /></el-icon> 生成复习建议
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="result || error" shadow="hover" class="result-card">
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" style="margin-bottom: 16px" />
      <MarkdownRenderer v-if="result" :content="result" />
    </el-card>

    <el-empty v-else-if="!loading" description="点击上方按钮，AI 将根据你的错题数据生成个性化复习建议" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import { getReviewSuggestion } from '@/api/ai'
import { getCoursePage } from '@/api/course'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

const courseId = ref<number | undefined>(undefined)
const courseList = ref<{ id: number; name: string }[]>([])
const loading = ref(false)
const result = ref('')
const error = ref('')

onMounted(async () => {
  try {
    const res = await getCoursePage({ pageNum: 1, pageSize: 100 })
    if ((res as any).code === 0 && (res as any).data) {
      courseList.value = ((res as any).data.records || []).map((c: any) => ({ id: c.id, name: c.name }))
    }
  } catch {}
})

const generate = async () => {
  loading.value = true
  result.value = ''
  error.value = ''
  try {
    const res = await getReviewSuggestion(courseId.value)
    if (res.code === 0 && res.data) {
      result.value = res.data.content
    } else {
      error.value = res.message || '生成失败'
    }
  } catch (e: any) {
    error.value = e?.response?.data?.message || e?.message || 'AI 服务调用失败，请检查配置'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.review-container { padding: 24px; max-width: 800px; margin: 0 auto; }
.page-header h2 { margin: 0 0 16px; font-size: 20px; color: #303133; }
.input-card { margin-bottom: 16px; }
.result-card { margin-top: 16px; }
</style>
