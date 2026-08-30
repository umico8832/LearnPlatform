<template>
  <el-card class="section-card" shadow="hover">
    <template #header><span>⚠️ 错因分析</span></template>
    <el-row :gutter="24">
      <el-col :xs="24" :sm="12">
        <h4>错题掌握程度分布</h4>
        <div class="mastery-bars">
          <div v-for="(count, label) in patterns.masteryDistribution" :key="label" class="mastery-item">
            <span class="mastery-label">{{ label }}</span>
            <el-progress
              :percentage="totalWrong > 0 ? Math.round((count / totalWrong) * 100) : 0"
              :color="masteryColor(label as string)"
              :stroke-width="20"
              :text-inside="true"
              :format="() => count + ' 道'"
            />
          </div>
        </div>
        <el-descriptions :column="1" border class="summary-descriptions">
          <el-descriptions-item label="反复出错题目">{{ patterns.repeatedErrorCount }} 道</el-descriptions-item>
          <el-descriptions-item label="近7天新增错题">{{ patterns.recentNewWrongCount }} 道</el-descriptions-item>
        </el-descriptions>
      </el-col>
      <el-col :xs="24" :sm="12">
        <h4>高频错题课程</h4>
        <div v-if="patterns.topErrorCourses.length">
          <div v-for="course in patterns.topErrorCourses" :key="course.courseId" class="error-course-item">
            <span class="course-name">{{ course.courseName }}</span>
            <el-tag type="danger" size="small">{{ course.wrongCount }} 道错题</el-tag>
          </div>
        </div>
        <el-empty v-else description="暂无错题数据" :image-size="60" />
      </el-col>
    </el-row>

    <el-row :gutter="24" class="distribution-row">
      <el-col :xs="24" :sm="12">
        <h4>📊 错题题型分布</h4>
        <div v-if="Object.keys(patterns.questionTypeDistribution).length">
          <div v-for="(count, typeName) in patterns.questionTypeDistribution" :key="typeName" class="mastery-item">
            <span class="mastery-label">{{ typeName }}</span>
            <el-progress
              :percentage="totalWrong > 0 ? Math.round((count / totalWrong) * 100) : 0"
              color="#409eff"
              :stroke-width="18"
              :text-inside="true"
              :format="() => count + ' 道'"
            />
          </div>
        </div>
        <el-empty v-else description="暂无数据" :image-size="40" />
      </el-col>
      <el-col :xs="24" :sm="12">
        <h4>⭐ 错题难度分布</h4>
        <div v-if="Object.keys(patterns.difficultyDistribution).length">
          <div v-for="(count, diff) in patterns.difficultyDistribution" :key="diff" class="mastery-item">
            <span class="mastery-label">{{ '⭐'.repeat(Number(diff)) }}</span>
            <el-progress
              :percentage="totalWrong > 0 ? Math.round((count / totalWrong) * 100) : 0"
              :color="difficultyColor(Number(diff))"
              :stroke-width="18"
              :text-inside="true"
              :format="() => count + ' 道'"
            />
          </div>
        </div>
        <el-empty v-else description="暂无数据" :image-size="40" />
      </el-col>
    </el-row>

    <div v-if="patterns.weeklyErrorTrend.length" class="detail-section">
      <h4>📈 近 4 周错题趋势</h4>
      <div class="mini-chart">
        <div v-for="(week, index) in patterns.weeklyErrorTrend" :key="index" class="chart-bar-group">
          <div class="chart-bar-wrapper">
            <div class="chart-bar error-trend" :style="{ height: weeklyBarHeight(week.count) }"></div>
          </div>
          <div class="chart-date">{{ week.label }}</div>
          <div class="chart-total">{{ week.count }}</div>
        </div>
      </div>
    </div>

    <div v-if="patterns.knowledgePointErrors.length" class="detail-section">
      <h4>🎯 知识点错因排名</h4>
      <el-table :data="patterns.knowledgePointErrors" stripe size="small">
        <el-table-column label="知识点" min-width="160">
          <template #default="{ row }">
            <div>
              <strong>{{ row.knowledgePointName }}</strong>
              <el-tag size="small" type="info" class="course-tag">{{ row.courseName }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="wrongCount" label="错题数" width="80" align="center">
          <template #default="{ row }"
            ><el-tag type="danger" size="small">{{ row.wrongCount }}</el-tag></template
          >
        </el-table-column>
        <el-table-column prop="totalAttempts" label="练习数" width="80" align="center" />
        <el-table-column label="正确率" width="120">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.round(row.correctRate)"
              :color="rateColor(row.correctRate)"
              :stroke-width="14"
              :text-inside="true"
            />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="patterns.repeatedErrors.length" class="detail-section">
      <h4>🔄 反复错题详情</h4>
      <el-table :data="patterns.repeatedErrors" stripe size="small">
        <el-table-column label="题目" min-width="240" show-overflow-tooltip>
          <template #default="{ row }"
            ><span>{{ row.questionContent }}</span></template
          >
        </el-table-column>
        <el-table-column prop="questionType" label="题型" width="80" align="center" />
        <el-table-column label="难度" width="80" align="center">
          <template #default="{ row }"
            ><span v-if="row.difficulty">{{ '⭐'.repeat(row.difficulty) }}</span></template
          >
        </el-table-column>
        <el-table-column prop="wrongCount" label="错次" width="70" align="center">
          <template #default="{ row }"
            ><el-tag type="danger" size="small">{{ row.wrongCount }}</el-tag></template
          >
        </el-table-column>
        <el-table-column label="掌握度" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="masteryLevelType(row.masteryLevel)" size="small">
              {{ masteryLevelLabel(row.masteryLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="knowledgePointName" label="知识点" width="120" />
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              @click="emit('similar-question', row.questionId, row.questionContent)"
            >
              找相似题
            </el-button>
            <el-button type="warning" link size="small" @click="emit('question-error-analysis', row.questionId)">
              错因分析
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ErrorPatternSummary } from '@/api/statistics'
import { difficultyColor, masteryColor, masteryLevelLabel, masteryLevelType, rateColor } from './diagnosisDisplay'

const props = defineProps<{ patterns: ErrorPatternSummary }>()
const emit = defineEmits<{
  'similar-question': [questionId: number, questionContent?: string]
  'question-error-analysis': [questionId: number]
}>()

const totalWrong = computed(() =>
  Object.values(props.patterns.masteryDistribution).reduce((sum, value) => sum + value, 0),
)
const maxWeeklyBarValue = computed(() => {
  const max = Math.max(...props.patterns.weeklyErrorTrend.map((week) => week.count))
  return max || 1
})

function weeklyBarHeight(value: number): string {
  return Math.max(4, (value / maxWeeklyBarValue.value) * 100) + 'px'
}
</script>

<style scoped>
.section-card,
.summary-descriptions {
  margin-top: var(--lp-space-4);
}

.distribution-row,
.detail-section {
  margin-top: var(--lp-space-5);
}

.mastery-bars {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-3);
}

.mastery-item {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
}

.mastery-label {
  min-width: 60px;
  font-size: var(--lp-text-sm);
  color: var(--lp-text-secondary);
}

.error-course-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--lp-space-2) 0;
  border-bottom: var(--lp-border-hairline);
}

.error-course-item:last-child {
  border-bottom: none;
}

.course-name {
  font-weight: var(--lp-weight-medium);
}

.course-tag {
  margin-left: var(--lp-space-2);
}

.mini-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  height: 160px;
  padding: 0 var(--lp-space-2);
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
  border-radius: var(--lp-radius-xs) var(--lp-radius-xs) 0 0;
  transition: height var(--lp-duration-slow) var(--lp-ease-out);
}

.chart-bar.error-trend {
  background: var(--lp-warning);
}

.chart-date {
  font-size: var(--lp-text-xs);
  color: var(--lp-text-muted);
  margin-top: var(--lp-space-1);
}

.chart-total {
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-semibold);
  color: var(--lp-text);
}

@media (max-width: 768px) {
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
