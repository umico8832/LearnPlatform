<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft } from '@lucide/vue'
import { LtButton, LtIconButton, LtSegmented } from '@/components/living'
import { useLivingTheme } from '@/composables/useLivingTheme'
import { collapseInto, findScene, specFromQuery, specToQuery, VizPlayer, type SceneModule, type SceneSpec } from '@/viz'

/** Full-page visualization with its own shareable link: the scene, operation and data live in the URL. */
useLivingTheme()

const route = useRoute()
const router = useRouter()
const entry = computed(() => findScene(String(route.params.scene ?? '')))
const module = shallowRef<SceneModule | null>(null)
const failed = ref(false)
const canGoBack = typeof window !== 'undefined' && typeof window.history.state?.back === 'string'

/** Back into the explanation this page was expanded from; the card morphs back into the embedded player. */
function goBack() {
  collapseInto(
    () =>
      new Promise<void>((resolve) => {
        const stop = router.afterEach(() => {
          stop()
          resolve()
        })
        router.back()
      }),
  )
}

async function load() {
  const target = entry.value
  module.value = null
  failed.value = false
  if (!target) return
  try {
    const loaded = await target.load()
    if (entry.value === target) module.value = loaded
  } catch {
    if (entry.value === target) failed.value = true
  }
}

watch(entry, load, { immediate: true })

const spec = computed<SceneSpec | null>(() => (module.value ? specFromQuery(module.value, route.query) : null))
const ops = computed(() => module.value?.ops.map((item) => ({ value: item.id, label: item.label })) ?? [])
const op = computed({
  get: () => spec.value?.op ?? '',
  set: (value: string) => spec.value && updateSpec({ ...spec.value, op: value }),
})
const opLabel = computed(() => ops.value.find((item) => item.value === op.value)?.label ?? '')

function updateSpec(next: SceneSpec) {
  if (module.value) void router.replace({ query: specToQuery(module.value, next) })
}

watch(
  () => [entry.value?.place.topic, opLabel.value],
  ([topic, label]) => {
    if (topic) document.title = `${topic}${label ? ` · ${label}` : ''} · LearnPlatform`
  },
  { immediate: true },
)
</script>

<template>
  <div class="viz-page">
    <header class="viz-page-head">
      <LtIconButton v-if="canGoBack" label="返回" @click="goBack">
        <ChevronLeft aria-hidden="true" />
      </LtIconButton>
      <div v-if="entry" class="viz-page-heading">
        <nav class="viz-crumbs" aria-label="所在位置">
          <span>{{ entry.place.course }}</span>
          <span aria-hidden="true">›</span>
          <span>{{ entry.place.chapter }}</span>
        </nav>
        <div class="viz-page-title-row">
          <h1 class="viz-page-title">{{ entry.place.topic }}</h1>
          <LtSegmented v-if="ops.length > 1" v-model="op" :options="ops" label="操作" />
          <span v-else-if="opLabel" class="viz-page-op">{{ opLabel }}</span>
        </div>
      </div>
    </header>

    <main class="viz-page-main">
      <VizPlayer v-if="spec" size="full" :title="entry?.place.topic" :spec="spec" @update:spec="updateSpec" />
      <div v-else class="viz-page-state">
        <template v-if="!entry">
          <p>没有找到这个可视化。</p>
          <LtButton @click="router.push('/')">回到首页</LtButton>
        </template>
        <template v-else-if="failed">
          <p>可视化加载失败，请检查网络后重试。</p>
          <LtButton @click="load">重试</LtButton>
        </template>
        <p v-else>正在准备动画…</p>
      </div>
    </main>
  </div>
</template>

<style scoped>
.viz-page {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  height: 100vh;
  min-height: 720px;
  max-width: 1640px;
  margin: 0 auto;
  padding: 0 28px 24px;
}

.viz-page-head {
  display: flex;
  align-items: center;
  gap: var(--lt-space-3);
  min-height: 84px;
}

.viz-page-head > :first-child:not(.viz-page-heading) {
  margin-left: -8px;
}

.viz-crumbs {
  display: flex;
  gap: 6px;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.viz-page-title-row {
  display: flex;
  align-items: center;
  gap: var(--lt-space-4);
  margin-top: 2px;
}

.viz-page-title {
  margin: 0;
  font-size: var(--lt-text-title2);
}

.viz-page-op {
  color: var(--lt-label-2);
  font-size: var(--lt-text-body);
}

.viz-page-main {
  min-height: 0;
}

.viz-page-state {
  display: grid;
  place-content: center;
  justify-items: center;
  gap: var(--lt-space-3);
  height: 100%;
  color: var(--lt-label-2);
}

.viz-page-state p {
  margin: 0;
}
</style>
