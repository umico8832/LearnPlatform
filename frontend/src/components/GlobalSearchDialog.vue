<script setup lang="ts">
/**
 * 全局搜索面板（⌘K 或 /）：顶部一行输入，下方按题目、课程、知识点分组列出结果；
 * 未输入时显示搜索历史和热门搜索。原生 <dialog> 负责顶层、背景失活与 Esc。
 */
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { BookOpen, Clock, FileText, Lightbulb, RotateCcw, Search, TrendingUp, X } from '@lucide/vue'
import type { SearchItem } from '@/api/search'
import { springAnimate, springs } from '@/motion'
import { useGlobalSearchShortcuts } from './search/useGlobalSearchShortcuts'
import { flattenSearchResults, searchResultCount, searchResultIndex } from './search/searchResultModel'
import { splitSearchMatch } from './search/searchText'
import { useGlobalSearchState } from './search/useGlobalSearchState'

type GroupKey = 'q' | 'c' | 'kp'

const router = useRouter()
const visible = ref(false)
const dialogRef = ref<HTMLDialogElement>()
const panelRef = ref<HTMLElement>()
const inputRef = ref<HTMLInputElement>()
const resultsRef = ref<HTMLElement>()
const activeIndex = ref(-1)
let returnFocus: HTMLElement | null = null
let entrance: Animation | null = null
const {
  keyword,
  loading,
  error,
  results,
  suggestions,
  suggestionsLoading,
  suggestionsError,
  historyError,
  historyUpdating,
  hasQuery,
  open: openState,
  close: closeState,
  search,
  retrySearch,
  clearHistory,
  removeHistory,
  retryHistory,
} = useGlobalSearchState()

const totalCount = computed(() => searchResultCount(results.value))
const activeOptionId = computed(() =>
  activeIndex.value >= 0 ? `global-search-option-${activeIndex.value}` : undefined,
)
const groups = computed(() =>
  [
    { key: 'q' as GroupKey, label: '题目', icon: FileText, items: results.value.questions },
    { key: 'c' as GroupKey, label: '课程', icon: BookOpen, items: results.value.courses },
    { key: 'kp' as GroupKey, label: '知识点', icon: Lightbulb, items: results.value.knowledgePoints },
  ].filter((group) => group.items.length > 0),
)
const isMobile = useGlobalSearchShortcuts(visible, open, close)

watch(
  visible,
  (nextVisible) => {
    const dialog = dialogRef.value
    if (!dialog) return
    if (nextVisible) {
      if (!dialog.open) {
        returnFocus = document.activeElement as HTMLElement | null
        dialog.showModal()
      }
      entrance?.cancel()
      entrance = panelRef.value
        ? springAnimate(
            panelRef.value,
            [
              { transform: 'translateY(-8px) scale(0.97)', opacity: 0 },
              { transform: 'none', opacity: 1 },
            ],
            springs.snappy,
          )
        : null
      void nextTick(() => inputRef.value?.focus())
    } else {
      closeState()
      activeIndex.value = -1
      if (dialog.open) dialog.close()
      returnFocus?.focus({ preventScroll: true })
      returnFocus = null
    }
  },
  { flush: 'post' },
)

onBeforeUnmount(() => {
  entrance?.cancel()
  if (dialogRef.value?.open) dialogRef.value.close()
})

function open() {
  visible.value = true
  openState()
}

function close() {
  visible.value = false
}

function onCancel(event: Event) {
  event.preventDefault()
  close()
}

function onDialogClick(event: MouseEvent) {
  if (event.target === dialogRef.value) close()
}

function handleInput() {
  activeIndex.value = -1
  search()
}

function fillKeyword(value: string) {
  keyword.value = value
  activeIndex.value = -1
  search(value)
  void nextTick(() => inputRef.value?.focus())
}

function flatIndex(group: GroupKey, index: number) {
  return searchResultIndex(results.value, group, index)
}

