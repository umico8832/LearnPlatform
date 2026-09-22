<template>
  <main class="page-directory">
    <header class="directory-header">
      <div>
        <div class="directory-heading">
          <h1>全局页面预览</h1>
          <span class="dev-label">仅开发环境</span>
        </div>
        <p>查找页面、填写访问参数，按需打开真实页面。</p>
      </div>
      <div class="header-links">
        <a href="/dev/auth-preview" target="_blank" rel="noopener noreferrer">认证状态预览 ↗</a>
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
              {{ group.label }} <span>{{ group.items.length }}</span>
            </h2>
            <button
              v-for="page in group.items"
              :key="page.id"
              type="button"
              :data-page="page.id"
              :class="{ 'is-selected': selected.id === page.id }"
              :aria-current="selected.id === page.id ? 'true' : undefined"
              @click="selectPage(page)"
            >
              <span>{{ page.title }}</span>
              <small>{{ displayPath(page) }}</small>
            </button>
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

          <p v-if="selected.notice" class="page-notice">{{ selected.notice }}</p>
          <p v-if="selected.group === 'auth'" class="page-notice">
            此处打开真实认证流程；查看不同步骤与结果，请使用上方“认证状态预览”。
          </p>

          <div v-if="selected.app === 'admin'" class="admin-settings">
            <label for="admin-base">管理端开发地址</label>
            <input id="admin-base" v-model="adminBase" type="url" spellcheck="false" :aria-invalid="!validAdminBase" />
            <p v-if="!validAdminBase" class="field-error" role="alert">
              请输入有效的 HTTP 或 HTTPS 地址，不含账号、查询参数或片段。
            </p>
            <p v-else class="field-help">管理端需单独启动：<code>npm run dev:admin</code>，并在管理端登录。</p>
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
              />
            </label>
            <p class="field-help">使用当前账号有权访问的真实记录 ID；不确定时可先从业务入口进入。</p>
          </div>

          <div class="page-actions">
            <button type="button" class="primary-action" :disabled="!targetUrl" @click="startPreview">开始预览</button>
            <a v-if="targetUrl" class="secondary-action" :href="targetUrl" target="_blank" rel="noopener noreferrer"
              >在新标签打开 ↗</a
            >
            <a v-if="entryUrl" :href="entryUrl" target="_blank" rel="noopener noreferrer">从业务入口进入 ↗</a>
          </div>
          <p class="field-help">
            {{ targetUrl ? '预览中的登录、计时和业务操作会实际生效。' : '填写有效的地址与页面参数后即可打开。' }}
          </p>
        </div>

        <PagePreviewFrame
          v-if="previewUrl"
          :key="previewUrl"
          :url="previewUrl"
          :title="selected.title"
          @stop="previewUrl = ''"
        />
        <div v-else class="preview-empty">
          <span class="preview-symbol" aria-hidden="true">↗</span>
          <h3>准备好后，开始预览</h3>
          <p>选择目录中的页面不会自动加载。点击“开始预览”，或在新标签中调试完整页面。</p>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { pageGroups, pages, type PageEntry } from './pageCatalog'
import { normalizeAdminBase, resolvePageUrl } from './pageDirectoryUrl'
import PagePreviewFrame from './PagePreviewFrame.vue'

const learnerOrigin = window.location.origin
const defaultAdminBase = new URL('/admin/', learnerOrigin)
defaultAdminBase.port = '5174'
const adminBase = ref(defaultAdminBase.href)
const validAdminBase = computed(() => normalizeAdminBase(adminBase.value))
const search = ref('')
const selected = ref(pages.find((page) => page.name === 'Home') ?? pages[0])
const params = ref<Record<string, string>>({})
const previewUrl = ref('')
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
const targetUrl = computed(() => resolvePageUrl(selected.value, params.value, learnerOrigin, adminBase.value))
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

function selectPage(page: PageEntry) {
  if (selected.value.id === page.id) return
  previewUrl.value = ''
  params.value = {}
  selected.value = page
}

function startPreview() {
  if (targetUrl.value) previewUrl.value = targetUrl.value
}

watch(
  [params, adminBase],
  () => {
    previewUrl.value = ''
  },
  { deep: true },
)
</script>

<style scoped src="./pageDirectory.css"></style>
