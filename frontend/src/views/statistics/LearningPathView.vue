<template>
  <div class="learning-path">
    <div class="page-header">
      <h2>🗺️ 学习路径推荐</h2>
      <p class="subtitle">基于你的练习数据，智能分析薄弱知识点并生成个性化学习路径</p>
    </div>

    <!-- 课程筛选 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <span class="filter-label">选择课程：</span>
        <el-select v-model="selectedCourseId" placeholder="全部课程" clearable @change="fetchData" style="width: 240px">
          <el-option label="全部课程" :value="0" />
          <el-option
            v-for="c in courses"
            :key="c.id"
            :label="c.name"
            :value="c.id"
          />
        </el-select>
        <el-button type="primary" :icon="Refresh" @click="fetchData" :loading="loading" style="margin-left: 12px">
          刷新
        </el-button>
      </div>
    </el-card>

    <!-- 概览卡片 -->
    <el-row :gutter="16" class="overview-cards" v-if="data">
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card mastery">
          <div class="stat-value">{{ data.overallMastery }}%</div>
          <div class="stat-label">总体掌握率</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card total">
          <div class="stat-value">{{ data.totalKnowledgePoints }}</div>
          <div class="stat-label">知识点总数</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card mastered">
          <div class="stat-value">{{ data.masteredCount }}</div>
          <div class="stat-label">已掌握</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card weak">
          <div class="stat-value">{{ data.weakCount }}</div>
          <div class="stat-label">需加强</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 各课程掌握概况 -->
    <el-card shadow="never" class="section-card" v-if="data && data.courseOverviews.length > 0">
      <template #header>
        <span>📊 各课程掌握概况</span>
      </template>
      <div class="course-overviews">
        <div v-for="co in data.courseOverviews" :key="co.courseId" class="course-item">
          <div class="course-header">
            <span class="course-name">{{ co.courseName }}</span>
            <span class="course-rate" :class="getRateClass(co.correctRate)">{{ co.correctRate }}%</span>
          </div>
          <el-progress
            :percentage="co.correctRate"
            :color="getProgressColor(co.correctRate)"
            :stroke-width="10"
          />
          <div class="course-meta">
            <span>知识点 {{ co.masteredPointCount }}/{{ co.knowledgePointCount }} 已掌握</span>
            <span>共 {{ co.totalAttempts }} 次练习</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 学习路径步骤 -->
    <el-card shadow="never" class="section-card" v-if="data && data.steps.length > 0">
      <template #header>
        <div class="section-header">
          <span>🛤️ 推荐学习路径</span>
          <el-tag type="info" size="small">按优先级排序，越靠前越需要优先学习</el-tag>
        </div>
      </template>

      <!-- 状态筛选 -->
      <div class="status-filter">
        <el-radio-group v-model="statusFilter" size="small">
          <el-radio-button value="ALL">全部</el-radio-button>
          <el-radio-button value="WEAK">薄弱</el-radio-button>
          <el-radio-button value="NEEDS_REVIEW">需复习</el-radio-button>
          <el-radio-button value="NOT_STARTED">未开始</el-radio-button>
          <el-radio-button value="MASTERED">已掌握</el-radio-button>
        </el-radio-group>
      </div>

      <el-table :data="filteredSteps" stripe style="width: 100%" :row-class-name="getRowClass">
        <el-table-column label="#" width="55" align="center">
          <template #default="{ row }">
            <span class="order-badge" :class="getStatusBadgeClass(row.masteryStatus)">{{ row.order }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="knowledgePointName" label="知识点" min-width="180">
          <template #default="{ row }">
            <div class="kp-name">{{ row.knowledgePointName }}</div>
            <div class="kp-course">{{ row.courseName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="掌握状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.masteryStatus)" size="small">
              {{ getStatusLabel(row.masteryStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="正确率" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.correctRate >= 0" :class="getRateClass(row.correctRate)">
              {{ row.correctRate }}%
            </span>
            <span v-else class="text-muted">未练习</span>
          </template>
        </el-table-column>
        <el-table-column label="练习/错题" width="110" align="center">
          <template #default="{ row }">
            <span>{{ row.totalAttempts }}/{{ row.wrongCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="学习建议" min-width="280">
          <template #default="{ row }">
            <span class="recommendation">{{ row.recommendation }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 空状态 -->
    <el-empty v-if="!loading && data && data.steps.length === 0" description="暂无知识点数据，请先添加课程和知识点" />

    <!-- 加载状态 -->
    <div v-if="loading && !data" class="loading-container">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <span>正在分析学习数据...</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Refresh, Loading } from '@element-plus/icons-vue'
import { getLearningPath, type LearningPath } from '@/api/statistics'
import { getAllCourses, type CourseVO } from '@/api/course'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const data = ref<LearningPath | null>(null)
const selectedCourseId = ref<number>(0)
const statusFilter = ref('ALL')
const courses = ref<CourseVO[]>([])

const filteredSteps = computed(() => {
  if (!data.value) return []
  if (statusFilter.value === 'ALL') return data.value.steps
  return data.value.steps.filter(s => s.masteryStatus === statusFilter.value)
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getLearningPath(selectedCourseId.value || undefined)
    data.value = res.data
  } catch (e: any) {
    ElMessage.error(e.message || '获取学习路径失败')
  } finally {
    loading.value = false
  }
}

async function fetchCourses() {
  try {
    const res = await getAllCourses()
    courses.value = (res as any).data || res || []
  } catch {
    // ignore
  }
}

function getStatusTagType(status: string) {
  switch (status) {
    case 'MASTERED': return 'success'
    case 'NEEDS_REVIEW': return 'warning'
    case 'WEAK': return 'danger'
    case 'NOT_STARTED': return 'info'
    default: return 'info'
  }
}

function getStatusLabel(status: string) {
  switch (status) {
    case 'MASTERED': return '已掌握'
    case 'NEEDS_REVIEW': return '需复习'
    case 'WEAK': return '薄弱'
    case 'NOT_STARTED': return '未开始'
    default: return status
  }
}

function getStatusBadgeClass(status: string) {
  switch (status) {
    case 'MASTERED': return 'badge-mastered'
    case 'NEEDS_REVIEW': return 'badge-review'
    case 'WEAK': return 'badge-weak'
    case 'NOT_STARTED': return 'badge-not-started'
    default: return ''
  }
}

function getRowClass({ row }: { row: any }) {
  if (row.masteryStatus === 'WEAK') return 'row-weak'
  return ''
}

function getRateClass(rate: number) {
  if (rate >= 70) return 'rate-good'
  if (rate >= 50) return 'rate-medium'
  return 'rate-bad'
}

function getProgressColor(rate: number) {
  if (rate >= 70) return '#67c23a'
  if (rate >= 50) return '#e6a23c'
  return '#f56c6c'
}

onMounted(() => {
  fetchCourses()
  fetchData()
})
</script>

<style scoped>
.learning-path {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 4px 0;
  font-size: 22px;
}

.subtitle {
  color: #909399;
  margin: 0;
  font-size: 14px;
}

.filter-card {
  margin-bottom: 16px;
}

.filter-row {
  display: flex;
  align-items: center;
}

.filter-label {
  font-weight: 500;
  margin-right: 8px;
  white-space: nowrap;
}

.overview-cards {
  margin-bottom: 16px;
}

.overview-cards .el-col {
  margin-bottom: 8px;
}

.stat-card {
  text-align: center;
  padding: 8px 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.stat-card.mastery .stat-value { color: #409eff; }
.stat-card.total .stat-value { color: #606266; }
.stat-card.mastered .stat-value { color: #67c23a; }
.stat-card.weak .stat-value { color: #f56c6c; }

.section-card {
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.course-overviews {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.course-item {
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafafa;
}

.course-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.course-name {
  font-weight: 600;
  font-size: 15px;
}

.course-rate {
  font-weight: 700;
  font-size: 16px;
}

.course-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
}

.status-filter {
  margin-bottom: 12px;
}

.order-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: #909399;
}

.badge-mastered { background: #67c23a; }
.badge-review { background: #e6a23c; }
.badge-weak { background: #f56c6c; }
.badge-not-started { background: #c0c4cc; }

.kp-name {
  font-weight: 500;
}

.kp-course {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.recommendation {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
}

.rate-good { color: #67c23a; font-weight: 600; }
.rate-medium { color: #e6a23c; font-weight: 600; }
.rate-bad { color: #f56c6c; font-weight: 600; }
.text-muted { color: #c0c4cc; }

:deep(.row-weak) {
  background-color: #fef0f0 !important;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 60px 0;
  color: #909399;
}

@media (max-width: 768px) {
  .stat-value { font-size: 22px; }
  .course-overviews { grid-template-columns: 1fr; }
  .filter-row { flex-wrap: wrap; gap: 8px; }
}
</style>
