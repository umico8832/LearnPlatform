import { afterEach, describe, expect, it, vi } from 'vitest'
import { Player } from '@/viz/engine/player'
import type { Frame, Step, Trace } from '@/viz/engine/types'

const frame: Frame = { nodes: [], edges: [], tags: [] }

function step(title: string, predict = false): Step {
  return {
    frame,
    line: null,
    title,
    note: title,
    vars: [],
    predict: predict
      ? {
          id: `q-${title}`,
          prompt: '下一步会怎样？',
          takeaway: '要点',
          options: [
            { id: 'right', text: '对', correct: true, feedback: '对' },
            { id: 'wrong', text: '错', feedback: '误区' },
          ],
        }
      : undefined,
  }
}

function trace(): Trace {
  return {
    steps: [step('0'), step('1'), step('2', true), step('3')],
    code: { file: 'a.c', lines: [], vars: {} },
    check: { ok: true, detail: '' },
  }
}

describe('播放器', () => {
  afterEach(() => vi.useRealTimers())

  it('下一步遇到未回答的检查点先提问，不揭晓该步', () => {
    const player = new Player()
    player.load(trace())
    player.next()
    player.next()
    expect(player.index).toBe(1)
    expect(player.asking).toBe(2)
    player.next()
    expect(player.index).toBe(1)
  })

  it('答错保留记录，答对后继续揭晓被守住的那一步', () => {
    const player = new Player()
    player.load(trace())
    player.revisit(2)
    expect(player.answer('wrong')).toEqual({ correct: false })
    expect(player.answers.get(2)?.solved).toBe(false)
    expect(player.answer('right')).toEqual({ correct: true })
    expect(player.answers.get(2)).toMatchObject({ solved: true, firstTry: false, tried: ['wrong', 'right'] })
    player.resolve(false)
    expect(player.asking).toBeNull()
    expect(player.index).toBe(2)
    player.next()
    expect(player.index).toBe(3)
  })

  it('直接看答案记为已揭晓，不算一次答对', () => {
    const player = new Player()
    player.load(trace())
    player.revisit(2)
    player.reveal()
    expect(player.answers.get(2)).toMatchObject({ solved: true, revealed: true, firstTry: false })
  })

  it('拖动时间轴可以跳过检查点，跳过的检查点保持未答', () => {
    const player = new Player()
    player.load(trace())
    player.seek(3)
    expect(player.index).toBe(3)
    expect(player.asking).toBeNull()
    expect(player.answers.has(2)).toBe(false)
    player.seek(99)
    expect(player.index).toBe(3)
  })

  it('自动播放按停留时间前进，在检查点停下等待回答', () => {
    vi.useFakeTimers()
    const player = new Player()
    const states: number[] = []
    player.subscribe((state) => states.push(state.index))
    player.load(trace())
    player.play()
    vi.advanceTimersByTime(10_000)
    expect(player.index).toBe(1)
    expect(player.asking).toBe(2)
    expect(player.playing).toBe(true)
    player.answer('right')
    player.resolve(player.playing)
    vi.advanceTimersByTime(10_000)
    expect(player.index).toBe(3)
    expect(player.playing).toBe(false)
    player.destroy()
  })
})
