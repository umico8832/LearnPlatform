<template>
  <section class="asset-type-observation">
    <div class="observation-header">
      <div>
        <strong>按资产类型观察同题表现</strong>
        <p>
          分别按每类资产的首次查看时间切分周期内作答；任一组少于 {{ effect.minimumComparisonSample }} 条或
          {{ effect.minimumDistinctUsers }}
          位学习者时不判断方向。多资产暴露样本可能重叠，因此不做资产排名或自动推荐。
        </p>
      </div>
    </div>
    <el-table :data="effect.assetTypeStats" stripe size="small" class="asset-type-effect-table">
      <el-table-column prop="assetTypeLabel" label="资产类型" min-width="128" />
      <el-table-column label="阅读后同题" min-width="118" align="right">
        <template #default="{ row }">
          <div class="asset-type-effect-value">
            <strong>{{ formatRate(row.afterViewCorrectRate) }}</strong>
            <small>{{ row.afterViewPracticeCount }} 条 · {{ row.afterViewUserCount }} 人</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="未阅读 / 阅读前" min-width="132" align="right">
        <template #default="{ row }">
          <div class="asset-type-effect-value">
            <strong>{{ formatRate(row.baselineCorrectRate) }}</strong>
            <small>{{ row.baselinePracticeCount }} 条 · {{ row.baselineUserCount }} 人</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="正确率差异" min-width="116" align="right">
        <template #default="{ row }">
          <strong :class="liftClass(row.correctRateLift)">{{ formatLift(row.correctRateLift) }}</strong>
        </template>
      </el-table-column>
      <el-table-column label="观察状态" min-width="126" align="center">
        <template #default="{ row }">
          <el-tooltip :content="row.conclusion" placement="top">
            <el-tag :type="conclusionTagType(row.conclusionLevel)" size="small" effect="light">
              {{ effectTagLabel(row.conclusionLevel) }}
            </el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="当前周期暂无可分类型观察的数据" :image-size="48" />
      </template>
    </el-table>
  </section>
</template>

<script setup lang="ts">
import type { AiLearningEffect } from '@/api/aiUsage'
import { conclusionTagType, effectTagLabel, formatLift, formatRate, liftClass } from './aiUsageDisplay'

defineProps<{ effect: AiLearningEffect }>()
</script>

<style scoped>
.asset-type-observation {
  margin-top: 22px;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.observation-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.observation-header strong {
  color: var(--lp-text-primary);
  font-size: 15px;
}
.observation-header p {
  margin: 6px 0 0;
  color: var(--lp-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.asset-type-effect-table {
  margin-top: 14px;
}
.asset-type-effect-value {
  display: inline-flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 6px;
}
.asset-type-effect-value strong {
  color: var(--lp-text-primary);
}
.asset-type-effect-value small {
  color: var(--lp-text-tertiary);
  font-size: 11px;
}
.positive {
  color: #67c23a;
}
.negative {
  color: #f56c6c;
}
.neutral {
  color: #909399;
}
</style>
