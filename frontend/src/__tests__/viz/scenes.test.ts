import { describe, expect, it } from 'vitest'
import type { Frame, SceneModule, SceneSpec, Trace } from '@/viz/engine/types'
import { binaryTree } from '@/viz/scenes/binary-tree'
import { linkedList } from '@/viz/scenes/linked-list'
import { stack } from '@/viz/scenes/stack'
import { scenes } from '@/viz/scenes'
import { normalizeSpec } from '@/viz/spec'

/** Small deterministic PRNG so failures are reproducible. */
function random(seed: number) {
  let state = seed >>> 0
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0
    return state / 2 ** 32
  }
}

function int(rand: () => number, min: number, max: number) {
  return min + Math.floor(rand() * (max - min + 1))
}

function permutation(rand: () => number, n: number) {
  const values = Array.from({ length: n }, (_, i) => i + 1)
  for (let i = n - 1; i > 0; i--) {
    const j = int(rand, 0, i)
    ;[values[i], values[j]] = [values[j], values[i]]
  }
  return values
}

/** Random tree as level-order tokens, kept within the scene's limits (≤ 12 nodes, ≤ 6 levels). */
function levelOrder(rand: () => number): (string | null)[] {
  const labels = 'ABCDEFGHIJKL'.split('')
  const count = int(rand, 1, 12)
  const children = new Map<string, [string | null, string | null]>([['A', [null, null]]])
  const depth = new Map([['A', 0]])
  for (let k = 1; k < count; k++) {
    const open = [...children.entries()].filter(([id, kids]) => depth.get(id)! < 5 && kids.includes(null))
    if (!open.length) break
    const [parent, kids] = open[int(rand, 0, open.length - 1)]
    const side = kids[0] === null && (kids[1] !== null || rand() < 0.5) ? 0 : 1
    kids[side] = labels[k]
    children.set(labels[k], [null, null])
    depth.set(labels[k], depth.get(parent)! + 1)
  }
  const tokens: (string | null)[] = ['A']
  const queue = ['A']
  while (queue.length) {
    for (const kid of children.get(queue.shift()!)!) {
      tokens.push(kid)
      if (kid) queue.push(kid)
    }
  }
  while (tokens[tokens.length - 1] === null) tokens.pop()
  return tokens
}

function randomSpec(module: SceneModule, rand: () => number): SceneSpec {
  const op = module.ops[int(rand, 0, module.ops.length - 1)].id
  if (module.id === 'stack') return { scene: 'stack', op, seq: permutation(rand, int(rand, 3, 7)) }
  if (module.id === 'binary-tree') return { scene: 'binary-tree', op, tree: levelOrder(rand) }
  const values = Array.from({ length: int(rand, 1, 7) }, () => int(rand, 0, 99))
  return normalizeSpec(module, { scene: 'linked-list', op, values, i: int(rand, 1, 8), e: int(rand, 0, 99) })
}

function ids(frame: Frame) {
  return new Set(frame.nodes.map((node) => node.id))
}

/** The scene contract from the viz README, checked on every step of a trace. */
function expectContract(trace: Trace, label: string) {
  expect(trace.check.ok, `${label} 自检：${trace.check.detail}`).toBe(true)
  expect(trace.steps.length, label).toBeGreaterThan(1)
  for (const [k, step] of trace.steps.entries()) {
    const where = `${label} 第 ${k + 1} 步`
    const nodes = ids(step.frame)
    expect(nodes.size, `${where} 结点 id 重复`).toBe(step.frame.nodes.length)
    expect(step.title.trim(), where).not.toBe('')
    expect(step.note.trim(), where).not.toBe('')
    if (step.line !== null) {
      expect(step.line, where).toBeGreaterThanOrEqual(1)
      expect(step.line, where).toBeLessThanOrEqual(trace.code.lines.length)
    }
    for (const edge of step.frame.edges) {
      expect(nodes.has(edge.from) && nodes.has(edge.to), `${where} 边 ${edge.id} 指向不存在的结点`).toBe(true)
    }
    for (const tag of step.frame.tags) {
      if (tag.target !== null) expect(nodes.has(tag.target), `${where} 变量 ${tag.label} 指向不存在的结点`).toBe(true)
    }
    if (step.predict) {
      const { options } = step.predict
      expect(
        options.filter((option) => option.correct),
        `${where} 检查点必须恰有一个正确选项`,
      ).toHaveLength(1)
      expect(new Set(options.map((option) => option.id)).size, where).toBe(options.length)
      for (const option of options) expect(option.feedback.trim(), `${where} 选项 ${option.id} 缺少反馈`).not.toBe('')
      expect(step.predict.takeaway.trim(), where).not.toBe('')
    }
  }
}

describe('可视化场景契约', () => {
  it('目录中的每个场景都能按需加载', async () => {
    const loaded = await Promise.all(scenes.map((entry) => entry.load()))
    expect(loaded.map((module) => module.id)).toEqual(scenes.map((entry) => entry.id))
  })

  it.each([linkedList, stack, binaryTree])('$id：默认数据的每种操作都满足契约', (module) => {
    for (const op of module.ops) expectContract(module.build(module.defaultSpec(op.id)), `${module.id}/${op.id}`)
  })

  it.each([linkedList, stack, binaryTree])('$id：随机数据下模拟器自检与契约都成立', (module) => {
    const rand = random(20261010)
    for (let run = 0; run < 60; run++) {
      const spec = randomSpec(module, rand)
      expectContract(module.build(spec), `${module.id} ${JSON.stringify(spec)}`)
    }
  })
})
