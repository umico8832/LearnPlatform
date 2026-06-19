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

      <!-- 未输入时的提示 -->
      <div v-else class="search-hints">
        <div class="hint-item">
          <el-icon><Promotion /></el-icon>
          <span>输入关键词搜索题目、课程和知识点</span>
        </div>
        <div class="hint-item hint-shortcut">
          <span>按 <kbd>/</kbd> 或 <kbd>⌘K</kbd> 快速打开搜索</span>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Loading, EditPen, Reading, Notebook, Promotion } from '@element-plus/icons-vue'
import { globalSearch, type SearchItem, type GlobalSearchResult } from '@/api/search'

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
  return results.value.questions.length
    + results.value.courses.length
    + results.value.knowledgePoints.length
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

// 关闭搜索
function close() {
  visible.value = false
}

// 关闭后清理
function handleClosed() {
  keyword.value = ''
  results.value = { questions: [], courses: [], knowledgePoints: [], totalCount: 0 }
  activeIndex.value = 0
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
  return [
    ...results.value.questions,
    ...results.value.courses,
    ...results.value.knowledgePoints,
  ]
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
  return str
    .replace(/&/g, '&')
    .replace(/</g, '<')
    .replace(/>/g, '>')
    .replace(/"/g, '"')
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
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  gap: 8px;
}

.search-icon {
  color: #909399;
  font-size: 18px;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 16px;
  line-height: 1.5;
  color: #303133;
  background: transparent;
}

.search-input::placeholder {
  color: #c0c4cc;
}

.shortcut-hint {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px 6px;
  font-size: 11px;
  color: #909399;
  background: #f0f2f5;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  flex-shrink: 0;
}

/* 搜索结果区 */
.search-results {
  overflow-y: auto;
  max-height: 50vh;
  padding: 8px 0;
}

.search-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  color: #909399;
  font-size: 14px;
}

.search-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  color: #c0c4cc;
  font-size: 14px;
}

/* 结果分组 */
.result-group {
  margin-bottom: 4px;
}

.group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px 4px;
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.group-count {
  margin-left: auto;
  font-size: 11px;
  color: #c0c4cc;
  font-weight: normal;
}

.result-item {
  display: flex;
  flex-direction: column;
  padding: 10px 16px;
  cursor: pointer;
  transition: background-color 0.15s;
  border-radius: 0;
}

.result-item:hover,
.result-item.active {
  background-color: #f0f5ff;
}

.item-title {
  font-size: 14px;
  color: #303133;
  line-height: 1.5;
  word-break: break-word;
}

.item-title :deep(mark) {
  background: #fdf6ec;
  color: #e6a23c;
  padding: 0 2px;
  border-radius: 2px;
}

.item-subtitle {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
  line-height: 1.4;
}

/* 提示区 */
.search-hints {
  padding: 32px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.hint-item {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #c0c4cc;
  font-size: 13px;
}

.hint-shortcut kbd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 1px 6px;
  font-size: 11px;
  color: #909399;
  background: #f0f2f5;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-family: inherit;
}

/* 移动端适配 */
@media (max-width: 767px) {
  .search-input {
    font-size: 16px; /* 避免 iOS 缩放 */
  }

  .result-item {
    padding: 12px 16px;
    min-height: 48px;
  }

  .search-results {
    max-height: 60vh;
  }
}
</style>