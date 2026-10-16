export { redoWhen, type RedoWhen } from '@/utils/redoDate'

/** A single option letter fits the option chip; anything longer is shown as plain text. */
export function isOptionKey(answer: string | null | undefined): answer is string {
  return typeof answer === 'string' && /^[A-Z]$/.test(answer.trim())
}

export const MASTERY_OPTIONS = [
  { value: '0', label: '未掌握' },
  { value: '1', label: '部分掌握' },
  { value: '2', label: '已掌握' },
] as const