function moveFocus(delta: number) {
  if (!totalCount.value) return
  if (activeIndex.value < 0) activeIndex.value = delta > 0 ? 0 : totalCount.value - 1
  else activeIndex.value = (activeIndex.value + delta + totalCount.value) % totalCount.value
  void nextTick(() =>
    resultsRef.value?.querySelector<HTMLElement>('[aria-selected="true"]')?.scrollIntoView({ block: 'nearest' }),
  )
}

function selectCurrent() {
  const item = flattenSearchResults(results.value)[activeIndex.value]
  if (item) navigateTo(item)
}

function navigateTo(item: SearchItem) {
  close()
  void router.push(item.link)
}

function splitMatch(text: string) {
  return splitSearchMatch(text, keyword.value)
}

defineExpose({ open })
</script>

<template>
  <dialog
    ref="dialogRef"
    class="lt global-search"
    data-appearance="dark"
    aria-labelledby="global-search-title"
    @cancel="onCancel"
    @click="onDialogClick"
  >
    <section ref="panelRef" class="panel" aria-label="全局搜索内容">
      <h2 id="global-search-title" class="lt-sr-only">全局搜索</h2>
      <div class="field">
        <Search class="field-icon" aria-hidden="true" />
        <input
          ref="inputRef"
          v-model="keyword"
          class="input"
          type="search"
          role="combobox"
          aria-label="搜索题目、课程或知识点"
          aria-controls="global-search-results"
          :aria-expanded="hasQuery"
          :aria-activedescendant="activeOptionId"
          autocomplete="off"
          spellcheck="false"
          placeholder="搜索题目、课程或知识点"
          @input="handleInput"
          @keydown.down.prevent="moveFocus(1)"
          @keydown.up.prevent="moveFocus(-1)"
          @keydown.enter.prevent="selectCurrent"
        />
        <kbd v-if="!isMobile" class="key">Esc</kbd>
        <button v-else type="button" class="icon-button" aria-label="关闭搜索" @click="close">
          <X aria-hidden="true" />
        </button>
      </div>

      <div
        v-if="hasQuery"
        id="global-search-results"
        ref="resultsRef"
        class="body"
        role="listbox"
        aria-label="搜索结果"
      >
        <p v-if="loading" class="state" role="status">正在搜索…</p>
        <div v-else-if="error" class="state" role="alert">
          <p><strong>搜索暂时无法完成</strong>{{ error }}</p>
          <button type="button" class="text-button" @click="retrySearch"><RotateCcw aria-hidden="true" />重试</button>
        </div>
        <p v-else-if="totalCount === 0" class="state" role="status">没有找到和“{{ keyword.trim() }}”匹配的内容</p>
        <template v-else>
          <section
            v-for="group in groups"
            :key="group.key"
            class="group"
            role="group"
            :aria-labelledby="`global-search-group-${group.key}`"
          >
            <h3 :id="`global-search-group-${group.key}`" class="group-title">
              {{ group.label }}<span>{{ group.items.length }}</span>
            </h3>
            <button
              v-for="(item, index) in group.items"
              :id="`global-search-option-${flatIndex(group.key, index)}`"
              :key="`${group.key}-${item.id}`"
              type="button"
              role="option"
              class="result"
              :aria-selected="flatIndex(group.key, index) === activeIndex"
              @click="navigateTo(item)"
              @mouseenter="activeIndex = flatIndex(group.key, index)"
            >
              <component :is="group.icon" class="result-icon" aria-hidden="true" />
              <span class="result-text">
                <span class="result-title"
                  ><template v-for="(segment, segmentIndex) in splitMatch(item.title)" :key="segmentIndex"
                    ><mark v-if="segment.match">{{ segment.text }}</mark
                    ><template v-else>{{ segment.text }}</template></template
                  ></span
                >
                <span v-if="item.subtitle" class="result-subtitle">{{ item.subtitle }}</span>
              </span>
            </button>
          </section>
        </template>
      </div>

      <div v-else class="body" aria-label="搜索建议" role="region">
        <p v-if="suggestionsLoading" class="state" role="status">正在读取搜索建议…</p>
        <div v-else-if="suggestionsError" class="state" role="alert">
          <p><strong>搜索建议暂时无法加载</strong>{{ suggestionsError }}</p>
          <button type="button" class="text-button" @click="openState"><RotateCcw aria-hidden="true" />重新加载</button>
        </div>
        <template v-else-if="suggestions.history.length || suggestions.hotKeywords.length">
          <section v-if="suggestions.history.length" class="group" aria-labelledby="global-search-history">
            <div class="group-head">
              <h3 id="global-search-history" class="group-title">搜索历史</h3>
              <button type="button" class="text-button" :disabled="historyUpdating" @click="clearHistory">清除</button>
            </div>
            <p v-if="historyError" class="history-error" role="status">
              {{ historyError }}
              <button type="button" class="text-button" :disabled="historyUpdating" @click="retryHistory">重试</button>
            </p>
            <ul class="history">
              <li v-for="item in suggestions.history" :key="item">
                <button type="button" class="result" @click="fillKeyword(item)">
                  <Clock class="result-icon" aria-hidden="true" /><span class="result-title">{{ item }}</span>
                </button>
                <button
                  type="button"
                  class="icon-button"
                  :disabled="historyUpdating"
                  :aria-label="`删除搜索历史：${item}`"
                  @click="removeHistory(item)"
                >
                  <X aria-hidden="true" />
                </button>
              </li>
            </ul>
          </section>
          <section v-if="suggestions.hotKeywords.length" class="group" aria-labelledby="global-search-hot">
            <h3 id="global-search-hot" class="group-title"><TrendingUp aria-hidden="true" />热门搜索</h3>
            <div class="hot">
              <button
                v-for="(item, index) in suggestions.hotKeywords"
                :key="item"
                type="button"
                class="hot-keyword"
                @click="fillKeyword(item)"
              >
                <span class="rank">{{ index + 1 }}</span
                >{{ item }}
              </button>
            </div>
          </section>
        </template>
        <p v-else class="state">输入关键词即可搜索题目、课程或知识点。</p>
      </div>
    </section>
  </dialog>
