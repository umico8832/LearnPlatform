<template>
  <div class="vi-block">
    <div class="vi-block-label">{{ element.label }}</div>
    <div ref="container" class="vi-mermaid-container" role="img" :aria-label="element.label" :aria-busy="rendering" />
    <p v-if="rendering" class="vi-mermaid-status" role="status">正在渲染图形…</p>
    <div v-else-if="renderError" class="vi-mermaid-render-error" role="alert">
      <p>图形无法呈现，已显示源代码。</p>
      <button type="button" @click="retryRender">重新渲染</button>
      <pre class="vi-mermaid-error">{{ element.code }}</pre>
    </div>
    <div v-if="element.caption" class="vi-mermaid-caption">{{ element.caption }}</div>
  </div>
</template>

<script lang="ts">
let mermaidInstance: typeof import('mermaid').default | null = null
let mermaidIdCounter = 0
const SVG_CONTENT_PADDING = 8

function getThemeToken(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

function fitSvgToContent(svg: SVGSVGElement) {
  if (typeof svg.getBBox !== 'function') return

  const bounds = svg.getBBox()
  if (!Number.isFinite(bounds.width) || !Number.isFinite(bounds.height) || bounds.width <= 0 || bounds.height <= 0)
    return

  const x = bounds.x - SVG_CONTENT_PADDING
  const y = bounds.y - SVG_CONTENT_PADDING
  const width = bounds.width + SVG_CONTENT_PADDING * 2
  const height = bounds.height + SVG_CONTENT_PADDING * 2
  svg.setAttribute('viewBox', `${x} ${y} ${width} ${height}`)
  svg.setAttribute('height', String(height))
}

async function ensureMermaid(): Promise<typeof import('mermaid').default> {
  if (!mermaidInstance) {
    const mod = await import('mermaid')
    const candidate = mod.default
    candidate.initialize({
      startOnLoad: false,
      theme: 'base',
      securityLevel: 'strict',
      // Mermaid 11 reads this global setting before the deprecated flowchart one.
      // SVG <text> survives the strict sanitizer; HTML labels would require foreignObject.
      htmlLabels: false,
      themeVariables: {
        primaryColor: getThemeToken('--lp-surface'),
        primaryTextColor: getThemeToken('--lp-text'),
        primaryBorderColor: getThemeToken('--lp-primary'),
        lineColor: getThemeToken('--lp-primary'),
        secondaryColor: getThemeToken('--lp-surface-subtle'),
        secondaryTextColor: getThemeToken('--lp-text'),
        secondaryBorderColor: getThemeToken('--lp-border-strong'),
        tertiaryColor: getThemeToken('--lp-surface-soft'),
        tertiaryTextColor: getThemeToken('--lp-text'),
        tertiaryBorderColor: getThemeToken('--lp-border'),
        textColor: getThemeToken('--lp-text'),
        mainBkg: getThemeToken('--lp-surface'),
        nodeBorder: getThemeToken('--lp-border-strong'),
        clusterBkg: getThemeToken('--lp-surface-subtle'),
        clusterBorder: getThemeToken('--lp-border'),
        edgeLabelBackground: getThemeToken('--lp-surface'),
        fontFamily: getThemeToken('--lp-font-sans'),
      },
      flowchart: { useMaxWidth: true, htmlLabels: false, curve: 'basis' },
    })
    mermaidInstance = candidate
  }
  return mermaidInstance
}
</script>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import DOMPurify from 'dompurify'
import type { VisualMermaidElement } from '@/api/ai'

const props = defineProps<{
  element: VisualMermaidElement
}>()

const container = ref<HTMLElement | null>(null)
const rendering = ref(false)
const renderError = ref(false)
let renderVersion = 0
let alive = true

async function renderMermaid(code: string) {
  const currentVersion = ++renderVersion
  rendering.value = true
  renderError.value = false
  await nextTick()

  const target = container.value
  if (!target) return

  try {
    const mermaid = await ensureMermaid()
    if (!alive || currentVersion !== renderVersion) return

    const id = `mermaid-${++mermaidIdCounter}-${Date.now()}`
    const { svg } = await mermaid.render(id, code)
    if (alive && currentVersion === renderVersion) {
      const sanitizedHost = document.createElement('div')
      sanitizedHost.innerHTML = DOMPurify.sanitize(`<div>${svg}</div>`, {
        USE_PROFILES: { html: true, svg: true, svgFilters: true },
        FORBID_TAGS: ['foreignObject', 'foreignobject', 'script'],
      })
      const sanitizedSvg = sanitizedHost.querySelector('svg')
      if (!sanitizedSvg) throw new Error('Mermaid 未生成可展示的图形')
      // DOMPurify preserves SVG foreignObject in some browser/parser combinations.
      // Keep the render surface text-only even if Mermaid configuration regresses.
      sanitizedSvg.querySelectorAll('foreignObject').forEach((node) => node.remove())
      target.replaceChildren(sanitizedSvg)
      fitSvgToContent(sanitizedSvg)
    }
  } catch {
    if (!alive || currentVersion !== renderVersion) return

    target.replaceChildren()
    renderError.value = true
  } finally {
    if (alive && currentVersion === renderVersion) rendering.value = false
  }
}

function retryRender() {
  void renderMermaid(props.element.code)
}

watch(
  () => props.element.code,
  (code) => void renderMermaid(code),
  { immediate: true },
)

onBeforeUnmount(() => {
  alive = false
  renderVersion++
})
</script>

<style scoped>
.vi-block {
  padding: var(--lp-space-4);
  background: var(--lp-surface-subtle);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
}
.vi-block-label {
  margin-bottom: var(--lp-space-2);
  color: var(--lp-text);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
}
.vi-mermaid-container {
  min-height: 2rem;
  padding-block: var(--lp-space-2);
  overflow-x: auto;
  text-align: center;
}
.vi-mermaid-container :deep(svg) {
  max-width: 100%;
  height: auto;
}
.vi-mermaid-status,
.vi-mermaid-render-error {
  margin: var(--lp-space-2) 0 0;
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
}
.vi-mermaid-render-error {
  display: grid;
  gap: var(--lp-space-2);
}
.vi-mermaid-render-error p {
  margin: 0;
}
.vi-mermaid-render-error button {
  justify-self: start;
  min-height: var(--lp-control-height-small);
  padding: 0 var(--lp-space-3);
  border: 1px solid var(--lp-border-strong);
  border-radius: var(--lp-radius-control);
  background: var(--lp-surface);
  color: var(--lp-primary);
  font: inherit;
  cursor: pointer;
}
.vi-mermaid-caption {
  margin-top: var(--lp-space-2);
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-xs);
  font-style: italic;
  text-align: center;
}
.vi-mermaid-error {
  padding: var(--lp-space-3);
  color: var(--lp-text-secondary);
  background: var(--lp-surface-inset);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-sm);
  font-size: var(--lp-text-xs);
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
