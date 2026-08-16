<template>
  <div class="npv">
    <!-- 描述 -->
    <div v-if="element.description" class="npv-description">{{ element.description }}</div>

    <!-- 时序图 -->
    <div class="npv-diagram">
      <!-- 实体头部 -->
      <div class="npv-entities">
        <div v-for="(entity, ei) in element.entities" :key="ei" class="npv-entity">
          <div class="npv-entity-box">{{ entity }}</div>
        </div>
      </div>

      <!-- 竖线（生命线） -->
      <div class="npv-lifelines">
        <div v-for="(_, ei) in element.entities" :key="ei" class="npv-lifeline" />
      </div>

      <!-- 消息 -->
      <div class="npv-messages">
        <div
          v-for="(msg, mi) in element.messages"
          :key="mi"
          class="npv-message"
          :class="{
            'npv-message--current': msg.state === 'current',
            'npv-message--highlight': msg.state === 'highlight',
          }"
        >
          <!-- 消息标签（中间） -->
          <div class="npv-message-label">{{ msg.content }}</div>

          <!-- 消息线 + 箭头 -->
          <svg class="npv-message-svg" :viewBox="`0 0 ${svgWidth} 24`">
            <!-- 左边界 x -->
            <line
              :x1="getEntityCenterX(msg.from)"
              y1="12"
              :x2="getEntityCenterX(msg.to)"
              y2="12"
              :stroke="msg.state === 'current' ? '#409eff' : msg.state === 'highlight' ? '#e6a23c' : '#606266'"
              stroke-width="2"
            />
            <!-- 箭头 -->
            <polygon
              :points="getArrowPoints(msg.from, msg.to)"
              :fill="msg.state === 'current' ? '#409eff' : msg.state === 'highlight' ? '#e6a23c' : '#606266'"
            />
          </svg>

          <!-- 消息描述 -->
          <div v-if="msg.description" class="npv-message-desc">{{ msg.description }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { NetworkProtocolElement } from '@/api/ai'

const props = defineProps<{
  element: NetworkProtocolElement
}>()

const entityWidth = 100
const entityGap = 80
const svgWidth = computed(() => {
  const n = props.element.entities.length
  return n * entityWidth + (n - 1) * entityGap + 40
})

function getEntityCenterX(index: number): number {
  const startX = 50
  return startX + index * (entityWidth + entityGap) + entityWidth / 2
}

function getArrowPoints(from: number, to: number): string {
  const cx = getEntityCenterX(to)
  if (to > from) {
    // 向右
    return `${cx - 8},6 ${cx},12 ${cx - 8},18`
  } else {
    // 向左
    return `${cx + 8},6 ${cx},12 ${cx + 8},18`
  }
}
</script>

<script lang="ts">
export default { name: 'NetworkProtocolViewer' }
</script>

<style scoped>
.npv {
  padding: 4px 0;
}

.npv-description {
  font-size: 12px;
  color: #909399;
  margin-bottom: 12px;
}

.npv-diagram {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px 8px;
  overflow-x: auto;
}

/* 实体头部 */
.npv-entities {
  display: flex;
  justify-content: center;
  gap: 80px;
  margin-bottom: 4px;
}

.npv-entity {
  width: 100px;
  display: flex;
  justify-content: center;
}

.npv-entity-box {
  background: #ecf5ff;
  border: 2px solid #409eff;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  text-align: center;
  white-space: nowrap;
}

/* 生命线 */
.npv-lifelines {
  display: flex;
  justify-content: center;
  gap: 80px;
  margin-bottom: 8px;
}

.npv-lifeline {
  width: 100px;
  display: flex;
  justify-content: center;
}

.npv-lifeline::after {
  content: '';
  display: block;
  width: 2px;
  height: calc(100% + 8px);
  background: repeating-linear-gradient(to bottom, #c0c4cc 0, #c0c4cc 4px, transparent 4px, transparent 8px);
}

/* 消息 */
.npv-messages {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 16px;
}

.npv-message {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.npv-message--current {
  background: #ecf5ff;
  border-radius: 6px;
  padding: 4px 8px;
}

.npv-message--highlight {
  background: #fdf6ec;
  border-radius: 6px;
  padding: 4px 8px;
}

.npv-message-label {
  font-size: 12px;
  font-weight: 600;
  color: #303133;
  background: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  margin-bottom: 2px;
  z-index: 1;
  white-space: nowrap;
}

.npv-message-svg {
  width: 100%;
  height: 24px;
  min-width: 200px;
}

.npv-message-desc {
  font-size: 11px;
  color: #909399;
  text-align: center;
  margin-top: 2px;
  max-width: 400px;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .npv-entities {
    gap: 40px;
  }

  .npv-entity {
    width: 70px;
  }

  .npv-entity-box {
    font-size: 11px;
    padding: 6px 8px;
  }

  .npv-lifelines {
    gap: 40px;
  }

  .npv-lifeline {
    width: 70px;
  }

  .npv-message-label {
    font-size: 11px;
  }

  .npv-message-desc {
    font-size: 10px;
  }
}
</style>
