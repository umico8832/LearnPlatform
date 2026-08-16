import { beforeEach } from 'vitest'
import { config } from '@vue/test-utils'
import {
  LpKicker,
  LpPageHeader,
  LpSectionHeading,
  LpStat,
  LpEmptyState,
  LpDivider,
  LpSignal,
  LpProgress,
  LpSkeleton,
} from '@/components/ui'

class MemoryStorage implements Storage {
  private data = new Map<string, string>()

  get length() {
    return this.data.size
  }

  clear() {
    this.data.clear()
  }

  getItem(key: string) {
    return this.data.get(key) ?? null
  }

  key(index: number) {
    return Array.from(this.data.keys())[index] ?? null
  }

  removeItem(key: string) {
    this.data.delete(key)
  }

  setItem(key: string, value: string) {
    this.data.set(key, String(value))
  }
}

Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: new MemoryStorage(),
})

beforeEach(() => {
  localStorage.clear()
})

/** 全局注册 LearnPlatform UI 基础组件，避免单测中出现未解析组件警告。 */
config.global.components = {
  LpKicker,
  LpPageHeader,
  LpSectionHeading,
  LpStat,
  LpEmptyState,
  LpDivider,
  LpSignal,
  LpProgress,
  LpSkeleton,
}
