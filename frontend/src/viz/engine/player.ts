import type { Predict, Trace } from './types'

/**
 * Generic step player. Owns the playhead, autoplay timing and prediction
 * checkpoints; it never inspects frames. A checkpoint attached to step k is
 * asked before step k is revealed. Scrubbing may skip a checkpoint; it stays
 * open on the timeline so the learner can come back to it.
 */

export interface Answer {
  /** Options tried, in order. */
  tried: string[]
  solved: boolean
  /** True when the first attempt was right. */
  firstTry: boolean
  revealed: boolean
}

export interface PlayerState {
  index: number
  playing: boolean
  speed: number
  /** Step whose prediction is currently asked, if any. */
  asking: number | null
}

type Listener = (state: PlayerState) => void

const BASE_DWELL = 1500

export class Player {
  trace: Trace | null = null
  index = 0
  playing = false
  speed = 1
  asking: number | null = null
  readonly answers = new Map<number, Answer>()
  private timer = 0
  private listeners = new Set<Listener>()

  load(trace: Trace, keepIndex = false): void {
    this.stopTimer()
    this.trace = trace
    this.answers.clear()
    this.asking = null
    this.playing = false
    this.index = keepIndex ? Math.min(this.index, trace.steps.length - 1) : 0
    this.emit()
  }

  get length(): number {
    return this.trace?.steps.length ?? 0
  }

  get atEnd(): boolean {
    return this.index >= this.length - 1
  }

  predictAt(index: number): Predict | undefined {
    return this.trace?.steps[index]?.predict
  }

  subscribe(fn: Listener): () => void {
    this.listeners.add(fn)
    fn(this.snapshot())
    return () => this.listeners.delete(fn)
  }

  play(): void {
    if (!this.trace || this.asking !== null) return
    if (this.atEnd) this.index = 0
    this.playing = true
    this.emit()
    this.schedule()
  }

  pause(): void {
    this.playing = false
    this.stopTimer()
    this.emit()
  }

  toggle(): void {
    if (this.playing) this.pause()
    else this.play()
  }

  setSpeed(speed: number): void {
    this.speed = speed
    this.emit()
    if (this.playing) this.schedule()
  }

  /** Advance one step, stopping at an unanswered checkpoint. */
  next(): void {
    if (!this.trace || this.atEnd) {
      this.pause()
      return
    }
    if (this.asking !== null) return
    const upcoming = this.index + 1
    if (this.predictAt(upcoming) && !this.answers.get(upcoming)?.solved) {
      this.ask(upcoming)
      return
    }
    this.index = upcoming
    this.emit()
    if (this.playing) this.schedule()
  }

  prev(): void {
    this.closeAsk()
    this.index = Math.max(0, this.index - 1)
    this.emit()
    if (this.playing) this.schedule()
  }

  restart(): void {
    this.closeAsk()
    this.index = 0
    this.pause()
  }

  /** Free scrubbing; never blocked by checkpoints. */
  seek(index: number): void {
    if (!this.trace) return
    this.closeAsk()
    this.index = Math.max(0, Math.min(index, this.length - 1))
    this.emit()
    if (this.playing) this.schedule()
  }

  /** Jump to just before a checkpoint and ask it. */
  revisit(index: number): void {
    this.pause()
    this.closeAsk()
    this.index = Math.max(0, index - 1)
    this.ask(index)
  }

  answer(optionId: string): { correct: boolean } {
    const at = this.asking
    const predict = at !== null ? this.predictAt(at) : undefined
    if (at === null || !predict) return { correct: false }
    const option = predict.options.find((o) => o.id === optionId)
    const correct = Boolean(option?.correct)
    const record = this.answers.get(at) ?? { tried: [], solved: false, firstTry: false, revealed: false }
    if (!record.tried.includes(optionId)) record.tried.push(optionId)
    if (correct && !record.solved) {
      record.solved = true
      record.firstTry = record.tried.length === 1
    }
    this.answers.set(at, record)
    this.emit()
    return { correct }
  }

  reveal(): void {
    const at = this.asking
    const predict = at !== null ? this.predictAt(at) : undefined
    if (at === null || !predict) return
    const record = this.answers.get(at) ?? { tried: [], solved: false, firstTry: false, revealed: false }
    record.revealed = true
    record.solved = true
    this.answers.set(at, record)
    this.emit()
  }

  /** Close the question and show the step it guarded. */
  resolve(resume: boolean): void {
    const at = this.asking
    if (at === null) return
    this.asking = null
    this.index = at
    this.playing = resume
    this.emit()
    if (resume) this.schedule()
  }

  private ask(index: number): void {
    this.stopTimer()
    this.asking = index
    this.emit()
  }

  private closeAsk(): void {
    if (this.asking !== null) {
      this.asking = null
    }
  }

  private schedule(): void {
    this.stopTimer()
    if (!this.trace) return
    const step = this.trace.steps[this.index]
    const dwell = (BASE_DWELL + Math.min(step.note.length, 80) * 14) / this.speed
    this.timer = window.setTimeout(() => {
      if (this.atEnd) this.pause()
      else this.next()
    }, dwell)
  }

  private stopTimer(): void {
    if (this.timer) window.clearTimeout(this.timer)
    this.timer = 0
  }

  /** Stop autoplay and drop listeners; the player is unusable afterwards. */
  destroy(): void {
    this.stopTimer()
    this.listeners.clear()
    this.trace = null
  }

  snapshot(): PlayerState {
    return { index: this.index, playing: this.playing, speed: this.speed, asking: this.asking }
  }

  private emit(): void {
    const state = this.snapshot()
    for (const fn of this.listeners) fn(state)
  }
}
