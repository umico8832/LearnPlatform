export function getAuthPreviewState<T extends string>(value: unknown, allowedStates: readonly T[]): T | undefined {
  if (!import.meta.env.DEV || typeof value !== 'string') return undefined
  return allowedStates.includes(value as T) ? (value as T) : undefined
}
