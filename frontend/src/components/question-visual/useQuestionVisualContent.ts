import type { VisualInteractiveData } from '@/api/ai'

export interface QuestionVisualContentState {
  data: VisualInteractiveData | null
  fallbackMode: boolean
  rawContent: string
}

function tryParseJson(text: string): VisualInteractiveData | null {
  try {
    return JSON.parse(text) as VisualInteractiveData
  } catch {
    return null
  }
}

function isVisualInteractiveData(value: VisualInteractiveData | null): value is VisualInteractiveData {
  return Boolean(value?.elements)
}

export function parseQuestionVisualContent(raw: string): QuestionVisualContentState {
  if (!raw) {
    return { data: null, fallbackMode: false, rawContent: '' }
  }

  const parsed = tryParseJson(raw)
  if (isVisualInteractiveData(parsed)) {
    return { data: parsed, fallbackMode: false, rawContent: '' }
  }

  const jsonBlockMatch = raw.match(/```(?:json)?\s*\n?([\s\S]*?)```/)
  if (jsonBlockMatch) {
    const extracted = tryParseJson(jsonBlockMatch[1].trim())
    if (isVisualInteractiveData(extracted)) {
      return { data: extracted, fallbackMode: false, rawContent: '' }
    }
  }

  return { data: null, fallbackMode: true, rawContent: raw }
}
