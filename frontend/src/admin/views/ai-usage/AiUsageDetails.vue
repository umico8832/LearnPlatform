<template>
  <el-row :gutter="16" class="chart-row">
    <el-col :xs="24" :md="12">
      <el-card shadow="hover">
        <template #header><span>功能调用详情</span></template>
        <el-table :data="overview.functionStats" stripe size="small" max-height="360">
          <el-table-column prop="functionType" label="功能" min-width="120" />
          <el-table-column prop="count" label="调用次数" width="90" align="right" />
          <el-table-column label="成功率" width="80" align="right">
            <template #default="{ row }">
              {{ row.count > 0 ? ((row.successCount / row.count) * 100).toFixed(1) : 0 }}%
            </template>
          </el-table-column>
          <el-table-column label="Tokens" width="90" align="right">
            <template #default="{ row }">{{ formatTokens(row.totalTokens) }}</template>
          </el-table-column>
          <el-table-column label="成本(USD)" width="100" align="right">
            <template #default="{ row }">{{ formatCost(row.totalCostUsd) }}</template>
          </el-table-column>
          <el-table-column label="平均耗时" width="90" align="right">
            <template #default="{ row }">{{ row.avgDuration ? row.avgDuration + 'ms' : '-' }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-col>
    <el-col :xs="24" :md="12">
      <el-card shadow="hover">
        <template #header><span>Top 活跃用户</span></template>
        <el-table :data="overview.topUsers" stripe size="small" max-height="360">
          <el-table-column label="#" width="50" type="index" align="center" />
          <el-table-column prop="username" label="用户名" min-width="100" />
          <el-table-column prop="callCount" label="调用次数" width="90" align="right" />
          <el-table-column label="Tokens" width="90" align="right">
            <template #default="{ row }">{{ formatTokens(row.totalTokens) }}</template>
          </el-table-column>
          <el-table-column label="成本(USD)" width="100" align="right">
            <template #default="{ row }">{{ formatCost(row.totalCostUsd) }}</template>
          </el-table-column>
          <el-table-column label="平均耗时" width="90" align="right">
            <template #default="{ row }">{{ row.avgDuration ? row.avgDuration + 'ms' : '-' }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-col>
  </el-row>

  <el-card v-if="overview.recentFailures?.length" shadow="hover" class="chart-card">
    <template #header>
      <div class="failure-header">
        <span>最近失败调用</span>
        <el-tag type="danger" size="small">{{ overview.recentFailures.length }} 条</el-tag>
      </div>
    </template>
    <el-table :data="overview.recentFailures" stripe size="small" max-height="400">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="functionType" label="功能" width="140" />
      <el-table-column prop="model" label="模型" width="140" />
      <el-table-column label="Trace ID" width="110">
        <template #default="{ row }">{{ row.traceId || '-' }}</template>
      </el-table-column>
      <el-table-column label="Prompt 指纹" width="110">
        <template #default="{ row }">{{ shortHash(row.promptHash) }}</template>
      </el-table-column>
      <el-table-column label="模型配置" width="110">
        <template #default="{ row }">{{ shortHash(row.modelConfigVersion) }}</template>
      </el-table-column>
      <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip />
      <el-table-column prop="createTime" label="时间" width="170" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import type { AiUsageOverview } from '@/api/aiUsage'
import { formatCost, formatTokens, shortHash } from './aiUsageDisplay'

defineProps<{ overview: AiUsageOverview }>()
</script>

<style scoped>
.chart-row,
.chart-card {
  margin-bottom: 16px;
}
.failure-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
