<template>
  <el-card shadow="never" class="report-card">
    <template #header>
      <div class="report-header">
        <div>
          <span>运营报告</span>
          <span class="report-subtitle">与前一 {{ report.days }} 天周期对比</span>
        </div>
        <el-tag :type="report.alerts.length ? 'warning' : 'success'" effect="light">
          {{ report.alerts.length ? `${report.alerts.length} 项待关注` : '暂无待处理提醒' }}
        </el-tag>
      </div>
    </template>
    <el-row :gutter="16" class="report-metrics">
      <el-col :xs="12" :sm="6">
        <div class="report-metric">
          <span>调用量环比</span>
          <strong :class="changeClass(report.changes.callsPercent)">{{
            formatChange(report.changes.callsPercent)
          }}</strong>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="report-metric">
          <span>Token 环比</span>
          <strong :class="changeClass(report.changes.tokensPercent)">{{
            formatChange(report.changes.tokensPercent)
          }}</strong>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="report-metric">
          <span>失败率变化</span>
          <strong :class="changeClass(report.changes.failureRatePointChange, true)">
            {{ formatPointChange(report.changes.failureRatePointChange) }}
          </strong>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="report-metric">
          <span>平均耗时环比</span>
          <strong :class="changeClass(report.changes.avgDurationPercent)">
            {{ formatChange(report.changes.avgDurationPercent) }}
          </strong>
        </div>
      </el-col>
    </el-row>
    <el-alert
      v-for="alert in report.alerts"
      :key="alert.type"
      :title="alert.message"
      :type="alert.level === 'WARNING' ? 'warning' : 'info'"
      :closable="false"
      show-icon
      class="usage-alert"
    >
      <template #default>
        <div class="usage-alert-content">
          <span v-if="alert.periodStart && alert.periodEnd" class="usage-alert-period">
            {{ alert.periodStart }} 至 {{ alert.periodEnd }}
          </span>
          <el-button
            v-if="alert.id && alert.status === 'OPEN'"
            link
            type="primary"
            :loading="acknowledgingId === alert.id"
            :disabled="acknowledgingId !== null"
            @click="$emit('acknowledge', alert.id)"
          >
            确认
          </el-button>
        </div>
      </template>
    </el-alert>
    <p v-if="!report.alerts.length" class="no-alerts">当前周期没有待处理的运营提醒。</p>
  </el-card>
</template>

<script setup lang="ts">
import type { AiUsageReport } from '@/api/aiUsage'
import { changeClass, formatChange, formatPointChange } from './aiUsageDisplay'

defineProps<{ report: AiUsageReport; acknowledgingId: number | null }>()
defineEmits<{ acknowledge: [id: number] }>()
</script>

<style scoped>
.no-alerts {
  margin: var(--lp-space-4) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.report-card {
  margin-bottom: 16px;
  border-left: 3px solid var(--lp-primary);
}
.report-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.report-subtitle {
  margin-left: 8px;
  color: var(--lp-text-muted);
  font-size: 13px;
  font-weight: normal;
}
.report-metrics {
  margin-bottom: 12px;
}
.report-metric {
  display: flex;
  min-height: 58px;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  padding: 0 12px;
  border-left: 1px solid var(--lp-border);
}
.report-metric span {
  color: var(--lp-text-muted);
  font-size: 13px;
}
.report-metric strong {
  font-size: 18px;
}
.positive {
  color: var(--lp-success);
}
.negative {
  color: var(--lp-danger);
}
.neutral {
  color: var(--lp-text-muted);
}
.usage-alert + .usage-alert {
  margin-top: 8px;
}
.usage-alert-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.usage-alert-period {
  color: var(--lp-text-muted);
  font-size: 12px;
}
@media (max-width: 768px) {
  .report-metric {
    border-left: none;
    padding: 8px 0;
  }
}
</style>
