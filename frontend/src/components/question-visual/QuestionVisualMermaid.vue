<template>
  <div class="vi-block">
    <div class="vi-block-label">{{ element.label }}</div>
    <div ref="container" class="vi-mermaid-container" />
    <div v-if="element.caption" class="vi-mermaid-caption">{{ element.caption }}</div>
  </div>
</template>

<script lang="ts">
let mermaidInstance: typeof import('mermaid').default | null = null
let mermaidIdCounter = 0

async function ensureMermaid(): Promise<typeof import('mermaid').default> {
  if (!mermaidInstance) {
    const mod = await import('mermaid')
    mermaidInstance = mod.default
    mermaidInstance.initialize({
      startOnLoad: false,
      theme: 'default',
      securityLevel: 'loose',
      flowchart: { useMaxWidth: true, htmlLabels: true, curve: 'basis' },
    })
  }
  return mermaidInstance
}
</script>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { VisualMermaidElement } from '@/api/ai'

const props = defineProps<{
  element: VisualMermaidElement
}>()

const container = ref<HTMLElement | null>(null)
let renderVersion = 0

async function renderMermaid(code: string) {
  const currentVersion = ++renderVersion
  await nextTick()

  const target = container.value
  if (!target) return

  const mermaid = await ensureMermaid()
  if (currentVersion !== renderVersion) return

  try {
    const id = `mermaid-${++mermaidIdCounter}-${Date.now()}`
    const { svg } = await mermaid.render(id, code)
    if (currentVersion === renderVersion) {
      target.innerHTML = svg
    }
  } catch {
    if (currentVersion !== renderVersion) return

    target.replaceChildren()
    const pre = document.createElement('pre')
    pre.className = 'vi-mermaid-error'
    pre.textContent = code
    target.appendChild(pre)
  }
}

watch(
  () => props.element.code,
  (code) => void renderMermaid(code),
  { immediate: true },
)

onBeforeUnmount(() => {
  renderVersion++
})
</script>

<style scoped>
.vi-block {
  background: #f8f9fa;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 14px;
}

.vi-block-label {
  color: #409eff;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.vi-mermaid-container {
  overflow-x: auto;
  padding: 8px 0;
  text-align: center;
}

.vi-mermaid-container :deep(svg) {
  height: auto;
  max-width: 100%;
}

.vi-mermaid-caption {
  color: #909399;
  font-size: 12px;
  font-style: italic;
  margin-top: 8px;
  text-align: center;
}

.vi-mermaid-error {
  background: #fef0f0;
  border: 1px solid #fbc4c4;
  border-radius: 4px;
  color: #f56c6c;
  font-size: 12px;
  overflow-x: auto;
  padding: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
