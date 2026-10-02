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
import type { VisualElementState, VisualTreeElement, VisualTreeNode } from '@/api/ai'

defineProps<{
  element: VisualTreeElement
}>()

function stateLabel(state: VisualElementState): string {
  return (
    (
      {
        current: '当前',
        visited: '已访问',
        sorted: '已排序',
        highlight: '重点',
        swapped: '已交换',
        done: '已完成',
        pending: '待处理',
      } as Partial<Record<VisualElementState, string>>
    )[state] ?? ''
  )
}

const TreeNode = defineComponent({
  name: 'QuestionVisualTreeNode',
  props: {
    node: { type: Object as PropType<VisualTreeNode>, required: true },
  },
  setup(props): () => VNode {
    return (): VNode => {
      const children = (props.node.children || []).map((child, index) => h(TreeNode, { node: child, key: index }))

      return h('div', { class: 'vi-tree-node-wrapper' }, [
        h(
          'div',
          {
            class: `vi-tree-node vi-tree-node--${props.node.state || 'default'}`,
            'aria-label':
              props.node.state && props.node.state !== 'default'
                ? `${props.node.name}，${stateLabel(props.node.state)}`
                : undefined,
          },
          [h('span', { class: 'vi-tree-node-name' }, props.node.name)],
        ),
        children.length > 0 ? h('div', { class: 'vi-tree-children' }, children) : null,
      ])
    }
  },
})
</script>

<style scoped>
.vi-block {
  padding: var(--lp-space-4);
  background: var(--lp-surface-subtle);
  border: var(--lp-border-hairline);
  border-radius: var(--lp-radius-md);
}
.vi-block-label {
  margin-bottom: var(--lp-space-2);
  color: var(--lp-text);
  font-size: var(--lp-text-sm);
  font-weight: var(--lp-weight-semibold);
}
.vi-tree {
  padding-block: var(--lp-space-2);
  overflow-x: auto;
}
.vi-tree-node-wrapper {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: max-content;
}
.vi-tree-node {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 50px;
  margin-bottom: var(--lp-space-1);
  padding: var(--lp-space-2) var(--lp-space-3);
  color: var(--lp-text);
  background: var(--lp-surface);
  border: 2px solid var(--lp-border-strong);
  border-radius: var(--lp-radius-full);
  font-size: var(--lp-text-sm);
}
.vi-tree-node--current {
  background: var(--lp-primary-soft);
  border-color: var(--lp-primary);
  font-weight: var(--lp-weight-semibold);
}
.vi-tree-node--visited {
  background: var(--lp-success-soft);
  border-color: var(--lp-success);
}
.vi-tree-node-name {
  white-space: nowrap;
}
.vi-tree-children {
  position: relative;
  display: flex;
  gap: var(--lp-space-4);
  padding-top: var(--lp-space-2);
}
.vi-tree-children::before {
  position: absolute;
  top: 0;
  left: 50%;
  width: 1px;
  height: var(--lp-space-2);
  background: var(--lp-border-strong);
  content: '';
}
</style>
