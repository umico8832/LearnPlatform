<template>
  <div class="ca-viewer">
    <!-- 控制栏 -->
    <div class="ca-controls">
      <div class="playback-buttons">
        <LtButton aria-label="上一步" @click="prevStep" :disabled="currentStep <= 0">
          <span><DArrowLeft /></span> 上一步
        </LtButton>
        <LtButton :aria-label="playing ? '暂停' : '播放'" :aria-pressed="playing" @click="togglePlay" variant="primary">
          <span v-if="playing"><VideoPause /></span>
          <span v-else><VideoPlay /></span>
          {{ playing ? '暂停' : '播放' }}
        </LtButton>
        <LtButton aria-label="下一步" @click="nextStep" :disabled="currentStep >= element.steps.length - 1">
          下一步 <span><DArrowRight /></span>
        </LtButton>
      </div>
      <span class="ca-step-info"> 步骤 {{ currentStep + 1 }} / {{ element.steps.length }} </span>
      <div class="ca-speed">
        <span class="ca-speed-label">速度：</span>
        <select v-model="speedMs" aria-label="动画速度">
          <option :value="500">快</option>
          <option :value="1000">正常</option>
          <option :value="2000">慢</option>
        </select>
      </div>
    </div>

    <!-- 进度条 -->
    <div class="ca-progress-bar">
      <div class="ca-progress-fill" :style="{ width: ((currentStep + 1) / element.steps.length) * 100 + '%' }" />
    </div>

    <!-- 主体：代码 + 变量面板 -->
    <div class="ca-body">
      <!-- 代码区域 -->
      <div class="ca-code-panel">
        <div v-if="element.language" class="ca-lang-tag">{{ element.language }}</div>
        <pre class="ca-code"><code><template v-for="(line, li) in highlightedLines" :key="li"><span
          :class="{
            'ca-line': true,
            'ca-line--active': isLineActive(li + 1),
            'ca-line--dim': !isLineActive(li + 1),
          }"
        ><span class="ca-line-num">{{ li + 1 }}</span><span v-html="line" /></span></template></code></pre>
      </div>

      <!-- 变量面板 -->
      <div class="ca-var-panel">
        <div class="ca-var-title">变量状态</div>
        <div v-if="currentStepData.variables.length === 0" class="ca-var-empty">暂无变量</div>
        <div v-else class="ca-var-list">
          <div
            v-for="(v, vi) in currentStepData.variables"
            :key="vi"
            class="ca-var-item"
            :class="{ 'ca-var-item--changed': v.changed }"
          >
            <span class="ca-var-name">{{ v.name }}</span>
            <span class="ca-var-eq">=</span>
            <span class="ca-var-value">{{ v.value }}</span>
            <span v-if="v.changed" class="ca-var-badge">已变化</span>
          </div>
        </div>

        <!-- 控制台输出 -->
        <div v-if="currentStepData.output" class="ca-output">
          <div class="ca-output-title">输出</div>
          <pre class="ca-output-text">{{ currentStepData.output }}</pre>
        </div>
      </div>
    </div>

    <!-- 步骤描述 -->
    <div class="ca-description">
      {{ currentStepData.description }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { LtButton } from '@/components/living'
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import {
  ChevronsLeft as DArrowLeft,
  ChevronsRight as DArrowRight,
  Play as VideoPlay,
  Pause as VideoPause,
} from '@lucide/vue'
import type { CodeAnimationElement } from '@/api/ai'
import hljs from 'highlight.js/lib/core'

// 按需加载常用语言（避免打包全部语言）
import python from 'highlight.js/lib/languages/python'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import java from 'highlight.js/lib/languages/java'
import cpp from 'highlight.js/lib/languages/cpp'
import c from 'highlight.js/lib/languages/c'
import sql from 'highlight.js/lib/languages/sql'
import go from 'highlight.js/lib/languages/go'
import rust from 'highlight.js/lib/languages/rust'
import css from 'highlight.js/lib/languages/css'
import xml from 'highlight.js/lib/languages/xml'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import ruby from 'highlight.js/lib/languages/ruby'
import php from 'highlight.js/lib/languages/php'
import swift from 'highlight.js/lib/languages/swift'
import kotlin from 'highlight.js/lib/languages/kotlin'
import csharp from 'highlight.js/lib/languages/csharp'

hljs.registerLanguage('python', python)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('java', java)
hljs.registerLanguage('cpp', cpp)
hljs.registerLanguage('c++', cpp)
hljs.registerLanguage('c', c)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('go', go)
hljs.registerLanguage('rust', rust)
hljs.registerLanguage('css', css)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('json', json)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('ruby', ruby)
hljs.registerLanguage('php', php)
hljs.registerLanguage('swift', swift)
hljs.registerLanguage('kotlin', kotlin)
hljs.registerLanguage('csharp', csharp)

const props = defineProps<{
  element: CodeAnimationElement
}>()

const currentStep = ref(0)
const playing = ref(false)
const speedMs = ref(1000)
let timer: ReturnType<typeof setTimeout> | null = null

const codeLines = computed(() => props.element.code.split('\n'))

/** 语言别名映射 */
const langAlias: Record<string, string> = {
  py: 'python',
  python3: 'python',
  js: 'javascript',
  ts: 'typescript',
  'c++': 'cpp',
  'c#': 'csharp',
  sh: 'bash',
  shell: 'bash',
  rb: 'ruby',
  kt: 'kotlin',
  htm: 'html',
}

function resolveLanguage(lang?: string): string {
  if (!lang) return ''
  const normalized = lang.toLowerCase().trim()
  return langAlias[normalized] || normalized
}

/**
 * 逐行高亮代码。
 * 对代码动画场景，逐行高亮比整块高亮更可靠（避免跨行 span 闭合问题），
 * 代价是失去跨行上下文（如多行字符串），对代码动画演示可接受。
 */
const highlightedLines = computed(() => {
  const lang = resolveLanguage(props.element.language)
  const canUseLang = lang && hljs.getLanguage(lang)

  return codeLines.value.map((line) => {
    if (!line) return ''
    try {
      if (canUseLang) {
        return hljs.highlight(line, { language: lang }).value
      }
      return hljs.highlightAuto(line).value
    } catch {
      return escapeHtml(line)
    }
  })
})

function escapeHtml(text: string): string {
  // 使用 unicode 转义避免编辑器自动格式化破坏 HTML 实体
  const amp = '\u0026'
  return text
    .replace(/&/g, amp + 'amp;')
    .replace(/</g, amp + 'lt;')
    .replace(/>/g, amp + 'gt;')
}

const currentStepData = computed(() => props.element.steps[currentStep.value])

function isLineActive(lineNum: number): boolean {
  const step = currentStepData.value
  return lineNum >= step.lineStart && lineNum <= step.lineEnd
}

function nextStep() {
  if (currentStep.value < props.element.steps.length - 1) {
    currentStep.value++
  } else {
    playing.value = false
  }
}

function prevStep() {
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

function togglePlay() {
  playing.value = !playing.value
}

watch(playing, (val) => {
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
  if (val) {
    scheduleNext()
  }
})

watch(speedMs, () => {
  if (playing.value) {
    if (timer) clearTimeout(timer)
    scheduleNext()
  }
})

function scheduleNext() {
  timer = setTimeout(() => {
    if (!playing.value) return
    if (currentStep.value < props.element.steps.length - 1) {
      currentStep.value++
      scheduleNext()
    } else {
      playing.value = false
    }
  }, speedMs.value)
}

watch(
  () => props.element,
  () => {
    currentStep.value = 0
    playing.value = false
  },
)

onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
})
</script>

