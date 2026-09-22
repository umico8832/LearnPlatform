<template>
  <main class="page-directory">
    <header class="directory-header">
      <div>
        <div class="directory-heading">
          <h1>全局页面预览</h1>
          <span class="dev-label">仅开发环境</span>
        </div>
        <p>选择页面即可预览，认证页面可直接切换步骤与结果。</p>
      </div>
      <div class="header-links">
        <a href="/" target="_blank" rel="noopener noreferrer">网站首页 ↗</a>
      </div>
    </header>

    <div class="directory-workspace">
      <aside class="directory-sidebar">
        <div class="directory-search">
          <label for="page-search">查找页面</label>
          <input id="page-search" v-model="search" type="search" placeholder="页面名称、路径或路由名" />
          <span class="result-count" role="status">{{ filteredPages.length }} / {{ pages.length }} 个页面</span>
        </div>
        <nav class="page-list" aria-label="全部页面">
          <section v-for="group in visibleGroups" :key="group.id">
            <h2>
              <button
                type="button"
                class="page-group-toggle"
                :class="{ 'has-selection': selected.group === group.id }"
                :aria-expanded="isGroupExpanded(group.id)"
                :aria-controls="`page-group-${group.id}`"
                @click="toggleGroup(group.id)"
              >
                <svg
                  class="group-chevron"
                  :class="{ 'is-expanded': isGroupExpanded(group.id) }"
                  viewBox="0 0 16 16"
                  width="16"
                  height="16"
                  fill="none"
                  aria-hidden="true"
                >
                  <path
                    d="m6 3 5 5-5 5"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
                <span class="group-label">{{ group.label }}</span>
                <span class="group-count">{{ group.items.length }}</span>
              </button>
            </h2>
            <div v-show="isGroupExpanded(group.id)" :id="`page-group-${group.id}`">
              <button
                v-for="page in group.items"
                :key="page.id"
                type="button"
                class="page-entry"
                :data-page="page.id"
                :class="{ 'is-selected': selected.id === page.id }"
                :aria-current="selected.id === page.id ? 'true' : undefined"
                @click="selectPage(page)"
              >
                <span>{{ page.title }}</span>
                <small>{{ displayPath(page) }}</small>
              </button>
            </div>
          </section>
          <p v-if="!filteredPages.length" class="search-empty">没有匹配的页面，请尝试名称或路径关键词。</p>
        </nav>
      </aside>

      <section class="directory-main" aria-labelledby="selected-page-title">
        <div class="page-details">
          <div class="page-title-row">
            <h2 id="selected-page-title">{{ selected.title }}</h2>
            <div class="page-badges">
              <span>{{ accessLabels[selected.access] }}</span>
              <span v-if="selected.hidden">菜单中隐藏</span>
              <span v-if="selected.placeholder">占位页面</span>
            </div>
          </div>
          <p class="route-path">{{ displayPath(selected) }}</p>
          <p class="route-name">{{ selected.name }}</p>

          <p v-if="selected.notice && !authState" class="page-notice">{{ selected.notice }}</p>

          <div v-if="selected.app === 'admin'" class="admin-settings">
            <label for="admin-base">管理端开发地址</label>
            <input
              id="admin-base"
              v-model="adminBase"
              type="url"
              spellcheck="false"
              :aria-invalid="!validAdminBase"
              @input="clearPreview"
              @keydown.enter.prevent="loadPreview"
            />
            <p v-if="!validAdminBase" class="field-error" role="alert">
              请输入有效的 HTTP 或 HTTPS 地址，不含账号、查询参数或片段。
            </p>
            <p v-else class="field-help">管理端需单独启动：<code>npm run dev:admin</code>。修改地址后按回车应用。</p>
          </div>

          <div v-if="selected.params.length" class="route-params">
            <label v-for="param in selected.params" :key="param" :for="`param-${param}`">
              <span>{{ parameterLabel(param) }}</span>
              <input
                :id="`param-${param}`"
                v-model="params[param]"
                type="text"
                inputmode="numeric"
                placeholder="填写已有记录的正整数 ID"
                autocomplete="off"
                :aria-invalid="invalidParams"
                aria-describedby="params-help"
                @input="clearPreview"
                @keydown.enter.prevent="loadPreview"
              />
            </label>
            <p id="params-help" class="field-help">填写真实记录 ID 后按回车预览；不确定时可先从业务入口进入。</p>
            <p v-if="invalidParams" class="field-error" role="alert">请输入有效的正整数记录 ID。</p>
          </div>

          <div class="page-tools">
            <label v-if="authOptions.length" class="auth-preview-options" for="auth-preview-state">
              预览状态
              <select id="auth-preview-state" :key="selected.id" v-model="authState" @change="loadPreview">
                <option v-for="option in authOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
                <option value="">真实流程</option>
              </select>
            </label>
            <div class="page-actions">
              <a v-if="targetUrl" class="secondary-action" :href="targetUrl" target="_blank" rel="noopener noreferrer"
                >在新标签打开 ↗</a
              >
              <a v-if="entryUrl" :href="entryUrl" target="_blank" rel="noopener noreferrer">从业务入口进入 ↗</a>
            </div>
          </div>
        </div>

        <PagePreviewFrame v-if="previewUrl" :url="previewUrl" :title="selected.title" />
        <p v-else class="preview-pending" role="status">
          {{ selected.params.length ? '填写真实记录 ID，按回车预览。' : '填写有效的管理端地址，按回车预览。' }}
        </p>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { pageGroups, pages, type PageEntry } from './pageCatalog'
