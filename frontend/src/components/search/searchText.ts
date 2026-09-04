export function highlightSearchMatch(text: string, keyword: string) {
  if (!keyword.trim()) return escapeHtml(text)
  const escapedKeyword = keyword.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return escapeHtml(text).replace(new RegExp(`(${escapedKeyword})`, 'gi'), '<mark>$1</mark>')
}

function escapeHtml(text: string) {
  return text.replace(/&/g, '&').replace(/</g, '<').replace(/>/g, '>').replace(/"/g, '"')
}
