import { expect, test } from '@playwright/test'
import type { CourseOverviewVO } from '../src/api/course'
import type { TutorAgentRunVO } from '../src/api/tutor'
import type { TutorAgentPlanVO } from '../src/api/tutorPlan'

test('Tutor Agent 提出课程安排，显式确认后恢复真实状态且不记录学习完成', async ({ page }, testInfo) => {
  test.setTimeout(90_000)
  await page.setViewportSize({ width: 1440, height: 900 })
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
  const before: CourseOverviewVO = (await (await overviewResponse).json()).data
  const courseUrl = page.url()
  const target = before.tutorProgress.find((item) => item.title === '线性表的定义与基本操作')!
  expect(target).toBeDefined()
  await page.goto(`${courseUrl}/tutor?knowledgePointId=${target.knowledgePointId}`)
  const panel = page.locator('.agent-panel')
  await expect(panel.getByTestId('agent-request-plan')).toBeEnabled()
  let confirmationRequests = 0
  page.on('request', (request) => {
    if (request.method() === 'POST' && request.url().endsWith('/plan/confirm')) confirmationRequests++
  })
  const proposalResponse = page.waitForResponse(
    (response) => response.request().method() === 'POST' && /\/agent-runs(?:\/[^/]+\/messages)?$/.test(response.url()),
  )
  const openedResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' && /\/agent-runs\/[^/]+\/messages\/\d+\/plan$/.test(response.url()),
  )
  await panel.getByTestId('agent-request-plan').click()
  const run: TutorAgentRunVO = (await (await proposalResponse).json()).data
  const action = run.messages.at(-1)?.actions.find((item) => item.type === 'PLAN')
  expect(action?.type).toBe('PLAN')
  const proposed: TutorAgentPlanVO = (await (await openedResponse).json()).data
  expect(proposed.confirmed).toBe(false)
  expect(proposed.confirmedAt).toBeNull()
  expect(proposed.steps.length).toBeGreaterThan(0)
  expect(proposed.steps.length).toBeLessThanOrEqual(3)
  expect(action?.type === 'PLAN' && action.steps).toEqual(proposed.steps)
  const plan = panel.locator('.agent-plan')
  await expect(plan.getByTestId('plan-confirm')).toBeEnabled()
  await expect(plan.getByTestId('plan-open-step')).toHaveCount(0)
  expect(confirmationRequests).toBe(0)

  const confirmResponse = page.waitForResponse(
    (response) => response.request().method() === 'POST' && response.url().endsWith('/plan/confirm'),
  )
  await plan.getByTestId('plan-confirm').click()
  const confirmed: TutorAgentPlanVO = (await (await confirmResponse).json()).data
  expect(confirmed.confirmed).toBe(true)
  expect(confirmed.confirmedAt).toBeTruthy()
  expect(confirmed.available).toBe(true)
  expect(confirmationRequests).toBe(1)
  await expect(plan.getByTestId('plan-confirm')).toBeDisabled()
  const restoredResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' && /\/agent-runs\/[^/]+\/messages\/\d+\/plan$/.test(response.url()),
  )
  await page.reload()
  expect((await (await restoredResponse).json()).data).toEqual(confirmed)
  await expect(plan.getByTestId('plan-confirm')).toHaveText('已确认这份安排')
  await expect(plan.getByTestId('plan-open-step').first()).toBeEnabled()

  const stateResponse = page.waitForResponse(
    (response) => response.request().method() === 'POST' && /\/agent-runs\/[^/]+\/messages$/.test(response.url()),
  )
  await panel.getByTestId('agent-input').fill('E2E_READ_PLAN_STATE')
  await panel.getByTestId('agent-submit').click()
  const state: TutorAgentRunVO = (await (await stateResponse).json()).data
  expect(state.messages.at(-1)?.content).toBe('服务端已记录计划确认；确认不代表完成学习。')
  await expect(panel).toContainText('服务端已记录计划确认')
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: testInfo.outputPath('tutor-agent-plan-desktop.png'), fullPage: true })
  const step = confirmed.steps[0]
  expect(step.type).toBe('TUTOR')
  await plan.getByTestId('plan-open-step').first().click()
  await expect(page).toHaveURL(new RegExp(`/tutor\\?knowledgePointId=${step.knowledgePointId}$`))

  const finalOverviewResponse = page.waitForResponse((response) => /\/my-courses\/\d+\/overview$/.test(response.url()))
  await page.goto(courseUrl)
  const after: CourseOverviewVO = (await (await finalOverviewResponse).json()).data
  expect(after.answeredCount).toBe(before.answeredCount)
  expect(after.correctCount).toBe(before.correctCount)
  expect(after.unresolvedWrongCount).toBe(before.unresolvedWrongCount)
  expect(after.tutorProgress.filter((item) => item.status === 'COMPLETED')).toEqual(
    before.tutorProgress.filter((item) => item.status === 'COMPLETED'),
  )
})
