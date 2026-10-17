import type { Predict, VarView } from '../../engine/types'

export type Algorithm = 'bfs' | 'dfs' | 'dijkstra' | 'prim' | 'kruskal' | 'topological'
export interface GraphEdge {
  readonly id: string
  readonly from: string
  readonly to: string
  readonly weight: number
}
export interface GraphInput {
  readonly directed: boolean
  readonly nodes: readonly string[]
  readonly edges: readonly GraphEdge[]
  readonly start?: string
  readonly target?: string
  readonly neighborOrder?: 'ascending-node-id'
  readonly zeroIndegreeOrder?: 'ascending-node-id'
  readonly tieBreak?: 'weight-then-edge-id'
}
export type Frozen<T> = T extends object ? { readonly [K in keyof T]: Frozen<T[K]> } : T
export interface GraphState {
  order: string[]
  frontier: string[]
  discovered: string[]
  activeNode?: string
  activeEdge?: string
  stack?: string[]
  distances?: Record<string, number | null>
  parents?: Record<string, string | null>
  parentEdges?: Record<string, string | null>
  selectedEdges?: string[]
  totalWeight?: number
  indegree?: Record<string, number>
  components?: number
  representatives?: Record<string, string>
}
export interface GraphStep {
  readonly id: string
  readonly title: string
  readonly note: string
  readonly phase: string
  readonly vars: readonly Frozen<VarView>[]
  readonly predict?: Frozen<Predict>
  readonly state: Frozen<GraphState>
}
export interface TraversalResult {
  kind: 'traversal'
  order: string[]
  parents: Record<string, string | null>
}
export interface PathResult {
  kind: 'shortest-path'
  distances: Record<string, number | null>
  parents: Record<string, string | null>
  parentEdges: Record<string, string | null>
  settled: string[]
}
export interface ForestResult {
  kind: 'spanning-forest'
  selectedEdges: string[]
  totalWeight: number
  components: number
  connected: boolean
}
export interface TopologicalResult {
  kind: 'topological'
  order: string[]
  acyclic: boolean
  remaining: string[]
}
export type GraphResult = TraversalResult | PathResult | ForestResult | TopologicalResult
export interface GraphTrace<R extends GraphResult = GraphResult> {
  readonly algorithm: Algorithm
  readonly input: Frozen<GraphInput>
  readonly steps: readonly GraphStep[]
  readonly result: Frozen<R>
}
