import { isAbortError } from '@/utils/errors'

export function ignoreAnimationCancellation(animation: Animation): Promise<void> {
  return animation.finished.then(undefined, (error: unknown) => {
    if (!isAbortError(error)) throw error
  })
}
