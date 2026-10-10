<script setup lang="ts">
import { ref } from 'vue'
import { Braces, Pause, Play, RotateCcw, SkipBack, SkipForward } from '@lucide/vue'
import {
  LtButton,
  LtCard,
  LtField,
  LtIconButton,
  LtSegmented,
  LtSheet,
  LtStepper,
  type SegmentOption,
} from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'

/** 开发预览：在浅色与深色两种外观下并排检查新基线的令牌与基础组件。 */
useLivingTheme()

const appearances = [
  { id: 'light', label: '浅色' },
  { id: 'dark', label: '深色' },
] as const

const swatches = [
  ['canvas', '画布'],
  ['surface', '卡片'],
  ['label', '正文'],
  ['label-2', '次要'],
  ['label-3', '辅助'],
  ['accent', '操作'],
  ['green', '正确'],
  ['orange', '注意'],
  ['red', '错误'],
  ['purple', '预测'],
]
const variables = ['blue', 'indigo', 'orange', 'green', 'pink', 'teal', 'purple', 'graphite']
const typeScale = [
  ['title1', '一道题从错到懂'],
  ['title2', '单链表的逆置'],
  ['title3', '第 3 步：p 指向下一个结点'],
  ['headline', '先预测，再揭晓'],
  ['reading', '头插法会把原链表的结点依次摘下，插到头结点之后，因此顺序恰好反过来。'],
  ['body', '界面正文，用于控件说明和短句。'],
  ['footnote', '脚注：模拟器自检通过'],
  ['caption', '说明文字 · 12 px'],
]

const ops: SegmentOption<'insert' | 'delete' | 'reverse'>[] = [
  { value: 'insert', label: '插入' },
  { value: 'delete', label: '删除' },
  { value: 'reverse', label: '逆置', hint: '3' },
]

interface Pane {
  op: 'insert' | 'delete' | 'reverse'
  size: 'sm' | 'md'
  playing: boolean
  values: string
  committed: string
  error: string
  position: number
  sheetOpen: boolean
  sheetOrigin: HTMLElement | null
}

function createPane(): Pane {
  return {
    op: 'reverse',
    size: 'md',
    playing: false,
    values: '3 1 4 1 5',
    committed: '3 1 4 1 5',
    error: '',
    position: 2,
    sheetOpen: false,
    sheetOrigin: null,
  }
}

const panes = ref<Record<string, Pane>>({ light: createPane(), dark: createPane() })

function commitValues(pane: Pane, text: string) {
  const parts = text
    .trim()
    .split(/[\s,，、]+/)
    .filter(Boolean)
  const numbers = parts.map(Number)
  if (numbers.some((n) => !Number.isInteger(n) || n < 0 || n > 99)) pane.error = '只能是 0–99 的整数'
  else if (numbers.length < 2 || numbers.length > 8) pane.error = '需要 2–8 个数'
  else {
    pane.error = ''
    pane.committed = numbers.join(' ')
    pane.values = pane.committed
  }
}

function cancelValues(pane: Pane) {
  pane.values = pane.committed
  pane.error = ''
}

function openSheet(pane: Pane, event: MouseEvent) {
  pane.sheetOrigin = event.currentTarget as HTMLElement
  pane.sheetOpen = true
}
</script>

