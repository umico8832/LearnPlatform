export function reviewStatusTag(label: string) {
  const tags: Record<string, 'info' | 'warning' | 'success' | 'danger'> = {
    新卡片: 'info',
    学习中: 'warning',
    已掌握: 'success',
    困难: 'danger',
  }
  return tags[label] || 'info'
}
