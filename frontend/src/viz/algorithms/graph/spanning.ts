import { compareEdge, graph, initial, prediction, record, sum } from './core'
import type { GraphInput } from './types'

export function prim(input: GraphInput) {
  const g = graph(input, 'undirected'),
    state = initial(),
    included = new Set<string>()
  const { emit, finish } = record('prim', g, state)
  state.selectedEdges = []
  state.totalWeight = 0
  state.components = 0
  const roots = g.start ? [g.start, ...g.nodes.filter((node) => node !== g.start)] : g.nodes
  for (const root of roots) {
    if (included.has(root)) continue
    delete state.activeEdge
    state.activeNode = root
    state.components++
    included.add(root)
    state.discovered.push(root)
    state.order.push(root)
    emit('开始一个分量', `从 ${root} 开始，候选边必须恰有一端已入树。`)
    while (true) {
      const cut = g.edges.filter((edge) => included.has(edge.from) !== included.has(edge.to)).sort(compareEdge)
      state.frontier = cut.map((edge) => edge.id)
      if (!cut.length) {
        emit('当前分量完成', '没有跨割边；如还有未入树顶点，将开始下一分量。')
        break
      }
      const edge = cut[0],
        node = included.has(edge.from) ? edge.to : edge.from
      state.activeEdge = edge.id
      emit(
        '选择最小跨割边',
        `选边 ${edge.id}，权 ${edge.weight}。`,
        prediction(
          `prim-select-${edge.id}`,
          '接下来可选哪条边？',
          edge.id,
          '任意两端都在树外的较小边',
          '只比较跨割边；同权按边标识升序。',
        ),
      )
      included.add(node)
      state.discovered.push(node)
      state.order.push(node)
      state.selectedEdges.push(edge.id)
      state.totalWeight = sum(state.totalWeight, edge.weight)
      state.activeNode = node
      state.frontier = g.edges
        .filter((candidate) => included.has(candidate.from) !== included.has(candidate.to))
        .sort(compareEdge)
        .map((candidate) => candidate.id)
      emit('加入新顶点和边', `${node} 加入当前生成树。`)
    }
  }
  delete state.activeEdge
  delete state.activeNode
  emit(
    '最小生成森林完成',
    state.components === 1 ? '原图连通，森林是一棵最小生成树。' : '原图不连通，结果是最小生成森林。',
  )
  return finish({
    kind: 'spanning-forest',
    selectedEdges: state.selectedEdges,
    totalWeight: state.totalWeight,
    components: state.components,
    connected: state.components === 1,
  })
}

export function kruskal(input: GraphInput) {
  const g = graph(input, 'undirected'),
    state = initial()
  const { emit, finish } = record('kruskal', g, state),
    parent = new Map(g.nodes.map((node) => [node, node]))
  const find = (node: string): string => {
    let current = node
    while (parent.get(current) !== current) current = parent.get(current)!
    return current
  }
  state.selectedEdges = []
  state.totalWeight = 0
  state.components = g.nodes.length
  state.representatives = Object.fromEntries(g.nodes.map((node) => [node, find(node)]))
  const ordered = [...g.edges].sort(compareEdge)
  state.frontier = ordered.map((edge) => edge.id)
  emit('按权排序全部边', '同权按边标识升序，使用并查集判断是否成环。')
  for (const edge of ordered) {
    state.activeEdge = edge.id
    const from = find(edge.from),
      to = find(edge.to),
      accepts = from !== to
    emit(
      '判断边是否成环',
      `${edge.id} 的两端${accepts ? '属于不同' : '已经属于同一'}分量。`,
      prediction(
        `kruskal-consider-${edge.id}`,
        '这条边应加入吗？',
        accepts ? '加入，不会成环' : '跳过，会成环',
        accepts ? '跳过，因为与前一条边共享顶点' : '加入，只要权值足够小',
        '是否同属一个连通分量决定会不会成环。',
      ),
    )
    state.frontier.shift()
    if (!accepts) {
      emit('跳过成环边', `${edge.id} 不加入。`)
      continue
    }
    parent.set(to, from)
    state.components--
    state.selectedEdges.push(edge.id)
    state.representatives = Object.fromEntries(g.nodes.map((node) => [node, find(node)]))
    state.totalWeight = sum(state.totalWeight, edge.weight)
    emit('合并两个分量', `${edge.id} 加入森林，总权 ${state.totalWeight}。`)
  }
  delete state.activeEdge
  emit('最小生成森林完成', state.components === 1 ? '图连通，已得到最小生成树。' : '图不连通，保留最小生成森林。')
  return finish({
    kind: 'spanning-forest',
    selectedEdges: state.selectedEdges,
    totalWeight: state.totalWeight,
    components: state.components,
    connected: state.components === 1,
  })
}
