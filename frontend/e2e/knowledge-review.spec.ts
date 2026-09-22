import { expect, test } from '@playwright/test'

test('管理员可在真实知识审核页面核对原文并批准撤回', async ({ page }, testInfo) => {
  test.setTimeout(60_000)
  await page.setViewportSize({ width: 1440, height: 900 })
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto('/admin/knowledge')
  await expect(page).toHaveURL(/\/admin\/login\?redirect=/)
  await page.getByPlaceholder('请输入用户名或邮箱').fill('admin')
  await page.getByPlaceholder('请输入密码').fill('admin123')
  const login = page.getByRole('button', { name: '登录管理系统' })
  await expect(login).toBeEnabled({ timeout: 15_000 })
  const loginResponse = page.waitForResponse(
    (response) => response.request().method() === 'POST' && response.url().endsWith('/api/auth/login'),
  )
  await login.click()
  expect((await (await loginResponse).json()).code).toBe(0)
  await expect(page).toHaveURL(/\/admin\/knowledge$/, { timeout: 15_000 })
  await expect(page.getByRole('heading', { name: '知识快照审核', exact: true })).toBeVisible()

  const imported = await page.evaluate(async () => {
    const response = await fetch('/api/admin/knowledge/import', {
      method: 'POST',
      headers: { Authorization: `Bearer ${localStorage.getItem('learn_platform_token')}` },
    })
    return response.json()
  })
  expect(imported.code).toBe(0)
  expect(imported.data.courseKey).toBe('e2e-knowledge-review')
  await page.getByRole('button', { name: '刷新版本' }).click()
  const row = page.getByRole('row').filter({ hasText: 'e2e-knowledge-review' })
  await expect(row).toContainText('待审核')
  await row.getByRole('button', { name: '查看原文' }).click()
  const drawer = page.getByRole('dialog', { name: '快照详情' })
  await expect(drawer.getByText('栈遵循后进先出。本段为隔离测试原创内容。')).toBeVisible()
  await expect(drawer.locator('.chunk-text').filter({ hasText: '<img src=x' })).toBeVisible()
  expect(await drawer.locator('.knowledge-chunk img').count()).toBe(0)
  await drawer.getByText('来源信息与内容哈希').first().click()
  await expect(drawer.locator('details[open]').getByText('仅供本项目自动化测试', { exact: false })).toBeVisible()
  await expect(drawer.getByRole('button', { name: '批准', exact: true })).toBeDisabled()
  await drawer.getByPlaceholder('记录内容核验、来源和使用许可的结论').fill('已核对原创隔离测试夹具，仅用于自动化测试。')
  await drawer.screenshot({ path: testInfo.outputPath('knowledge-review-desktop.png') })
  await drawer.getByRole('button', { name: '批准', exact: true }).click()
  await page.getByRole('dialog', { name: '确认审核' }).getByRole('button', { name: '确认', exact: true }).click()
  await expect(drawer.getByRole('button', { name: '批准', exact: true })).toHaveCount(0)
  await expect(drawer.getByRole('button', { name: '撤回', exact: true })).toBeVisible()
  await expect(drawer.getByText('已核对原创隔离测试夹具，仅用于自动化测试。', { exact: true })).toBeVisible()
  await drawer.getByPlaceholder('记录内容核验、来源和使用许可的结论').fill('隔离测试已完成，撤回本测试版本。')
  await drawer.getByRole('button', { name: '撤回', exact: true }).click()
  await page.getByRole('dialog', { name: '确认审核' }).getByRole('button', { name: '确认', exact: true }).click()
  await expect(drawer.getByText('此版本已撤回，不能重新批准。需要恢复内容时请导入新版本。')).toBeVisible()
  await page.reload()
  await expect(page.getByRole('row').filter({ hasText: 'e2e-knowledge-review' })).toContainText('已撤回')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(errors).toEqual([])
})
