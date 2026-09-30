import type confettiType from 'canvas-confetti'

let generation = 0
let activeConfetti: typeof confettiType | undefined
let mediaQuery: MediaQueryList | undefined

function resetForReducedMotion() {
  if (mediaQuery?.matches) resetCelebrationConfetti()
}

export function resetCelebrationConfetti() {
  generation += 1
  activeConfetti?.reset()
  activeConfetti = undefined
  mediaQuery?.removeEventListener('change', resetForReducedMotion)
}

export async function launchCelebrationConfetti() {
  if (typeof window === 'undefined') return
  mediaQuery ??= window.matchMedia('(prefers-reduced-motion: reduce)')
  mediaQuery.addEventListener('change', resetForReducedMotion)
  if (mediaQuery.matches) return
  const requestGeneration = ++generation
  try {
    const { default: confetti } = await import('canvas-confetti')
    if (requestGeneration !== generation || mediaQuery.matches) return
    activeConfetti = confetti
    const styles = getComputedStyle(document.documentElement)
    const colors = ['--lp-reward-xp', '--lp-reward-achievement', '--lp-home-mint'].map((token) =>
      styles.getPropertyValue(token).trim(),
    )
    await activeConfetti({
      particleCount: 72,
      spread: 64,
      startVelocity: 32,
      origin: { y: 0.2 },
      colors,
      disableForReducedMotion: true,
      zIndex: 390,
    })
  } catch {
    // 装饰资源加载失败时，已保存的奖励与学习操作仍可正常显示。
  } finally {
    if (requestGeneration === generation) {
      activeConfetti = undefined
      mediaQuery.removeEventListener('change', resetForReducedMotion)
    }
  }
}
