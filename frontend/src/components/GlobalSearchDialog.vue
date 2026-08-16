<template>
  <el-dialog
    v-model="visible"
    :show-header="false"
    :width="isMobile ? '95%' : '600px'"
    :top="isMobile ? '5vh' : '12vh'"
    :append-to-body="true"
    :close-on-click-modal="true"
    :close-on-press-escape="true"
    class="global-search-dialog"
    @closed="handleClosed"
    @opened="handleOpened"
  >
    <div class="search-container">
      <!-- 搜索输入框 -->
      <div class="search-input-wrapper">
        <el-icon class="search-icon"><Search /></el-icon>
        <input
          ref="inputRef"
          v-model="keyword"
          class="search-input"
          placeholder="搜索题目、课程、知识点…"
          @input="handleInput"
          @keydown.escape="close"
          @keydown.down.prevent="moveFocus(1)"
          @keydown.up.prevent="moveFocus(-1)"
          @keydown.enter.prevent="selectCurrent"
        />
        <kbd v-if="!isMobile" class="shortcut-hint">ESC</kbd>
      </div>

      <!-- 搜索结果 -->
      <div v-if="keyword.trim()" class="search-results" ref="resultsRef">
        <!-- 加载状态 -->
        <div v-if="loading" class="search-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>搜索中…</span>
        </div>

        <!-- 无结果 -->
        <div v-else-if="totalCount === 0 && !loading" class="search-empty">
          <el-icon><Search /></el-icon>
          <span>未找到匹配结果</span>
        </div>

        <!-- 有结果 -->
        <template v-else>
          <!-- 题目 -->
          <div v-if="results.questions.length > 0" class="result-group">
            <div class="group-title">
              <el-icon><EditPen /></el-icon>
              <span>题目</span>
              <span class="group-count">{{ results.questions.length }}</span>
            </div>
            <div
              v-for="(item, idx) in results.questions"
              :key="'q-' + item.id"
              :class="['result-item', { active: flatIndex('q', idx) === activeIndex }]"
              @click="navigateTo(item)"
              @mouseenter="activeIndex = flatIndex('q', idx)"
            >
              <div class="item-title" v-html="highlightMatch(item.title)"></div>
              <div class="item-subtitle">{{ item.subtitle }}</div>
            </div>
          </div>

          <!-- 课程 -->
          <div v-if="results.courses.length > 0" class="result-group">
            <div class="group-title">
              <el-icon><Reading /></el-icon>
              <span>课程</span>
              <span class="group-count">{{ results.courses.length }}</span>
            </div>
            <div
              v-for="(item, idx) in results.courses"
              :key="'c-' + item.id"
              :class="['result-item', { active: flatIndex('c', idx) === activeIndex }]"
              @click="navigateTo(item)"
              @mouseenter="activeIndex = flatIndex('c', idx)"
            >
              <div class="item-title" v-html="highlightMatch(item.title)"></div>
              <div class="item-subtitle">{{ item.subtitle }}</div>
            </div>
          </div>

          <!-- 知识点 -->
          <div v-if="results.knowledgePoints.length > 0" class="result-group">
            <div class="group-title">
              <el-icon><Notebook /></el-icon>
              <span>知识点</span>
              <span class="group-count">{{ results.knowledgePoints.length }}</span>
            </div>
            <div
              v-for="(item, idx) in results.knowledgePoints"
              :key="'kp-' + item.id"
              :class="['result-item', { active: flatIndex('kp', idx) === activeIndex }]"
              @click="navigateTo(item)"
              @mouseenter="activeIndex = flatIndex('kp', idx)"
            >
              <div class="item-title" v-html="highlightMatch(item.title)"></div>
              <div class="item-subtitle">{{ item.subtitle }}</div>
            </div>
          </div>
        </template>
      </div>

      <!-- 未输入时：搜索历史 + 热门搜索 -->
      <div v-else class="search-suggestions">
        <!-- 搜索历史 -->
        <div v-if="suggestions.history.length > 0" class="suggestion-section">
          <div class="section-header">
            <span class="section-title">
              <el-icon><Clock /></el-icon>
              搜索历史
            </span>
            <span class="section-action" @click.stop="handleClearHistory">清除</span>
          </div>
          <div class="history-list">
            <div
              v-for="(item, idx) in suggestions.history"
              :key="'h-' + idx"
              class="history-item"
              @click="fillKeyword(item)"
            >
              <el-icon class="history-icon"><Clock /></el-icon>
              <span class="history-text">{{ item }}</span>
              <el-icon class="history-delete" @click.stop="handleRemoveHistory(item)"><Close /></el-icon>
            </div>
          </div>
        </div>

        <!-- 热门搜索 -->
        <div v-if="suggestions.hotKeywords.length > 0" class="suggestion-section">
          <div class="section-header">
            <span class="section-title">
              <el-icon><TrendCharts /></el-icon>
              热门搜索
            </span>
          </div>
          <div class="hot-keyword-list">
            <span
              v-for="(item, idx) in suggestions.hotKeywords"
              :key="'hot-' + idx"
              class="hot-keyword-tag"
              @click="fillKeyword(item)"
            >
              <span class="hot-rank" :class="{ 'top-3': idx < 3 }">{{ idx + 1 }}</span>
              {{ item }}
            </span>
          </div>
        </div>

        <!-- 无历史也无热门时的默认提示 -->
        <div v-if="suggestions.history.length === 0 && suggestions.hotKeywords.length === 0" class="search-hints">
          <div class="hint-item">
            <el-icon><Promotion /></el-icon>
            <span>输入关键词搜索题目、课程和知识点</span>
          </div>
          <div class="hint-item hint-shortcut">
            <span>按 <kbd>/</kbd> 或 <kbd>⌘K</kbd> 快速打开搜索</span>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import {
  Search,
  Loading,
  EditPen,
  Reading,
  Notebook,
  Promotion,
  Clock,
  Close,
  TrendCharts,
} from '@element-plus/icons-vue'
import {
  globalSearch,
  getSearchSuggestions,
  clearSearchHistory,
  removeSearchHistoryItem,
  type SearchItem,
  type GlobalSearchResult,
  type SearchSuggestions,
} from '@/api/search'

