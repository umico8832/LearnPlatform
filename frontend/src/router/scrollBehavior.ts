import type { RouterScrollBehavior } from 'vue-router'

const RESTORE_TIMEOUT_MS = 8_000
let cancelPendingRestore: (() => void) | undefined

function cancelRestore() {
  cancelPendingRestore?.()
  cancelPendingRestore = undefined
}

function contentCanReach(top: number) {
  const main = document.querySelector('main')
  const height = Math.max(document.documentElement.scrollHeight, main?.scrollHeight ?? 0)
  return height - window.innerHeight >= top
}

function restoreWhenReady(savedPosition: { left: number; top: number }) {
  cancelRestore()
  if (contentCanReach(savedPosition.top)) return savedPosition

  return new Promise<false | { left: number; top: number }>((resolve) => {
    let settled = false
    let observer: ResizeObserver | undefined
    const finish = (position: false | { left: number; top: number }) => {
      if (settled) return
      settled = true
      observer?.disconnect()
      window.clearTimeout(timeout)
      window.removeEventListener('wheel', cancel)
      window.removeEventListener('touchstart', cancel)
      window.removeEventListener('pointerdown', cancel)
      window.removeEventListener('keydown', cancel)
      if (cancelPendingRestore === cancel) cancelPendingRestore = undefined
      resolve(position)
    }
    const cancel = () => finish(false)
    const check = () => {
      if (contentCanReach(savedPosition.top)) finish(savedPosition)
    }
    const timeout = window.setTimeout(cancel, RESTORE_TIMEOUT_MS)
    cancelPendingRestore = cancel
    window.addEventListener('wheel', cancel, { passive: true })
    window.addEventListener('touchstart', cancel)
    window.addEventListener('pointerdown', cancel)
    window.addEventListener('keydown', cancel)
    if (typeof ResizeObserver !== 'undefined') {
      observer = new ResizeObserver(check)
      observer.observe(document.documentElement)
      const main = document.querySelector('main')
      if (main) observer.observe(main)
    }
    check()
  })
}

export const learnerScrollBehavior: RouterScrollBehavior = (to, from, savedPosition) => {
  cancelRestore()
  if (savedPosition) return restoreWhenReady(savedPosition)

  if (to.hash) {
    let id = to.hash.slice(1)
    try {
      id = decodeURIComponent(id)
    } catch {
      // 外部链接可能含有不完整的编码，仍按原始 ID 查找。
    }
    const target = document.getElementById(id)
    if (target) return { el: target, top: Number.parseFloat(getComputedStyle(target).scrollMarginTop) || 0 }
  }

  if (to.path === from.path) return false
  return { top: 0, left: 0 }
}
