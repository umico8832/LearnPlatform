<template>
  <div class="practice-container">
    <div class="practice-header">
      <h2>刷题练习</h2>
      <p class="subtitle">选择刷题模式，开始练习</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row" v-if="stats">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value">{{ stats.totalAnswered }}</div>
            <div class="stat-label">总答题数</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-correct">
            <div class="stat-value">{{ stats.correctCount }}</div>
            <div class="stat-label">答对数</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-wrong">
            <div class="stat-value">{{ stats.wrongCount }}</div>
            <div class="stat-label">答错数</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-rate">
            <div class="stat-value">{{ stats.correctRate }}%</div>
            <div class="stat-label">正确率</div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 刷题配置 -->
    <el-card class="config-card">
      <template #header>
        <div class="card-header">
          <span>刷题设置</span>
        </div>
      </template>

      <el-form :model="form" label-width="100px">
        <el-form-item label="选择课程">
          <el-select v-model="form.courseId" placeholder="全部课程" clearable style="width: 100%">
            <el-option
              v-for="course in courseList"
              :key="course.id"
              :label="course.name"
              :value="course.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="题型">
          <el-select v-model="form.questionType" placeholder="全部题型" clearable style="width: 100%">
            <el-option label="单选题" value="SINGLE_CHOICE" />
            <el-option label="多选题" value="MULTIPLE_CHOICE" />
            <el-option label="判断题" value="TRUE_FALSE" />
            <el-option label="填空题" value="FILL_BLANK" />
            <el-option label="简答题" value="SHORT_ANSWER" />
          </el-select>
        </el-form-item>

        <el-form-item label="难度">
          <el-rate v-model="form.difficulty" :max="5" allow-half />
        </el-form-item>

        <el-form-item label="题目数量">
          <el-input-number v-model="form.count" :min="1" :max="50" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" size="large" @click="startPractice" :loading="loading">
            开始刷题
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPracticeQuestions, getPracticeStats } from '@/api/practice'
import type { PracticeStatsVO } from '@/api/practice'
import request from '@/utils/request'

const router = useRouter()
const loading = ref(false)
const stats = ref<PracticeStatsVO | null>(null)
const courseList = ref<any[]>([])

const form = reactive({
  courseId: undefined as number | undefined,
  questionType: '' as string,
  difficulty: undefined as number | undefined,
  count: 10
})

onMounted(() => {
  loadStats()
  loadCourses()
})

const loadStats = async () => {
  try {
    const res = await getPracticeStats()
    if (res.code === 0) {
      stats.value = res.data
    }
  } catch (e) {
    // ignore
  }
}

const loadCourses = async () => {
  try {
    const res = await request.get<any, any>('/api/courses', { params: { pageNum: 1, pageSize: 100 } })
    if (res.code === 0) {
      courseList.value = res.data?.records || []
    }
  } catch (e) {
    // ignore
  }
}

const startPractice = async () => {
  loading.value = true
  try {
    const params: any = { count: form.count }
    if (form.courseId) params.courseId = form.courseId
    if (form.questionType) params.questionType = form.questionType
    if (form.difficulty) params.difficulty = form.difficulty

    const res = await getPracticeQuestions(params)
    if (res.code === 0 && res.data && res.data.length > 0) {
      // 将题目列表存入 sessionStorage 并跳转
      sessionStorage.setItem('practice_questions', JSON.stringify(res.data))
      router.push({ name: 'PracticeSession' })
    } else {
      ElMessage.warning('未找到符合条件的题目，请调整筛选条件')
    }
  } catch (e) {
    ElMessage.error('获取题目失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.practice-container {
  padding: 24px;
  max-width: 900px;
  margin: 0 auto;
}

.practice-header {
  margin-bottom: 24px;
}

.practice-header h2 {
  margin: 0 0 8px;
  font-size: 24px;
  color: #303133;
}

.subtitle {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.stats-row {
  margin-bottom: 24px;
}

.stat-card {
  text-align: center;
  padding: 16px 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #409eff;
}

.stat-correct .stat-value {
  color: #67c23a;
}

.stat-wrong .stat-value {
  color: #f56c6c;
}

.stat-rate .stat-value {
  color: #e6a23c;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 8px;
}

.config-card {
  margin-top: 24px;
}

.card-header {
  font-weight: 600;
  font-size: 16px;
}
</style>