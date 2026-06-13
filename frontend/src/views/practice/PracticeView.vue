<template>
  <div class="practice-container">
    <div class="practice-header">
      <h2>刷题练习</h2>
      <p class="subtitle">选择刷题模式，开始练习</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <el-row :gutter="20">
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card" v-loading="statsLoading" element-loading-background="rgba(255,255,255,0.8)">
            <div class="stat-value">{{ stats?.totalAnswered ?? 0 }}</div>
            <div class="stat-label">总答题数</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card stat-correct" v-loading="statsLoading" element-loading-background="rgba(255,255,255,0.8)">
            <div class="stat-value">{{ stats?.correctCount ?? 0 }}</div>
            <div class="stat-label">答对数</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card stat-wrong" v-loading="statsLoading" element-loading-background="rgba(255,255,255,0.8)">
            <div class="stat-value">{{ stats?.wrongCount ?? 0 }}</div>
            <div class="stat-label">答错数</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card stat-rate" v-loading="statsLoading" element-loading-background="rgba(255,255,255,0.8)">
            <div class="stat-value">{{ stats?.correctRate ?? 0 }}%</div>
            <div class="stat-label">正确率</div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 智能推荐卡片 -->
    <el-card class="adaptive-card" v-loading="adaptiveLoading" element-loading-background="rgba(255,255,255,0.8)">
      <template #header>
        <div class="card-header">
          <span>🧠 智能推荐</span>
          <el-tag type="success" size="small" v-if="adaptiveSummary">自适应模式</el-tag>
        </div>
      </template>

      <div v-if="adaptiveSummary" class="adaptive-content">
        <div class="adaptive-overview">
          <div class="overview-item">
            <span class="overview-label">总答题</span>
            <span class="overview-value">{{ adaptiveSummary.totalAnswered }}</span>
          </div>
          <div class="overview-item">
            <span class="overview-label">整体正确率</span>
            <span class="overview-value">{{ adaptiveSummary.overallCorrectRate }}%</span>
          </div>
          <div class="overview-item">
            <span class="overview-label">推荐难度</span>
            <span class="overview-value recommend-diff">
              {{ adaptiveSummary.recommendedDifficulty.toFixed(1) }}
              <el-rate
                :model-value="adaptiveSummary.recommendedDifficulty"
                disabled
                allow-half
                :max="5"
                size="small"
              />
            </span>
          </div>
        </div>

        <div class="difficulty-bars">
          <div
            v-for="item in adaptiveSummary.difficultyDetails"
            :key="item.difficulty"
            class="diff-bar-row"
          >
            <span class="diff-label">{{ item.label }}</span>
            <div class="diff-bar-wrapper">
              <div class="diff-bar-bg">
                <div
                  class="diff-bar-fill"
                  :style="{ width: item.weight * 100 + '%', backgroundColor: diffColors[item.difficulty - 1] }"
                ></div>
              </div>
            </div>
            <span class="diff-weight">{{ (item.weight * 100).toFixed(0) }}%</span>
            <span class="diff-rate" v-if="item.total > 0">正确率 {{ item.correctRate }}%</span>
            <span class="diff-rate" v-else>暂无数据</span>
          </div>
        </div>

        <div class="adaptive-actions">
          <el-button type="primary" size="large" @click="startAdaptivePractice" :loading="adaptiveStartLoading">
            🎯 开始智能推荐练习
          </el-button>
          <el-select v-model="adaptiveForm.courseId" placeholder="全部课程" clearable style="width: 200px; margin-left: 12px">
            <el-option
              v-for="course in courseList"
              :key="course.id"
              :label="course.name"
              :value="course.id"
            />
          </el-select>
          <el-input-number v-model="adaptiveForm.count" :min="5" :max="50" style="margin-left: 12px" />
        </div>
      </div>

      <div v-else-if="!adaptiveLoading" class="adaptive-empty">
        <el-empty description="暂无答题记录，开始刷题后将根据你的表现智能推荐题目">
          <el-button type="primary" @click="scrollToConfig">开始刷题</el-button>
        </el-empty>
      </div>
    </el-card>

    <!-- 刷题配置 -->
    <el-card class="config-card" ref="configCardRef">
      <template #header>
        <div class="card-header">
          <span>自选模式</span>
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
import { getPracticeQuestions, getPracticeStats, getAdaptiveQuestions, getAdaptiveSummary } from '@/api/practice'
import type { PracticeStatsVO, AdaptiveSummaryVO } from '@/api/practice'
import request from '@/utils/request'

