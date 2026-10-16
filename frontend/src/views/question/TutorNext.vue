<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRight, RotateCcw } from '@lucide/vue'
import { LtButton } from '@/components/living'
import type { TutorAction } from '@/api/questionTutor'

/**
 * The one thing to do next before the solution opens, as the tutor suggests
 * it: another hint while the ladder has levels left, otherwise the solution.
 * The tutor's reason sits beside the button.
 */
const props = defineProps<{
  action: TutorAction | null
  reason: string
  pending: boolean
  /** A failed hint, solution or suggestion request. */
  error: string
  /** The suggestion itself failed to load, so there is no action to offer. */
  failed: boolean
}>()

const emit = defineEmits<{ hint: []; reveal: []; retry: [] }>()

const HINT_LABELS = { 1: '给我一点提示', 2: '再具体一点', 3: '看一个相似的例子' } as const

const label = computed(() => {
  const action = props.action
  if (action?.type === 'HINT') return props.pending ? '正在取提示…' : HINT_LABELS[action.level]
  return props.pending ? '正在打开…' : '看正解'
})

function act() {
  if (props.action?.type === 'HINT') emit('hint')
  else emit('reveal')
}
</script>

<template>
  <div class="next">
    <template v-if="failed">
      <LtButton @click="emit('retry')">
        <template #icon><RotateCcw aria-hidden="true" /></template>
        重试
      </LtButton>
      <span class="next-note" data-tone="error">{{ error }}</span>
    </template>
    <template v-else-if="action">
      <LtButton variant="primary" size="lg" :disabled="pending" @click="act">
        {{ label }}
        <template #trailing><ArrowRight aria-hidden="true" /></template>
      </LtButton>
      <span class="next-note" :data-tone="error ? 'error' : undefined">{{ error || reason }}</span>
    </template>
  </div>
</template>

<style scoped>
.next {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--lt-space-4);
  margin-top: var(--lt-space-6);
}

.next:empty {
  display: none;
}

.next-note {
  max-width: 44ch;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
  line-height: 1.5;
}

.next-note[data-tone='error'] {
  color: var(--lt-red-text);
}
</style>
