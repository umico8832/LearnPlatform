import { bfs, dfs, topological } from './traversals'
import { dijkstra } from './weighted'
import { prim, kruskal } from './spanning'
import type { Algorithm, GraphInput } from './types'
export { bfs, dfs, topological, dijkstra, prim, kruskal }
export type * from './types'

export function buildGraphTrace(algorithm: Algorithm, input: GraphInput) {
  switch (algorithm) {
    case 'bfs':
      return bfs(input)
    case 'dfs':
      return dfs(input)
    case 'dijkstra':
      return dijkstra(input)
    case 'prim':
      return prim(input)
    case 'kruskal':
      return kruskal(input)
    case 'topological':
      return topological(input)
    default:
      throw new RangeError('不支持的图算法')
  }
}
