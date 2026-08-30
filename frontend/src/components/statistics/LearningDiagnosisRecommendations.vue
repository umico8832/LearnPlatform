<template>
  <el-card v-if="courseMasteries.length" class="section-card" shadow="hover">
    <template #header><span>📖 课程掌握概况</span></template>
    <el-table :data="courseMasteries" stripe>
      <el-table-column prop="courseName" label="课程" min-width="140" />
      <el-table-column label="正确率" width="140">
        <template #default="{ row }">
          <el-progress
            :percentage="Math.round(row.correctRate)"
            :color="rateColor(row.correctRate)"
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

  <el-card v-if="recommendations.length" class="section-card" shadow="hover">
    <template #header>
      <div class="card-header">
        <span>🎯 今日推荐题目</span>
        <el-button type="primary" size="small" @click="emit('start-recommend-practice')">开始练习</el-button>
      </div>
    </template>
    <el-table :data="recommendations" stripe>
      <el-table-column label="题目内容" min-width="280" show-overflow-tooltip>
        <template #default="{ row }"
          ><span>{{ row.questionContent }}</span></template
        >
      </el-table-column>
      <el-table-column label="推荐原因" width="160">
        <template #default="{ row }">
          <el-tag :type="reasonType(row.reason)" size="small">{{ row.reasonDescription }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="questionType" label="题型" width="80" align="center" />
      <el-table-column label="难度" width="100" align="center">
        <template #default="{ row }"
          ><span v-if="row.difficulty">{{ '⭐'.repeat(row.difficulty) }}</span></template
        >
      </el-table-column>
      <el-table-column prop="courseName" label="课程" width="120" />
      <el-table-column prop="knowledgePointName" label="知识点" width="120" />
      <el-table-column label="操作" width="100" align="center">
        <template #default="{ row }">
          <el-button
            type="primary"
            link
            size="small"
            @click="emit('similar-question', row.questionId, row.questionContent)"
          >
            找相似题
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import type { CourseMastery, RecommendedQuestion } from '@/api/statistics'
import { rateColor, reasonType } from './diagnosisDisplay'

defineProps<{
  courseMasteries: CourseMastery[]
  recommendations: RecommendedQuestion[]
}>()

const emit = defineEmits<{
  'start-recommend-practice': []
  'similar-question': [questionId: number, questionContent?: string]
}>()
</script>

<style scoped>
.section-card {
  margin-top: var(--lp-space-4);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
