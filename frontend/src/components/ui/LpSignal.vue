<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheck, Clock, Warning, QuestionFilled, Right } from '@element-plus/icons-vue'

/**
 * 学习信号：把一个「值得处理的事项」用一句话说明原因。
 * 原因必须是真实学习事实（错题数、到期复习、最近作答），不是 AI 玄学推荐。
 */
const props = withDefaults(
  defineProps<{
    title: string
    reason: string
    tone?: 'default' | 'primary' | 'success' | 'warning' | 'danger'
    icon?: 'continue' | 'review' | 'wrong' | 'done' | 'next'
  }>(),
  { tone: 'default', icon: 'next' },
)

const iconComponent = computed(() => {
  const map = {
    continue: Right,
    review: Clock,
    wrong: Warning,
    done: CircleCheck,
    next: QuestionFilled,
  } as const
  return map[props.icon]
})
</script>

<template>
  <div class="lp-signal" :data-tone="tone">
    <span class="lp-signal-icon" aria-hidden="true">
      <el-icon :size="16"><component :is="iconComponent" /></el-icon>
    </span>
    <div class="lp-signal-copy">
      <strong class="lp-signal-title">{{ title }}</strong>
      <span class="lp-signal-reason">{{ reason }}</span>
    </div>
    <div v-if="$slots.actions" class="lp-signal-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.lp-signal {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  min-width: 0;
  padding: var(--lp-space-3) var(--lp-space-4);
  background: var(--lp-surface);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
}
.lp-signal-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  border-radius: var(--lp-radius-full);
  background: var(--lp-surface-soft);
  color: var(--lp-text-secondary);
}
.lp-signal[data-tone='primary'] .lp-signal-icon {
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
}
.lp-signal[data-tone='success'] .lp-signal-icon {
  background: var(--lp-success-soft);
  color: var(--lp-success);
}
.lp-signal[data-tone='warning'] .lp-signal-icon {
  background: var(--lp-warning-soft);
  color: var(--lp-warning);
}
.lp-signal[data-tone='danger'] .lp-signal-icon {
  background: var(--lp-danger-soft);
  color: var(--lp-danger);
}
.lp-signal-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}
.lp-signal-title {
  color: var(--lp-text);
  font-size: var(--lp-text-base);
  font-weight: var(--lp-weight-semibold);
  line-height: var(--lp-leading-snug);
}
.lp-signal-reason {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-sm);
  line-height: var(--lp-leading-snug);
}
.lp-signal-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
}
</style>
