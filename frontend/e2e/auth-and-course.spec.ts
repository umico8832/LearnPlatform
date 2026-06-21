import { expect, test } from '@playwright/test'

test('用户可通过真实登录流程访问课程列表', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByAltText('验证码')).toBeVisible()

  await page.getByPlaceholder('请输入用户名').fill('testuser')
  await page.getByPlaceholder('请输入密码').fill('test123')
  await page.getByPlaceholder('请输入计算结果').fill('42')
  await page.getByRole('button', { name: '登 录' }).click()

  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByText('AI 题库系统', { exact: true })).toBeVisible()

  await page.getByText('课程列表', { exact: true }).click()
  await expect(page).toHaveURL(/\/courses$/)
  await expect(page.getByRole('heading', { name: '课程列表' })).toBeVisible()
  await expect(page.locator('.course-card').first()).toBeVisible()
})
