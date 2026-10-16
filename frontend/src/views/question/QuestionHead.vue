<script setup lang="ts">
import { computed } from 'vue'
import { ChevronLeft } from '@lucide/vue'
import { LtIconButton } from '@/components/living'
import type { UnderstandingPosition } from '@/api/understanding'

/** Where this question sits (course › chapter, then the knowledge point as the title) and whether it is a preview. */
const props = defineProps<{
  position: UnderstandingPosition | null
  loading: boolean
  previewing: boolean
  courseLink: string
}>()

const emit = defineEmits<{ back: [] }>()

const crumbs = computed(() => {
  const place = props.position
  if (!place) return []
  return place.chapterName && place.chapterName !== place.knowledgePointName
    ? [place.courseName, place.chapterName]
    : [place.courseName]
})
</script>

<template>
  <header class="head">
    <div class="head-rail">
      <LtIconButton label="返回" @click="emit('back')">
        <ChevronLeft aria-hidden="true" />
      </LtIconButton>
    </div>
    <div v-if="position" class="head-title">
      <nav class="crumbs" aria-label="所在位置">
        <template v-for="(crumb, i) in crumbs" :key="crumb">
          <span v-if="i > 0" aria-hidden="true">›</span>
          <RouterLink v-if="i === 0" :to="courseLink">{{ crumb }}</RouterLink>
          <span v-else>{{ crumb }}</span>
        </template>
      </nav>
      <h1 class="title">{{ position.knowledgePointName }}</h1>
      <p v-if="previewing" class="preview" role="status">
        <span class="preview-label">预览 · 含待审核内容</span>
        预览不记录作答、提示和复习。
        <RouterLink :to="{ query: {} }">退出预览</RouterLink>
      </p>
    </div>
    <div v-else-if="loading" class="head-title" aria-hidden="true">
      <span class="skeleton" style="width: 140px; height: 12px" />
      <span class="skeleton" style="width: 220px; height: 26px; margin-top: 8px" />
    </div>
  </header>
</template>

<style scoped>
.head {
  display: grid;
  grid-template-columns: var(--rail) minmax(0, 1fr);
  column-gap: var(--rail-gap);
  align-items: end;
  padding: var(--lt-space-7) 0;
}

.head-rail {
  margin-left: -8px;
}

.head-title {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.crumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  color: var(--lt-label-3);
  font-size: var(--lt-text-caption);
}

.crumbs a {
  color: var(--lt-label-2);
  text-decoration: none;
}

.crumbs a:hover {
  color: var(--lt-accent);
}

.title {
  margin: 4px 0 0;
  font-size: var(--lt-text-title2);
}

.preview {
  margin: var(--lt-space-3) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}

.preview-label {
  margin-right: 6px;
  padding: 2px 10px;
  border-radius: var(--lt-radius-pill);
  background: var(--lt-orange-soft);
  color: var(--lt-orange-text);
  font-weight: 600;
}

.preview a {
  margin-left: 4px;
  color: var(--lt-accent);
}

.skeleton {
  display: block;
  border-radius: 8px;
  background: var(--lt-fill-2);
}
</style>
