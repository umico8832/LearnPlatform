import { readFileSync, readdirSync } from 'node:fs'
import { basename, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import type { SceneModule, SceneSpec } from '@/viz/engine/types'
import { linkedList } from '@/viz/scenes/linked-list'
import { stack } from '@/viz/scenes/stack'
import { binaryTree } from '@/viz/scenes/binary-tree'
import { activeParams, applyPauseAt, paramMax, parseParam } from '@/viz/spec'

const modules: Record<string, SceneModule> = {
  'linked-list': linkedList,
  stack,
  'binary-tree': binaryTree,
}

interface DraftViz {
  title: string
  spec: SceneSpec
}
interface DraftQuestion {
  id: string
  options: { label: string; content: string }[]
  correctAnswer: string
  viz: DraftViz | null
  misconceptions: Record<string, { viz?: DraftViz }>
  variant: { options: { label: string; content: string }[]; correctAnswer: string; viz: DraftViz | null }
}

const files = readdirSync(resolve(process.cwd(), '../content/drafts'), { recursive: true, encoding: 'utf8' })
  .filter((name) => /^(m5-.*|p27-.*)\.json$/.test(basename(name)))
  .sort()
const questions = files.flatMap((name) => {
  const document = JSON.parse(readFileSync(resolve(process.cwd(), '../content/drafts', name), 'utf8')) as {
    questions: DraftQuestion[]
  }
  return document.questions
})

function validateViz(viz: DraftViz, where: string, expectedStackResult?: '序列合法' | '序列不合法') {
  const spec = viz.spec
  const module = modules[spec.scene]
  expect(module, `${where}: 未知场景`).toBeDefined()
  expect(
    module.ops.some((op) => op.id === spec.op),
    `${where}: 未知操作`,
  ).toBe(true)
  const params = activeParams(module, spec.op)
  const allowed = new Set(['scene', 'op', 'pauseAt', ...params.map((param) => param.key)])
  expect(
    Object.keys(spec).every((key) => allowed.has(key)),
    `${where}: spec 含多余参数`,
  ).toBe(true)
  for (const def of params) {
    const value = spec[def.key]
    expect(value, `${where}: 缺少 ${def.key}`).toBeDefined()
    if (def.type === 'int') {
      expect(Number.isInteger(value), `${where}: ${def.key} 必须为整数`).toBe(true)
      expect(value as number, `${where}: ${def.key} 越界`).toBeGreaterThanOrEqual(def.min)
      expect(value as number, `${where}: ${def.key} 越界`).toBeLessThanOrEqual(paramMax(def, spec))
    } else if (def.type === 'list') {
      expect(Array.isArray(value), `${where}: ${def.key} 必须为数组`).toBe(true)
      const numbers = value as number[]
      expect(numbers.length, `${where}: ${def.key} 长度无效`).toBeGreaterThanOrEqual(def.min)
      expect(numbers.length, `${where}: ${def.key} 长度无效`).toBeLessThanOrEqual(def.max)
      expect(
        numbers.every((number) => Number.isInteger(number) && number >= 0 && number <= 99),
        `${where}: ${def.key} 含非法元素`,
      ).toBe(true)
      expect(def.validate?.(numbers) ?? null, `${where}: ${def.key} 不符合场景约束`).toBeNull()
    } else {
      const parsed = parseParam(def, def.format(value))
      expect(parsed.error, `${where}: ${def.key} 无法解析`).toBeUndefined()
      expect(parsed.value, `${where}: ${def.key} 解析后改变`).toEqual(value)
    }
  }

  const trace = module.build(spec)
  expect(trace.check.ok, `${where}: ${trace.check.detail}`).toBe(true)
  if (expectedStackResult && spec.scene === 'stack') {
    expect(trace.check.detail, `${where}: 出栈可达性与讲解身份不符`).toBe(expectedStackResult)
  }
  expect(trace.steps.length, `${where}: 场景无步骤`).toBeGreaterThan(1)
  const checkpoints = new Set(trace.steps.flatMap((step) => (step.predict ? [step.predict.id] : [])))
  if (spec.pauseAt !== undefined) {
    expect(Array.isArray(spec.pauseAt), `${where}: pauseAt 必须为数组`).toBe(true)
    const entries = spec.pauseAt as (string | { id: string; guess?: string })[]
    const ids = entries.map((entry) => (typeof entry === 'string' ? entry : entry.id))
    expect(new Set(ids).size, `${where}: 停顿点重复`).toBe(ids.length)
    for (const id of ids) expect(checkpoints.has(id), `${where}: 停顿点 ${id} 不存在`).toBe(true)
    const selected = applyPauseAt(trace, entries).steps.filter((step) => step.predict)
    expect(selected.length, `${where}: 实际停顿数不符`).toBe(ids.length)
  }
}

describe('M5 待审核题目草稿的正式场景', () => {
  it('所有场景描述均通过参数契约与确定性模拟器', () => {
    let count = 0
    for (const question of questions) {
      if (question.viz) {
        validateViz(question.viz, `${question.id}.viz`, '序列合法')
        count++
      }
      for (const [label, misconception] of Object.entries(question.misconceptions)) {
        if (misconception.viz) {
          validateViz(misconception.viz, `${question.id}.misconception.${label}.viz`, '序列不合法')
          count++
        }
      }
      if (question.variant.viz) {
        validateViz(question.variant.viz, `${question.id}.variant.viz`, '序列合法')
        count++
      }
    }
    expect(count).toBeGreaterThan(0)
  })

  it('合法出栈序列题只有标注答案满足可达性及变式容量限制', () => {
    const question = questions.find((item) => item.id === 'st-01')!
    for (const [part, capacity] of [
      [question, Infinity],
      [question.variant, 2],
    ] as const) {
      const validLabels = part.options
        .filter((option) => {
          const seq = option.content.split(',').map(Number)
          if (stack.build({ scene: 'stack', op: 'check', seq }).check.detail !== '序列合法') return false
          const values: number[] = []
          let next = 1
          for (const target of seq) {
            while (next <= target) {
              values.push(next++)
              if (values.length > capacity) return false
            }
            if (values.pop() !== target) return false
          }
          return true
        })
        .map((option) => option.label)
      expect(validLabels).toEqual([part.correctAnswer])
    }
  })

  it('三元素出栈序列计数题的容量不限与容量 2 答案由逐排列模拟得到', () => {
    const question = questions.find((item) => item.id === 'st-07')!
    const permutations = (values: number[]): number[][] =>
      values.length === 0
        ? [[]]
        : values.flatMap((value, index) =>
            permutations(values.filter((_, i) => i !== index)).map((tail) => [value, ...tail]),
          )
    for (const [part, n, capacity] of [
      [question, 3, Infinity],
      [question.variant, 3, 2],
    ] as const) {
      const reachable = permutations(Array.from({ length: n }, (_, i) => i + 1)).filter((seq) => {
        if (stack.build({ scene: 'stack', op: 'check', seq }).check.detail !== '序列合法') return false
        if (capacity === Infinity) return true
        const values: number[] = []
        let next = 1
        for (const target of seq) {
          while (next <= target) {
            if (values.length >= capacity) return false
            values.push(next++)
          }
          if (values.pop() !== target) return false
        }
        return true
      }).length
      const chosen = part.options.find((option) => option.label === part.correctAnswer)!
      expect(Number.parseInt(chosen.content, 10)).toBe(reachable)
    }
  })
})
