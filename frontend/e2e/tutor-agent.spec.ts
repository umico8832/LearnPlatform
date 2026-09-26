import { expect, test } from '@playwright/test'
import type { TutorAgentRunVO } from '../src/api/tutor'
import type { CourseOverviewVO } from '../src/api/course'

test('Tutor Agent 可追问、刷新恢复，并在上游失败后继续同一对话', async ({ page }, testInfo) => {
  test.setTimeout(90_000)
  await page.setViewportSize({ width: 1440, height: 900 })
  const pageErrors: string[] = []
  page.on('pageerror', (error) => pageErrors.push(error.message))
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名或邮箱').fill('testuser')
  await page.getByPlaceholder('请输入密码').fill('test123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/my-courses$/, { timeout: 15_000 })
  await page.goto('/courses')
  await page
    .locator('.course-card')
    .filter({ hasText: '408 数据结构' })
    .getByRole('button', { name: '查看课程' })
    .click()
  await expect(page.getByRole('button', { name: '查看题目' })).toBeVisible()
  const overviewResponse = page.waitForResponse((response) => /\/my-courses\/\d+\/overview$/.test(response.url()))
  const join = page.getByRole('button', { name: '加入课程库' })
  await ((await join.isVisible()) ? join : page.getByRole('button', { name: '进入课程空间' })).click()
  const overview: CourseOverviewVO = (await (await overviewResponse).json()).data
  const courseUrl = page.url()
  await page.goto(`${courseUrl}/tutor?knowledgePointId=${overview.tutorProgress[0].knowledgePointId}`)
  const panel = page.locator('.agent-panel')
  await expect(panel).toBeVisible()

  await panel.getByTestId('agent-input').fill('请解释一下本节内容')
  await expect(panel.getByTestId('agent-submit')).toBeEnabled()
  await page.route(
    '**/agent-runs',
    async (route) => {
      const response = await route.fetch()
      expect((await response.json()).code).toBe(0)
      await route.abort('connectionreset')
    },
    { times: 1 },
  )
  const firstResponse = page.waitForResponse(
    (response) => response.request().method() === 'GET' && response.url().endsWith('/agent-runs/latest'),
  )
  await panel.getByTestId('agent-submit').click()
  const first: TutorAgentRunVO = (await (await firstResponse).json()).data
  expect(first.status).toBe('WAITING_USER')
  expect(first.messages).toHaveLength(2)
  expect(first).not.toHaveProperty('executionKey')
  await expect(panel.locator('.agent-message')).toHaveCount(2)

  await page.reload()
  await expect(panel.locator('.agent-message')).toHaveCount(2)
  await expect(panel).toContainText('等待你的问题')
  const continuedResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' && response.url().endsWith(`/agent-runs/${first.runKey}/messages`),
  )
  await panel.getByTestId('agent-input').fill('继续说明')
  await panel.getByTestId('agent-submit').click()
  const continued: TutorAgentRunVO = (await (await continuedResponse).json()).data
  expect(continued.runKey).toBe(first.runKey)
  expect(continued.messages).toHaveLength(4)
  await expect(panel.locator('.agent-message')).toHaveCount(4)

  await panel.getByTestId('agent-input').fill('E2E_FAIL_AGENT')
  await panel.getByTestId('agent-submit').click()
  await expect(panel).toContainText('可重试')
  await expect(panel.getByTestId('agent-input')).toHaveValue('E2E_FAIL_AGENT')
  await expect(panel.locator('.agent-message')).toHaveCount(4)
  await panel.getByTestId('agent-input').fill('重新说明本节内容')
  await panel.getByTestId('agent-submit').click()
  await expect(panel.locator('.agent-message')).toHaveCount(6)
  await page.evaluate(() => {
    for (const key of Object.keys(sessionStorage)) {
      if (key.startsWith('lp:tutor-agent-run:')) sessionStorage.removeItem(key)
    }
  })
  await page.reload()
  await expect(panel.locator('.agent-message')).toHaveCount(6)
  await panel.screenshot({ path: testInfo.outputPath('tutor-agent-desktop.png') })
  expect(pageErrors).toEqual([])
})
