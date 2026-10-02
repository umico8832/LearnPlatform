<template>
  <div class="npv">
    <p v-if="element.description" class="npv-description">{{ element.description }}</p>
    <div class="npv-diagram" role="region" :aria-label="element.label || '协议消息时序图'" tabindex="0">
      <div
        class="npv-canvas"
        :style="{ width: `${svgWidth}px`, '--entity-gap': `${entityGap}px`, '--canvas-inset': `${canvasInset}px` }"
      >
        <div
          class="npv-entities"
          :style="{ gridTemplateColumns: `repeat(${element.entities.length}, ${entityWidth}px)` }"
        >
          <div v-for="(entity, index) in element.entities" :key="index" class="npv-entity">{{ entity }}</div>
        </div>
        <ol class="npv-messages">
          <li
            v-for="(msg, index) in element.messages"
            :key="index"
            class="npv-message"
            :class="{
              'npv-message--current': msg.state === 'current',
              'npv-message--highlight': msg.state === 'highlight',
            }"
            :aria-current="msg.state === 'current' ? 'step' : undefined"
          >
            <div class="npv-message-route">
              <span>{{ element.entities[msg.from] }} → {{ element.entities[msg.to] }}</span>
              <span v-if="msg.state === 'current'" class="npv-state">当前步骤</span>
              <span v-else-if="msg.state === 'highlight'" class="npv-state">重点步骤</span>
            </div>
            <div class="npv-message-label">{{ msg.content }}</div>
            <svg class="npv-message-svg" :viewBox="`0 0 ${svgWidth} 24`" aria-hidden="true" focusable="false">
              <line
                :x1="entityCenter(msg.from)"
                y1="12"
                :x2="entityCenter(msg.to)"
                y2="12"
                stroke="currentColor"
                stroke-width="2"
              />
              <polygon :points="arrowPoints(msg.from, msg.to)" fill="currentColor" />
            </svg>
            <p v-if="msg.description" class="npv-message-desc">{{ msg.description }}</p>
          </li>
        </ol>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { NetworkProtocolElement } from '@/api/ai'

const props = defineProps<{ element: NetworkProtocolElement }>()
const entityWidth = 100
const entityGap = 80
const canvasInset = 20
const svgWidth = computed(
  () => Math.max(1, props.element.entities.length) * (entityWidth + entityGap) - entityGap + canvasInset * 2,
)
function entityCenter(index: number) {
  return canvasInset + index * (entityWidth + entityGap) + entityWidth / 2
}
function arrowPoints(from: number, to: number) {
  const end = entityCenter(to)
  const start = end + (to > from ? -8 : 8)
  return `${start},6 ${end},12 ${start},18`
}
</script>

<style scoped>
.npv {
  padding: var(--lp-space-1) 0;
}
.npv-description {
  margin: 0 0 var(--lp-space-3);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-body);
}
.npv-diagram {
  overflow-x: auto;
  padding: var(--lp-space-4) var(--lp-space-2);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
}
.npv-diagram:focus-visible {
  outline: var(--lp-focus-width) solid var(--lp-focus-ring);
  outline-offset: var(--lp-focus-offset);
}
.npv-canvas {
  margin: 0 auto;
}
.npv-entities {
  display: grid;
  align-items: stretch;
  gap: var(--entity-gap);
  padding: 0 var(--canvas-inset);
  margin-bottom: var(--lp-space-4);
}
.npv-entity {
  padding: var(--lp-space-2);
  border: 1px solid var(--lp-border-strong);
  border-radius: var(--lp-radius-sm);
  background: var(--lp-surface-soft);
  color: var(--lp-text);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
  line-height: var(--lp-leading-body);
  text-align: center;
  overflow-wrap: anywhere;
}
.npv-messages {
  display: grid;
  gap: var(--lp-space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}
.npv-message {
  padding: var(--lp-space-2) 0;
  border: 1px solid transparent;
  border-radius: var(--lp-radius-sm);
  color: var(--lp-text-secondary);
  text-align: center;
}
.npv-message--current {
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
  border-color: var(--lp-primary);
}
.npv-message--highlight {
  color: var(--lp-warning);
  background: var(--lp-warning-soft);
  border-color: currentColor;
  border-style: dashed;
}
.npv-message-route {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: var(--lp-space-2);
  padding: 0 var(--lp-space-3);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
  overflow-wrap: anywhere;
}
.npv-state {
  color: var(--lp-text);
  font-weight: var(--lp-weight-medium);
}
.npv-message-label {
  padding: var(--lp-space-1) var(--lp-space-3) 0;
  color: var(--lp-text);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
  line-height: var(--lp-leading-body);
  overflow-wrap: anywhere;
}
.npv-message-svg {
  display: block;
  width: 100%;
  height: 24px;
}
.npv-message-desc {
  margin: 0;
  padding: 0 var(--lp-space-3);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
  line-height: var(--lp-leading-body);
  overflow-wrap: anywhere;
}
@media (forced-colors: active) {
  .npv-message--current,
  .npv-message--highlight {
    border-color: CanvasText;
  }
}
</style>