const router = useRouter()

// 对话框可见性
const visible = ref(false)
const keyword = ref('')
const loading = ref(false)
const results = ref<GlobalSearchResult>({
  questions: [],
  courses: [],
  knowledgePoints: [],
  totalCount: 0,
})
const activeIndex = ref(0)

// 搜索建议
const suggestions = ref<SearchSuggestions>({
  history: [],
  hotKeywords: [],
})

const inputRef = ref<HTMLInputElement>()
const resultsRef = ref<HTMLDivElement>()

// 防抖定时器
let debounceTimer: ReturnType<typeof setTimeout> | null = null

// 移动端检测
const isMobile = ref(window.innerWidth < 768)
function checkMobile() {
  isMobile.value = window.innerWidth < 768
}

// 总结果数
const totalCount = computed(() => {
  return results.value.questions.length + results.value.courses.length + results.value.knowledgePoints.length
})

// 将分类+偏移映射为扁平 activeIndex
function flatIndex(group: 'q' | 'c' | 'kp', localIdx: number): number {
  let base = 0
  if (group === 'c') base = results.value.questions.length
  if (group === 'kp') base = results.value.questions.length + results.value.courses.length
  return base + localIdx
}

// 打开搜索
function open() {
  visible.value = true
  keyword.value = ''
  results.value = { questions: [], courses: [], knowledgePoints: [], totalCount: 0 }
  activeIndex.value = 0
  nextTick(() => {
    inputRef.value?.focus()
  })
}

// 对话框打开后加载建议
async function handleOpened() {
  await loadSuggestions()
}

// 加载搜索建议
async function loadSuggestions() {
  try {
    const res = await getSearchSuggestions()
    suggestions.value = res.data
  } catch {
    suggestions.value = { history: [], hotKeywords: [] }
  }
}

// 关闭搜索
function close() {
  visible.value = false
}

// 关闭后清理
function handleClosed() {
  keyword.value = ''
  results.value = { questions: [], courses: [], knowledgePoints: [], totalCount: 0 }
  activeIndex.value = 0
  suggestions.value = { history: [], hotKeywords: [] }
}

// 填充关键词并触发搜索
function fillKeyword(kw: string) {
  keyword.value = kw
  nextTick(() => {
    doSearch(kw)
  })
}

// 输入防抖
function handleInput() {
  if (debounceTimer) clearTimeout(debounceTimer)
  const q = keyword.value.trim()
  if (!q) {
    results.value = { questions: [], courses: [], knowledgePoints: [], totalCount: 0 }
    return
  }
  debounceTimer = setTimeout(() => {
    doSearch(q)
  }, 250)
}

// 执行搜索
async function doSearch(q: string) {
  loading.value = true
  activeIndex.value = 0
  try {
    const res = await globalSearch(q, 5)
    results.value = res.data
  } catch {
    results.value = { questions: [], courses: [], knowledgePoints: [], totalCount: 0 }
  } finally {
    loading.value = false
  }
}

