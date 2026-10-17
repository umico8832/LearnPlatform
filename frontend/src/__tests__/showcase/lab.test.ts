import { describe, expect, it } from 'vitest'
import { linkedList } from '@/viz/scenes/linked-list'
import { REVERSE_CODE } from '@/views/home/showcase/lab/code'
import { beats, detailFrame } from '@/views/home/showcase/lab/frames'

const stripComment = (line: string) => line.replace(/\s*\/\/.*$/, '')

describe('homepage lab', () => {
  const trace = linkedList.build({ scene: 'linked-list', op: 'reverse', values: [3, 7, 1, 9, 4], pauseAt: [] })

  it('shows the simulator listing line for line, only the comments translated', () => {
    expect(REVERSE_CODE.lines.map(stripComment)).toEqual(trace.code.lines.map(stripComment))
    expect(REVERSE_CODE.vars).toEqual(trace.code.vars)
  })

  it('maps every bare beat back to the statement it shows, in order', () => {
    const picked = beats(trace.steps)
    expect(picked.length).toBeLessThan(trace.steps.length)
    expect(picked[0].source).toBe(0)
    picked.forEach((beat, i) => {
      if (i > 0) expect(beat.source).toBeGreaterThan(picked[i - 1].source)
      expect(beat.frame.nodes.some((node) => node.head)).toBe(false)
    })
  })

  it('keeps variables but no Chinese captions in the code view', () => {
    for (const step of trace.steps) {
      const frame = detailFrame(step.frame)
      expect(frame.tags).toEqual(step.frame.tags)
      expect(frame.nodes.every((node) => node.sub === undefined)).toBe(true)
    }
  })
})