<template>
  <main class="kit">
    <section
      v-for="appearance in appearances"
      :key="appearance.id"
      class="lt kit-pane"
      :data-appearance="appearance.id"
      :aria-label="`${appearance.label}外观`"
    >
      <header class="kit-head">
        <h1>新视觉基线 · {{ appearance.label }}</h1>
        <p>会动的课本：阅读安静，理解会动。</p>
      </header>

      <LtCard as="section" heading="h2">
        <template #title>颜色只承载含义</template>
        <div class="kit-swatches">
          <div v-for="[token, name] in swatches" :key="token" class="kit-swatch">
            <span class="kit-chip" :style="{ background: `var(--lt-${token})` }" />
            <span>{{ name }}</span>
            <code>{{ token }}</code>
          </div>
        </div>
        <div class="kit-vars">
          <span v-for="name in variables" :key="name" class="kit-var" :style="{ background: `var(--lt-var-${name})` }">
            {{ name }}
          </span>
        </div>
      </LtCard>

      <LtCard as="section" heading="h2">
        <template #title>字号</template>
        <p
          v-for="[size, sample] in typeScale"
          :key="size"
          class="kit-type"
          :data-size="size"
          :style="{ fontSize: `var(--lt-text-${size})` }"
        >
          <span>{{ sample }}</span>
          <code>{{ size }}</code>
        </p>
        <p class="kit-numerals">0 1 2 3 4 5 6 7 8 9</p>
      </LtCard>

      <LtCard as="section" heading="h2">
        <template #title>控件</template>
        <template #meta>弹簧：snappy</template>
        <div class="kit-row">
          <LtSegmented v-model="panes[appearance.id].op" :options="ops" label="操作" />
          <LtSegmented
            v-model="panes[appearance.id].size"
            :options="[
              { value: 'sm', label: '小' },
              { value: 'md', label: '中' },
            ]"
            label="尺寸"
            size="sm"
          />
        </div>
        <div class="kit-row">
          <LtButton variant="primary" :size="panes[appearance.id].size">继续</LtButton>
          <LtButton :size="panes[appearance.id].size">直接看答案</LtButton>
          <LtButton variant="quiet" :size="panes[appearance.id].size">稍后再说</LtButton>
          <LtButton :size="panes[appearance.id].size" disabled>不可用</LtButton>
        </div>
        <div class="kit-row">
          <LtIconButton label="回到开头"><RotateCcw aria-hidden="true" /></LtIconButton>
          <LtIconButton label="上一步"><SkipBack aria-hidden="true" /></LtIconButton>
          <LtIconButton
            :label="panes[appearance.id].playing ? '暂停' : '播放'"
            variant="primary"
            size="lg"
            @click="panes[appearance.id].playing = !panes[appearance.id].playing"
          >
            <Pause v-if="panes[appearance.id].playing" fill="currentColor" aria-hidden="true" />
            <Play v-else fill="currentColor" aria-hidden="true" />
          </LtIconButton>
          <LtIconButton label="下一步"><SkipForward aria-hidden="true" /></LtIconButton>
          <LtIconButton label="AI 场景描述" variant="quiet" size="sm" @click="openSheet(panes[appearance.id], $event)">
            <Braces aria-hidden="true" />
          </LtIconButton>
        </div>
        <div class="kit-row">
          <LtField
            v-model="panes[appearance.id].values"
            label="数据"
            description="2–8 个 0–99 的整数，用空格分隔"
            :error="panes[appearance.id].error"
            @commit="commitValues(panes[appearance.id], $event)"
            @cancel="cancelValues(panes[appearance.id])"
          />
          <LtStepper v-model="panes[appearance.id].position" label="位置" :min="1" :max="6" />
        </div>
      </LtCard>

      <LtSheet
        v-model:open="panes[appearance.id].sheetOpen"
        title="AI 场景描述"
        :origin="panes[appearance.id].sheetOrigin"
      >
        <p class="kit-sheet-text">
          AI 导师只决定“展示什么”：用哪种结构、哪组数据、在哪里停下来提问。每一步怎么变，由确定性的模拟器算出来。
        </p>
        <pre class="kit-sheet-code">{ "scene": "linked-list", "op": "{{ panes[appearance.id].op }}" }</pre>
      </LtSheet>
    </section>
  </main>
</template>

<style scoped>
.kit {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  min-height: 100vh;
}

.kit-pane {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-5);
  padding: var(--lt-space-8) var(--lt-space-7);
  background: var(--lt-canvas);
}

.kit-head h1 {
  margin: 0;
  font-size: var(--lt-text-title1);
}

.kit-head p {
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-label-2);
}

.kit-swatches {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--lt-space-3);
}

.kit-swatch {
  display: grid;
  gap: 2px;
  font-size: var(--lt-text-caption);
}

.kit-swatch code,
.kit-type code {
  color: var(--lt-label-3);
  font: var(--lt-text-caption2) / 1.4 var(--lt-font-mono);
}

.kit-chip {
  height: 40px;
  margin-bottom: 4px;
  border-radius: var(--lt-radius-sm);
  box-shadow: inset 0 0 0 0.5px var(--lt-separator);
}

.kit-vars {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: var(--lt-space-4);
}

.kit-var {
  padding: 3px 9px;
  border-radius: var(--lt-radius-pill);
  color: var(--lt-on-accent);
  font: 600 var(--lt-text-caption) / 1.3 var(--lt-font-mono);
}

.kit-type {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--lt-space-4);
  margin: 0 0 var(--lt-space-2);
  line-height: var(--lt-leading-tight);
}

.kit-type[data-size='reading'] {
  line-height: var(--lt-leading-reading);
}

.kit-type:is([data-size='title1'], [data-size='title2'], [data-size='title3'], [data-size='headline']) span {
  font-weight: 600;
  letter-spacing: var(--lt-tracking-title);
}

.kit-numerals {
  margin: var(--lt-space-3) 0 0;
  font: 600 var(--lt-text-title2) / 1 var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.kit-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--lt-space-3);
  margin-top: var(--lt-space-4);
}

.kit-sheet-text {
  margin: 0 0 var(--lt-space-4);
  color: var(--lt-label-2);
  font-size: var(--lt-text-subhead);
  line-height: var(--lt-leading-reading);
}

.kit-sheet-code {
  margin: 0;
  padding: 14px 16px;
  border-radius: var(--lt-radius-md);
  background: var(--lt-code-bg);
  font: 12.5px / 1.65 var(--lt-font-mono);
}
</style>
