import { expect, test } from '@playwright/test'

test('OAuth 回调保留明确失败与返回登录的恢复入口', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.emulateMedia({ colorScheme: 'dark' })
  await page.goto('/oauth/callback?error=account_exists')
  await expect(page.getByRole('heading', { name: '登录未完成' })).toBeVisible()
  await expect(page.getByRole('status')).toContainText('该邮箱已有账号，请先使用原登录方式。')
  await expect(page).toHaveURL(/\/oauth\/callback$/)
  await expect(page.locator('html')).toHaveAttribute('data-appearance', 'dark')
  await expect(page.locator('.el-button, .el-input, .el-alert')).toHaveCount(0)
  await page.getByRole('button', { name: '返回登录' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
})
