import { describe, expect, it } from 'vitest'
import { ignoreAnimationCancellation } from '@/motion'

function animationWithFinished(finished: Promise<void>): Animation {
  return { finished } as unknown as Animation
}

describe('ignoreAnimationCancellation', () => {
  it('消化取消动画的 AbortError', async () => {
    await expect(
      ignoreAnimationCancellation(animationWithFinished(Promise.reject(new DOMException('canceled', 'AbortError')))),
    ).resolves.toBeUndefined()
  })

  it('继续暴露非取消错误', async () => {
    await expect(
      ignoreAnimationCancellation(animationWithFinished(Promise.reject(new Error('unexpected')))),
    ).rejects.toThrow('unexpected')
  })
})
