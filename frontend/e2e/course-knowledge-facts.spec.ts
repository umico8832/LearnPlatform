import { prepareTurnstile } from './helpers/turnstile'
import { expect, test, type Page } from '@playwright/test'
import type { CourseKnowledgePointFactVO, CourseOverviewVO } from '../src/api/course'

function factsResponse(page: Page) {
  return page.waitForResponse((response) => response.url().includes('/knowledge-point-facts?') && response.ok())
}

async function findKnowledgePoint(page: Page, id: number, firstRecords: CourseKnowledgePointFactVO[]) {
  const panel = page.locator('.knowledge-facts-panel')
  let records = firstRecords
  for (let pageNumber = 1; pageNumber <= 30; pageNumber++) {
    const fact = records.find((item) => item.knowledgePointId === id)
    if (fact) {
      const expand = panel.locator('.facts-toggle')
      if (await expand.isVisible()) await expand.click()
      const row = panel
        .locator('.fact-row')
        .filter({ has: page.getByRole('heading', { name: fact.knowledgePointName, exact: true }) })
      await expect(row).toBeVisible()
      return { fact, row }
    }
    const response = factsResponse(page)
    await panel.locator('.btn-next').click()
    records = (await (await response).json()).data.records
  }
  throw new Error('分页中未找到已审查知识点')
}

test('课程空间从首节学习并在真实理解检查后展示知识点记录', async ({ page }, testInfo) => {
  test.setTimeout(60_000)
  await page.setViewportSize({ width: 1440, height: 900 })
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('response', (response) => {
    if (response.url().includes('/api/') && response.status() >= 500)
      errors.push(`${response.status()} ${response.url()}`)
  })
  await prepareTurnstile(page)
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名或邮箱').fill('testuser')
  await page.getByPlaceholder('请输入密码').fill('test123')
  const loginButton = page.getByRole('button', { name: '登录', exact: true })
  await expect(loginButton).toBeEnabled({ timeout: 15_000 })
  const loginResponse = page.waitForResponse(
    (response) => response.request().method() === 'POST' && response.url().endsWith('/api/auth/login'),
  )
  await loginButton.click()
  const response = await loginResponse
  expect(response.status()).toBe(200)
  expect((await response.json()) as { code: number }).toMatchObject({ code: 0 })
  await expect(page).toHaveURL(/\/my-courses$/, { timeout: 15_000 })
  await page.goto('/courses')
  await page
    .locator('.course-card')
    .filter({ hasText: '408 数据结构' })
    .getByRole('button', { name: '查看课程' })
    .click()
  await expect(page.getByRole('button', { name: '查看题目' })).toBeVisible()
  const beforeOverviewResponse = page.waitForResponse((response) => /\/my-courses\/\d+\/overview$/.test(response.url()))
  const beforeFactsResponse = factsResponse(page)
  const joinButton = page.getByRole('button', { name: '加入课程库' })
  const overviewButton = page.getByRole('button', { name: '进入课程空间' })
  await expect(joinButton.or(overviewButton)).toBeEnabled()
  await ((await joinButton.isVisible()) ? joinButton : overviewButton).click()
  await expect(page).toHaveURL(/\/my-courses\/\d+$/)
  const overviewUrl = page.url()
  const overview: CourseOverviewVO = (await (await beforeOverviewResponse).json()).data
  const initialFacts = (await (await beforeFactsResponse).json()).data
  expect(initialFacts.records).toEqual([])
  await expect(page.locator('.knowledge-facts-panel')).toContainText('暂无知识点学习记录')
  await expect(page.locator('.activity-panel')).toHaveCount(0)
  await expect(page.locator('.continue-section button')).toHaveCount(1)
  const pointId = overview.tutorProgress[0].knowledgePointId
  expect(overview.recommendedTargets[0].knowledgePointId).toBe(pointId)
  await page.screenshot({ path: testInfo.outputPath('course-space-new-1440.png'), fullPage: true })
  await page.locator('.continue-section button').click()
  await expect(page).toHaveURL(new RegExp(`/tutor\\?knowledgePointId=${pointId}$`))
  await page.getByRole('radiogroup').locator('label').first().click()
  const answerResponse = page.waitForResponse(
    (response) => response.url().endsWith('/check') && response.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '提交检查' }).click()
  const answer = (await (await answerResponse).json()).data
  await expect(page.getByRole('button', { name: '提交检查' })).toBeDisabled()

  const afterOverviewResponse = page.waitForResponse((response) => /\/my-courses\/\d+\/overview$/.test(response.url()))
  const afterFactsResponse = factsResponse(page)
  await page.goto(overviewUrl)
  const updated: CourseOverviewVO = (await (await afterOverviewResponse).json()).data
  expect(updated.answeredCount).toBe(overview.answeredCount + 1)
  expect(updated.correctCount).toBe(overview.correctCount + Number(answer.correct))
  const after = await findKnowledgePoint(page, pointId, (await (await afterFactsResponse).json()).data.records)
  expect(after.fact.answeredCount).toBe(1)
  expect(after.fact.correctCount).toBe(Number(answer.correct))
  await expect(after.row.getByText(String(after.fact.answeredCount), { exact: true }).first()).toBeVisible()
  await expect(page.locator('.activity-panel .lp-stat').first().locator('.lp-stat-value')).toHaveText(
    String(updated.answeredCount),
  )

  await after.row.getByRole('button', { name: '进入教学', exact: true }).focus()
  await expect(after.row.getByRole('button', { name: '进入教学', exact: true })).toBeFocused()
  await page.locator('.knowledge-facts-panel').screenshot({ path: testInfo.outputPath('knowledge-facts-desktop.png') })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(after.row.getByRole('button', { name: '进入教学', exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.locator('.knowledge-facts-panel').screenshot({ path: testInfo.outputPath('knowledge-facts-mobile.png') })

  const refreshedResponse = factsResponse(page)
  await page.getByRole('button', { name: '刷新记录' }).click()
  const refreshed = await findKnowledgePoint(page, pointId, (await (await refreshedResponse).json()).data.records)
  expect(refreshed.fact.answeredCount).toBe(after.fact.answeredCount)
  await refreshed.row.getByRole('button', { name: '进入教学', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`/tutor\\?knowledgePointId=${pointId}$`))
  expect(errors).toEqual([])
})
