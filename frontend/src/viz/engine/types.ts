/**
 * Trace contract shared by every scene.
 *
 * spec (what an AI tutor decides to show)
 *   → simulator (deterministic, computes every step of the algorithm)
 *   → layout (turns structure state into positioned primitives)
 *   → Trace, which the player plays and the stage renders.
 *
 * Frames are declarative and keyed by id: the stage diffs consecutive frames
 * and springs every primitive from where it is to where it should be, so
 * scrubbing backwards or jumping anywhere needs no inverse operations.
 */

export type Tone = 'default' | 'accent' | 'success' | 'warning' | 'danger' | 'muted'

/** Stable identity colours for program variables; also used in code and watch panels. */
export type VarColor = 'blue' | 'indigo' | 'orange' | 'green' | 'pink' | 'teal' | 'purple' | 'graphite'

export type NodeKind = 'record' | 'cell' | 'circle'

export interface NodeView {
  id: string
  kind: NodeKind
  /** Centre position in world units. */
  x: number
  y: number
  text: string
  /** Small caption under the node (index, address, …). */
  sub?: string
  tone?: Tone
  /** record: draw the data field as a head node (头结点). */
  head?: boolean
  /** record: what the pointer field shows when it has no outgoing edge. */
  ptrMark?: 'null' | 'unset'
  /** Emphasise one field while it is being read or written. */
  focus?: 'data' | 'ptr' | 'all'
  /** On first appearance, fly out of this node instead of fading in place. */
  enterFrom?: string
}

export type EdgeRoute = 'auto' | 'over' | 'under' | 'straight'

export interface EdgeView {
  id: string
  from: string
  /** Port on the source node; records use 'ptr', circles 'left' | 'right'. */
  port?: string
  to: string
  tone?: Tone
  route?: EdgeRoute
  /** 'arrow' for pointers, 'line' for tree links. */
  style?: 'arrow' | 'line'
}

export interface TagView {
  id: string
  label: string
  color: VarColor
  /** Node the variable points at; null means NULL and the tag parks at `park`. */
  target: string | null
  side?: 'below' | 'above' | 'left' | 'right'
  park?: { x: number; y: number }
}

export interface LabelView {
  id: string
  x: number
  y: number
  text: string
  align?: 'start' | 'center' | 'end'
  tone?: Tone
}

export interface RegionView {
  id: string
  x: number
  y: number
  w: number
  h: number
  label?: string
  style?: 'well' | 'outline'
}

export interface Frame {
  nodes: NodeView[]
  edges: EdgeView[]
  tags: TagView[]
  labels?: LabelView[]
  regions?: RegionView[]
}

/** Primitives drawn dashed on top of the frame, e.g. a learner's prediction. */
export interface GhostLayer {
  nodes?: NodeView[]
  edges?: EdgeView[]
  tags?: TagView[]
}

export interface VarView {
  name: string
  value: string
  color?: VarColor
}

export interface PredictOption {
  id: string
  text: string
  correct?: boolean
  /** Misconception-aware feedback shown when this option is picked. */
  feedback: string
  /** What the stage would look like if this option were true. */
  ghost?: GhostLayer
}

export interface Predict {
  id: string
  prompt: string
  options: PredictOption[]
  /** Short takeaway shown once the learner has the right answer. */
  takeaway: string
}

export interface Step {
  frame: Frame
  /** 1-based line in the code listing, or null when no line runs. */
  line: number | null
  /** The statement or event, written as code when possible. */
  title: string
  note: string
  vars: VarView[]
  /** Asked before this step is revealed. */
  predict?: Predict
  /** Group label shown in the caption, e.g. "第 2 轮". */
  phase?: string
}

export interface CodeListing {
  file: string
  lines: string[]
  /** Variable names to tint in the listing. */
  vars: Record<string, VarColor>
}

export interface Trace {
  steps: Step[]
  code: CodeListing
  /** Deterministic self-check of the simulator result. */
  check: { ok: boolean; detail: string }
}

export type ParamDef =
  | {
      key: string
      label: string
      type: 'list'
      min: number
      max: number
      ops?: string[]
      /** Extra scene rule, e.g. "must be a permutation"; returns an error message. */
      validate?: (values: number[]) => string | null
    }
  | {
      key: string
      label: string
      type: 'int'
      min: number
      max: number | ((spec: SceneSpec) => number)
      ops?: string[]
    }
  | {
      key: string
      label: string
      type: 'text'
      ops?: string[]
      /** Input width in characters. */
      size?: number
      hint: string
      parse: (text: string) => { value?: unknown; error?: string }
      format: (value: unknown) => string
    }

export interface SceneSpec {
  scene: string
  op: string
  [key: string]: unknown
}

export interface SceneModule {
  id: string
  title: string
  ops: { id: string; label: string }[]
  params: ParamDef[]
  defaultSpec(op?: string): SceneSpec
  build(spec: SceneSpec): Trace
}
