import { expect, type Browser, type Page } from '@playwright/test'

type ApiEnvelope<T> = { code: number; message?: string; data: T }

async function adminApi<T>(page: Page, path: string, body: unknown): Promise<T> {
  const result = await page.evaluate(
    async ({ path, body }) => {
      const response = await fetch(`/api${path}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('learn_platform_token')}`,
        },
        body: JSON.stringify(body),
      })
      return (await response.json()) as ApiEnvelope<unknown>
    },
    { path, body },
  )
  expect(result.code, `管理员创建隔离学习者失败：${result.message ?? 'unknown error'}`).toBe(0)
  return result.data as T
}

async function loginLearner(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名或邮箱').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/my-courses$/, { timeout: 15_000 })
}

/** Creates a unique USER with the real admin API, then signs in through the learner UI. */
export async function createLearnerAndLogin(browser: Browser, learner: Page, label = 'learner') {
  const suffix = `${Date.now()}${Math.floor(Math.random() * 1_000_000)}`
  const username = `e2e${label
    .replace(/[^a-z0-9]/gi, '')
    .slice(0, 12)
    .toLowerCase()}${suffix}`.slice(0, 50)
  const password = `E2e!${suffix}Learner`
  await learner.goto('/login')
  const origin = new URL(learner.url()).origin
  const adminContext = await browser.newContext()
  try {
    const admin = await adminContext.newPage()
    await admin.goto(`${origin}/admin/login`)
    await admin.getByPlaceholder('请输入用户名或邮箱').fill('admin')
    await admin.getByPlaceholder('请输入密码').fill('admin123')
    await admin.getByRole('button', { name: '登录管理系统' }).click()
    await expect(admin).not.toHaveURL(/\/login/, { timeout: 15_000 })
    await adminApi<{ id: number }>(admin, '/admin/users', {
      username,
      password,
      nickname: 'E2E 隔离学习者',
      role: 'USER',
    })
  } finally {
    await adminContext.close()
  }
  await loginLearner(learner, username, password)
  return { username }
}
