<template>
  <section class="preview-panel" aria-label="页面预览">
    <header class="preview-toolbar">
      <span role="status">{{ loading ? '正在载入…' : '页面预览' }}</span>
      <div class="preview-controls">
        <label for="preview-width">桌面宽度</label>
        <select id="preview-width" v-model.number="width">
          <option :value="1280">1280 px</option>
          <option :value="1440">1440 px</option>
          <option :value="1920">1920 px</option>
        </select>
        <button type="button" @click="refresh">刷新</button>
        <button type="button" @click="$emit('stop')">停止预览</button>
      </div>
    </header>
    <div ref="canvas" class="preview-canvas">
      <div class="preview-shell" :style="{ width: `${width * scale}px`, height: `${900 * scale}px` }">
        <iframe
          :key="revision"
          :src="url"
          :title="`${title}预览`"
          :style="{ width: `${width}px`, height: '900px', transform: `scale(${scale})` }"
          @load="loading = false"
        />
      </div>
    </div>
    <p class="preview-help">
      若页面空白或无法连接，请确认对应开发服务已启动，或在新标签中打开。需要登录时，按页面提示完成登录。
    </p>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

defineProps<{ url: string; title: string }>()
defineEmits<{ stop: [] }>()
const width = ref(1440)
const canvas = ref<HTMLElement>()
const availableWidth = ref(0)
const scale = computed(() => (availableWidth.value ? Math.min(1, availableWidth.value / width.value) : 1))
const loading = ref(true)
const revision = ref(0)
let observer: ResizeObserver | undefined

function refresh() {
  loading.value = true
  revision.value++
}

onMounted(() => {
  observer = new ResizeObserver(([entry]) => {
    availableWidth.value = entry.contentRect.width
  })
  if (canvas.value) observer.observe(canvas.value)
})
onBeforeUnmount(() => observer?.disconnect())
</script>

<style scoped>
.preview-panel {
  border: var(--lp-border-light);
  border-radius: var(--lp-radius-md);
  overflow: hidden;
}
.preview-toolbar,
.preview-controls {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
  flex-wrap: wrap;
}
.preview-toolbar {
  justify-content: space-between;
  padding: var(--lp-space-3) var(--lp-space-4);
  background: var(--lp-surface);
  font-size: var(--lp-text-sm);
  border-bottom: var(--lp-border-light);
}
button,
select {
  padding: var(--lp-space-1) var(--lp-space-2);
  border: var(--lp-border-strong-line);
  border-radius: var(--lp-radius-sm);
  background: var(--lp-surface);
  color: var(--lp-text-secondary);
  cursor: pointer;
}
button:hover {
  color: var(--lp-primary);
  border-color: var(--lp-primary);
}
.preview-canvas {
  background: var(--lp-surface-inset);
  overflow: auto;
}
.preview-shell {
  margin-inline: auto;
  background: var(--lp-surface);
}
iframe {
  display: block;
  border: 0;
  transform-origin: top left;
  background: var(--lp-surface);
}
.preview-help {
  padding: var(--lp-space-3) var(--lp-space-4);
  margin: 0;
  font-size: var(--lp-text-xs);
  color: var(--lp-text-secondary);
  border-top: var(--lp-border-light);
  background: var(--lp-surface);
}
</style>
