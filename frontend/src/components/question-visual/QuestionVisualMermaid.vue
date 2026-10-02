<template>
  <div class="vi-block">
    <div class="vi-block-label">{{ element.label }}</div>
    <div ref="container" class="vi-mermaid-container" role="img" :aria-label="element.label" />
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
    mermaidInstance = mod.default
    mermaidInstance.initialize({
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
let renderVersion = 0
let alive = true

async function renderMermaid(code: string) {
  const currentVersion = ++renderVersion
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
    const pre = document.createElement('pre')
    pre.className = 'vi-mermaid-error'
    pre.setAttribute('role', 'status')
    pre.setAttribute('aria-label', '图形无法呈现，显示源代码')
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
