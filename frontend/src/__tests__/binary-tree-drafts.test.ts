import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { binaryTree } from '@/viz/scenes/binary-tree'
import type { SceneSpec } from '@/viz/engine/types'

const draft = JSON.parse(readFileSync(resolve(process.cwd(), '../content/drafts/p27-binary-tree.json'), 'utf8'))
interface Part {
  options: { label: string; content: string }[]
  correctAnswer: string
  viz: { spec: SceneSpec } | null
}
const get = (id: string) => draft.questions.find((q: { id: string }) => q.id === id)
const answer = (part: Part) =>
  part.options.find((option) => option.label === part.correctAnswer)!.content.replace(/`/g, '')
const sequence = (part: Part) => binaryTree.build(part.viz!.spec).check.detail.split(' ').slice(1)

function validArray(text: string) {
  const values = text
    .replace(/`|R=|{|}/g, '')
    .split(',')
    .map(Number)
  return values[0] > 0 && values.every((value, i) => value === -1 || i === 0 || values[Math.floor((i - 1) / 2)] > 0)
}

describe('binary-tree content answers', () => {
  it('keeps all eight questions and variants pending', () => {
    expect(draft.questions).toHaveLength(8)
    for (const q of draft.questions) {
      expect(q.contentSource.reviewStatus).toBe('PENDING')
      expect(q.variant.source.reviewStatus).toBe('PENDING')
    }
  })

  it('checks array parent existence for every exam and variant option', () => {
    const q = get('bt-02')
    expect(
      q.options.filter((o: { content: string }) => !validArray(o.content)).map((o: { label: string }) => o.label),
    ).toEqual([q.correctAnswer])
    expect(
      q.variant.options
        .filter((o: { content: string }) => validArray(o.content))
        .map((o: { label: string }) => o.label),
    ).toEqual([q.variant.correctAnswer])
  })

  it('matches traversal answers and next visits to the formal simulator', () => {
    const pre = get('bt-03')
    for (const part of [pre, pre.variant, get('bt-05'), get('bt-05').variant]) {
      expect(sequence(part).join(',')).toBe(answer(part))
    }
    const post = get('bt-04')
    const visits = sequence(post)
    expect(visits[visits.indexOf('D') + 1]).toBe(answer(post))
    expect(visits[visits.indexOf('E') + 1]).toBe(answer(post.variant))
  })

  it('checks degree and complete-tree bounds independently', () => {
    const q = get('bt-06')
    const possible = (two: number, one: number) =>
      Array.from({ length: 30 }, (_, zero) => zero).filter((zero) => one + 2 * two === zero + one + two - 1)
    expect(answer(q)).toBe(`${possible(7, 4)[0]} 个`)
    const leaves = possible(3, 2)[0]
    expect(answer(q.variant)).toBe(`${leaves + 3 + 2} 个`)
    expect([10, 11].map((total) => [2 * 5, 2 * 5 + 1].filter((child) => child <= total))).toEqual([[10], [10, 11]])
    expect(answer(get('bt-07'))).toBe('只有左孩子')
    expect(answer(get('bt-07').variant)).toBe('2 个')
  })

  it('enumerates ordered trees instead of assuming preorder/postorder uniqueness', () => {
    type Tree = null | { label: string; left: Tree; right: Tree }
    const trees = (labels: string[]): Tree[] => {
      if (!labels.length) return [null]
      const [label, ...rest] = labels
      return Array.from({ length: rest.length + 1 }, (_, leftSize) => leftSize).flatMap((size) =>
        trees(rest.slice(0, size)).flatMap((left) => trees(rest.slice(size)).map((right) => ({ label, left, right }))),
      )
    }
    const post = (tree: Tree): string => (tree ? post(tree.left) + post(tree.right) + tree.label : '')
    expect(trees(['A', 'B', 'C']).filter((tree) => post(tree) === 'CBA')).toHaveLength(4)
    expect(trees(['A', 'B']).filter((tree) => post(tree) === 'BA')).toHaveLength(2)
    expect(answer(get('bt-08'))).toBe('4 种')
    expect(answer(get('bt-08').variant)).toBe('2 种')
  })
})
