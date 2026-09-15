export interface SearchTextSegment {
  text: string
  match: boolean
}

export function splitSearchMatch(text: string, keyword: string): SearchTextSegment[] {
  const normalizedKeyword = keyword.trim()
  if (!normalizedKeyword) return [{ text, match: false }]

  const escapedKeyword = normalizedKeyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const pattern = new RegExp(escapedKeyword, 'gi')
  const segments: SearchTextSegment[] = []
  let lastIndex = 0
  let match: RegExpExecArray | null

  while ((match = pattern.exec(text))) {
    if (match.index > lastIndex) segments.push({ text: text.slice(lastIndex, match.index), match: false })
    segments.push({ text: match[0], match: true })
    lastIndex = pattern.lastIndex
  }

  if (lastIndex < text.length) segments.push({ text: text.slice(lastIndex), match: false })
  return segments.length ? segments : [{ text, match: false }]
}
