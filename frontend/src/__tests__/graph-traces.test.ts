import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { bfs, dfs, dijkstra, prim, kruskal, topological, buildGraphTrace } from '@/viz/algorithms/graph'
import type { Algorithm, GraphInput, GraphTrace } from '@/viz/algorithms/graph'

const make = (directed: boolean, nodes: string[], edges: [string, string, number][], start = nodes[0]): GraphInput => ({
  directed,
  nodes,
  start,
  edges: edges.map(([from, to, weight], i) => ({ id: `e${String(i).padStart(2, '0')}`, from, to, weight })),
})
function floyd(g: GraphInput, unit = false) {
  const d = g.nodes.map((a) => g.nodes.map((b) => (a === b ? 0 : Infinity)))
  for (const e of g.edges) {
    const a = g.nodes.indexOf(e.from),
      b = g.nodes.indexOf(e.to),
      w = unit ? 1 : e.weight
    d[a][b] = Math.min(d[a][b], w)
    if (!g.directed) d[b][a] = Math.min(d[b][a], w)
  }
  for (let k = 0; k < d.length; k++)
    for (let i = 0; i < d.length; i++) for (let j = 0; j < d.length; j++) d[i][j] = Math.min(d[i][j], d[i][k] + d[k][j])
  return d
}
function bruteForest(g: GraphInput) {
  const reach = floyd({ ...g, edges: g.edges.map((e) => ({ ...e, weight: 1 })) })
  const components = g.nodes.filter((_, i) => !reach[i].slice(0, i).some(Number.isFinite)).length
  let best = Infinity
  for (let mask = 0; mask < 2 ** g.edges.length; mask++) {
    const edges = g.edges.filter((_, i) => mask & (2 ** i))
    if (edges.length !== g.nodes.length - components) continue
    const candidate = floyd({ ...g, edges: edges.map((e) => ({ ...e, weight: 1 })) })
    if (reach.every((row, i) => row.every((v, j) => Number.isFinite(v) === Number.isFinite(candidate[i][j]))))
      best = Math.min(
        best,
        edges.reduce((total, edge) => total + edge.weight, 0),
      )
  }
  return { weight: best, components }
}
function iterativeDfs(g: GraphInput) {
  const stack = [g.start!],
    seen = new Set<string>(),
    out: string[] = []
  while (stack.length) {
    const node = stack.pop()!
    if (seen.has(node)) continue
    seen.add(node)
    out.push(node)
    const next = g.edges.flatMap((edge) =>
      edge.from === node ? [edge.to] : !g.directed && edge.to === node ? [edge.from] : [],
    )
    stack.push(...next.sort().reverse())
  }
  return out
}
function permutations(xs: string[]): string[][] {
  return xs.length ? xs.flatMap((x, i) => permutations(xs.filter((_, j) => i !== j)).map((tail) => [x, ...tail])) : [[]]
}
function frozen(value: unknown): boolean {
  return !value || typeof value !== 'object' || (Object.isFrozen(value) && Object.values(value).every(frozen))
}
function snapshots(trace: GraphTrace) {
  expect(frozen(trace)).toBe(true)
  expect(new Set(trace.steps.map((step) => step.id)).size).toBe(trace.steps.length)
  const predictions = trace.steps.flatMap((step) => (step.predict ? [step.predict] : []))
  expect(predictions.length).toBeGreaterThan(0)
  expect(new Set(predictions.map((p) => p.id)).size).toBe(predictions.length)
  for (const p of predictions) {
    expect(p.options.filter((o) => o.correct)).toHaveLength(1)
    expect(new Set(p.options.map((o) => o.text)).size).toBe(p.options.length)
  }
}
const diamond = make(
  false,
  ['A', 'B', 'C', 'D', 'E'],
  [
    ['A', 'B', 4],
    ['A', 'C', 1],
    ['B', 'D', 1],
    ['C', 'D', 2],
  ],
)

