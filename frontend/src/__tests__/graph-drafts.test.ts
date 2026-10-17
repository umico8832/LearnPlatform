import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

type Edge = { id: string; from: string; to: string; weight: number }
type Graph = { directed: boolean; nodes: string[]; edges: Edge[]; start?: string; finish?: string; target?: string }
const document = JSON.parse(
  readFileSync(resolve(process.cwd(), '../content/drafts/unimported/p27-graphs.json'), 'utf8'),
)
const get = (id: string) => document.questions.find((q: { id: string }) => q.id === id)
const input = (part: ReturnType<typeof get>): Graph => part.vizCandidates[0].input
const plain = (s: string) => s.replace(/`/g, '')
const answer = (part: ReturnType<typeof get>): string =>
  plain(part.options.find((o: { label: string }) => o.label === part.correctAnswer).content)
function neighbors(g: Graph, node: string) {
  return g.edges.flatMap((e) => (e.from === node ? [e.to] : !g.directed && e.to === node ? [e.from] : [])).sort()
}
function visits(g: Graph, depth: boolean) {
  const seen = new Set<string>(),
    result: string[] = []
  const walk = (v: string) => {
    seen.add(v)
    result.push(v)
    for (const w of neighbors(g, v)) if (!seen.has(w)) walk(w)
  }
  if (depth) walk(g.start!)
  else {
    const queue = [g.start!]
    seen.add(g.start!)
    while (queue.length) {
      const v = queue.shift()!
      result.push(v)
      for (const w of neighbors(g, v))
        if (!seen.has(w)) {
          seen.add(w)
          queue.push(w)
        }
    }
  }
  return result
}
function minTree(g: Graph) {
  let best = Infinity
  for (let mask = 0; mask < 2 ** g.edges.length; mask++) {
    const edges = g.edges.filter((_, i) => mask & (2 ** i))
    if (edges.length !== g.nodes.length - 1) continue
    const candidate = { ...g, edges, start: g.nodes[0] }
    if (visits(candidate, false).length === g.nodes.length)
      best = Math.min(
        best,
        edges.reduce((s, e) => s + e.weight, 0),
      )
  }
  return best
}
function distances(g: Graph, start: string) {
  const d = g.nodes.map((a) => g.nodes.map((b) => (a === b ? 0 : Infinity)))
  for (const e of g.edges) {
    const a = g.nodes.indexOf(e.from),
      b = g.nodes.indexOf(e.to)
    d[a][b] = Math.min(d[a][b], e.weight)
    if (!g.directed) d[b][a] = Math.min(d[b][a], e.weight)
  }
  for (let k = 0; k < d.length; k++)
    for (let i = 0; i < d.length; i++) for (let j = 0; j < d.length; j++) d[i][j] = Math.min(d[i][j], d[i][k] + d[k][j])
  return d[g.nodes.indexOf(start)]
}
const permutations = (values: string[]): string[][] =>
  !values.length
    ? [[]]
    : values.flatMap((v, i) => permutations(values.filter((_, j) => j !== i)).map((rest) => [v, ...rest]))
const validOrder = (g: Graph, order: string[]) => g.edges.every((e) => order.indexOf(e.from) < order.indexOf(e.to))
function projectPaths(g: Graph): number[] {
  const walk = (v: string, seen: Set<string>): number[] => {
    if (v === g.finish) return [0]
    if (seen.has(v)) throw new Error('candidate AOE cycle')
    const next = new Set(seen).add(v)
    return g.edges.filter((e) => e.from === v).flatMap((e) => walk(e.to, next).map((w) => w + e.weight))
  }
  return walk(g.start!, new Set())
}

describe('unimported graph author facts', () => {
  it('keeps ten questions and all design candidates outside live scenes', () => {
    expect(document.storage).toBe('DRAFT_ONLY')
    expect(document.questions).toHaveLength(10)
    for (const q of document.questions)
      for (const part of [q, q.variant]) {
        expect(part.viz).toBeNull()
        const source = part === q ? part.contentSource : part.source
        expect(source.reviewStatus).toBe('PENDING')
        for (const c of part.vizCandidates) {
          expect(c.status).toBe('CANDIDATE')
          const g: Graph = c.input
          expect(new Set(g.nodes).size).toBe(g.nodes.length)
          expect(g.edges.every((e) => g.nodes.includes(e.from) && g.nodes.includes(e.to))).toBe(true)
        }
      }
  })
  it('counts stored adjacency entries from the declared direction', () => {
    const q = get('gr-01')
    expect(answer(q)).toBe(`${input(q).edges.length * 2} 条`)
    expect(answer(q.variant)).toBe(`${input(q.variant).edges.length} 条`)
  })
  it('derives BFS and recursive DFS discovery orders with the given neighbor order', () => {
    const q = get('gr-02')
    expect(answer(q)).toBe(visits(input(q), false).join(','))
    expect(answer(q.variant)).toBe(visits(input(q.variant), true).join(','))
    expect(visits(input(get('gr-03')), true)).toEqual(['A', 'B', 'D', 'C'])
    expect(visits(input(get('gr-03').variant), true)).toEqual(['E'])
  })
  it('checks minimum tree totals by enumerating all candidate trees', () => {
    const q = get('gr-05')
    expect(Number(answer(q))).toBe(minTree(input(q)))
    expect(Number(answer(q.variant))).toBe(minTree(input(q.variant)))
    expect(minTree(input(get('gr-04')))).toBe(6)
    const g = input(get('gr-04'))
    const selected = new Set(['A', 'B'])
    const cross = g.edges.filter((e) => selected.has(e.from) !== selected.has(e.to)).sort((a, b) => a.weight - b.weight)
    expect([cross[0].from, cross[0].to, cross[0].weight]).toEqual(['B', 'C', 2])
  })
  it('checks shortest distances with Floyd-Warshall rather than Dijkstra', () => {
    const q = get('gr-07')
    for (const part of [q, q.variant]) {
      const g = input(part)
      expect(Number(answer(part))).toBe(distances(g, g.start!)[g.nodes.indexOf(g.target!)])
    }
    expect(distances(input(get('gr-06')), 'A')).toEqual([0, 1, 1, 1])
  })
  it('enumerates topological orders and detects the added cycle', () => {
    const q = get('gr-08'),
      g = input(q)
    const valid = q.options.filter((o: { content: string }) => validOrder(g, plain(o.content).split(',')))
    expect(valid.map((o: { label: string }) => o.label)).toEqual([q.correctAnswer])
    expect(permutations(g.nodes).filter((order) => validOrder(input(q.variant), order))).toHaveLength(0)
  })
  it('checks AOE completion as the longest complete source-to-sink path', () => {
    for (const id of ['gr-09', 'gr-10']) {
      const q = get(id)
      for (const part of [q, q.variant]) expect(Number(answer(part))).toBe(Math.max(...projectPaths(input(part))))
    }
    const c = get('gr-10').vizCandidates[0]
    expect(projectPaths({ ...c.baselineInput, start: 'S', finish: 'T' })).toEqual([6, 6])
    expect(projectPaths(c.input).sort()).toEqual([5, 6])
  })
})