<script lang="ts">
export default { name: 'CodeAnimationViewer' }
</script>

<style scoped>
.ca-viewer {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-2);
}

/* 控制栏 */
.ca-controls {
  display: flex;
  align-items: center;
  gap: var(--lt-space-3);
  flex-wrap: wrap;
}

.ca-step-info {
  font-size: var(--lt-text-footnote);
  color: var(--lt-label-2);
  white-space: nowrap;
}

.ca-speed {
  display: flex;
  align-items: center;
  gap: var(--lt-space-1);
  margin-left: auto;
}

.ca-speed-label {
  font-size: var(--lt-text-footnote);
  color: var(--lt-label-2);
}

/* 进度条 */
.ca-progress-bar {
  height: var(--lt-space-1);
  background: var(--lt-separator);
  border-radius: calc(var(--lt-space-1) * 0.5);
  overflow: hidden;
}

.ca-progress-fill {
  height: 100%;
  background: var(--lt-accent-fill);
  border-radius: calc(var(--lt-space-1) * 0.5);
  transition: width 0.3s ease;
}

/* 主体 */
.ca-body {
  display: flex;
  gap: var(--lt-space-3);
  border: calc(var(--lt-space-1) * 0.25) solid var(--lt-separator);
  border-radius: calc(var(--lt-space-1) * 1.5);
  overflow: hidden;
  background: var(--lt-code-bg);
}

/* 代码面板 */
.ca-code-panel {
  flex: 1;
  min-width: 0;
  position: relative;
  overflow-x: auto;
}

.ca-lang-tag {
  position: absolute;
  top: var(--lt-space-1);
  right: var(--lt-space-2);
  font-size: var(--lt-text-footnote);
  color: var(--lt-label-2);
  background: var(--lt-fill-2);
  padding: calc(var(--lt-space-1) * 0.25) calc(var(--lt-space-1) * 1.5);
  border-radius: calc(var(--lt-space-1) * 0.75);
  text-transform: uppercase;
}

.ca-code {
  margin: 0;
  padding: var(--lt-space-3) 0;
  font-family: var(--lt-font-mono);
  font-size: var(--lt-text-footnote);
  line-height: 1.7;
  color: var(--lt-label);
  overflow-x: auto;
}

.ca-code code {
  display: block;
}

.ca-line {
  display: block;
  padding: 0 var(--lt-space-3) 0 0;
  white-space: pre;
  transition: background 0.2s ease;
}