// 清除搜索历史
async function handleClearHistory() {
  try {
    await clearSearchHistory()
    suggestions.value.history = []
  } catch {
    // ignore
  }
}

// 删除单条搜索历史
async function handleRemoveHistory(kw: string) {
  try {
    await removeSearchHistoryItem(kw)
    suggestions.value.history = suggestions.value.history.filter((h) => h !== kw)
  } catch {
    // ignore
  }
}

// 键盘导航
function moveFocus(delta: number) {
  const total = totalCount.value
  if (total === 0) return
  activeIndex.value = (activeIndex.value + delta + total) % total
  scrollToActive()
}

// 回车选择当前项
function selectCurrent() {
  const all = getAllItems()
  if (all[activeIndex.value]) {
    navigateTo(all[activeIndex.value])
  }
}

// 滚动到活动项
function scrollToActive() {
  nextTick(() => {
    const el = resultsRef.value?.querySelector('.result-item.active')
    el?.scrollIntoView({ block: 'nearest' })
  })
}

// 获取所有项的扁平列表
function getAllItems(): SearchItem[] {
  return [...results.value.questions, ...results.value.courses, ...results.value.knowledgePoints]
}

// 导航到选中项
function navigateTo(item: SearchItem) {
  close()
  router.push(item.link)
}

// 高亮匹配文本
function highlightMatch(text: string): string {
  if (!keyword.value.trim()) return escapeHtml(text)
  const escaped = escapeRegex(keyword.value.trim())
  const regex = new RegExp(`(${escaped})`, 'gi')
  return escapeHtml(text).replace(regex, '<mark>$1</mark>')
}

function escapeHtml(str: string): string {
  return str.replace(/&/g, '&').replace(/</g, '<').replace(/>/g, '>').replace(/"/g, '"')
}

function escapeRegex(str: string): string {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

// 全局键盘事件
function handleGlobalKeydown(e: KeyboardEvent) {
  // Cmd+K / Ctrl+K
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    if (visible.value) {
      close()
    } else {
      open()
    }
    return
  }
  // "/" 键（不在输入框内时）
  if (e.key === '/' && !visible.value && !isInputFocused()) {
    e.preventDefault()
    open()
  }
}

function isInputFocused(): boolean {
  const tag = document.activeElement?.tagName?.toLowerCase()
  return tag === 'input' || tag === 'textarea' || document.activeElement?.getAttribute('contenteditable') === 'true'
}

onMounted(() => {
  window.addEventListener('keydown', handleGlobalKeydown)
  window.addEventListener('resize', checkMobile)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
  window.removeEventListener('resize', checkMobile)
  if (debounceTimer) clearTimeout(debounceTimer)
})

// 暴露 open 方法给父组件
defineExpose({ open })
</script>

<style scoped>
.global-search-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.search-container {
  display: flex;
  flex-direction: column;
  max-height: 60vh;
}

/* 搜索输入区 */
.search-input-wrapper {
  display: flex;
  align-items: center;
  padding: var(--lp-space-3) var(--lp-space-4);
  border-bottom: var(--lp-border-hairline);
  gap: var(--lp-space-2);
}

.search-icon {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xl);
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: var(--lp-text-lg);
  line-height: var(--lp-leading-snug);
  color: var(--lp-text);
  background: transparent;
}

.search-input::placeholder {
  color: var(--lp-ink-300);
}

.shortcut-hint {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: var(--lp-space-1) var(--lp-space-2);
  font-size: var(--lp-text-xs);
  color: var(--lp-text-muted);
  background: var(--lp-surface-soft);
  border: 1px solid var(--lp-border-strong);
  border-radius: var(--lp-radius-xs);
  flex-shrink: 0;
}

/* 搜索结果区 */
.search-results {
  overflow-y: auto;
  max-height: 50vh;
  padding: var(--lp-space-2) 0;
}

.search-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--lp-space-2);
  padding: var(--lp-space-6);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-base);
}

.search-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--lp-space-2);
  padding: var(--lp-space-6);
  color: var(--lp-ink-300);
  font-size: var(--lp-text-base);
}

/* 结果分组 */
.result-group {
  margin-bottom: var(--lp-space-1);
}

.group-title {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  padding: var(--lp-space-2) var(--lp-space-4) var(--lp-space-1);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-semibold);
  color: var(--lp-text-muted);
  text-transform: uppercase;
  letter-spacing: var(--lp-tracking-wide);
}

.group-count {
  margin-left: auto;
  font-size: var(--lp-text-xs);
  color: var(--lp-ink-300);
  font-weight: var(--lp-weight-normal);
}

