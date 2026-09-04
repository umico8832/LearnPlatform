<template>
  <div class="vi-block">
    <div class="vi-block-label">{{ element.label }}</div>
    <div class="vi-tree">
      <TreeNode :node="element.root" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { defineComponent, h } from 'vue'
import type { PropType, VNode } from 'vue'
import type { VisualTreeElement, VisualTreeNode } from '@/api/ai'

defineProps<{
  element: VisualTreeElement
}>()

const TreeNode = defineComponent({
  name: 'QuestionVisualTreeNode',
  props: {
    node: { type: Object as PropType<VisualTreeNode>, required: true },
  },
  setup(props): () => VNode {
    return (): VNode => {
      const children = (props.node.children || []).map((child, index) => h(TreeNode, { node: child, key: index }))

      return h('div', { class: 'vi-tree-node-wrapper' }, [
        h('div', { class: `vi-tree-node vi-tree-node--${props.node.state || 'default'}` }, [
          h('span', { class: 'vi-tree-node-name' }, props.node.name),
        ]),
        children.length > 0 ? h('div', { class: 'vi-tree-children' }, children) : null,
      ])
    }
  },
})
</script>

<style scoped>
.vi-block {
  background: #f8f9fa;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 14px;
}

.vi-block-label {
  color: #409eff;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.vi-tree {
  padding: 8px 0;
}

.vi-tree-node-wrapper {
  align-items: center;
  display: flex;
  flex-direction: column;
  position: relative;
}

.vi-tree-node {
  align-items: center;
  background: #fff;
  border: 2px solid #dcdfe6;
  border-radius: 20px;
  display: inline-flex;
  font-size: 13px;
  justify-content: center;
  margin-bottom: 4px;
  min-width: 50px;
  padding: 6px 12px;
}

.vi-tree-node--default {
  border-color: #dcdfe6;
}

.vi-tree-node--current {
  background: #ecf5ff;
  border-color: #409eff;
  font-weight: 600;
}

.vi-tree-node--visited {
  background: #f0f9eb;
  border-color: #67c23a;
}

.vi-tree-node-name {
  white-space: nowrap;
}

.vi-tree-children {
  display: flex;
  gap: 16px;
  padding-top: 8px;
  position: relative;
}

.vi-tree-children::before {
  background: #dcdfe6;
  content: '';
  height: 8px;
  left: 50%;
  position: absolute;
  top: 0;
  width: 1px;
}
</style>