.ca-line--active {
  background: var(--lt-fill-2);
  border-left: calc(var(--lt-space-1) * 0.75) solid var(--lt-accent-fill);
  padding-left: calc(var(--lt-space-1) * 2.25);
}

.ca-line--dim {
  padding-left: var(--lt-space-3);
  opacity: 0.5;
}

.ca-line-num {
  display: inline-block;
  width: var(--lt-space-7);
  text-align: right;
  margin-right: var(--lt-space-3);
  color: var(--lt-label-2);
  user-select: none;
  font-size: var(--lt-text-footnote);
}

/* 变量面板 */
.ca-var-panel {
  width: calc(var(--lt-space-1) * 55);
  flex-shrink: 0;
  background: var(--lt-surface);
  border-left: calc(var(--lt-space-1) * 0.25) solid var(--lt-separator);
  padding: var(--lt-space-2) calc(var(--lt-space-1) * 2.5);
  display: flex;
  flex-direction: column;
  gap: calc(var(--lt-space-1) * 1.5);
}

.ca-var-title {
  font-size: var(--lt-text-footnote);
  font-weight: 600;
  color: var(--lt-label-2);
  padding-bottom: var(--lt-space-1);
  border-bottom: calc(var(--lt-space-1) * 0.25) solid var(--lt-separator);
}

.ca-var-empty {
  font-size: var(--lt-text-footnote);
  color: var(--lt-label-2);
  padding: var(--lt-space-1) 0;
}

.ca-var-list {
  display: flex;
  flex-direction: column;
  gap: calc(var(--lt-space-1) * 0.75);
}

.ca-var-item {
  display: flex;
  align-items: center;
  gap: var(--lt-space-1);
  padding: calc(var(--lt-space-1) * 0.75) calc(var(--lt-space-1) * 1.5);
  border-radius: calc(var(--lt-space-1) * 0.75);
  font-size: var(--lt-text-footnote);
  font-family: var(--lt-font-mono);
  transition: background 0.2s ease;
}

.ca-var-item--changed {
  background: var(--lt-fill-2);
}

.ca-var-name {
  color: var(--lt-syn-key);
}

.ca-var-eq {
  color: var(--lt-label);
}

.ca-var-value {
  color: var(--lt-syn-string);
}

.ca-var-badge {
  font-size: var(--lt-text-footnote);
  color: var(--lt-on-tint);
  background: var(--lt-orange);
  padding: 0 var(--lt-space-1);
  border-radius: calc(var(--lt-space-1) * 0.75);
  margin-left: auto;
  font-family: sans-serif;
}

/* 输出区域 */
.ca-output {
  margin-top: var(--lt-space-1);
  border-top: calc(var(--lt-space-1) * 0.25) solid var(--lt-separator);
  padding-top: calc(var(--lt-space-1) * 1.5);
}

.ca-output-title {
  font-size: var(--lt-text-footnote);
  color: var(--lt-label-2);
  margin-bottom: var(--lt-space-1);
}

.ca-output-text {
  margin: 0;
  font-size: var(--lt-text-footnote);
  color: var(--lt-syn-comment);
  font-family: var(--lt-font-mono);
  white-space: pre-wrap;
  word-break: break-all;
}

/* 步骤描述 */
.ca-description {
  background: var(--lt-accent-soft);
  border: calc(var(--lt-space-1) * 0.25) solid var(--lt-accent-soft);
  border-radius: calc(var(--lt-space-1) * 1.5);
  padding: var(--lt-space-2) var(--lt-space-3);
  font-size: var(--lt-text-footnote);
  color: var(--lt-label);
  display: flex;
  align-items: flex-start;
  gap: var(--lt-space-2);
  line-height: 1.5;
}

/* highlight.js 主题覆盖（全局 CSS 影响 v-html 内容） */
:deep(.hljs) {
  background: transparent;
  padding: 0;
}

/* 练习页不再载入旧版 github-dark；高亮语义直接映射到新基线的代码令牌。 */
:deep(.hljs-keyword),
:deep(.hljs-selector-tag),
:deep(.hljs-literal) {
  color: var(--lt-syn-keyword);
}

:deep(.hljs-string),
:deep(.hljs-attr),
:deep(.hljs-template-variable) {
  color: var(--lt-syn-string);
}

:deep(.hljs-number),
:deep(.hljs-symbol),
:deep(.hljs-bullet) {
  color: var(--lt-syn-number);
}

:deep(.hljs-title),
:deep(.hljs-type),
:deep(.hljs-built_in) {
  color: var(--lt-syn-type);
}

:deep(.hljs-comment),
:deep(.hljs-quote) {
  color: var(--lt-syn-comment);
}

/* 移动端适配 */
@media (max-width: 768px) {
  .ca-body {
    flex-direction: column;
  }
  .ca-var-panel {
    width: 100%;
    border-left: none;
    border-top: calc(var(--lt-space-1) * 0.25) solid var(--lt-separator);
  }
  .ca-speed {
    margin-left: 0;
  }
}
</style>
