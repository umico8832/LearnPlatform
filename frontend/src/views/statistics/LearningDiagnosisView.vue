<template>
  <div class="learning-diagnosis">
    <el-page-header @back="$router.back()">
      <template #content>
        <span class="page-title">🧠 学习诊断</span>
      </template>
    </el-page-header>

    <div v-if="loading" v-loading="true" style="height: 400px"></div>

    <template v-else-if="data">
      <!-- 每日建议 -->
      <el-card class="advice-card" shadow="hover">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <span>💡 每日学习建议</span>
            <el-button
              type="primary"
              size="small"
              :loading="aiAdviceLoading"
              @click="generateAiAdvice"
              :disabled="aiAdviceStreaming"
            >
              🤖 AI 个性化建议
            </el-button>
          </div>
        </template>
        <div class="advice-content">
          <p v-for="(line, i) in adviceLines" :key="i">{{ line }}</p>
        </div>
      </el-card>

      <!-- AI 个性化建议 -->
      <el-card v-if="aiAdviceContent || aiAdviceLoading" class="ai-advice-card" shadow="hover">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <span>🤖 AI 个性化学习建议</span>
            <el-tag v-if="aiAdviceStreaming" type="success" size="small" effect="light">
              生成中...
            </el-tag>
            <el-tag v-else-if="aiAdviceContent" type="info" size="small" effect="light">
              AI 生成
            </el-tag>
          </div>
        </template>
        <div v-if="aiAdviceLoading && !aiAdviceContent" v-loading="true" style="height: 100px"></div>
        <div v-else class="ai-advice-content">
          <MarkdownRenderer :content="aiAdviceContent" />
        </div>
      </el-card>

      <!-- 核心指标卡片 -->
      <el-row :gutter="16" class="stat-row">
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value">{{ data.totalPractice }}</div>
            <div class="stat-label">总刷题数</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value">{{ data.overallCorrectRate }}%</div>
            <div class="stat-label">总正确率</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value">{{ data.activeDaysLast30 }}天</div>
            <div class="stat-label">近30天活跃</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value">{{ data.streakDays }}天</div>
            <div class="stat-label">连续刷题</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 知识点薄弱诊断 -->
      <el-card class="section-card" shadow="hover" v-if="data.weakPoints.length">
        <template #header>
          <span>📚 知识点薄弱诊断</span>
        </template>
        <el-table :data="data.weakPoints" stripe>
          <el-table-column label="知识点" min-width="160">
            <template #default="{ row }">
              <div>
                <strong>{{ row.knowledgePointName }}</strong>
                <el-tag size="small" type="info" style="margin-left: 8px">{{ row.courseName }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="正确率" width="120">
            <template #default="{ row }">
              <el-progress
                :percentage="row.correctRate >= 0 ? Math.round(row.correctRate) : 0"
                :color="getRateColor(row.correctRate)"
                :stroke-width="18"
                :text-inside="true"
              />
            </template>
          </el-table-column>
          <el-table-column prop="totalAttempts" label="练习数" width="80" align="center" />
          <el-table-column prop="wrongCount" label="错题数" width="80" align="center" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.masteryStatus)" size="small">
                {{ getStatusLabel(row.masteryStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="diagnosis" label="诊断" min-width="260" show-overflow-tooltip />
        </el-table>
      </el-card>

      <!-- 学习习惯 -->
      <el-card class="section-card" shadow="hover" v-if="data.learningHabit">
        <template #header>
          <span>📊 学习习惯分析</span>
        </template>
        <el-row :gutter="24">
          <el-col :xs="24" :sm="12">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="日均刷题">{{ data.learningHabit.avgDailyPractice }} 道</el-descriptions-item>
              <el-descriptions-item label="偏好题型">{{ data.learningHabit.preferredQuestionType }}</el-descriptions-item>
              <el-descriptions-item label="偏好课程">{{ data.learningHabit.preferredCourse }}</el-descriptions-item>
              <el-descriptions-item label="学习频次">
                <el-tag :type="data.learningHabit.frequencyLevel === 'ACTIVE' ? 'success' : data.learningHabit.frequencyLevel === 'MODERATE' ? 'warning' : 'danger'" size="small">
                  {{ data.learningHabit.frequencyDescription }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </el-col>
          <el-col :xs="24" :sm="12">
            <div class="chart-container">
              <h4>近 7 天刷题趋势</h4>
              <div class="mini-chart">
                <div v-for="(day, i) in data.learningHabit.weeklyTrend" :key="i" class="chart-bar-group">
                  <div class="chart-bar-wrapper">
                    <div class="chart-bar correct" :style="{ height: getBarHeight(day.correct) }"></div>
                    <div class="chart-bar wrong" :style="{ height: getBarHeight(day.wrong) }"></div>
                  </div>
                  <div class="chart-date">{{ day.date.slice(5) }}</div>
                  <div class="chart-total">{{ day.total }}</div>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <!-- 错因分析 -->
      <el-card class="section-card" shadow="hover" v-if="data.errorPatterns">
        <template #header>
          <span>⚠️ 错因分析</span>
        </template>
        <el-row :gutter="24">
          <el-col :xs="24" :sm="12">
            <h4>错题掌握程度分布</h4>
            <div class="mastery-bars">
              <div v-for="(count, label) in data.errorPatterns.masteryDistribution" :key="label" class="mastery-item">
                <span class="mastery-label">{{ label }}</span>
                <el-progress
                  :percentage="totalWrong > 0 ? Math.round(count / totalWrong * 100) : 0"
                  :color="getMasteryColor(label as string)"
                  :stroke-width="20"
                  :text-inside="true"
                  :format="() => count + ' 道'"
                />
              </div>
            </div>
            <el-descriptions :column="1" border style="margin-top: 16px">
              <el-descriptions-item label="反复出错题目">{{ data.errorPatterns.repeatedErrorCount }} 道</el-descriptions-item>
              <el-descriptions-item label="近7天新增错题">{{ data.errorPatterns.recentNewWrongCount }} 道</el-descriptions-item>
            </el-descriptions>
          </el-col>
          <el-col :xs="24" :sm="12">
            <h4>高频错题课程</h4>
            <div v-if="data.errorPatterns.topErrorCourses.length">
              <div v-for="c in data.errorPatterns.topErrorCourses" :key="c.courseId" class="error-course-item">
                <span class="course-name">{{ c.courseName }}</span>
                <el-tag type="danger" size="small">{{ c.wrongCount }} 道错题</el-tag>
              </div>
            </div>
            <el-empty v-else description="暂无错题数据" :image-size="60" />
          </el-col>
        </el-row>
      </el-card>

      <!-- 课程掌握概况 -->
      <el-card class="section-card" shadow="hover" v-if="data.courseMasteries.length">
        <template #header>
          <span>📖 课程掌握概况</span>
        </template>
        <el-table :data="data.courseMasteries" stripe>
          <el-table-column prop="courseName" label="课程" min-width="140" />
          <el-table-column label="正确率" width="140">
            <template #default="{ row }">
              <el-progress
                :percentage="Math.round(row.correctRate)"
                :color="getRateColor(row.correctRate)"
                :stroke-width="16"
                :text-inside="true"
              />
            </template>
          </el-table-column>
          <el-table-column prop="totalAttempts" label="练习数" width="80" align="center" />
          <el-table-column prop="wrongCount" label="错题数" width="80" align="center" />
          <el-table-column prop="knowledgePointCount" label="知识点总数" width="100" align="center" />
          <el-table-column prop="weakPointCount" label="薄弱知识点" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.weakPointCount > 0 ? 'danger' : 'success'" size="small">
                {{ row.weakPointCount }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 每日推荐题目 -->
      <el-card class="section-card" shadow="hover" v-if="data.dailyRecommendations.length">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <span>🎯 今日推荐题目</span>
            <el-button type="primary" size="small" @click="startRecommendPractice">
              开始练习
            </el-button>
          </div>
        </template>
        <el-table :data="data.dailyRecommendations" stripe>
          <el-table-column label="题目内容" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.questionContent }}</span>
            </template>
          </el-table-column>
          <el-table-column label="推荐原因" width="160">
            <template #default="{ row }">
              <el-tag :type="getReasonType(row.reason)" size="small">{{ row.reasonDescription }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="questionType" label="题型" width="80" align="center" />
          <el-table-column label="难度" width="100" align="center">
            <template #default="{ row }">
              <span v-if="row.difficulty">{{ '⭐'.repeat(row.difficulty) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="courseName" label="课程" width="120" />
          <el-table-column prop="knowledgePointName" label="知识点" width="120" />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getLearningDiagnosis, getAiAdviceStream, type LearningDiagnosis } from '@/api/statistics'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(true)
const data = ref<LearningDiagnosis | null>(null)

// AI 个性化建议
const aiAdviceLoading = ref(false)
const aiAdviceStreaming = ref(false)
const aiAdviceContent = ref('')
const aiAdviceAbortController = ref<AbortController | null>(null)

async function generateAiAdvice() {
  aiAdviceLoading.value = true
  aiAdviceStreaming.value = true
  aiAdviceContent.value = ''

  try {
    aiAdviceAbortController.value = new AbortController()
    const response = await getAiAdviceStream()

    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('无法读取响应流')
    }

    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          continue
        }
        if (line.startsWith('data:')) {
          const jsonStr = line.slice(5).trim()
          if (!jsonStr) continue
          try {
            const parsed = JSON.parse(jsonStr)
            if (parsed.content !== undefined) {
              aiAdviceContent.value += parsed.content
            }
          } catch {
            // skip non-JSON data
          }
        }
      }
    }
  } catch (e: any) {
    if (e.name !== 'AbortError') {
      ElMessage.error('AI 建议生成失败: ' + (e.message || '未知错误'))
    }
  } finally {
    aiAdviceLoading.value = false
    aiAdviceStreaming.value = false
    aiAdviceAbortController.value = null
  }
}

const adviceLines = computed(() => {
  if (!data.value?.dailyAdvice) return []
  return data.value.dailyAdvice.split('\n').filter(l => l.trim())
})

const totalWrong = computed(() => {
  if (!data.value?.errorPatterns?.masteryDistribution) return 0
  const dist = data.value.errorPatterns.masteryDistribution
  return Object.values(dist).reduce((sum, v) => sum + v, 0)
})

const maxBarValue = computed(() => {
  if (!data.value?.learningHabit?.weeklyTrend) return 1
  const max = Math.max(...data.value.learningHabit.weeklyTrend.map(d => d.total))
  return max || 1
})

function getBarHeight(value: number): string {
  return Math.max(0, (value / maxBarValue.value) * 100) + 'px'
}

function getRateColor(rate: number): string {
  if (rate >= 80) return '#67c23a'
  if (rate >= 60) return '#e6a23c'
  return '#f56c6c'
}

function getStatusType(status: string): 'danger' | 'warning' | 'info' | undefined {
  switch (status) {
    case 'WEAK': return 'danger'
    case 'NEEDS_REVIEW': return 'warning'
    case 'NOT_STARTED': return 'info'
    default: return undefined
  }
}

function getStatusLabel(status: string): string {
  switch (status) {
    case 'WEAK': return '薄弱'
    case 'NEEDS_REVIEW': return '需复习'
    case 'NOT_STARTED': return '未开始'
    default: return status
  }
}

function getMasteryColor(label: string): string {
  if (label.includes('未掌握')) return '#f56c6c'
  if (label.includes('部分')) return '#e6a23c'
  return '#67c23a'
}

function getReasonType(reason: string): 'danger' | 'warning' | 'info' | undefined {
  switch (reason) {
    case 'ERROR_PRONE': return 'danger'
    case 'WEAK_POINT_REINFORCE': return 'warning'
    case 'SPACED_REVIEW': return undefined
    default: return 'info'
  }
}

function startRecommendPractice() {
  if (!data.value?.dailyRecommendations?.length) return
  const qIds = data.value.dailyRecommendations.map(q => q.questionId).join(',')
  router.push({ path: '/practice/session', query: { questionIds: qIds } })
}


onMounted(async () => {
  try {
    const res = await getLearningDiagnosis()
    data.value = res.data
  } catch (e: any) {
    ElMessage.error('加载学习诊断失败: ' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.learning-diagnosis {
  padding: 0 0 24px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
}

.advice-card {
  margin-top: 20px;
}

.advice-content {
  font-size: 15px;
  line-height: 1.8;
  color: #303133;
}

.advice-content p {
  margin: 0 0 4px;
}

.stat-row {
  margin-top: 16px;
}

.stat-card {
  text-align: center;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #409eff;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.section-card {
  margin-top: 16px;
}

.chart-container h4 {
  margin: 0 0 12px;
  font-size: 14px;
  color: #606266;
}

.mini-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  height: 160px;
  padding: 0 8px;
}

.chart-bar-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}

.chart-bar-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 100px;
}

.chart-bar {
  width: 16px;
  min-height: 2px;
  border-radius: 2px 2px 0 0;
  transition: height 0.3s;
}

.chart-bar.correct {
  background: #67c23a;
}

.chart-bar.wrong {
  background: #f56c6c;
}

.chart-date {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}

.chart-total {
  font-size: 12px;
  font-weight: 600;
  color: #303133;
}

.mastery-bars {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mastery-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mastery-label {
  min-width: 60px;
  font-size: 13px;
  color: #606266;
}

.error-course-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.error-course-item:last-child {
  border-bottom: none;
}

.course-name {
  font-weight: 500;
}

.ai-advice-card {
  margin-top: 16px;
  border-left: 3px solid #409eff;
}

.ai-advice-content {
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
}

.ai-advice-content :deep(h1),
.ai-advice-content :deep(h2),
.ai-advice-content :deep(h3) {
  margin-top: 16px;
  margin-bottom: 8px;
  color: #1a1a1a;
}

.ai-advice-content :deep(ul),
.ai-advice-content :deep(ol) {
  padding-left: 20px;
}

.ai-advice-content :deep(p) {
  margin: 8px 0;
}

.ai-advice-content :deep(code) {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 13px;
}

@media (max-width: 768px) {
  .stat-value {
    font-size: 22px;
  }

  .mini-chart {
    height: 120px;
  }

  .chart-bar-wrapper {
    height: 70px;
  }

  .chart-bar {
    width: 10px;
  }
}
</style>