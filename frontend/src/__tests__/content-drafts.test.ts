import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
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
  viz: DraftViz | null
  misconceptions: Record<string, { viz?: DraftViz }>
  variant: { viz: DraftViz | null }
}

const files = ['m5-linear-list.json', 'm5-stack.json', 'm5-queue.json']
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
})
