<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { LtField, LtStepper } from '@/components/living'
import type { ParamDef, SceneModule, SceneSpec } from '../engine/types'
import { activeParams, formatParam, paramMax, parseParam } from '../spec'

/** The scene's parameter schema as compact inputs; learners can try their own data. */
const props = defineProps<{ module: SceneModule; spec: SceneSpec }>()
const emit = defineEmits<{ change: [patch: Record<string, unknown>] }>()

const drafts = reactive<Record<string, string>>({})
const errors = reactive<Record<string, string>>({})

const params = computed(() => activeParams(props.module, props.spec.op))

function reset() {
  for (const def of params.value) {
    drafts[def.key] = formatParam(def, props.spec[def.key])
    errors[def.key] = ''
  }
}

watch(() => [props.spec, params.value], reset, { immediate: true })

function describe(def: ParamDef): string {
  if (def.type === 'list') return `${def.min}–${def.max} 个 0–99 的整数，用空格分隔`
  if (def.type === 'text') return def.hint
  return ''
}

function commit(def: ParamDef, text: string) {
  const result = parseParam(def, text)
  errors[def.key] = result.error ?? ''
  if (result.error !== undefined) return
  if (formatParam(def, result.value) !== formatParam(def, props.spec[def.key]))
    emit('change', { [def.key]: result.value })
}

function step(def: ParamDef, value: number) {
  emit('change', { [def.key]: value })
}
</script>

<template>
  <div class="viz-params">
    <template v-for="def in params" :key="def.key">
      <LtStepper
        v-if="def.type === 'int'"
        :model-value="Number(spec[def.key] ?? def.min)"
        :label="def.label"
        :min="def.min"
        :max="paramMax(def, spec)"
        @update:model-value="step(def, $event)"
      />
      <LtField
        v-else
        v-model="drafts[def.key]"
        :label="def.label"
        :error="errors[def.key]"
        :description="describe(def)"
        :chars="def.type === 'text' ? (def.size ?? 14) : 14"
        @commit="commit(def, $event)"
        @cancel="reset"
      />
    </template>
  </div>
</template>

<style scoped>
.viz-params {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
</style>
