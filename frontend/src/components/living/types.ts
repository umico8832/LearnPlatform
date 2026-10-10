export interface SegmentOption<V extends string = string> {
  value: V
  label: string
  disabled?: boolean
  /** Small trailing badge, e.g. a count. */
  hint?: string
}
