<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Stage } from '../engine/stage'
import type { Frame, GhostLayer } from '../engine/types'
import '../stage.css'

/** Thin Vue host for the framework-agnostic stage; the engine owns the DOM inside. */
const props = defineProps<{ frame: Frame | null; ghost: GhostLayer | null; label: string }>()

const host = ref<HTMLDivElement | null>(null)
let stage: Stage | null = null

function paint() {
  if (stage && props.frame) stage.render(props.frame, props.ghost)
}

onMounted(() => {
  if (!host.value) return
  stage = new Stage(host.value)
  paint()
})

onBeforeUnmount(() => {
  stage?.destroy()
  stage = null
})

watch(() => [props.frame, props.ghost], paint)
</script>

<template>
  <div ref="host" class="viz-stage" role="img" :aria-label="label" />
</template>

<style scoped>
.viz-stage {
  min-height: 0;
  border-radius: var(--lt-radius-md);
}
</style>
