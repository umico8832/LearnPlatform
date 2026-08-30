import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import type { UserConfig } from 'vite'
import adminConfig from '../../../vite.admin.config'

describe('管理端 Vite 入口', () => {
  it('开发服务器将 HTML 的源码入口映射到项目 src 目录', () => {
    const html = readFileSync(resolve(process.cwd(), 'admin/index.html'), 'utf8')
    const aliases = (adminConfig as UserConfig).resolve?.alias as Record<string, string>

    expect(html).toContain('src="/src/admin/main.ts"')
    expect(aliases['/src']).toBe(resolve(process.cwd(), 'src'))
  })
})
