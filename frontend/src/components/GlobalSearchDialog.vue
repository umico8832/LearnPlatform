<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Clock, Close, EditPen, Notebook, Reading, Search, TrendCharts } from '@element-plus/icons-vue'
import type { SearchItem } from '@/api/search'
import { useGlobalSearchShortcuts } from './search/useGlobalSearchShortcuts'
import { flattenSearchResults, searchResultCount, searchResultIndex } from './search/searchResultModel'
import { splitSearchMatch } from './search/searchText'
import { useGlobalSearchState } from './search/useGlobalSearchState'

const router = useRouter()
const visible = ref(false)
const inputRef = ref<HTMLInputElement>()
const resultsRef = ref<HTMLElement>()
const activeIndex = ref(-1)
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
const isMobile = useGlobalSearchShortcuts(visible, open, close)

watch(visible, (nextVisible) => {
  if (!nextVisible) closeState()
})

function open() {
  visible.value = true
  openState()
}

function close() {
  visible.value = false
  closeState()
}

function handleOpened() {
  nextTick(() => inputRef.value?.focus())
}

function handleClosed() {
  activeIndex.value = -1
}

function handleInput() {
  activeIndex.value = -1
  search()
}

function fillKeyword(value: string) {
  keyword.value = value
  activeIndex.value = -1
  search(value)
  nextTick(() => inputRef.value?.focus())
}

function flatIndex(group: 'q' | 'c' | 'kp', index: number) {
  return searchResultIndex(results.value, group, index)
}

function optionId(group: 'q' | 'c' | 'kp', index: number) {
  return `global-search-option-${flatIndex(group, index)}`
}