import { normalizeAdminBase, resolvePageUrl } from './pageDirectoryUrl'
import { getAuthPreviewOptions } from './authPreviewOptions'
import PagePreviewFrame from './PagePreviewFrame.vue'

const learnerOrigin = window.location.origin
const defaultAdminBase = new URL('/admin/', learnerOrigin)
defaultAdminBase.port = '5174'
const adminBase = ref(defaultAdminBase.href)
const validAdminBase = computed(() => normalizeAdminBase(adminBase.value))
const search = ref('')
const selected = ref(pages.find((page) => page.name === 'Home') ?? pages[0])
const expandedGroups = ref(new Set<PageEntry['group']>([selected.value.group]))
const searchCollapsedGroups = ref(new Set<PageEntry['group']>())
const params = ref<Record<string, string>>({})
const authOptions = computed(() => getAuthPreviewOptions(selected.value.name))
const authState = ref(authOptions.value[0]?.value ?? '')
const attempted = ref(false)
const accessLabels = { public: '公开访问', login: '需要登录', admin: '需要管理员' }
const filteredPages = computed(() => {
  const query = search.value.trim().toLowerCase()
  return pages.filter((page) => `${page.title} ${displayPath(page)} ${page.name}`.toLowerCase().includes(query))
})
const visibleGroups = computed(() =>
  pageGroups
    .map((group) => ({ ...group, items: filteredPages.value.filter((page) => page.group === group.id) }))
    .filter((group) => group.items.length),
)
const targetUrl = computed(() => {
  const resolved = resolvePageUrl(selected.value, params.value, learnerOrigin, adminBase.value)
  if (!resolved) return undefined
  const url = new URL(resolved)
  if (authOptions.value.some((option) => option.value === authState.value)) {
    url.searchParams.set('auth-preview', authState.value)
  }
  return url.href
})
const previewUrl = ref(targetUrl.value ?? '')
const invalidParams = computed(() => attempted.value && selected.value.params.length > 0 && !targetUrl.value)
const entryUrl = computed(() => {
  if (!selected.value.entryPath) return undefined
  return resolvePageUrl(
    { ...selected.value, path: selected.value.entryPath, params: [] },
    {},
    learnerOrigin,
    adminBase.value,
  )
})

function displayPath(page: PageEntry) {
  return page.app === 'admin' ? `/admin${page.path}` : page.path
}

function parameterLabel(param: string) {
  return { id: '课程 ID', recordId: '考试记录 ID', sessionId: '试卷学习会话 ID' }[param] ?? param
}

function isGroupExpanded(group: PageEntry['group']) {
  return search.value.trim() ? !searchCollapsedGroups.value.has(group) : expandedGroups.value.has(group)
}

function toggleGroup(group: PageEntry['group']) {
  const groups = search.value.trim() ? searchCollapsedGroups.value : expandedGroups.value
  if (groups.has(group)) groups.delete(group)
  else groups.add(group)
}

watch(search, () => searchCollapsedGroups.value.clear())

function selectPage(page: PageEntry) {
  expandedGroups.value.add(page.group)
  if (selected.value.id === page.id) return
  previewUrl.value = ''
  params.value = {}
  selected.value = page
  authState.value = getAuthPreviewOptions(page.name)[0]?.value ?? ''
  attempted.value = false
  previewUrl.value = targetUrl.value ?? ''
}

function loadPreview() {
  attempted.value = true
  previewUrl.value = targetUrl.value ?? ''
}

function clearPreview() {
  previewUrl.value = ''
  attempted.value = false
}
</script>

<style scoped src="./pageDirectory.css"></style>
