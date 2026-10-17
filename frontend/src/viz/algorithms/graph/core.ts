import type { Predict } from '../../engine/types'
import type { Algorithm, Frozen, GraphEdge, GraphInput, GraphResult, GraphState, GraphStep, GraphTrace } from './types'

export const compareId = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0)
export const compareEdge = (a: GraphEdge, b: GraphEdge) => a.weight - b.weight || compareId(a.id, b.id)
export function freeze<T>(value: T): Frozen<T> {
  if (value && typeof value === 'object') {
    for (const child of Object.values(value)) freeze(child)
    Object.freeze(value)
  }
  return value as Frozen<T>
}
export function graph(input: GraphInput, mode: 'any' | 'undirected' | 'directed' = 'any') {
  if (typeof input.directed !== 'boolean' || !input.nodes.length || input.nodes.length > 64 || input.edges.length > 512)
    throw new RangeError('需要1–64个顶点、至多512条边和明确的方向')
  const nodes = [...input.nodes].sort(compareId)
  if (
    nodes.some((id) => typeof id !== 'string' || !/^[A-Za-z0-9_-]{1,40}$/.test(id)) ||
    new Set(nodes).size !== nodes.length
  )
    throw new RangeError('顶点标识须为唯一的短字母、数字、下划线或连字符')
  if (
    (input.start !== undefined && !nodes.includes(input.start)) ||
    (input.target !== undefined && !nodes.includes(input.target))
  )
    throw new RangeError('起点或目标不在图中')
  if ((mode === 'undirected' && input.directed) || (mode === 'directed' && !input.directed))
    throw new RangeError('图方向与算法不匹配')
  if (
    (input.neighborOrder !== undefined && input.neighborOrder !== 'ascending-node-id') ||
    (input.zeroIndegreeOrder !== undefined && input.zeroIndegreeOrder !== 'ascending-node-id') ||
    (input.tieBreak !== undefined && input.tieBreak !== 'weight-then-edge-id')
  )
    throw new RangeError('不支持该破并列顺序')
  const edges = input.edges.map((edge) => ({ ...edge })).sort((a, b) => compareId(a.id, b.id))
  if (
    new Set(edges.map((e) => e.id)).size !== edges.length ||
    edges.some(
      (e) =>
        typeof e.id !== 'string' ||
        !/^[A-Za-z0-9_-]{1,40}$/.test(e.id) ||
        !nodes.includes(e.from) ||
        !nodes.includes(e.to) ||
        !Number.isSafeInteger(e.weight),
    )
  )
    throw new RangeError('边需要唯一标识、有效端点与安全整数权值')
  return { ...input, nodes, edges }
}
export function source(g: GraphInput) {
  if (g.start === undefined) throw new RangeError('该算法需要起点')
  return g.start
}
export function adjacent(g: GraphInput, node: string) {
  return g.edges
    .flatMap((edge) =>
      edge.from === node
        ? [{ node: edge.to, edge }]
        : !g.directed && edge.to === node
          ? [{ node: edge.from, edge }]
          : [],
    )
    .sort((a, b) => compareId(a.node, b.node) || compareId(a.edge.id, b.edge.id))
}
export function sum(a: number, b: number) {
  const value = a + b
  if (!Number.isSafeInteger(value)) throw new RangeError('累计权值超出安全整数范围')
  return value
}
export function record(algorithm: Algorithm, input: GraphInput, state: GraphState) {
  const steps: GraphStep[] = []
  const emit = (title: string, note: string, predict?: Predict) => {
    steps.push(
      freeze({
        id: `${algorithm}-step-${steps.length}`,
        title,
        note,
        phase: algorithm,
        vars: [
          { name: 'frontier', value: state.frontier.join(', ') },
          { name: 'output', value: state.order.join(', ') },
        ],
        state: structuredClone(state),
        ...(predict ? { predict: structuredClone(predict) } : {}),
      }),
    )
  }
  const finish = <R extends GraphResult>(result: R): GraphTrace<R> =>
    freeze({
      algorithm,
      input: structuredClone(input),
      steps,
      result: structuredClone(result),
    })
  return { emit, finish }
}
export function prediction(id: string, prompt: string, correct: string, wrong: string, takeaway: string): Predict {
  return {
    id,
    prompt,
    options: [
      { id: 'right', text: correct, correct: true, feedback: takeaway },
      { id: 'wrong', text: wrong, feedback: takeaway },
    ],
    takeaway,
  }
}
export function initial(): GraphState {
  return { order: [], frontier: [], discovered: [] }
}
export function parentMap(nodes: readonly string[]): Record<string, string | null> {
  return Object.fromEntries(nodes.map((node) => [node, null]))
}