const router = useRouter()
const loading = ref(false)
const statsLoading = ref(true)
const adaptiveLoading = ref(true)
const adaptiveStartLoading = ref(false)
const stats = ref<PracticeStatsVO | null>(null)
const adaptiveSummary = ref<AdaptiveSummaryVO | null>(null)
const courseList = ref<any[]>([])
const configCardRef = ref<any>(null)

const diffColors = ['#67c23a', '#409eff', '#e6a23c', '#f56c6c', '#909399']

const form = reactive({
  courseId: undefined as number | undefined,
  questionType: '' as string,
  difficulty: undefined as number | undefined,
  count: 10
})

const adaptiveForm = reactive({
  courseId: undefined as number | undefined,
  count: 10
})

onMounted(() => {
  loadStats()
  loadCourses()
  loadAdaptiveSummary()
})

const loadStats = async () => {
  try {
    const res = await getPracticeStats()
    if (res.code === 0) {
      stats.value = res.data
    }
  } catch (e) {
    // ignore
  } finally {
    statsLoading.value = false
  }
}

const loadCourses = async () => {
  try {
    const res = await request.get<any, any>('/courses', { params: { pageNum: 1, pageSize: 100 } })
    if (res.code === 0) {
      courseList.value = res.data?.records || []
    }
  } catch (e) {
    // ignore
  }
}

const loadAdaptiveSummary = async () => {
  try {
    const res = await getAdaptiveSummary()
    if (res.code === 0 && res.data && res.data.totalAnswered > 0) {
      adaptiveSummary.value = res.data
    }
  } catch (e) {
    // ignore
  } finally {
    adaptiveLoading.value = false
  }
}

const startAdaptivePractice = async () => {
  adaptiveStartLoading.value = true
  try {
    const params: any = { count: adaptiveForm.count }
    if (adaptiveForm.courseId) params.courseId = adaptiveForm.courseId

    const res = await getAdaptiveQuestions(params)
    if (res.code === 0 && res.data && res.data.length > 0) {
      sessionStorage.setItem('practice_questions', JSON.stringify(res.data))
      sessionStorage.setItem('practice_mode', 'adaptive')
      router.push({ name: 'PracticeSession' })
    } else {
      ElMessage.warning('暂无可推荐的题目，请先添加题目或调整筛选条件')
    }
  } catch (e) {
    ElMessage.error('获取题目失败')
  } finally {
    adaptiveStartLoading.value = false
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
      sessionStorage.setItem('practice_questions', JSON.stringify(res.data))
      sessionStorage.removeItem('practice_mode')
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

const scrollToConfig = () => {
  configCardRef.value?.$el?.scrollIntoView({ behavior: 'smooth' })
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

.adaptive-card {
  margin-bottom: 24px;
  border: 1px solid #e4e7ed;
}

.adaptive-content {
  padding-top: 4px;
}

.adaptive-overview {
  display: flex;
  gap: 24px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.overview-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.overview-label {
  font-size: 13px;
  color: #909399;
}

.overview-value {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.recommend-diff :deep(.el-rate) {
  display: inline-flex;
}

.difficulty-bars {
  margin-bottom: 20px;
}

.diff-bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 13px;
}

.diff-label {
  width: 36px;
  text-align: right;
  color: #606266;
  flex-shrink: 0;
}

.diff-bar-wrapper {
  flex: 1;
  min-width: 0;
}

.diff-bar-bg {
  height: 12px;
  background: #f0f2f5;
  border-radius: 6px;
  overflow: hidden;
}

.diff-bar-fill {
  height: 100%;
  border-radius: 6px;
  transition: width 0.6s ease;
  min-width: 2px;
}

.diff-weight {
  width: 40px;
  text-align: right;
  font-weight: 600;
  color: #303133;
  flex-shrink: 0;
}

.diff-rate {
  width: 90px;
  text-align: right;
  color: #909399;
  flex-shrink: 0;
}

.adaptive-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0;
}

.adaptive-empty {
  padding: 12px 0;
}

.config-card {
  margin-top: 24px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 16px;
}
</style>