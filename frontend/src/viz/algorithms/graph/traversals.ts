import { adjacent, compareId, graph, initial, parentMap, prediction, record, source } from './core'
import type { GraphInput } from './types'

export function bfs(input: GraphInput) {
  const g = graph(input),
    start = source(g),
    state = initial(),
    parents = parentMap(g.nodes)
  const { emit, finish } = record('bfs', g, state),
    seen = new Set([start])
  state.parents = parents
  state.frontier.push(start)
  state.discovered.push(start)
  emit('起点入队', '入队时即标记，避免多个前驱把同一顶点重复入队。')
  while (state.frontier.length) {
    const node = state.frontier.shift()!
    delete state.activeEdge
    state.activeNode = node
    state.order.push(node)
    emit('队首出队', `处理顶点 ${node} 的邻接边。`)
    for (const next of adjacent(g, node)) {
      state.activeEdge = next.edge.id
      if (seen.has(next.node)) {
        emit('跳过已发现顶点', `${next.node} 已标记，不重复入队。`)
        continue
      }
      const predict = prediction(
        `bfs-discover-${next.node}`,
        `沿当前边发现 ${next.node} 后怎样处理？`,
        '立即标记并入队',
        '等到出队才首次标记',
        '发现时标记，保证每个顶点至多入队一次。',
      )
      seen.add(next.node)
      parents[next.node] = node
      state.frontier.push(next.node)
      state.discovered.push(next.node)
      emit('标记并入队', `${next.node} 的前驱为 ${node}。`, predict)
    }
  }
  delete state.activeEdge
  delete state.activeNode
  emit('搜索结束', '只覆盖起点可达的分量。')
  return finish({ kind: 'traversal', order: state.order, parents })
}

export function dfs(input: GraphInput) {
  const g = graph(input),
    start = source(g),
    state = initial(),
    parents = parentMap(g.nodes)
  const { emit, finish } = record('dfs', g, state),
    seen = new Set<string>()
  state.parents = parents
  state.stack = []
  const visit = (node: string) => {
    seen.add(node)
    state.discovered.push(node)
    state.order.push(node)
    state.stack!.push(node)
    state.frontier = [...state.stack!]
    state.activeNode = node
    emit('进入并标记', `首次进入 ${node}，按顶点标识升序扫描邻点。`)
    for (const next of adjacent(g, node)) {
      state.activeNode = node
      state.activeEdge = next.edge.id
      if (seen.has(next.node)) {
        emit('跳过已访问顶点', '回溯不清除本次搜索的访问标记。')
        continue
      }
      emit(
        '选择递归分支',
        `下一次递归进入 ${next.node}。`,
        prediction(
          `dfs-enter-${next.node}`,
          '接下来先做什么？',
          `进入 ${next.node} 的分支`,
          '先访问所有同层顶点',
          'DFS先完成当前分支，再返回扫描后续邻点。',
        ),
      )
      parents[next.node] = node
      visit(next.node)
    }
    state.stack!.pop()
    state.frontier = [...state.stack!]
    state.activeNode = node
    delete state.activeEdge
    emit('返回调用者', `${node} 的邻接分支处理完成，访问标记仍保留。`)
  }
  visit(start)
  delete state.activeNode
  emit('搜索结束', '未从其他分量另启搜索。')
  return finish({ kind: 'traversal', order: state.order, parents })
}

export function topological(input: GraphInput) {
  const g = graph(input, 'directed'),
    state = initial()
  const { emit, finish } = record('topological', g, state)
  state.indegree = Object.fromEntries(g.nodes.map((node) => [node, 0]))
  for (const edge of g.edges) state.indegree[edge.to]++
  state.frontier = g.nodes.filter((node) => state.indegree![node] === 0)
  emit('收集零入度顶点', '每次选择当前零入度集合中字典序最小的顶点。')
  while (state.frontier.length) {
    const node = state.frontier.shift()!
    delete state.activeEdge
    delete state.activeNode
    emit(
      '选择零入度顶点',
      `下一项为 ${node}。`,
      prediction(
        `topological-output-${node}`,
        '可以输出哪个顶点？',
        node,
        '任意未输出顶点',
        '只能从当前零入度集合选择。',
      ),
    )
    state.activeNode = node
    state.order.push(node)
    state.discovered.push(node)
    emit('输出顶点', `输出 ${node} 后再删除其出边。`)
    for (const next of adjacent(g, node)) {
      state.activeEdge = next.edge.id
      state.indegree[next.node]--
      if (state.indegree[next.node] === 0) state.frontier.push(next.node)
      state.frontier.sort(compareId)
      emit('删除出边并减入度', `${next.node} 的入度减一，降到零才进入候选集合。`)
    }
  }
  const remaining = g.nodes.filter((node) => !state.order.includes(node)),
    acyclic = remaining.length === 0
  delete state.activeEdge
  delete state.activeNode
  emit(
    acyclic ? '拓扑排序完成' : '存在有向环',
    acyclic ? '全部顶点已经输出。' : '零入度集合耗尽，已输出前缀不能当作全图拓扑序。',
  )
  return finish({ kind: 'topological', order: state.order, acyclic, remaining })
}
