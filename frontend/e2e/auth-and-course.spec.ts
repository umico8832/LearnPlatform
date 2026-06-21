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

test('用户答错后可在错题本更新掌握程度并重练', async ({ page }) => {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill('testuser')
  await page.getByPlaceholder('请输入密码').fill('test123')
  await page.getByPlaceholder('请输入计算结果').fill('42')
  await page.getByRole('button', { name: '登 录' }).click()
  await expect(page).toHaveURL(/\/$/)

  await page.getByRole('menuitem', { name: '刷题练习' }).click()
  await expect(page).toHaveURL(/\/practice$/)
  await page.locator('.config-card input').last().fill('1')
  await page.getByRole('button', { name: '开始刷题' }).click()
  await expect(page).toHaveURL(/\/practice\/session$/)

  // 演示题库中的三道题均将第二个选项设为错误答案，保证会进入错题闭环。
  await page.locator('.question-card .option-item').nth(1).click()
  await page.getByRole('button', { name: '提交答案' }).click()
  const resultDialog = page.getByRole('dialog')
  await expect(resultDialog).toContainText('答错了')
  await resultDialog.getByRole('button', { name: '查看结果' }).click()
  await expect(page.getByText('练习完成！')).toBeVisible()

  await page.getByRole('menuitem', { name: '错题本' }).click()
  await expect(page).toHaveURL(/\/wrong-questions$/)
  const wrongCard = page.locator('.wrong-card').first()
  await expect(wrongCard).toBeVisible()
  await wrongCard.getByText('部分掌握', { exact: true }).click()
  await expect(page.getByText('掌握程度已更新')).toBeVisible()

  await page.getByRole('button', { name: '重练错题' }).click()
  await expect(page).toHaveURL(/\/practice\/session$/)
  await expect(page.getByText('错题重练', { exact: true })).toBeVisible()
})
