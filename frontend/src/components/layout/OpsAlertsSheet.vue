<script setup lang="ts">
import { RotateCcw } from '@lucide/vue'
import { LtButton, LtSheet } from '@/components/living'
import type { AiUsageAlert } from '@/api/aiUsage'

/** Administrators' open AI usage alerts, acknowledged one by one. */
defineProps<{
  alerts: AiUsageAlert[]
  loading: boolean
  error: string
  acknowledgeErrors: Record<number, string>
  isAcknowledging: (id: number) => boolean
}>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ refresh: []; acknowledge: [id: number] }>()
</script>

<template>
  <LtSheet v-model:open="open" title="AI 运营提醒" :width="480">
    <div class="toolbar">
      <LtButton size="sm" variant="quiet" :disabled="loading" @click="emit('refresh')">
        <template #icon><RotateCcw :size="14" aria-hidden="true" /></template>
        刷新
      </LtButton>
    </div>
    <p v-if="loading" class="status" role="status">正在读取提醒…</p>
    <div v-else-if="error" class="status" role="alert">
      <p>{{ error }}</p>
      <LtButton size="sm" @click="emit('refresh')">重新加载</LtButton>
    </div>
    <ul v-else-if="alerts.length" class="alerts">
      <li v-for="alert in alerts" :key="alert.id || alert.type" class="alert">
        <div class="alert-body">
          <p class="alert-title">
            <span class="level" :data-level="alert.level">{{ alert.level === 'WARNING' ? '告警' : '提示' }}</span>
            {{ alert.type }}
          </p>
          <p class="alert-message">{{ alert.message }}</p>
          <p v-if="alert.periodStart && alert.periodEnd" class="alert-period">
            {{ alert.periodStart }} 至 {{ alert.periodEnd }}
          </p>
          <p v-if="alert.id && acknowledgeErrors[alert.id]" class="alert-error" role="status">
            {{ acknowledgeErrors[alert.id] }}
          </p>
        </div>
        <LtButton
          v-if="alert.id"
          size="sm"
          :disabled="isAcknowledging(alert.id)"
          @click="emit('acknowledge', alert.id)"
        >
          {{ isAcknowledging(alert.id) ? '确认中…' : '确认' }}
        </LtButton>
      </li>
    </ul>
    <p v-else class="status">没有未确认的提醒。</p>
  </LtSheet>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: flex-end;
  margin: calc(var(--lt-space-2) * -1) 0 var(--lt-space-2);
}

.status {
  display: grid;
  justify-items: center;
  gap: var(--lt-space-3);
  margin: 0;
  padding: var(--lt-space-6) 0;
  color: var(--lt-label-2);
  text-align: center;
}

.status p {
  margin: 0;
}

.alerts {
  margin: 0;
  padding: 0;
  list-style: none;
}

.alert {
  display: flex;
  align-items: flex-start;
  gap: var(--lt-space-4);
  padding: var(--lt-space-4) 0;
  border-top: 0.5px solid var(--lt-separator);
}

.alert-body {
  flex: 1;
  min-width: 0;
}

.alert-title {
  display: flex;
  margin: 0;
  align-items: center;
  gap: var(--lt-space-2);
  font-weight: 600;
}

.level {
  padding: 0 8px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
  font-weight: 500;
  line-height: 20px;
}

.level[data-level='WARNING'] {
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
}

.alert-message {
  margin: 6px 0 0;
  color: var(--lt-label-2);
  line-height: 1.55;
}

.alert-period {
  margin: 4px 0 0;
  color: var(--lt-label-3);
  font-size: var(--lt-text-footnote);
  font-variant-numeric: tabular-nums;
}

.alert-error {
  margin: 6px 0 0;
  color: var(--lt-red-text);
  font-size: var(--lt-text-footnote);
}
</style>
