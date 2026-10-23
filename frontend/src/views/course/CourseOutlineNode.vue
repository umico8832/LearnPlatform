<script setup lang="ts">
import { ChevronRight, FileText, Folder } from '@lucide/vue'
import { LtButton } from '@/components/living'
import type { KnowledgePointVO } from '@/api/knowledgePoint'
defineProps<{ node: KnowledgePointVO; canStudy: boolean }>()
defineEmits<{ study: [id: number] }>()
</script>
<template>
  <li class="outline-node">
    <details v-if="node.children?.length">
      <summary>
        <ChevronRight class="chevron" aria-hidden="true" /><Folder aria-hidden="true" /><span>{{ node.name }}</span>
      </summary>
      <div
        v-if="node.description || (canStudy && node.contentKey && node.contentReviewStatus === 'REVIEWED')"
        class="branch-copy"
      >
        <p v-if="node.description">{{ node.description }}</p>
        <LtButton
          v-if="canStudy && node.contentKey && node.contentReviewStatus === 'REVIEWED'"
          size="sm"
          @click="$emit('study', node.id)"
          >开始学习</LtButton
        >
      </div>
      <ul>
        <CourseOutlineNode
          v-for="child in node.children"
          :key="child.id"
          :node="child"
          :can-study="canStudy"
          @study="$emit('study', $event)"
        />
      </ul>
    </details>
    <div v-else class="leaf-row">
      <FileText aria-hidden="true" />
      <div class="node-copy">
        <h3>{{ node.name }}</h3>
        <p v-if="node.description">{{ node.description }}</p>
      </div>
      <LtButton
        v-if="canStudy && node.contentKey && node.contentReviewStatus === 'REVIEWED'"
        size="sm"
        @click="$emit('study', node.id)"
        >开始学习</LtButton
      >
    </div>
  </li>
</template>
<style scoped>
.outline-node + .outline-node {
  border-top: calc(var(--lt-space-1) / 4) solid var(--lt-hairline);
}
summary,
.leaf-row {
  display: flex;
  align-items: center;
  gap: var(--lt-space-3);
  padding: var(--lt-space-5) 0;
}
summary {
  cursor: pointer;
  list-style: none;
  font-weight: 600;
}
summary::-webkit-details-marker {
  display: none;
}
summary span,
.node-copy {
  flex: 1;
  min-width: 0;
}
svg {
  flex: none;
  width: var(--lt-space-4);
  height: var(--lt-space-4);
  color: var(--lt-label-2);
}
.chevron {
  transition: transform var(--lt-dur-snappy) var(--lt-ease-snappy);
}
details[open] > summary .chevron {
  transform: rotate(90deg);
}
ul {
  list-style: none;
  margin: 0;
  padding: 0 0 0 var(--lt-space-6);
}
h3 {
  margin: 0;
  font-size: var(--lt-text-body);
}
p {
  margin: var(--lt-space-2) 0 0;
  color: var(--lt-label-2);
  line-height: var(--lt-leading-reading);
}
.branch-copy {
  padding: 0 0 var(--lt-space-4) var(--lt-space-7);
}
</style>
