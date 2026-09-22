<template>
  <main class="auth-preview-page">
    <header class="auth-preview-header">
      <div>
        <span class="auth-preview-badge">仅开发环境</span>
        <h1>认证页面预览</h1>
        <p>直接查看页面与流程状态，不请求接口或触发人机验证。</p>
      </div>
      <nav class="auth-preview-links" aria-label="预览工具">
        <a href="/dev/pages">全部页面</a>
        <a :href="frameSrc" target="_blank" rel="noopener noreferrer">在新标签打开</a>
      </nav>
    </header>

    <div class="auth-preview-workspace">
      <nav class="auth-preview-navigation" aria-label="认证页面状态">
        <section v-for="group in previewGroups" :key="group.label">
          <h2>{{ group.label }}</h2>
          <button
            v-for="item in group.items"
            :key="item.id"
            type="button"
            :class="{ 'is-active': activeId === item.id }"
            :aria-current="activeId === item.id ? 'page' : undefined"
            @click="activeId = item.id"
          >
            <span>{{ item.title }}</span>
            <small>{{ item.description }}</small>
          </button>
        </section>
      </nav>

      <section class="auth-preview-stage" aria-labelledby="preview-title">
        <div class="auth-preview-toolbar">
          <div>
            <strong id="preview-title">{{ activePreview.title }}</strong>
            <span>{{ activeViewport.width }} × {{ activeViewport.height }}</span>
          </div>
          <div class="auth-preview-viewports" aria-label="预览尺寸">
            <button
              v-for="viewport in viewports"
              :key="viewport.id"
              type="button"
              :class="{ 'is-active': viewportId === viewport.id }"
              :aria-pressed="viewportId === viewport.id"
              @click="viewportId = viewport.id"
            >
              {{ viewport.label }}
            </button>
          </div>
          <button type="button" class="auth-preview-refresh" @click="refreshKey++">刷新预览</button>
        </div>

        <div ref="canvasElement" class="auth-preview-canvas">
          <div class="auth-preview-frame-shell" :style="frameShellStyle">
            <iframe
              :key="`${frameSrc}-${refreshKey}`"
              :src="frameSrc"
              :title="`${activePreview.title}预览`"
              :style="frameStyle"
            />
          </div>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

interface PreviewItem {
  id: string
  title: string
  description: string
  path: string
  state: string
}

const previewGroups: Array<{ label: string; items: PreviewItem[] }> = [
  {
    label: '登录与注册',
    items: [
      { id: 'login', title: '登录', description: '默认表单', path: '/login', state: 'default' },
      { id: 'register-1', title: '注册 · 账户信息', description: '第 1 步', path: '/register', state: 'step-1' },
      { id: 'register-2', title: '注册 · 邮箱验证', description: '第 2 步', path: '/register', state: 'step-2' },
      { id: 'register-3', title: '注册 · 设置密码', description: '第 3 步', path: '/register', state: 'step-3' },
    ],
  },
  {
    label: '找回密码',
    items: [
      { id: 'forgot-form', title: '申请重置', description: '填写邮箱', path: '/forgot-password', state: 'form' },
      { id: 'forgot-sent', title: '邮件已发送', description: '可重新发送', path: '/forgot-password', state: 'sent' },
      { id: 'forgot-resent', title: '邮件已重发', description: '完成状态', path: '/forgot-password', state: 'resent' },
      {
        id: 'forgot-support',
        title: '联系支持',
        description: '重发已达上限',
        path: '/forgot-password',
        state: 'support',
      },
    ],
  },
  {
    label: '设置新密码',
    items: [
      { id: 'reset-checking', title: '验证链接', description: '加载状态', path: '/reset-password', state: 'checking' },
      { id: 'reset-form', title: '设置新密码', description: '有效链接', path: '/reset-password', state: 'form' },
      { id: 'reset-success', title: '密码已重置', description: '完成状态', path: '/reset-password', state: 'success' },
      { id: 'reset-error', title: '链接失效', description: '错误状态', path: '/reset-password', state: 'error' },
    ],
  },
  {
    label: '第三方登录',
    items: [
      { id: 'oauth-loading', title: 'Google 登录', description: '处理中', path: '/oauth/callback', state: 'loading' },
      { id: 'oauth-error', title: 'Google 登录', description: '未完成', path: '/oauth/callback', state: 'error' },
    ],
  },
]

const previews = previewGroups.flatMap((group) => group.items)
const viewports = [
  { id: 'desktop', label: '桌面', width: 1440, height: 900 },
  { id: 'tablet', label: '平板', width: 768, height: 900 },
  { id: 'mobile', label: '手机', width: 390, height: 844 },
] as const

const activeId = ref(previews[0].id)
const viewportId = ref<(typeof viewports)[number]['id']>('desktop')
const refreshKey = ref(0)
const canvasElement = ref<HTMLElement>()
const canvasWidth = ref(0)
const activePreview = computed(() => previews.find((item) => item.id === activeId.value) ?? previews[0])
const activeViewport = computed(() => viewports.find((viewport) => viewport.id === viewportId.value) ?? viewports[0])
const frameScale = computed(() =>
  canvasWidth.value > 0 ? Math.min(1, canvasWidth.value / activeViewport.value.width) : 1,
)
const frameSrc = computed(
  () => `${activePreview.value.path}?auth-preview=${encodeURIComponent(activePreview.value.state)}`,
)
const frameShellStyle = computed(() => ({
  width: `${activeViewport.value.width * frameScale.value}px`,
  height: `${activeViewport.value.height * frameScale.value}px`,
}))
const frameStyle = computed(() => ({
  width: `${activeViewport.value.width}px`,
  height: `${activeViewport.value.height}px`,
  transform: `scale(${frameScale.value})`,
}))

