import { adjacent, compareId, graph, initial, parentMap, prediction, record, source, sum } from './core'
import type { GraphInput } from './types'

export function dijkstra(input: GraphInput) {
  const g = graph(input),
    start = source(g),
    state = initial(),
    parents = parentMap(g.nodes),
    parentEdges = parentMap(g.nodes)
  if (g.edges.some((edge) => edge.weight < 0)) throw new RangeError('Dijkstra不支持负权边')
  const { emit, finish } = record('dijkstra', g, state),
    settled = new Set<string>()
  const distances: Record<string, number | null> = Object.fromEntries(g.nodes.map((node) => [node, null]))
  distances[start] = 0
  state.distances = distances
  state.parents = parents
  state.parentEdges = parentEdges
  const candidates = () =>
    g.nodes
      .filter((node) => !settled.has(node) && distances[node] !== null)
      .sort((a, b) => distances[a]! - distances[b]! || compareId(a, b))
  state.frontier = candidates()
  emit('初始化距离', '起点距离为零，null表示尚不可达；有限距离在确定前仍是暂定值。')
  while (state.frontier.length) {
    const node = state.frontier[0]
    delete state.activeEdge
    delete state.activeNode
    emit(
      '选择最小暂定距离',
      `下一次确定 ${node}。`,
      prediction(
        `dijkstra-settle-${node}`,
        '下一次确定哪个顶点？',
        node,
        '只看起点直接边最短的顶点',
        '比较所有尚未确定且可达顶点的暂定距离，同距按顶点标识升序。',
      ),
    )
    settled.add(node)
    state.order.push(node)
    state.discovered.push(node)
    state.activeNode = node
    state.frontier = candidates()
    emit('确定最短距离', `${node} 的最短距离确定为 ${distances[node]}。`)
    for (const next of adjacent(g, node)) {
      state.activeEdge = next.edge.id
      if (settled.has(next.node)) {
        emit('跳过已确定顶点', `${next.node} 已确定。`)
        continue
      }
      const proposed = sum(distances[node]!, next.edge.weight)
      if (distances[next.node] === null || proposed < distances[next.node]!) {
        distances[next.node] = proposed
        parents[next.node] = node
        parentEdges[next.node] = next.edge.id
        state.frontier = candidates()
        emit('松弛成功', `${next.node} 的暂定距离更新为 ${proposed}，尚未最终确定。`)
      } else emit('不更新距离', '候选路径未严格缩短距离，保留原前驱。')
    }
  }
  delete state.activeEdge
  delete state.activeNode
  emit('最短路结束', '不可达顶点保留null；target不会提前截断其余顶点的计算。')
  return finish({ kind: 'shortest-path', distances, parents, parentEdges, settled: state.order })
}
