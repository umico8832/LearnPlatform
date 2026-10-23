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
function getThemeColor(name: string): string {
  const probe = document.createElement('span')
  probe.style.color = `var(${name})`
  document.body.append(probe)
  const color = getComputedStyle(probe).color
  probe.remove()
  return color
}

function getThemeFont(): string {
  return getComputedStyle(document.documentElement).getPropertyValue('--lt-font').trim()
}

function svgContentPadding(): number {
  return Number.parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--lt-space-2'))
}

function fitSvgToContent(svg: SVGSVGElement) {
  if (typeof svg.getBBox !== 'function') return

  const bounds = svg.getBBox()
  if (!Number.isFinite(bounds.width) || !Number.isFinite(bounds.height) || bounds.width <= 0 || bounds.height <= 0)
    return

  const padding = svgContentPadding()
  const x = bounds.x - padding
  const y = bounds.y - padding
  const width = bounds.width + padding * 2
  const height = bounds.height + padding * 2
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
        primaryColor: getThemeColor('--lt-surface'),
        primaryTextColor: getThemeColor('--lt-label'),
        primaryBorderColor: getThemeColor('--lt-accent'),
        lineColor: getThemeColor('--lt-accent'),
        secondaryColor: getThemeColor('--lt-well'),
        secondaryTextColor: getThemeColor('--lt-label'),
        secondaryBorderColor: getThemeColor('--lt-separator'),
        tertiaryColor: getThemeColor('--lt-fill-2'),
        tertiaryTextColor: getThemeColor('--lt-label'),
        tertiaryBorderColor: getThemeColor('--lt-separator'),
        textColor: getThemeColor('--lt-label'),
        mainBkg: getThemeColor('--lt-surface'),
        nodeBorder: getThemeColor('--lt-separator'),
        clusterBkg: getThemeColor('--lt-well'),
        clusterBorder: getThemeColor('--lt-separator'),
        edgeLabelBackground: getThemeColor('--lt-surface'),
        fontFamily: getThemeFont(),
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
  padding: var(--lt-space-4);
  background: var(--lt-well);
  border: thin solid var(--lt-separator);
  border-radius: var(--lt-radius-md);
}
.vi-block-label {
  margin-bottom: var(--lt-space-2);
  color: var(--lt-label);
  font-size: var(--lt-text-footnote);
  font-weight: 600;
}
.vi-mermaid-container {
  min-height: calc(var(--lt-text-body) * 2);
  padding-block: var(--lt-space-2);
  overflow-x: auto;
  text-align: center;
}
.vi-mermaid-container :deep(svg) {
  max-width: 100%;
  height: auto;
}
.vi-mermaid-status,
.vi-mermaid-render-error {
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
}
.vi-mermaid-render-error {
  display: grid;
  gap: var(--lt-space-2);
}
.vi-mermaid-render-error p {
  margin: 0;
}
.vi-mermaid-render-error button {
  justify-self: start;
  min-height: var(--lt-control-sm);
  padding: 0 var(--lt-space-3);
  border: calc(var(--lt-space-1) * 0.25) solid var(--lt-separator);
  border-radius: var(--lt-radius-pill);
  background: var(--lt-surface);
  color: var(--lt-accent);
  font: inherit;
  cursor: pointer;
}
.vi-mermaid-caption {
  margin-top: var(--lt-space-2);
  color: var(--lt-label-2);
  font-size: var(--lt-text-caption);
  font-style: italic;
  text-align: center;
}
.vi-mermaid-error {
  padding: var(--lt-space-3);
  color: var(--lt-label-2);
  background: var(--lt-well);
  border: thin solid var(--lt-separator);
  border-radius: var(--lt-radius-sm);
  font-size: var(--lt-text-caption);
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
