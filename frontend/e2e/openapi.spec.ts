import { expect, test } from '@playwright/test'

test('API 文档保留原入口并通过 Nginx 加载 Swagger UI', async ({ page, request }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))

  const docs = await request.get('/v3/api-docs/default')
  expect(docs.status()).toBe(200)
  expect((await docs.json()).paths['/api/auth/login']).toBeDefined()
  expect((await request.get('/api/auth/me')).status()).toBe(401)

  await page.goto('/doc.html')
  await expect(page).toHaveURL(/\/swagger-ui\/index\.html$/)
  await expect(page.getByRole('heading', { name: /AI 题库与错题复习系统 - API 接口文档/ })).toBeVisible()
  await expect(page.locator('.opblock-tag').first()).toBeVisible()
  expect(errors).toEqual([])
})