function moveFocus(delta: number) {
  if (!totalCount.value) return
  if (activeIndex.value < 0) activeIndex.value = delta > 0 ? 0 : totalCount.value - 1
  else activeIndex.value = (activeIndex.value + delta + totalCount.value) % totalCount.value
  nextTick(() =>
    resultsRef.value
      ?.querySelector<HTMLElement>('.result-item[aria-selected="true"]')
      ?.scrollIntoView({ block: 'nearest' }),
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
  <el-dialog
    v-model="visible"
    title="全局搜索"
    :width="isMobile ? '95%' : '600px'"
    :top="isMobile ? '5vh' : '12vh'"
    append-to-body
    class="global-search-dialog"
    aria-label="全局搜索"
    @closed="handleClosed"
    @opened="handleOpened"
  >
    <template #header="{ titleId }">
      <div class="search-header">
        <h2 :id="titleId">全局搜索</h2>
        <p>搜索题目、课程或知识点</p>
      </div>
    </template>
    <section class="search-container" aria-label="全局搜索内容">
      <div class="search-input-wrapper">
        <el-icon class="search-icon" aria-hidden="true"><Search /></el-icon>
        <input
          ref="inputRef"
          v-model="keyword"
          class="search-input"
          type="search"
          role="combobox"
          aria-label="搜索题目、课程或知识点"
          aria-controls="global-search-results"
          :aria-expanded="hasQuery"
          :aria-activedescendant="activeOptionId"
          autocomplete="off"
          placeholder="搜索题目、课程或知识点"
          @input="handleInput"
          @keydown.escape="close"
          @keydown.down.prevent="moveFocus(1)"
          @keydown.up.prevent="moveFocus(-1)"
          @keydown.enter.prevent="selectCurrent"
        />
        <kbd v-if="!isMobile" class="shortcut-hint">Esc</kbd>
      </div>

      <section
        v-if="hasQuery"
        id="global-search-results"
        ref="resultsRef"
        class="search-results"
        role="listbox"
        aria-label="搜索结果"
      >
        <LpStatePanel v-if="loading" state="loading" loading-label="正在搜索" class="search-state" />
        <LpStatePanel
          v-else-if="error"
          state="error"
          title="搜索暂时无法完成"
          :description="error"
          retry-label="重试"
          @retry="retrySearch"
        />
        <div v-else-if="totalCount === 0" class="search-empty" role="status">
          <el-icon aria-hidden="true"><Search /></el-icon>
          <span>未找到匹配结果</span>
        </div>
        <template v-else>
          <section
            v-if="results.questions.length"
            class="result-group"
            role="group"
            aria-labelledby="global-search-questions"
          >
            <h3 id="global-search-questions" class="group-title">
              <el-icon aria-hidden="true"><EditPen /></el-icon>题目 <span>{{ results.questions.length }}</span>
            </h3>
            <button
              v-for="(item, index) in results.questions"
              :id="optionId('q', index)"
              :key="`q-${item.id}`"
              type="button"
              role="option"
              class="result-item"
              :aria-selected="flatIndex('q', index) === activeIndex"
              @click="navigateTo(item)"
              @mouseenter="activeIndex = flatIndex('q', index)"
            >
              <span class="item-title"
                ><template v-for="(segment, segmentIndex) in splitMatch(item.title)" :key="segmentIndex"
                  ><mark v-if="segment.match">{{ segment.text }}</mark
                  ><template v-else>{{ segment.text }}</template></template
                ></span
              >
              <span class="item-subtitle">{{ item.subtitle }}</span>
            </button>
          </section>
          <section
            v-if="results.courses.length"
            class="result-group"
            role="group"
            aria-labelledby="global-search-courses"
          >
            <h3 id="global-search-courses" class="group-title">
              <el-icon aria-hidden="true"><Reading /></el-icon>课程 <span>{{ results.courses.length }}</span>
            </h3>
            <button
              v-for="(item, index) in results.courses"
              :id="optionId('c', index)"
              :key="`c-${item.id}`"
              type="button"
              role="option"
              class="result-item"
              :aria-selected="flatIndex('c', index) === activeIndex"
              @click="navigateTo(item)"
              @mouseenter="activeIndex = flatIndex('c', index)"
            >
              <span class="item-title"
                ><template v-for="(segment, segmentIndex) in splitMatch(item.title)" :key="segmentIndex"
                  ><mark v-if="segment.match">{{ segment.text }}</mark
                  ><template v-else>{{ segment.text }}</template></template
                ></span
              >
              <span class="item-subtitle">{{ item.subtitle }}</span>
            </button>
          </section>
          <section
            v-if="results.knowledgePoints.length"
            class="result-group"
            role="group"
            aria-labelledby="global-search-knowledge-points"
          >
            <h3 id="global-search-knowledge-points" class="group-title">
              <el-icon aria-hidden="true"><Notebook /></el-icon>知识点 <span>{{ results.knowledgePoints.length }}</span>
            </h3>
            <button
              v-for="(item, index) in results.knowledgePoints"
              :id="optionId('kp', index)"
              :key="`kp-${item.id}`"
              type="button"
              role="option"
              class="result-item"
              :aria-selected="flatIndex('kp', index) === activeIndex"
              @click="navigateTo(item)"
              @mouseenter="activeIndex = flatIndex('kp', index)"
            >
              <span class="item-title"
                ><template v-for="(segment, segmentIndex) in splitMatch(item.title)" :key="segmentIndex"
                  ><mark v-if="segment.match">{{ segment.text }}</mark
                  ><template v-else>{{ segment.text }}</template></template
                ></span
              >
              <span class="item-subtitle">{{ item.subtitle }}</span>
            </button>
          </section>
        </template>
      </section>

      <section v-else class="search-suggestions" aria-label="搜索建议">
        <LpSkeleton v-if="suggestionsLoading" :rows="2" label="正在读取搜索建议" />
        <LpStatePanel
          v-else-if="suggestionsError"
          state="error"
          title="搜索建议暂时无法加载"
          :description="suggestionsError"
          retry-label="重新加载"
          @retry="openState"
        />
        <template v-else-if="suggestions.history.length || suggestions.hotKeywords.length">
          <section v-if="suggestions.history.length" class="suggestion-section">
            <div class="section-header">
              <h3 class="section-title">
                <el-icon aria-hidden="true"><Clock /></el-icon>搜索历史
              </h3>
              <button type="button" class="section-action" :disabled="historyUpdating" @click="clearHistory">
                清除
              </button>
            </div>
            <p v-if="historyError" class="history-error" role="status">
              {{ historyError }} <button type="button" :disabled="historyUpdating" @click="retryHistory">重试</button>
            </p>
            <ul class="history-list">
              <li v-for="item in suggestions.history" :key="item" class="history-item">
                <el-icon class="history-icon" aria-hidden="true"><Clock /></el-icon>
                <button type="button" class="history-text" @click="fillKeyword(item)">{{ item }}</button>
                <button
                  type="button"
                  class="history-delete"
                  :disabled="historyUpdating"
                  :aria-label="`删除搜索历史：${item}`"
                  @click="removeHistory(item)"
                >
                  <el-icon aria-hidden="true"><Close /></el-icon>
                </button>
              </li>
            </ul>
          </section>
          <section v-if="suggestions.hotKeywords.length" class="suggestion-section">
            <div class="section-header">
              <h3 class="section-title">
                <el-icon aria-hidden="true"><TrendCharts /></el-icon>热门搜索
              </h3>
            </div>
            <div class="hot-keyword-list">
              <button
                v-for="(item, index) in suggestions.hotKeywords"
                :key="item"
                type="button"
                class="hot-keyword-tag"
                @click="fillKeyword(item)"
              >
                <span class="hot-rank">{{ index + 1 }}</span
                >{{ item }}
              </button>
            </div>
          </section>
        </template>
        <p v-else class="search-helper">输入关键词即可搜索题目、课程或知识点。</p>
      </section>
    </section>
  </el-dialog>
</template>

<style scoped>
:global(.global-search-dialog .el-dialog__body) {
  padding: 0;
}
:global(.global-search-dialog .el-dialog__header) {
  padding: var(--lp-space-5) var(--lp-space-5) var(--lp-space-2);
}
.search-container {
  display: flex;
  flex-direction: column;
  max-height: 60vh;
}
.search-header {
  padding-right: var(--lp-space-6);
}
.search-header h2,
.search-header p {
  margin: 0;
}
.search-header h2 {
  color: var(--lp-text);
  font-size: var(--lp-text-lg);
}
.search-header p,
.search-helper {
  color: var(--lp-text-secondary);
  font-size: var(--lp-text-sm);
}
.search-header p {
  margin-top: var(--lp-space-1);
}
.search-input-wrapper {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  padding: var(--lp-space-3) var(--lp-space-5);
  border-bottom: var(--lp-border-hairline);
}
.search-icon {
  flex-shrink: 0;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xl);
}
.search-input {
  flex: 1;
  min-width: 0;
  min-height: var(--lp-control-height);
  padding: 0 var(--lp-space-2);
  color: var(--lp-text);
  font: inherit;
  font-size: var(--lp-text-lg);
  line-height: var(--lp-leading-snug);
  background: transparent;
  border: 0;
  appearance: none;
}
.search-input::placeholder {
  color: var(--lp-text-muted);
}
.search-input:focus-visible {
  outline: var(--lp-focus-width) solid var(--lp-focus-ring);
  outline-offset: var(--lp-focus-offset);
}
.shortcut-hint {
  padding: var(--lp-space-1) var(--lp-space-2);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  background: var(--lp-surface-soft);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-xs);
}
.search-results,
.search-suggestions {
  overflow-y: auto;
  max-height: 50vh;
  padding: var(--lp-space-2) 0;
}
.search-state {
  margin: var(--lp-space-2) var(--lp-space-4);
}
.search-empty,
.search-helper {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  padding: var(--lp-space-6) var(--lp-space-4);
}
.result-group {
  margin-bottom: var(--lp-space-1);
}
.group-title,
.section-title {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  margin: 0;
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-semibold);
  letter-spacing: var(--lp-tracking-wide);
}
.group-title {
  padding: var(--lp-space-2) var(--lp-space-4) var(--lp-space-1);
}
.group-title span {
  margin-left: auto;
  font-weight: var(--lp-weight-normal);
}
.result-item {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: var(--lp-space-3) var(--lp-space-4);
  color: var(--lp-text);
  font: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  cursor: pointer;
}
.result-item:hover,
.result-item[aria-selected='true'] {
  background: var(--lp-primary-soft);
}
.result-item:focus-visible,
.section-action:focus-visible,
.history-text:focus-visible,
.history-delete:focus-visible,
.hot-keyword-tag:focus-visible {
  outline: var(--lp-focus-width) solid var(--lp-focus-ring);
  outline-offset: var(--lp-focus-offset);
}
.item-title {
  line-height: var(--lp-leading-snug);
  word-break: break-word;
}
.item-title mark {
  padding: 0 var(--lp-space-1);
  color: var(--lp-warning);
  background: var(--lp-warning-soft);
  border-radius: var(--lp-radius-xs);
}
.item-subtitle {
  margin-top: var(--lp-space-1);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  line-height: var(--lp-leading-snug);
}
.suggestion-section {
  padding: var(--lp-space-1) 0;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--lp-space-2) var(--lp-space-4) var(--lp-space-1);
}
.section-action,
.history-error button {
  padding: 0;
  color: var(--lp-primary);
  font: inherit;
  font-size: var(--lp-text-xs);
  background: transparent;
  border: 0;
  cursor: pointer;
}
.history-error {
  margin: 0;
  padding: var(--lp-space-1) var(--lp-space-4);
  color: var(--lp-danger);
  font-size: var(--lp-text-xs);
}
.history-list {
  display: flex;
  flex-direction: column;
  padding: 0;
  margin: 0;
  list-style: none;
}
.history-item {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  padding: var(--lp-space-2) var(--lp-space-4);
}
.history-icon {
  flex-shrink: 0;
  color: var(--lp-ink-300);
}
.history-text {
  flex: 1;
  overflow: hidden;
  padding: 0;
  color: var(--lp-text);
  font: inherit;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: transparent;
  border: 0;
  cursor: pointer;
}
.history-delete {
  display: grid;
  place-items: center;
  min-width: var(--lp-control-height-small);
  min-height: var(--lp-control-height-small);
  padding: 0;
  color: var(--lp-text-muted);
  background: transparent;
  border: 0;
  cursor: pointer;
}
.history-delete:hover {
  color: var(--lp-danger);
}
.hot-keyword-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lp-space-2);
  padding: var(--lp-space-2) var(--lp-space-4) var(--lp-space-3);
}
.hot-keyword-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--lp-space-2);
  padding: var(--lp-space-2) var(--lp-space-3);
  color: var(--lp-text-secondary);
  font: inherit;
  font-size: var(--lp-text-sm);
  background: var(--lp-surface-soft);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-full);
  cursor: pointer;
}
.hot-keyword-tag:hover {
  color: var(--lp-primary);
  background: var(--lp-primary-soft);
}
.hot-rank {
  min-width: var(--lp-space-3);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  font-variant-numeric: tabular-nums;
}
@media (max-width: 767px) {
  .search-results,
  .search-suggestions {
    max-height: 60vh;
  }
  .result-item {
    min-height: 48px;
  }
}
</style>