</template>

<style scoped>
.global-search {
  position: fixed;
  inset: 0;
  width: 100vw;
  max-width: none;
  height: 100vh;
  max-height: none;
  margin: 0;
  padding: 14vh var(--lt-space-4) var(--lt-space-4);
  border: 0;
  background: var(--lt-scrim);
  color: var(--lt-label);
  backdrop-filter: blur(8px);
}

.global-search[open] {
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.global-search::backdrop {
  background: transparent;
}

.panel {
  display: flex;
  flex-direction: column;
  width: min(640px, 100%);
  max-height: 72vh;
  overflow: hidden;
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface-raised);
  box-shadow: var(--lt-shadow-float);
  backdrop-filter: blur(40px) saturate(180%);
}

.field {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--lt-space-3);
  padding: var(--lt-space-4) var(--lt-space-4) var(--lt-space-4) var(--lt-space-5);
  border-bottom: 0.5px solid var(--lt-separator);
}

.field-icon {
  flex: none;
  width: 20px;
  height: 20px;
  color: var(--lt-label-3);
}

.input {
  flex: 1;
  min-width: 0;
  height: var(--lt-control-md);
  padding: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--lt-label);
  font: var(--lt-text-title3) / 1.2 var(--lt-font);
  appearance: none;
}

.input::placeholder {
  color: var(--lt-label-3);
}

.input::-webkit-search-cancel-button {
  display: none;
}

.key {
  flex: none;
  padding: 2px var(--lt-space-2);
  border-radius: var(--lt-radius-sm);
  background: var(--lt-fill-2);
  color: var(--lt-label-3);
  font: var(--lt-text-caption) / 1.4 var(--lt-font);
}

.body {
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: var(--lt-space-2) var(--lt-space-2) var(--lt-space-3);
}

