import type { SceneModule } from '../engine/types'

/**
 * Scene catalogue. Each scene is loaded on demand so an embedded player only
 * downloads the structure it shows. `place` locates the scene under its
 * knowledge point; visualizations are reached from there, not from the main nav.
 */
export interface SceneEntry {
  id: string
  place: { course: string; chapter: string; topic: string }
  load: () => Promise<SceneModule>
}

export const scenes: SceneEntry[] = [
  {
    id: 'linked-list',
    place: { course: '数据结构', chapter: '线性表', topic: '单链表' },
    load: () => import('./linked-list').then((module) => module.linkedList),
  },
  {
    id: 'stack',
    place: { course: '数据结构', chapter: '栈和队列', topic: '栈' },
    load: () => import('./stack').then((module) => module.stack),
  },
  {
    id: 'binary-tree',
    place: { course: '数据结构', chapter: '树与二叉树', topic: '二叉树的遍历' },
    load: () => import('./binary-tree').then((module) => module.binaryTree),
  },
]

export function findScene(id: string): SceneEntry | undefined {
  return scenes.find((entry) => entry.id === id)
}
