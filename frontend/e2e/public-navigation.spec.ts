import { expect, test } from '@playwright/test'

test('匿名未知地址可通过404返回首页，学习入口仍要求登录', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/unknown-learning-page')
  await expect(page).toHaveURL(/\/unknown-learning-page$/)
  await expect(page).toHaveTitle('页面不存在 · LearnPlatform')
  await expect(page.getByRole('heading', { name: '404' })).toBeVisible()
  const home = page.getByRole('link', { name: '返回首页' })
  await home.focus()
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Learn')
  await expect(page.getByRole('link', { name: 'Sign in', exact: true })).toBeVisible()
  await expect(page).toHaveTitle('Learn to understand · LearnPlatform')
  await page.goto('/unknown-learning-page')
  const courses = page.getByRole('link', { name: '查看课程' })
  await courses.focus()
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  expect(new URL(page.url()).searchParams.get('redirect')).toBe('/courses')
  await expect(page.getByPlaceholder('请输入用户名或邮箱')).toBeVisible()
  await expect(page.locator('.el-overlay:visible')).toHaveCount(0)
})

test('展示首页与五个公开占位入口可访问且无页面错误', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Learn')
  await page.getByRole('link', { name: 'See how it works' }).click()
  await expect(page.locator('#see-it')).toBeInViewport()
  for (const path of ['/product', '/learning', '/resources', '/roadmap', '/about']) {
    await page.goto('/')
    const link = page.locator(`footer a[href="${path}"]`).first()
    await link.click()
    await expect(page).toHaveURL(new RegExp(`${path}$`))
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  }
  expect(errors).toEqual([])
})