describe('graph algorithm traces', () => {
  it('BFS marks on enqueue and DFS keeps markers across backtracking', () => {
    expect(bfs(diamond).result.order).toEqual(['A', 'B', 'C', 'D'])
    expect(dfs(diamond).result.order).toEqual(['A', 'B', 'D', 'C'])
    expect(bfs(diamond).steps.filter((s) => s.predict?.id === 'bfs-discover-D')).toHaveLength(1)
    expect(dfs(diamond).result.parents.C).toBe('D')
    expect(dfs({ ...diamond, start: 'E' }).result.order).toEqual(['E'])
  })
  it('distinguishes tentative and settled distances, including zero and parallel edges', () => {
    const g = make(
      true,
      ['S', 'A', 'B', 'T', 'X'],
      [
        ['S', 'A', 4],
        ['S', 'B', 1],
        ['B', 'A', 1],
        ['A', 'T', 1],
        ['B', 'T', 5],
        ['B', 'A', 0],
      ],
      'S',
    )
    const trace = dijkstra(g)
    expect(trace.result.distances).toEqual({ S: 0, A: 1, B: 1, T: 2, X: null })
    expect(trace.result.parentEdges.A).toBe('e05')
    expect(
      trace.steps.some(
        (step) => step.title === '松弛成功' && step.state.distances?.A === 4 && !step.state.order.includes('A'),
      ),
    ).toBe(true)
    expect(trace.result.settled).not.toContain('X')
    expect(() => dijkstra({ ...g, edges: [...g.edges, { id: 'negative', from: 'X', to: 'X', weight: -1 }] })).toThrow(
      /负权/,
    )
  })
  it('uses weight then edge ID in Prim and skips cycles in Kruskal', () => {
    const g = make(
      false,
      ['A', 'B', 'C', 'D'],
      [
        ['A', 'B', 1],
        ['B', 'C', 1],
        ['C', 'D', 1],
        ['A', 'C', 1],
        ['A', 'D', 1],
        ['B', 'D', 1],
      ],
    )
    expect(prim(g).result.selectedEdges).toEqual(['e00', 'e01', 'e02'])
    expect(kruskal(g).result.selectedEdges).toEqual(['e00', 'e01', 'e02'])
    expect(kruskal(g).steps.some((s) => s.title === '跳过成环边')).toBe(true)
    expect(prim(g).result.totalWeight).toBe(3)
    expect(dijkstra(g).result.distances.D).toBe(1)
    const disconnected = make(
      false,
      ['A', 'B', 'C'],
      [
        ['A', 'B', -2],
        ['A', 'A', -5],
      ],
    )
    for (const trace of [prim(disconnected), kruskal(disconnected)]) {
      expect(trace.result).toMatchObject({ connected: false, components: 2, totalWeight: -2 })
      expect(trace.result.selectedEdges).toEqual(['e00'])
    }
  })
  it('topological output respects dynamic zero-indegree order and reports cyclic remainder', () => {
    const g = make(
      true,
      ['A', 'B', 'C', 'D', 'Z'],
      [
        ['A', 'B', 1],
        ['B', 'C', 1],
        ['C', 'B', 1],
        ['C', 'D', 1],
      ],
    )
    expect(topological(g).result).toEqual({
      kind: 'topological',
      order: ['A', 'Z'],
      acyclic: false,
      remaining: ['B', 'C', 'D'],
    })
    expect(topological(make(true, ['B', 'A', 'C'], [['B', 'C', 1]])).result.order).toEqual(['A', 'B', 'C'])
    expect(topological(make(true, ['A'], [['A', 'A', 1]])).result.acyclic).toBe(false)
  })
  it('provides immutable independent snapshots and deterministic results for reordered input', () => {
    const dag = make(
      true,
      ['A', 'B', 'C'],
      [
        ['A', 'C', 2],
        ['B', 'C', 1],
      ],
    )
    for (const algorithm of ['bfs', 'dfs', 'dijkstra', 'prim', 'kruskal', 'topological'] as Algorithm[]) {
      const g = algorithm === 'topological' ? dag : diamond
      const trace = buildGraphTrace(algorithm, g)
      snapshots(trace)
      expect(
        buildGraphTrace(algorithm, { ...g, nodes: [...g.nodes].reverse(), edges: [...g.edges].reverse() }),
      ).toEqual(trace)
      expect(trace.steps[0].state.order).toEqual(algorithm === 'dfs' || algorithm === 'prim' ? ['A'] : [])
      expect(trace.steps[0].state).not.toBe(trace.steps[trace.steps.length - 1].state)
      expect(Reflect.set(trace.steps[0].state.order, 0, 'mutated')).toBe(false)
    }
    const g = structuredClone(diamond)
    const trace = bfs(g)
    expect(trace.steps[0].state.parents?.D).toBe(null)
    expect(trace.result.parents.D).toBe('B')
    expect(Object.isFrozen(g)).toBe(false)
    ;(g.nodes as string[])[0] = 'changed'
    expect(trace.input.nodes).not.toContain('changed')
  })
  it('rejects invalid graph contracts, unsupported order and unsafe arithmetic', () => {
    expect(() => bfs({ ...diamond, start: undefined })).toThrow(/起点/)
    expect(() => bfs({ ...diamond, start: 'missing' })).toThrow()
    expect(() => bfs({ ...diamond, nodes: ['A', 'A'] })).toThrow()
    expect(() => bfs({ ...diamond, edges: [...diamond.edges, diamond.edges[0]] })).toThrow()
    expect(() => bfs({ ...diamond, edges: [{ id: 'bad', from: 'A', to: 'missing', weight: 1 }] })).toThrow()
    expect(() => bfs({ ...diamond, edges: [{ id: 'bad', from: 'A', to: 'B', weight: NaN }] })).toThrow()
    expect(() => bfs({ ...diamond, neighborOrder: 'descending' } as unknown as GraphInput)).toThrow()
    expect(() => prim({ ...diamond, directed: true })).toThrow()
    expect(() => kruskal({ ...diamond, directed: true })).toThrow()
    expect(() => topological(diamond)).toThrow()
    const large = make(
      true,
      ['A', 'B', 'C'],
      [
        ['A', 'B', Number.MAX_SAFE_INTEGER],
        ['B', 'C', 1],
      ],
    )
    expect(() => dijkstra(large)).toThrow(/累计/)
    expect(() => prim({ ...large, directed: false })).toThrow(/累计/)
  })
  it('handles isolated vertices, self-loops and special object-property IDs safely', () => {
    const g = make(false, ['__proto__', 'constructor', 'X'], [['__proto__', 'constructor', 0]], '__proto__')
    expect(bfs(g).result.parents.constructor).toBe('__proto__')
    expect(dijkstra(g).result.distances.constructor).toBe(0)
    for (const trace of [bfs(make(false, ['A'], [])), dfs(make(false, ['A'], [])), dijkstra(make(false, ['A'], []))])
      expect(trace.steps.at(-1)?.state.order).toEqual(['A'])
    expect(prim(make(false, ['A'], [])).result).toMatchObject({ connected: true, totalWeight: 0, components: 1 })
  })
  it('cross-checks seeded small graphs against Floyd-Warshall, iterative DFS and exhaustive forests/topological orders', () => {
    let seed = 731
    const next = () => {
      seed = (seed * 1664525 + 1013904223) >>> 0
      return seed
    }
    for (let sample = 0; sample < 48; sample++) {
      const nodes = ['A', 'B', 'C', 'D'],
        edges: [string, string, number][] = []
      for (let i = 0; i < nodes.length; i++)
        for (let j = i + 1; j < nodes.length; j++) if (next() % 3) edges.push([nodes[i], nodes[j], (next() % 7) - 3])
      const g = make(false, nodes, edges),
        unweighted = floyd(g, true)[0]
      const out = bfs(g).result.order
      expect(new Set(out).size).toBe(out.length)
      expect([...out].sort()).toEqual(nodes.filter((_, i) => Number.isFinite(unweighted[i])))
      expect(out.map((n) => unweighted[nodes.indexOf(n)])).toEqual(
        out.map((n) => unweighted[nodes.indexOf(n)]).sort((a, b) => a - b),
      )
      expect(dfs(g).result.order).toEqual(iterativeDfs(g))
      const expected = bruteForest(g)
      for (const result of [prim(g).result, kruskal(g).result]) {
        expect(result.totalWeight).toBe(expected.weight)
        expect(result.components).toBe(expected.components)
        expect(result.selectedEdges.length).toBe(nodes.length - expected.components)
        const selected = g.edges.filter((edge) => result.selectedEdges.includes(edge.id))
        expect(selected.reduce((weight, edge) => weight + edge.weight, 0)).toBe(result.totalWeight)
        const connected = floyd({ ...g, edges: selected }, true)
        expect(connected.map((row) => row.map(Number.isFinite))).toEqual(
          floyd(g, true).map((row) => row.map(Number.isFinite)),
        )
      }
      const directed = make(
        true,
        nodes,
        edges.map(([a, b, w]) => (next() % 2 ? [a, b, Math.abs(w)] : [b, a, Math.abs(w)])),
      )
      const reference = floyd(directed)[0]
      const paths = dijkstra(directed).result
      expect(paths.distances).toEqual(
        Object.fromEntries(nodes.map((n, i) => [n, Number.isFinite(reference[i]) ? reference[i] : null])),
      )
      for (const node of nodes.filter((n) => paths.distances[n] !== null)) {
        let current = node,
          weight = 0,
          hops = 0
        while (current !== directed.start) {
          const edge = directed.edges.find((e) => e.id === paths.parentEdges[current])!
          expect(edge.to).toBe(current)
          weight += edge.weight
          current = paths.parents[current]!
          expect(++hops).toBeLessThan(nodes.length)
        }
        expect(weight).toBe(paths.distances[node])
      }
      const valid = permutations(nodes).filter((order) =>
        directed.edges.every((e) => order.indexOf(e.from) < order.indexOf(e.to)),
      )
      const topo = topological(directed).result
      expect(topo.acyclic).toBe(valid.length > 0)
      if (valid.length) expect(topo.order).toEqual(valid[0])
    }
  })
  it('snapshots preserve queue, call-stack, settled-distance, cut and union invariants', () => {
    for (const step of bfs(diamond).steps) {
      expect(new Set(step.state.frontier).size).toBe(step.state.frontier.length)
      expect(step.state.frontier.every((node) => step.state.discovered.includes(node))).toBe(true)
      if (step.title === '队首出队') expect(step.state.activeEdge).toBeUndefined()
    }
    for (const step of dfs(diamond).steps) {
      expect(step.state.frontier).toEqual(step.state.stack)
      for (let i = 1; i < step.state.stack!.length; i++)
        expect(step.state.parents![step.state.stack![i]]).toBe(step.state.stack![i - 1])
    }
    const paths = dijkstra(diamond)
    for (const step of paths.steps) {
      for (const settled of step.state.order)
        expect(step.state.distances![settled]).toBe(paths.result.distances[settled])
      expect(step.state.frontier.some((node) => step.state.order.includes(node))).toBe(false)
      if (step.title === '选择最小暂定距离') expect(step.state.activeEdge).toBeUndefined()
    }
    for (const step of prim(diamond).steps) {
      expect(step.state.selectedEdges!.length).toBe(step.state.discovered.length - step.state.components!)
      for (const id of step.state.frontier) {
        const edge = diamond.edges.find((edge) => edge.id === id)!
        expect(step.state.discovered.includes(edge.from)).not.toBe(step.state.discovered.includes(edge.to))
      }
    }
    for (const step of kruskal(diamond).steps) {
      expect(new Set(Object.values(step.state.representatives!)).size).toBe(step.state.components)
      for (const id of step.state.selectedEdges!) {
        const edge = diamond.edges.find((edge) => edge.id === id)!
        expect(step.state.representatives![edge.from]).toBe(step.state.representatives![edge.to])
      }
    }
  })
  it('builds all 15 supported B28 candidate traces without registering a scene', () => {
    const document = JSON.parse(
      readFileSync(resolve(process.cwd(), '../content/drafts/unimported/p27-graphs.json'), 'utf8'),
    )
    const supported: Algorithm[] = ['bfs', 'dfs', 'dijkstra', 'prim', 'kruskal', 'topological']
    const candidates = document.questions
      .flatMap(
        (q: {
          vizCandidates: { algorithm: Algorithm; input: GraphInput }[]
          variant: { vizCandidates: { algorithm: Algorithm; input: GraphInput }[] }
        }) => [...q.vizCandidates, ...q.variant.vizCandidates],
      )
      .filter((c: { algorithm: Algorithm }) => supported.includes(c.algorithm))
    expect(candidates).toHaveLength(15)
    for (const candidate of candidates)
      expect(buildGraphTrace(candidate.algorithm, candidate.input).steps.length).toBeGreaterThan(1)
  })
})
