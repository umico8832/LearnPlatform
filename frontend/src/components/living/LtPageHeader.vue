<script setup lang="ts">
/**
 * 学员外框内页面的统一页头：标题、可选说明、右侧操作；返回入口放在 lead 插槽。
 * 宽度与左右留白由 LearnerShell 的内容区负责，这里只管纵向节奏和对齐。
 */
defineProps<{
  title: string
  description?: string
  titleId?: string
}>()
</script>

<template>
  <header class="lt-page-header">
    <div v-if="$slots.lead" class="lead"><slot name="lead" /></div>
    <div class="row">
      <div class="text">
        <h1 :id="titleId" class="title">{{ title }}</h1>
        <p v-if="description" class="description">{{ description }}</p>
        <slot />
      </div>
      <div v-if="$slots.actions" class="actions"><slot name="actions" /></div>
    </div>
  </header>
</template>

<style scoped>
.lt-page-header {
  margin-bottom: var(--lt-space-7);
}

.lead {
  margin-bottom: var(--lt-space-3);
}

.row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--lt-space-4) var(--lt-space-6);
}

.text {
  min-width: 0;
  flex: 1 1 24rem;
}

.title {
  margin: 0;
  color: var(--lt-label);
  font-size: var(--lt-text-title1);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.description {
  max-width: 60ch;
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-label-2);
  font-size: var(--lt-text-body);
  line-height: 1.5;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--lt-space-2);
}
</style>
