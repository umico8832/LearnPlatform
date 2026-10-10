import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import type { SceneSpec } from '../viz/engine/types'
import { binaryTree } from '../viz/scenes/binary-tree'
import { stack } from '../viz/scenes/stack'
import { applyPauseAt } from '../viz/spec'

const migration = readFileSync(
  resolve(
    process.cwd(),
    '../backend/app/src/main/resources/db/migration/V107__seed_question_understanding_examples.sql',
  ),
  'utf8',
)

const specs = [...migration.matchAll(/CAST\('((?:[^']|'')*)' AS JSON\)/g)].map(
  ([, literal]) => JSON.parse(literal.replace(/''/g, "'")) as SceneSpec,
)

function traceFor(spec: SceneSpec) {
  const trace = spec.scene === 'stack' ? stack.build(spec) : binaryTree.build(spec)
  return applyPauseAt(trace, spec.pauseAt)
}

describe('M2 种子内容可视化', () => {
  it('逐一解析 SQL JSON 字面量，模拟结果正确且每个 spec 显式声明暂停点', () => {
    expect(specs).toHaveLength(9)
    for (const spec of specs) {
      expect(spec).toHaveProperty('pauseAt')
      const trace = traceFor(spec)
      expect(trace.check.ok, JSON.stringify(spec)).toBe(true)
      if (spec.scene === 'stack') {
        const expected = (spec.seq as number[]).join(',') === '3,2,1,5,4' ? '序列合法' : '序列不合法'
        expect(trace.check.detail).toBe(expected)
      } else {
        const expected = (spec.tree as (string | null)[])[3] === 'c' ? 'c e f d b g a' : 'e f d c b g a'
        expect(trace.check.detail).toContain(expected)
      }
    }
  })

  it('tree-first-visit 只停一次，选项含学习者猜测 c 及针对性反馈', () => {
    const treeGuess = specs.filter(
      (spec) =>
        spec.scene === 'binary-tree' &&
        Array.isArray(spec.pauseAt) &&
        spec.pauseAt.some((entry) => typeof entry === 'object'),
    )
    expect(treeGuess).toHaveLength(1)
    const stops = traceFor(treeGuess[0]).steps.filter((step) => step.predict)
    expect(stops).toHaveLength(1)
    expect(stops[0].predict?.id).toBe('tree-first-visit')
    const guess = stops[0].predict?.options.find((option) => option.id === 'c')
    expect(guess?.text).toBe('c')
    expect(guess?.feedback).toContain('c 的左子树里还有 d、e、f')
  })

  it('空数组一路播放；字符串检查点只停在栈被压住的元素', () => {
    for (const spec of specs) {
      const stops = traceFor(spec).steps.filter((step) => step.predict)
      if (Array.isArray(spec.pauseAt) && spec.pauseAt.length === 0) {
        expect(stops, JSON.stringify(spec)).toHaveLength(0)
      } else if (spec.scene === 'stack') {
        expect(stops, JSON.stringify(spec)).toHaveLength(1)
        expect(stops[0].predict?.id).toBe('stack-buried')
        expect(stops[0].title).toContain('≠')
      }
    }
  })
})
