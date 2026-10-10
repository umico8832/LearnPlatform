import { describe, expect, it } from 'vitest'
import type { Trace } from '@/viz/engine/types'
import { binaryTree } from '@/viz/scenes/binary-tree'
import { linkedList } from '@/viz/scenes/linked-list'
import { stack } from '@/viz/scenes/stack'
import { applyPauseAt, specFromQuery, specToQuery, visibleSpec } from '@/viz/spec'

describe('场景描述与链接', () => {
  it('链接只包含当前操作用到的参数，并能还原同一描述', () => {
    const spec = { scene: 'linked-list', op: 'insert', values: [3, 1, 4], i: 2, e: 9 }
    const query = specToQuery(linkedList, spec)
    expect(query).toEqual({ op: 'insert', values: '3,1,4', i: '2', e: '9' })
    expect(specFromQuery(linkedList, query)).toMatchObject(spec)

    expect(specToQuery(linkedList, { ...spec, op: 'reverse' })).toEqual({ op: 'reverse', values: '3,1,4' })
  })

  it('缺失或非法的链接参数回退到场景默认值，数值被夹到合法范围', () => {
    const fallback = linkedList.defaultSpec()
    expect(specFromQuery(linkedList, { op: 'teleport', values: 'x y' })).toMatchObject({
      op: fallback.op,
      values: fallback.values,
    })
    expect(specFromQuery(linkedList, { op: 'delete', values: '5,6', i: '9' })).toMatchObject({ values: [5, 6], i: 2 })
    expect(specFromQuery(stack, { seq: '1,1,2' })).toMatchObject({ seq: stack.defaultSpec().seq })
  })

  it('文本参数按场景自己的解析规则读写', () => {
    const spec = specFromQuery(binaryTree, { op: 'preorder', tree: 'A B C # D' })
    expect(spec.tree).toEqual(['A', 'B', 'C', null, 'D'])
    expect(specToQuery(binaryTree, spec).tree).toBe('A B C # D')
  })

  it('AI 描述只展示当前操作用到的参数', () => {
    expect(visibleSpec(linkedList, { scene: 'linked-list', op: 'reverse', values: [1], i: 1, e: 2 })).toEqual({
      scene: 'linked-list',
      op: 'reverse',
      values: [1],
    })
  })

  it('pauseAt 只保留指定的检查点，未指定时全部保留', () => {
    const trace = linkedList.build(linkedList.defaultSpec('reverse'))
    const ids = trace.steps.flatMap((step) => (step.predict ? [step.predict.id] : []))
    expect(ids.length).toBeGreaterThan(1)
    const count = (t: Trace) => t.steps.filter((step) => step.predict).length
    expect(count(applyPauseAt(trace, undefined))).toBe(ids.length)
    expect(count(applyPauseAt(trace, [ids[0]]))).toBe(1)
    expect(count(applyPauseAt(trace, []))).toBe(0)
  })
})
