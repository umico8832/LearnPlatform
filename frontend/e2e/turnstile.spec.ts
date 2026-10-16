import { expect, test } from '@playwright/test'
import { prepareTurnstile } from './helpers/turnstile'

test('登录隔离浏览器端验证码 CDN，服务端仍要求验证码和正确密码', async ({ page, request }) => {
  const credentials = { account: 'testuser', password: 'test123' }
  for (const challenge of [{}, { turnstileToken: '' }]) {
    const response = await request.post('/api/auth/login', { data: { ...credentials, ...challenge } })
    const result = await response.json()
    expect(result.code).toBe(1001)
    expect(result).not.toHaveProperty('data')
  }
  const rejected = await request.post('/api/auth/login', {
    data: { ...credentials, password: 'incorrect-e2e-password', turnstileToken: 'XXXX.DUMMY.TOKEN.XXXX' },
  })
  expect((await rejected.json()).code).toBe(1002)

  const escaped: string[] = []
  await page.context().route('https://challenges.cloudflare.com/**', (route) => {
    escaped.push(route.request().url())
    return route.abort()
  })
  await prepareTurnstile(page)
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名或邮箱').fill(credentials.account)
  await page.getByPlaceholder('请输入密码').fill(credentials.password)
  const response = page.waitForResponse(
    (item) => item.request().method() === 'POST' && item.url().endsWith('/api/auth/login'),
  )
  await page.getByRole('button', { name: '登录', exact: true }).click()
  expect((await (await response).json()).code).toBe(0)
  await expect(page).toHaveURL(/\/my-courses$/)
  expect(escaped).toEqual([])
})