.state {
  display: grid;
  justify-items: start;
  gap: var(--lt-space-2);
  margin: 0;
  padding: var(--lt-space-5) var(--lt-space-3);
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}

.state p {
  margin: 0;
}

.state strong {
  display: block;
  margin-bottom: var(--lt-space-1);
  color: var(--lt-label);
  font-size: var(--lt-text-body);
}

.group + .group {
  margin-top: var(--lt-space-2);
}

.group-head {
  display: flex;
  align-items: last baseline;
  justify-content: space-between;
  padding-right: var(--lt-space-2);
}

.group-title {
  display: flex;
  align-items: center;
  gap: var(--lt-space-2);
  margin: 0;
  padding: var(--lt-space-3) var(--lt-space-3) var(--lt-space-1);
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
  font-weight: 600;
}

.group-title span {
  font-variant-numeric: tabular-nums;
  font-weight: 400;
}

.group-title svg {
  width: 14px;
  height: 14px;
}

.result {
  display: flex;
  flex: 1;
  align-items: flex-start;
  gap: var(--lt-space-3);
  width: 100%;
  min-width: 0;
  padding: var(--lt-space-2) var(--lt-space-3);
  border: 0;
  border-radius: var(--lt-radius-md);
  background: transparent;
  color: var(--lt-label);
  font: var(--lt-text-body) / 1.4 var(--lt-font);
  text-align: left;
  cursor: pointer;
}

.result:hover,
.result[aria-selected='true'] {
  background: var(--lt-fill-2);
}

.result-icon {
  flex: none;
  width: 18px;
  height: 18px;
  margin-top: 2px;
  color: var(--lt-label-3);
}

.result[aria-selected='true'] .result-icon {
  color: var(--lt-accent);
}

.result-text {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.result-title {
  overflow: hidden;
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.result-title mark {
  background: none;
  color: var(--lt-accent);
  font-weight: 600;
}

.result-subtitle {
  overflow: hidden;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history {
  margin: 0;
  padding: 0;
  list-style: none;
}

.history li {
  display: flex;
  align-items: center;
  gap: var(--lt-space-1);
}

.history .result-title {
  display: block;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.history-error {
  margin: 0;
  padding: 0 var(--lt-space-3) var(--lt-space-2);
  color: var(--lt-red-text);
  font-size: var(--lt-text-caption);
}

.hot {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-2);
  padding: var(--lt-space-2) var(--lt-space-3);
}

.hot-keyword {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-2);
  height: var(--lt-control-sm);
  padding: 0 var(--lt-space-3);
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-fill-2);
  color: var(--lt-label);
  font: var(--lt-text-footnote) / 1 var(--lt-font);
  cursor: pointer;
}

.hot-keyword:hover {
  background: var(--lt-fill-hover);
}

.rank {
  color: var(--lt-label-3);
  font-family: var(--lt-font-rounded);
  font-variant-numeric: tabular-nums;
}

.text-button {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-1);
  padding: var(--lt-space-1) var(--lt-space-2);
  border: 0;
  border-radius: var(--lt-radius-pill);
  background: transparent;
  color: var(--lt-accent);
  font: 500 var(--lt-text-footnote) / 1 var(--lt-font);
  cursor: pointer;
}

.text-button svg {
  width: 14px;
  height: 14px;
}

.icon-button {
  display: grid;
  flex: none;
  place-items: center;
  width: var(--lt-control-sm);
  height: var(--lt-control-sm);
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--lt-label-3);
  cursor: pointer;
}

.icon-button:hover:not(:disabled) {
  background: var(--lt-fill-2);
  color: var(--lt-label);
}

.icon-button svg {
  width: 16px;
  height: 16px;
}

.text-button:disabled,
.icon-button:disabled {
  color: var(--lt-label-3);
  cursor: not-allowed;
}

@media (max-width: 720px) {
  .global-search {
    padding-top: var(--lt-space-4);
  }

  .panel {
    max-height: calc(100dvh - var(--lt-space-8));
  }

  .result {
    min-height: 44px;
  }
}
</style>
