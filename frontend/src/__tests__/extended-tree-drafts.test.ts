import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { binaryTree } from '@/viz/scenes/binary-tree'

const draft = JSON.parse(readFileSync(resolve(process.cwd(), '../content/drafts/p27-tree-extended.json'), 'utf8'))
const get = (id: string) => draft.questions.find((q: { id: string }) => q.id === id)
const plain = (s: string) => s.replace(/`/g, '')
const answer = (q: ReturnType<typeof get>) =>
  plain(q.options.find((o: { label: string }) => o.label === q.correctAnswer).content)

function huffman(weights: number[]) {
  let queue = weights.map((weight, i) => ({ weight, leaves: [i] }))
  const depths = weights.map(() => 0)
  let cost = 0
  while (queue.length > 1) {
    queue.sort((a, b) => a.weight - b.weight)
    const [a, b, ...rest] = queue
    const leaves = [...a.leaves, ...b.leaves]
    leaves.forEach((i) => depths[i]++)
    cost += a.weight + b.weight
    queue = [...rest, { weight: a.weight + b.weight, leaves }]
  }
  return { cost, average: cost / weights.reduce((a, b) => a + b, 0), depths }
}
const prefix = (codes: string[]) =>
  codes.every((code, i) => codes.every((other, j) => i === j || !other.startsWith(code)))
function searchPath(path: number[], target: number) {
  let lower = -Infinity,
    upper = Infinity
  return path.every((key, i) => {
    if (key <= lower || key >= upper) return false
    if (key === target) return i === path.length - 1
    if (target < key) upper = key
    else lower = key
    return i !== path.length - 1
  })
}

describe('remaining tree-topic draft facts', () => {
  it('keeps ten complete questions and variants pending', () => {
    expect(draft.questions).toHaveLength(10)
    for (const q of draft.questions) {
      expect(q.contentSource.reviewStatus).toBe('PENDING')
      expect(q.variant.source.reviewStatus).toBe('PENDING')
    }
  })
  it('computes weighted path lengths and code-length thresholds by repeated merges', () => {
    expect(huffman([3, 4, 5, 6, 8, 10])).toMatchObject({ cost: 90, average: 2.5 })
    expect(answer(get('te-05'))).toBe('2.5')
    expect(huffman([1, 2, 4, 8]).cost).toBe(25)
    expect(huffman([1, 2, 4, 8]).average).toBeCloseTo(5 / 3)
    expect(answer(get('te-05').variant)).toBe('5/3')
    const depths = huffman([2, 3, 4, 6, 8, 10, 11]).depths
    expect(depths.filter((depth) => depth >= 3)).toHaveLength(5)
    expect(depths.filter((depth) => depth >= 4)).toHaveLength(2)
    expect(answer(get('te-07'))).toBe('5 个')
    expect(answer(get('te-07').variant)).toBe('2 个')
  })
  it('checks prefix inclusion for every coding option', () => {
    const q = get('te-06')
    const options = (part: typeof q, expected: boolean) =>
      part.options
        .filter((o: { content: string }) => prefix(plain(o.content).split('、')) === expected)
        .map((o: { label: string }) => o.label)
    expect(options(q, true)).toEqual([q.correctAnswer])
    expect(options(q.variant, false)).toEqual([q.variant.correctAnswer])
  })
  it('uses the formal simulator for the converted forest and search-tree traversals', () => {
    const converted = binaryTree.build(get('te-03').viz.spec)
    expect(converted.check).toEqual({ ok: true, detail: '先序序列 A D E B F' })
    for (const part of [get('te-08'), get('te-08').variant]) {
      const trace = binaryTree.build(part.viz.spec)
      expect(trace.check.ok).toBe(true)
      expect(trace.check.detail.split(' ').slice(1).join(',')).toBe(answer(part))
    }
  })
  it('validates every search path against all ancestor bounds', () => {
    const q = get('te-09')
    for (const [part, target] of [
      [q, 60],
      [q.variant, 35],
    ] as const) {
      const invalid = part.options.filter(
        (o: { content: string }) => !searchPath(plain(o.content).split(',').map(Number), target),
      )
      expect(invalid.map((o: { label: string }) => o.label)).toEqual([part.correctAnswer])
    }
  })
})
