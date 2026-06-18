<template>
  <div class="opv">
    <!-- 描述 -->
    <div v-if="element.description" class="opv-description">{{ element.description }}</div>

    <!-- 过程步骤展示 -->
    <div class="opv-steps">
      <div
        v-for="(step, si) in element.steps"
        :key="si"
        class="opv-step"
        :class="{ 'opv-step--active': si === currentStep }"
      >
        <div class="opv-step-header" @click="currentStep = si">
          <span class="opv-step-num">{{ si + 1 }}</span>
          <span class="opv-step-desc">{{ step.description }}</span>
          <span class="opv-step-toggle">{{ si === currentStep ? '▼' : '▶' }}</span>
        </div>
        <div v-if="si === currentStep" class="opv-step-body">
          <!-- 进程状态表格 -->
          <table class="opv-state-table">
            <thead>
              <tr>
                <th>进程/线程</th>
                <th>状态</th>
                <th>信息</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(item, ii) in step.state"
                :key="ii"
                :class="`opv-state-row--${item.state}`"
              >
                <td class="opv-state-name">{{ item.name }}</td>
                <td>
                  <span class="opv-state-badge" :class="`opv-state-badge--${item.state}`">
                    {{ getStateLabel(item.state) }}
                  </span>
                </td>
                <td class="opv-state-info">{{ item.info || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 甘特图 -->
    <div v-if="element.ganttChart && element.ganttChart.length > 0" class="opv-gantt-section">
      <div class="opv-gantt-title">📊 调度甘特图</div>
      <div class="opv-gantt">
        <!-- 时间刻度 -->
        <div class="opv-gantt-timeline">
          <div class="opv-gantt-label-col" />
          <div class="opv-gantt-bars-col">
            <span
              v-for="t in ganttMaxTime"
              :key="t"
              class="opv-gantt-tick"
              :style="{ left: getGanttPos(t - 1) + '%' }"
            >{{ t - 1 }}</span>
          </div>
        </div>
        <!-- 甘特条 -->
        <div
          v-for="(item, gi) in element.ganttChart"
          :key="gi"
          class="opv-gantt-row"
        >
          <div class="opv-gantt-label-col">{{ item.label }}</div>
          <div class="opv-gantt-bars-col">
            <div
              class="opv-gantt-bar"
              :style="{
                left: getGanttPos(item.start) + '%',
                width: getGanttPos(item.end - item.start) + '%',
              }"
            >
              <span class="opv-gantt-bar-label">{{ item.start }}-{{ item.end }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { OsProcessElement } from '@/api/ai'

const props = defineProps<{
  element: OsProcessElement
}>()

const currentStep = ref(0)

const ganttMaxTime = computed(() => {
  if (!props.element.ganttChart || props.element.ganttChart.length === 0) return 20
  return Math.max(...props.element.ganttChart.map(g => g.end), 10) + 2
})

function getGanttPos(value: number): number {
  const max = ganttMaxTime.value || 20
  return Math.round((value / max) * 100)
}

function getStateLabel(state: string): string {
  const labels: Record<string, string> = {
    running: '🏃 运行中',
    ready: '✅ 就绪',
    waiting: '⏳ 等待',
    blocked: '🔒 阻塞',
    terminated: '⛔ 终止',
    new: '🆕 新建',
  }
  return labels[state] || state
}
</script>

<script lang="ts">
export default { name: 'OsProcessViewer' }
</script>

<style scoped>
.opv {
  padding: 4px 0;
}

.opv-description {
  font-size: 12px;
  color: #909399;
  margin-bottom: 12px;
}

/* 步骤 */
.opv-steps {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.opv-step {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
  transition: border-color 0.2s;
}

.opv-step--active {
  border-color: #409eff;
}

.opv-step-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
  background: #fafafa;
  transition: background 0.2s;
}

.opv-step-header:hover {
  background: #f0f2f5;
}

.opv-step--active .opv-step-header {
  background: #ecf5ff;
}

.opv-step-num {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.opv-step-desc {
  flex: 1;
  font-size: 13px;
  color: #303133;
}

.opv-step-toggle {
  font-size: 10px;
  color: #909399;
}

.opv-step-body {
  padding: 8px 14px 14px;
  background: #fff;
}

/* 状态表格 */
.opv-state-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.opv-state-table th {
  background: #f5f7fa;
  padding: 6px 10px;
  text-align: left;
  font-weight: 600;
  color: #606266;
  border-bottom: 1px solid #ebeef5;
}

.opv-state-table td {
  padding: 6px 10px;
  border-bottom: 1px solid #f0f2f5;
}

.opv-state-name {
  font-weight: 500;
  color: #303133;
}

.opv-state-info {
  color: #909399;
}

.opv-state-row--running {
  background: #f0f9eb;
}

.opv-state-row--ready {
  background: #ecf5ff;
}

.opv-state-row--waiting,
.opv-state-row--blocked {
  background: #fdf6ec;
}

.opv-state-row--terminated {
  background: #fef0f0;
}

/* 状态徽章 */
.opv-state-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
}

.opv-state-badge--running {
  background: #f0f9eb;
  color: #67c23a;
  border: 1px solid #c2e7b0;
}

.opv-state-badge--ready {
  background: #ecf5ff;
  color: #409eff;
  border: 1px solid #b3d8ff;
}

.opv-state-badge--waiting,
.opv-state-badge--blocked {
  background: #fdf6ec;
  color: #e6a23c;
  border: 1px solid #f5dab1;
}

.opv-state-badge--terminated {
  background: #fef0f0;
  color: #f56c6c;
  border: 1px solid #fbc4c4;
}

/* 甘特图 */
.opv-gantt-section {
  margin-top: 16px;
  background: #f8f9fa;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 14px;
}

.opv-gantt-title {
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
  margin-bottom: 10px;
}

.opv-gantt {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.opv-gantt-timeline {
  display: flex;
  align-items: flex-end;
}

.opv-gantt-label-col {
  min-width: 60px;
  font-size: 12px;
  color: #606266;
  font-weight: 500;
  padding-right: 8px;
  text-align: right;
  white-space: nowrap;
}

.opv-gantt-bars-col {
  flex: 1;
  position: relative;
  height: 20px;
}

.opv-gantt-tick {
  position: absolute;
  transform: translateX(-50%);
  font-size: 10px;
  color: #909399;
  bottom: 0;
}

.opv-gantt-row {
  display: flex;
  align-items: center;
}

.opv-gantt-bar {
  position: absolute;
  height: 24px;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
}

.opv-gantt-bar-label {
  font-size: 10px;
  color: #fff;
  font-weight: 600;
  white-space: nowrap;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .opv-step-header {
    padding: 8px 10px;
  }

  .opv-step-desc {
    font-size: 12px;
  }

  .opv-state-table {
    font-size: 11px;
  }

  .opv-state-table th,
  .opv-state-table td {
    padding: 4px 6px;
  }

  .opv-gantt-label-col {
    min-width: 40px;
    font-size: 11px;
  }
}
</style>