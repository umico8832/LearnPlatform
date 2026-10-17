import type { Frame, Step } from '@/viz/engine/types'

/**
 * The homepage shows the run, not the program: no code, no variable names, no head
 * node or null marks, nothing that needs a programmer to read it. The simulator still
 * computes every step; steps that only move a variable leave the picture unchanged,
 * so they are dropped and every remaining step visibly changes something.
 */
export function bareFrame(frame: Frame): Frame {
  const heads = new Set(frame.nodes.filter((node) => node.head).map((node) => node.id))
  return {
    nodes: frame.nodes.filter((node) => !heads.has(node.id)).map((node) => ({ ...node, sub: undefined })),
    edges: frame.edges.filter((edge) => !heads.has(edge.from) && !heads.has(edge.to)),
    tags: [],
  }
}

function signature(frame: Frame): string {
  return JSON.stringify([
    frame.nodes.map((n) => [n.id, n.x, n.y, n.text, n.tone, n.focus]),
    frame.edges.map((e) => [e.id, e.to, e.tone]),
  ])
}

export interface Beat {
  frame: Frame
  /** Loop round the step belongs to, used only to space the timeline. */
  group: string
  note: string
  /** The simulator step this beat shows, so the code view can land on the same moment. */
  source: number
}

export function beats(steps: Step[]): Beat[] {
  const out: Beat[] = []
  let last = ''
  steps.forEach((step, source) => {
    const frame = bareFrame(step.frame)
    const key = signature(frame)
    if (key === last) return
    last = key
    out.push({ frame, group: step.phase ?? '', note: step.note, source })
  })
  return out
}

/**
 * The same run for someone who opened the code: every statement is a step again,
 * the variables ride on their nodes (L among them, on the head node it holds).
 * Only the textbook's Chinese captions stay out.
 */
export function detailFrame(frame: Frame): Frame {
  return {
    nodes: frame.nodes.map((node) => ({ ...node, sub: undefined })),
    edges: frame.edges,
    tags: frame.tags,
  }
}
