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

test('课程知识点事实在真实理解检查后更新并保持分页深链与移动端可用', async ({ page }, testInfo) => {
  test.setTimeout(60_000)
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('response', (response) => {
    if (response.url().includes('/api/') && response.status() >= 500)
      errors.push(`${response.status()} ${response.url()}`)
  })
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
  await ((await joinButton.isVisible()) ? joinButton : page.getByRole('button', { name: '进入课程空间' })).click()
  await expect(page).toHaveURL(/\/my-courses\/\d+$/)
  const overviewUrl = page.url()
  const overview: CourseOverviewVO = (await (await beforeOverviewResponse).json()).data
  const initialFacts = (await (await beforeFactsResponse).json()).data
  expect(initialFacts.records.length).toBeLessThanOrEqual(10)
  const pointId = overview.tutorProgress[0].knowledgePointId
  const before = await findKnowledgePoint(page, pointId, initialFacts.records)
  await before.row.getByRole('button', { name: '进入教学' }).click()
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
  expect(after.fact.answeredCount).toBe(before.fact.answeredCount + 1)
  expect(after.fact.correctCount).toBe(before.fact.correctCount + Number(answer.correct))
  await expect(after.row.locator('dd').nth(0)).toHaveText(String(after.fact.answeredCount))
  await expect(after.row.locator('dd').nth(1)).toHaveText(String(after.fact.correctCount))
  await expect(page.locator('.activity-panel .lp-stat').first().locator('.lp-stat-value')).toHaveText(
    String(updated.answeredCount),
  )

  await after.row.getByRole('button', { name: '复习', exact: true }).focus()
  await expect(after.row.getByRole('button', { name: '复习', exact: true })).toBeFocused()
  await page.locator('.knowledge-facts-panel').screenshot({ path: testInfo.outputPath('knowledge-facts-desktop.png') })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(after.row.getByRole('button', { name: '复习', exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.locator('.knowledge-facts-panel').screenshot({ path: testInfo.outputPath('knowledge-facts-mobile.png') })

  const refreshedResponse = factsResponse(page)
  await page.getByRole('button', { name: '刷新记录' }).click()
  const refreshed = await findKnowledgePoint(page, pointId, (await (await refreshedResponse).json()).data.records)
  expect(refreshed.fact.answeredCount).toBe(after.fact.answeredCount)
  await refreshed.row.getByRole('button', { name: '复习', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`/review\\?.*knowledgePointId=${pointId}`))
  await expect(page.getByText(refreshed.fact.knowledgePointName, { exact: false }).first()).toBeVisible()
  expect(errors).toEqual([])
})
