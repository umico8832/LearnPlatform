/**
 * 数据结构可视化（ADR-0011）：AI 只写场景描述，确定性模拟器逐步计算，舞台只负责播放。
 * engine/ 与 scenes/ 与框架无关；VizPlayer 是唯一的 Vue 入口，嵌入与整页两种尺寸共用。
 */
export { default as VizPlayer } from './VizPlayer.vue'
export type { SceneModule, SceneSpec, Trace } from './engine/types'
export { findScene, scenes, type SceneEntry } from './scenes'
export { specFromQuery, specToQuery } from './spec'
export { collapseInto } from './expandTransition'
