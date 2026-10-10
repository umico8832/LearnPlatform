import type { VarColor } from '../engine/types'

export interface CodeToken {
  text: string
  kind?: 'keyword' | 'type' | 'number' | 'comment' | 'var'
  color?: VarColor
}

const TYPES = new Set(['void', 'int', 'bool', 'char', 'LNode', 'LinkList', 'SqStack', 'BiTree', 'BiTNode', 'ElemType'])
const KEYWORDS = new Set(['while', 'if', 'else', 'for', 'return', 'true', 'false', 'sizeof', 'NULL', 'malloc', 'free'])
const WORD = /\b([A-Za-z_]\w*)\b|\b(\d+)\b/g

/** Split one C line into tinted tokens; program variables keep their identity colour. */
export function tokenizeLine(line: string, vars: Record<string, VarColor>): CodeToken[] {
  const at = line.indexOf('//')
  const code = at >= 0 ? line.slice(0, at) : line
  const tokens: CodeToken[] = []
  let cursor = 0
  for (const match of code.matchAll(WORD)) {
    const start = match.index ?? 0
    if (start > cursor) tokens.push({ text: code.slice(cursor, start) })
    const [text, word, number] = match
    if (number) tokens.push({ text, kind: 'number' })
    else if (vars[word]) tokens.push({ text, kind: 'var', color: vars[word] })
    else if (TYPES.has(word)) tokens.push({ text, kind: 'type' })
    else if (KEYWORDS.has(word)) tokens.push({ text, kind: 'keyword' })
    else tokens.push({ text })
    cursor = start + text.length
  }
  if (cursor < code.length) tokens.push({ text: code.slice(cursor) })
  if (at >= 0) tokens.push({ text: line.slice(at), kind: 'comment' })
  return tokens
}

export type JsonToken = { text: string; kind?: 'key' | 'string' | 'number' }

/** Tint pretty-printed JSON without HTML injection; short number arrays stay on one line. */
export function tokenizeJson(value: unknown): JsonToken[] {
  const json = JSON.stringify(value, null, 2).replace(
    /\[\s+([\d,\s]+?)\s+\]/g,
    (_, inner: string) => `[${inner.split(/,\s*/).join(', ')}]`,
  )
  const tokens: JsonToken[] = []
  const pattern = /("(?:[^"\\]|\\.)*")(\s*:)?|\b(\d+)\b/g
  let cursor = 0
  for (const match of json.matchAll(pattern)) {
    const start = match.index ?? 0
    if (start > cursor) tokens.push({ text: json.slice(cursor, start) })
    const [text, string, colon, number] = match
    if (number) tokens.push({ text, kind: 'number' })
    else if (colon) tokens.push({ text: string, kind: 'key' }, { text: colon })
    else tokens.push({ text, kind: 'string' })
    cursor = start + text.length
  }
  if (cursor < json.length) tokens.push({ text: json.slice(cursor) })
  return tokens
}
