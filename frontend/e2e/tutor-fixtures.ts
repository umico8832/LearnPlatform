import { randomUUID } from 'node:crypto'
import { expect } from '@playwright/test'
import type { Page, TestInfo } from '@playwright/test'

interface ApiResult<T> {
  code: number
  message: string
  data?: T
}

export async function readApiData<T>(response: { status(): number; json(): Promise<unknown> }): Promise<T> {
  expect(response.status()).toBe(200)
  const result = (await response.json()) as ApiResult<T>
  expect(result.code, result.message).toBe(0)
  expect(result.data, '成功响应应包含 data').not.toBeNull()
  expect(result.data, '成功响应应包含 data').toBeDefined()
  return result.data as T
}

async function login(page: Page, account: string, password: string) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名或邮箱').fill(account)
  await page.getByPlaceholder('请输入密码').fill(password)
  const button = page.getByRole('button', { name: '登录', exact: true })
  await expect(button).toBeEnabled({ timeout: 15_000 })
  const response = page.waitForResponse(
    (item) => item.request().method() === 'POST' && item.url().endsWith('/api/auth/login'),
  )
  await button.click()
  const session = await readApiData<{ token: string }>(await response)
  await expect(page).toHaveURL(/\/my-courses$/, { timeout: 15_000 })
  return session.token
}

/** 每次重试创建独立学习账号，避免共享学习事实、记忆和 AI 用量。 */
export async function loginAsIsolatedTutorUser(page: Page, testInfo: TestInfo, purpose: string) {
  const adminToken = await login(page, 'admin', 'admin123')
  const username = `e2e_tutor_${purpose}_${testInfo.retry}_${randomUUID().slice(0, 8)}`
  const password = 'test123'
  const response = await page.context().request.post(new URL('/api/admin/users', page.url()).toString(), {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: { username, password, role: 'USER' },
  })
  const created = await readApiData<{ username: string }>(response)
  expect(created.username).toBe(username)

  await page.evaluate(() => localStorage.clear())
  await login(page, username, password)
}
