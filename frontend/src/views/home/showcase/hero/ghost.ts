/**
 * The demo cursor's contract with the card scenes. A scene scripts its own demo
 * with these moves; every move rejects with `Halt` once the visitor takes over, so a
 * script simply stops wherever it was.
 */
export class Halt extends Error {
  constructor() {
    super('Demo halted')
  }
}

export interface Ghost {
  /** Glide to the element's centre and press it; resolves at the press. */
  click(target: Element | null | undefined): Promise<void>
  /** Glide to a point in the cursor's own layer and press there, for things drawn on a canvas. */
  clickAt(x: number, y: number): Promise<void>
  /** Drift by an offset without pressing, e.g. to step out of the way of what it started. */
  moveBy(dx: number, dy: number): Promise<void>
  /** Press the element and pull it sideways by `dx`, reporting the offset every frame. */
  drag(target: Element | null | undefined, dx: number, move: (offset: number) => void): Promise<void>
  /** Wait, unless the visitor takes over first. */
  rest(ms: number): Promise<void>
}

export interface DemoScene {
  demo(ghost: Ghost): Promise<void>
}

export function rest(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) return reject(new Halt())
    const abort = () => {
      window.clearTimeout(timer)
      reject(new Halt())
    }
    const timer = window.setTimeout(() => {
      signal.removeEventListener('abort', abort)
      resolve()
    }, ms)
    signal.addEventListener('abort', abort, { once: true })
  })
}

export function nextFrame(signal: AbortSignal): Promise<number> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) return reject(new Halt())
    requestAnimationFrame((now) => (signal.aborted ? reject(new Halt()) : resolve(now)))
  })
}
