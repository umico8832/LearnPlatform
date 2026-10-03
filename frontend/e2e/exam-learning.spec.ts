import { expect, test, type Page } from '@playwright/test'
import { createLearnerAndLogin } from './helpers/registerLearner'

async function findPublishedPaper(page: Page, title: string) {
  await page.locator('.paper-filters .el-select').first().click()
  await Promise.all([
    page.waitForResponse(
      (response) => response.url().includes('/api/exam/papers') && response.request().method() === 'GET',
    ),
    page.getByRole('option', { name: '官方原题' }).click(),
  ])
  await page.getByRole('textbox', { name: '按试卷名称查找' }).fill(title)
  await Promise.all([
    page.waitForResponse(
      (response) => response.url().includes('/api/exam/papers') && response.request().method() === 'GET',
    ),
    page.getByRole('button', { name: '查找试卷' }).click(),
  ])

  const card = page.locator('.exam-card').filter({ hasText: title }).first()
  await expect(card).toBeVisible({ timeout: 15_000 })
  return card
}

test('试卷学习的补充解析遵守 Markdown 响应契约', async ({ page, browser }, testInfo) => {
  test.setTimeout(90_000)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await createLearnerAndLogin(browser, page, 'exam-learning-markdown')
  await page.goto('/courses')
  await page
    .locator('.course-card')
    .filter({ hasText: '408 数据结构' })
    .getByRole('button', { name: '查看课程' })
    .click()
  const join = page.getByRole('button', { name: '加入课程库', exact: true })
  await expect(join).toBeVisible()
  await join.click()
  await expect(page).toHaveURL(/\/my-courses\/\d+$/)

  await page.goto('/exams')
  const officialCard = await findPublishedPaper(page, '2026 年 408 真题·数据结构选择题')
  await expect(officialCard).toBeVisible()
  await officialCard.getByRole('button', { name: '学习模式' }).click()
  await expect(page).toHaveURL(/\/exams\/learn\/\d+$/)

  await page.locator('.question-card .option-item input').first().check()
  await page.getByRole('button', { name: '提交答案', exact: true }).click()
  await expect(page.locator('.exam-learning-feedback')).toBeVisible()

  const assistant = page.getByRole('region', { name: 'AI 学习助手', exact: true })
  await assistant.getByRole('button', { name: '补充解析', exact: true }).click()
  await expect(assistant.locator('.ai-result')).toHaveAttribute('aria-busy', 'false')
  await expect(assistant.getByRole('button', { name: '停止生成' })).toHaveCount(0)
  const markdown = assistant.locator('.markdown-body')
  await expect(markdown).toContainText('补充解析')
  await expect(markdown).not.toContainText('answerLabels')
  await assistant.screenshot({ path: testInfo.outputPath('exam-learning-markdown-analysis.png') })
})
