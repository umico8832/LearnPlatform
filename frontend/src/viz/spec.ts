import type { ParamDef, SceneModule, SceneSpec, Trace } from './engine/types'

type IntDef = Extract<ParamDef, { type: 'int' }>
type ListDef = Extract<ParamDef, { type: 'list' }>

export type ParseResult<T> = { value: T; error?: undefined } | { value?: undefined; error: string }

export function paramMax(def: IntDef, spec: SceneSpec): number {
  return typeof def.max === 'function' ? def.max(spec) : def.max
}

/** Parameters the current operation actually uses. */
export function activeParams(module: SceneModule, op: string): ParamDef[] {
  return module.params.filter((def) => !def.ops || def.ops.includes(op))
}

/** Clamp every numeric parameter so a spec is always simulatable. */
export function normalizeSpec(module: SceneModule, spec: SceneSpec): SceneSpec {
  const next = { ...spec }
  for (const def of module.params) {
    if (def.type !== 'int') continue
    const value = Number(next[def.key] ?? def.min)
    next[def.key] = Math.max(def.min, Math.min(paramMax(def, next), Number.isFinite(value) ? value : def.min))
  }
  return next
}

/** The spec as the AI would write it: only parameters the operation uses. */
export function visibleSpec(module: SceneModule, spec: SceneSpec): SceneSpec {
  const hidden = new Set(module.params.filter((def) => def.ops && !def.ops.includes(spec.op)).map((def) => def.key))
  return Object.fromEntries(Object.entries(spec).filter(([key]) => !hidden.has(key))) as SceneSpec
}

/**
 * A checkpoint to pause at: its predict id, or the id with what the learner
 * believed there (e.g. the node they took to come first), so the question can
 * offer that belief and answer it.
 */
export type PauseEntry = string | { id: string; guess?: string }

function entryId(entry: unknown): string | undefined {
  if (typeof entry === 'string') return entry
  const id = (entry as { id?: unknown } | null)?.id
  return typeof id === 'string' ? id : undefined
}

/** The learner's belief recorded for one checkpoint in `spec.pauseAt`. */
export function guessAt(spec: SceneSpec, id: string): string | undefined {
  if (!Array.isArray(spec.pauseAt)) return undefined
  for (const entry of spec.pauseAt) {
    const guess = (entry as { guess?: unknown } | null)?.guess
    if (entryId(entry) === id && typeof guess === 'string') return guess
  }
  return undefined
}

/** Keep only the checkpoints the spec asks for (all by default; an empty list plays straight through). */
export function applyPauseAt(trace: Trace, pauseAt: unknown): Trace {
  if (!Array.isArray(pauseAt)) return trace
  const ids = new Set(pauseAt.map(entryId))
  return {
    ...trace,
    steps: trace.steps.map((step) =>
      step.predict && !ids.has(step.predict.id) ? { ...step, predict: undefined } : step,
    ),
  }
}

/** Learner-typed list of 0–99 integers, separated by spaces or commas. */
export function parseList(def: ListDef, text: string): ParseResult<number[]> {
  const numbers = text
    .trim()
    .split(/[\s,，、]+/)
    .filter(Boolean)
    .map(Number)
  if (numbers.some((n) => !Number.isInteger(n) || n < 0 || n > 99)) return { error: '只能是 0–99 的整数' }
  if (numbers.length < def.min || numbers.length > def.max) return { error: `需要 ${def.min}–${def.max} 个数` }
  const custom = def.validate?.(numbers)
  return custom ? { error: custom } : { value: numbers }
}

export function formatParam(def: ParamDef, value: unknown): string {
  if (def.type === 'list') return ((value as number[] | undefined) ?? []).join(' ')
  if (def.type === 'text') return def.format(value)
  return String(value ?? def.min)
}

export function parseParam(def: ParamDef, text: string): ParseResult<unknown> {
  if (def.type === 'list') return parseList(def, text)
  if (def.type === 'text') {
    const result = def.parse(text)
    return result.error ? { error: result.error } : { value: result.value }
  }
  const value = Number(text)
  return Number.isInteger(value) ? { value } : { error: '需要整数' }
}

/** A shareable link: the operation plus the parameters it uses. */
export function specToQuery(module: SceneModule, spec: SceneSpec): Record<string, string> {
  const query: Record<string, string> = { op: spec.op }
  for (const def of activeParams(module, spec.op)) {
    query[def.key] =
      def.type === 'list' ? ((spec[def.key] as number[]) ?? []).join(',') : formatParam(def, spec[def.key])
  }
  return query
}

/** Rebuild a spec from a link; anything missing or invalid falls back to the scene default. */
export function specFromQuery(module: SceneModule, query: Record<string, unknown>): SceneSpec {
  const requested = typeof query.op === 'string' ? query.op : undefined
  const op = module.ops.some((item) => item.id === requested) ? requested : undefined
  const spec = module.defaultSpec(op)
  for (const def of activeParams(module, spec.op)) {
    const raw = query[def.key]
    if (typeof raw !== 'string') continue
    const parsed = parseParam(def, raw)
    if (parsed.error === undefined) spec[def.key] = parsed.value
  }
  return normalizeSpec(module, spec)
}

/** Same scene and operation: a parameter edit, so the playhead may stay. */
export function sameStory(a: SceneSpec | null, b: SceneSpec): boolean {
  return a !== null && a.scene === b.scene && a.op === b.op
}
