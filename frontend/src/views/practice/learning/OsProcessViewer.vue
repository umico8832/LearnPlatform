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
        <button type="button" class="opv-step-header" :aria-expanded="si === currentStep" @click="currentStep = si">
          <span class="opv-step-num">{{ si + 1 }}</span>
          <span class="opv-step-desc">{{ step.description }}</span>
          <span class="opv-step-toggle">{{ si === currentStep ? '▼' : '▶' }}</span>
        </button>
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
              <tr v-for="(item, ii) in step.state" :key="ii" :class="`opv-state-row--${item.state}`">
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
              >{{ t - 1 }}</span
            >
          </div>
        </div>
        <!-- 甘特条 -->
        <div v-for="(item, gi) in element.ganttChart" :key="gi" class="opv-gantt-row">
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
  return Math.max(...props.element.ganttChart.map((g) => g.end), 10) + 2
})

function getGanttPos(value: number): number {
  const max = ganttMaxTime.value || 20
  return Math.round((value / max) * 100)
}

function getStateLabel(state: string): string {
  const labels: Record<string, string> = {
    running: '运行中',
    ready: '就绪',
    waiting: '等待',
    blocked: '阻塞',
    terminated: '终止',
    new: '新建',
  }
  return labels[state] || state
}
</script>

<script lang="ts">
export default { name: 'OsProcessViewer' }
</script>

<style scoped>
.opv {
  padding: var(--lt-space-1) 0;
}

.opv-description {
  font-size: var(--lt-text-footnote);
  color: var(--lt-label-2);
  margin-bottom: var(--lt-space-3);
}

/* 步骤 */
.opv-steps {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-1);
}

.opv-step {
  border: calc(var(--lt-space-1) * 0.25) solid var(--lt-separator);
  border-radius: calc(var(--lt-space-1) * 1.5);
  overflow: hidden;
  transition: border-color 0.2s;
}

.opv-step--active {
  border-color: var(--lt-accent-fill);
}

.opv-step-header {
  width: 100%;
  border: 0;
  display: flex;
  align-items: center;
  gap: calc(var(--lt-space-1) * 2.5);
  padding: calc(var(--lt-space-1) * 2.5) calc(var(--lt-space-1) * 3.5);
  cursor: pointer;
  background: var(--lt-well);
  transition: background 0.2s;
}

.opv-step-header:hover {
  background: var(--lt-fill-2);
}

.opv-step--active .opv-step-header {
  background: var(--lt-accent-soft);
}

.opv-step-num {
  flex-shrink: 0;
  width: var(--lt-space-6);
  height: var(--lt-space-6);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--lt-accent-fill);
  color: var(--lt-on-accent);
  font-size: var(--lt-text-footnote);
  font-weight: 600;
}

.opv-step-desc {
  flex: 1;
  font-size: var(--lt-text-footnote);
  color: var(--lt-label);
}

.opv-step-toggle {
  font-size: var(--lt-text-footnote);
  color: var(--lt-label-2);
}

.opv-step-body {
  padding: var(--lt-space-2) calc(var(--lt-space-1) * 3.5) calc(var(--lt-space-1) * 3.5);
  background: var(--lt-well);
}

/* 状态表格 */
.opv-state-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--lt-text-footnote);
}

.opv-state-table th {
  background: var(--lt-well);
  padding: calc(var(--lt-space-1) * 1.5) calc(var(--lt-space-1) * 2.5);
  text-align: left;
  font-weight: 600;
  color: var(--lt-label-2);
  border-bottom: calc(var(--lt-space-1) * 0.25) solid var(--lt-separator);
}

.opv-state-table td {
  padding: calc(var(--lt-space-1) * 1.5) calc(var(--lt-space-1) * 2.5);
  border-bottom: calc(var(--lt-space-1) * 0.25) solid var(--lt-fill-2);
}

.opv-state-name {
  font-weight: 500;
  color: var(--lt-label);
}

.opv-state-info {
  color: var(--lt-label-2);
}

.opv-state-row--running {
  background: var(--lt-green-soft);
}

.opv-state-row--ready {
  background: var(--lt-accent-soft);
}

.opv-state-row--waiting,
.opv-state-row--blocked {
  background: var(--lt-orange-soft);
}

.opv-state-row--terminated {
  background: var(--lt-orange-soft);
}

/* 状态徽章 */
.opv-state-badge {
  display: inline-block;
  padding: calc(var(--lt-space-1) * 0.5) var(--lt-space-2);
  border-radius: calc(var(--lt-space-1) * 2.5);
  font-size: var(--lt-text-footnote);
  font-weight: 500;
}

.opv-state-badge--running {
  background: var(--lt-green-soft);
  color: var(--lt-green-text);
  border: calc(var(--lt-space-1) * 0.25) solid var(--lt-green-soft);
}

.opv-state-badge--ready {
  background: var(--lt-accent-soft);
  color: var(--lt-accent-fill);
  border: calc(var(--lt-space-1) * 0.25) solid var(--lt-accent-soft);
}

.opv-state-badge--waiting,
.opv-state-badge--blocked {
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
  border: calc(var(--lt-space-1) * 0.25) solid var(--lt-orange-soft);
}

.opv-state-badge--terminated {
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
  border: calc(var(--lt-space-1) * 0.25) solid var(--lt-orange-soft);
}

/* 甘特图 */
.opv-gantt-section {
  margin-top: var(--lt-space-4);
  background: var(--lt-well);
  border: calc(var(--lt-space-1) * 0.25) solid var(--lt-separator);
  border-radius: var(--lt-space-2);
  padding: var(--lt-space-3) calc(var(--lt-space-1) * 3.5);
}

.opv-gantt-title {
  font-size: var(--lt-text-footnote);
  font-weight: 600;
  color: var(--lt-accent-fill);
  margin-bottom: calc(var(--lt-space-1) * 2.5);
}

.opv-gantt {
  display: flex;
  flex-direction: column;
  gap: calc(var(--lt-space-1) * 1.5);
}

.opv-gantt-timeline {
  display: flex;
  align-items: flex-end;
}

.opv-gantt-label-col {
  min-width: calc(var(--lt-space-1) * 15);
  font-size: var(--lt-text-footnote);
  color: var(--lt-label-2);
  font-weight: 500;
  padding-right: var(--lt-space-2);
  text-align: right;
  white-space: nowrap;
}

.opv-gantt-bars-col {
  flex: 1;
  position: relative;
  height: var(--lt-space-5);
}

.opv-gantt-tick {
  position: absolute;
  transform: translateX(-50%);
  font-size: var(--lt-text-footnote);
  color: var(--lt-label-2);
  bottom: 0;
}

.opv-gantt-row {
  display: flex;
  align-items: center;
}

.opv-gantt-bar {
  position: absolute;
  height: var(--lt-space-6);
  background: var(--lt-accent-fill);
  border-radius: var(--lt-space-1);
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: var(--lt-space-6);
}

.opv-gantt-bar-label {
  font-size: var(--lt-text-footnote);
  color: var(--lt-on-accent);
  font-weight: 600;
  white-space: nowrap;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .opv-step-header {
    padding: var(--lt-space-2) calc(var(--lt-space-1) * 2.5);
  }

  .opv-step-desc {
    font-size: var(--lt-text-footnote);
  }

  .opv-state-table {
    font-size: var(--lt-text-footnote);
  }

  .opv-state-table th,
  .opv-state-table td {
    padding: var(--lt-space-1) calc(var(--lt-space-1) * 1.5);
  }

  .opv-gantt-label-col {
    min-width: var(--lt-space-8);
    font-size: var(--lt-text-footnote);
  }
}
</style>
