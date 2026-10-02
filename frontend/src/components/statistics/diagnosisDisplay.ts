export function rateColor(rate: number): string {
  if (rate >= 80) return 'var(--lp-success)'
  if (rate >= 60) return 'var(--lp-warning)'
  return 'var(--lp-danger)'
}

export function statusType(status: string): 'danger' | 'warning' | 'info' | undefined {
  if (status === 'WEAK') return 'danger'
  if (status === 'NEEDS_REVIEW') return 'warning'
  if (status === 'NOT_STARTED') return 'info'
  return undefined
}

export function statusLabel(status: string): string {
  if (status === 'WEAK') return '薄弱'
  if (status === 'NEEDS_REVIEW') return '需复习'
  if (status === 'NOT_STARTED') return '未开始'
  return status
}

export function masteryColor(label: string): string {
  if (label.includes('未掌握')) return 'var(--lp-danger)'
  if (label.includes('部分')) return 'var(--lp-warning)'
  return 'var(--lp-success)'
}

export function reasonType(reason: string): 'danger' | 'warning' | 'info' | undefined {
  if (reason === 'ERROR_PRONE') return 'danger'
  if (reason === 'WEAK_POINT_REINFORCE') return 'warning'
  if (reason === 'SPACED_REVIEW') return undefined
  return 'info'
}

export function similarityColor(score: number): string {
  if (score >= 80) return 'var(--lp-success)'
  if (score >= 60) return 'var(--lp-warning)'
  return 'var(--lp-primary)'
}

export function difficultyColor(difficulty: number): string {
  if (difficulty <= 1) return 'var(--lp-success)'
  if (difficulty <= 2) return 'var(--lp-primary)'
  if (difficulty <= 3) return 'var(--lp-warning)'
  if (difficulty <= 4) return 'var(--lp-danger)'
  return 'var(--lp-text-muted)'
}

export function masteryLevelType(level: number | null): 'danger' | 'warning' | 'success' | 'info' {
  if (level === 0) return 'danger'
  if (level === 1) return 'warning'
  if (level === 2) return 'success'
  return 'info'
}

export function masteryLevelLabel(level: number | null): string {
  if (level === 0) return '未掌握'
  if (level === 1) return '部分掌握'
  if (level === 2) return '已掌握'
  return '未知'
}