.result-item {
  display: flex;
  flex-direction: column;
  padding: var(--lp-space-3) var(--lp-space-4);
  cursor: pointer;
  transition: background-color var(--lp-duration-fast) var(--lp-ease-out);
  border-radius: 0;
}

.result-item:hover,
.result-item.active {
  background-color: var(--lp-primary-soft);
}

.item-title {
  font-size: var(--lp-text-base);
  color: var(--lp-text);
  line-height: var(--lp-leading-snug);
  word-break: break-word;
}

.item-title :deep(mark) {
  background: var(--lp-warning-soft);
  color: var(--lp-warning);
  padding: 0 var(--lp-space-1);
  border-radius: var(--lp-radius-xs);
}

.item-subtitle {
  font-size: var(--lp-text-xs);
  color: var(--lp-text-muted);
  margin-top: var(--lp-space-1);
  line-height: var(--lp-leading-snug);
}

/* 搜索建议区（历史 + 热门） */
.search-suggestions {
  overflow-y: auto;
  max-height: 50vh;
  padding: var(--lp-space-2) 0;
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

.section-title {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-semibold);
  color: var(--lp-text-muted);
  text-transform: uppercase;
  letter-spacing: var(--lp-tracking-wide);
}

.section-action {
  font-size: var(--lp-text-xs);
  color: var(--lp-ink-300);
  cursor: pointer;
  transition: color var(--lp-duration-normal) var(--lp-ease-out);
}

.section-action:hover {
  color: var(--lp-primary);
}

/* 搜索历史列表 */
.history-list {
  display: flex;
  flex-direction: column;
}

.history-item {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  padding: var(--lp-space-2) var(--lp-space-4);
  cursor: pointer;
  transition: background-color var(--lp-duration-fast) var(--lp-ease-out);
}

.history-item:hover {
  background-color: var(--lp-surface-soft);
}

.history-icon {
  color: var(--lp-ink-300);
  font-size: var(--lp-text-base);
  flex-shrink: 0;
}

.history-text {
  flex: 1;
  font-size: var(--lp-text-base);
  color: var(--lp-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-delete {
  color: var(--lp-ink-300);
  font-size: var(--lp-text-base);
  cursor: pointer;
  opacity: 0;
  transition:
    opacity var(--lp-duration-normal) var(--lp-ease-out),
    color var(--lp-duration-normal) var(--lp-ease-out);
  flex-shrink: 0;
}

.history-item:hover .history-delete {
  opacity: 1;
}

.history-delete:hover {
  color: var(--lp-danger);
}

/* 热门搜索标签 */
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
  font-size: var(--lp-text-sm);
  color: var(--lp-text-secondary);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-full);
  cursor: pointer;
  transition:
    background-color var(--lp-duration-normal) var(--lp-ease-out),
    color var(--lp-duration-normal) var(--lp-ease-out),
    border-color var(--lp-duration-normal) var(--lp-ease-out);
  border: 1px solid transparent;
}

.hot-keyword-tag:hover {
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
  border-color: var(--lp-blue-200);
}

.hot-rank {
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-bold);
  color: var(--lp-ink-300);
  min-width: 14px;
  text-align: center;
}

.hot-rank.top-3 {
  color: var(--lp-warning);
}

/* 默认提示区 */
.search-hints {
  padding: var(--lp-space-8) var(--lp-space-4);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--lp-space-3);
}

.hint-item {
  display: flex;
  align-items: center;
  gap: var(--lp-space-2);
  color: var(--lp-ink-300);
  font-size: var(--lp-text-sm);
}

.hint-shortcut kbd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: var(--lp-space-1) var(--lp-space-2);
  font-size: var(--lp-text-xs);
  color: var(--lp-text-muted);
  background: var(--lp-surface-soft);
  border: 1px solid var(--lp-border-strong);
  border-radius: var(--lp-radius-xs);
  font-family: inherit;
}

/* 移动端适配 */
@media (max-width: 767px) {
  .search-input {
    font-size: var(--lp-text-lg); /* 避免 iOS 缩放 */
  }

  .result-item {
    padding: var(--lp-space-3) var(--lp-space-4);
    min-height: 48px;
  }

  .search-results {
    max-height: 60vh;
  }

  .search-suggestions {
    max-height: 60vh;
  }

  .history-delete {
    opacity: 1;
  }

  .hot-keyword-tag {
    padding: var(--lp-space-2) var(--lp-space-3);
    font-size: var(--lp-text-base);
  }
}
</style>