let canvasObserver: ResizeObserver | undefined

onMounted(() => {
  if (!canvasElement.value) return
  canvasObserver = new ResizeObserver(([entry]) => {
    canvasWidth.value = entry?.contentRect.width ?? 0
  })
  canvasObserver.observe(canvasElement.value)
})

onBeforeUnmount(() => canvasObserver?.disconnect())
</script>

<style scoped>
.auth-preview-page {
  min-height: 100dvh;
  padding: var(--lp-space-8);
  background: var(--lp-bg);
  color: var(--lp-text);
}
.auth-preview-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--lp-space-6);
  max-width: var(--lp-container-max);
  margin: 0 auto var(--lp-space-6);
}
.auth-preview-header h1 {
  margin: var(--lp-space-2) 0 0;
  font-size: var(--lp-text-5xl);
  line-height: var(--lp-leading-tight);
}
.auth-preview-header p {
  margin: var(--lp-space-2) 0 0;
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-body);
}
.auth-preview-header a {
  flex: 0 0 auto;
  min-height: var(--lp-space-10);
  padding: var(--lp-space-2) var(--lp-space-4);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface);
  color: var(--lp-link);
  font-weight: var(--lp-weight-semibold);
  line-height: var(--lp-space-6);
  text-decoration: none;
}

.auth-preview-links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lp-space-3);
}
.auth-preview-badge {
  display: inline-flex;
  padding: var(--lp-space-1) var(--lp-space-2);
  border-radius: var(--lp-radius-full);
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-semibold);
}
.auth-preview-workspace {
  display: grid;
  grid-template-columns: var(--lp-sidebar-width) minmax(0, 1fr);
  gap: var(--lp-space-5);
  max-width: var(--lp-container-max);
  margin: 0 auto;
}
.auth-preview-navigation,
.auth-preview-stage {
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-xl);
  background: var(--lp-surface);
  box-shadow: var(--lp-shadow-sm);
}
.auth-preview-navigation {
  align-self: start;
  padding: var(--lp-space-4);
}
.auth-preview-navigation section + section {
  margin-top: var(--lp-space-5);
}
.auth-preview-navigation h2 {
  margin: 0 0 var(--lp-space-2);
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-semibold);
  letter-spacing: var(--lp-tracking-wide);
}
.auth-preview-navigation button {
  display: grid;
  width: 100%;
  min-height: 44px;
  padding: var(--lp-space-2) var(--lp-space-3);
  border: 0;
  border-radius: var(--lp-radius-md);
  background: transparent;
  color: var(--lp-text);
  text-align: left;
  cursor: pointer;
}
.auth-preview-navigation button:hover {
  background: var(--lp-surface-subtle);
}
.auth-preview-navigation button.is-active {
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
}
.auth-preview-navigation small {
  margin-top: 2px;
  color: var(--lp-text-muted);
}
.auth-preview-stage {
  min-width: 0;
  overflow: hidden;
}
.auth-preview-toolbar {
  display: flex;
  align-items: center;
  gap: var(--lp-space-4);
  padding: var(--lp-space-3) var(--lp-space-4);
  border-bottom: var(--lp-border-hairline);
}
.auth-preview-toolbar > div:first-child {
  display: grid;
  margin-right: auto;
}
.auth-preview-toolbar span {
  color: var(--lp-text-muted);
  font-size: var(--lp-text-xs);
}
.auth-preview-viewports {
  display: flex;
  gap: var(--lp-space-1);
  padding: var(--lp-space-1);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-surface-subtle);
}
.auth-preview-viewports button,
.auth-preview-refresh {
  min-height: var(--lp-space-10);
  padding: var(--lp-space-2) var(--lp-space-3);
  border: 0;
  border-radius: var(--lp-radius-md);
  background: transparent;
  color: var(--lp-text-secondary);
  font-weight: var(--lp-weight-medium);
  cursor: pointer;
}
.auth-preview-viewports button.is-active {
  background: var(--lp-surface);
  color: var(--lp-text);
  box-shadow: var(--lp-shadow-xs);
}
.auth-preview-refresh {
  border: var(--lp-border-hairline);
  background: var(--lp-surface);
}
.auth-preview-navigation button:focus-visible,
.auth-preview-viewports button:focus-visible,
.auth-preview-refresh:focus-visible,
.auth-preview-header a:focus-visible {
  outline: 2px solid var(--lp-primary);
  outline-offset: var(--lp-space-1);
}
.auth-preview-canvas {
  min-height: 720px;
  padding: var(--lp-space-4);
  overflow: auto;
  background: var(--lp-surface-inset);
}
.auth-preview-canvas iframe {
  display: block;
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-lg);
  background: var(--lp-paper-0);
  box-shadow: var(--lp-shadow-md);
  transform-origin: top left;
}
.auth-preview-frame-shell {
  margin: 0 auto;
}
@media (max-width: 760px) {
  .auth-preview-page {
    padding: var(--lp-space-4);
  }
  .auth-preview-header,
  .auth-preview-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .auth-preview-header a {
    align-self: flex-start;
  }
  .auth-preview-workspace {
    grid-template-columns: 1fr;
  }
  .auth-preview-navigation {
    display: flex;
    gap: var(--lp-space-5);
    overflow-x: auto;
  }
  .auth-preview-navigation section {
    flex: 0 0 210px;
  }
  .auth-preview-navigation section + section {
    margin-top: 0;
  }
  .auth-preview-viewports {
    align-self: flex-start;
  }
}
</style>
