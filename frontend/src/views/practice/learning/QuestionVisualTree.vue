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
  padding: var(--lt-space-4);
  background: var(--lt-well);
  border: thin solid var(--lt-separator);
  border-radius: var(--lt-radius-md);
}
.vi-block-label {
  margin-bottom: var(--lt-space-2);
  color: var(--lt-label);
  font-size: var(--lt-text-footnote);
  font-weight: 600;
}
.vi-tree {
  padding-block: var(--lt-space-2);
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
  min-width: calc(var(--lt-space-1) * 12.5);
  margin-bottom: var(--lt-space-1);
  padding: var(--lt-space-2) var(--lt-space-3);
  color: var(--lt-label);
  background: var(--lt-surface);
  border: calc(var(--lt-space-1) * 0.5) solid var(--lt-separator);
  border-radius: var(--lt-radius-pill);
  font-size: var(--lt-text-footnote);
}
.vi-tree-node--current {
  background: var(--lt-accent-soft);
  border-color: var(--lt-accent);
  font-weight: 600;
}
.vi-tree-node--visited {
  background: var(--lt-green-soft);
  border-color: var(--lt-green-text);
}
.vi-tree-node-name {
  white-space: nowrap;
}
.vi-tree-children {
  position: relative;
  display: flex;
  gap: var(--lt-space-4);
  padding-top: var(--lt-space-2);
}
.vi-tree-children::before {
  position: absolute;
  top: 0;
  left: 50%;
  width: calc(var(--lt-space-1) * 0.25);
  height: var(--lt-space-2);
  background: var(--lt-separator);
  content: '';
}
</style>
