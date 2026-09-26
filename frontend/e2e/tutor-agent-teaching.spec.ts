import { expect, test } from '@playwright/test'
import type { CourseOverviewVO, TutorCheckResultVO } from '../src/api/course'
import type { TutorAgentRunVO } from '../src/api/tutor'

test('Tutor Agent 将理解检查交给学习者，并依据服务端结果继续指导', async ({ page }, testInfo) => {
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
  const overview: CourseOverviewVO = (await (await overviewResponse).json()).data
  const courseUrl = page.url()
  const target = overview.tutorProgress.find((item) => item.title === '线性表的定义与基本操作')
  expect(target).toBeDefined()
  await page.goto(`${courseUrl}/tutor?knowledgePointId=${target!.knowledgePointId}`)

  const panel = page.locator('.agent-panel')
  const check = page.getByTestId('tutor-check')
  await expect(panel).toBeVisible()
  await panel.getByTestId('agent-input').fill('E2E_REQUEST_CHECK')
  const requestCheckResponse = page.waitForResponse(
    (response) => response.request().method() === 'POST' && /\/agent-runs$/.test(response.url()),
  )
  await panel.getByTestId('agent-submit').click()
  const requestedRun: TutorAgentRunVO = (await (await requestCheckResponse).json()).data
  expect(requestedRun.messages.at(-1)?.actions).toEqual([{ type: 'CHECK' }])

  await panel.getByTestId('agent-request-check').click()
  await expect(check).toBeFocused()

  // 固定课节和选项，避免依赖课程目录排序；实际结果仍由服务端判分。
  const wrongOption = page.getByRole('radio', { name: '可以有多个前驱，只要元素值不同' })
  await page.getByRole('radiogroup').locator('label').filter({ hasText: '可以有多个前驱，只要元素值不同' }).click()
  await expect(wrongOption).toBeChecked({ timeout: 10_000 })
  const checkResponse = page.waitForResponse(
    (response) => response.request().method() === 'POST' && /\/tutor-sessions\/[^/]+\/check$/.test(response.url()),
    { timeout: 15_000 },
  )
  await page.getByRole('button', { name: '提交检查' }).click()
  const submitted: TutorCheckResultVO = (await (await checkResponse).json()).data
  expect(submitted.correct).toBe(false)
  await expect(check).toContainText('需要再想一步')

  await page.reload()
  await expect(wrongOption).toBeDisabled()
  await expect(wrongOption).toBeChecked()
  await expect(page.getByRole('button', { name: '提交检查' })).toBeDisabled()
  await expect(panel.getByTestId('agent-request-check')).toBeVisible()
  await expect(check).toContainText('需要再想一步')
  await page.locator('main.tutor').screenshot({ path: testInfo.outputPath('tutor-agent-teaching-desktop.png') })

  const followUpResponse = page.waitForResponse(
    (response) => response.request().method() === 'POST' && /\/agent-runs\/[^/]+\/messages$/.test(response.url()),
  )
  await panel.getByTestId('agent-follow-up-check').click()
  const followUp: TutorAgentRunVO = (await (await followUpResponse).json()).data
  expect(followUp.messages.at(-1)?.content).toBe('服务端判分结果：回答不正确。')
  await expect(panel).toContainText('服务端判分结果：回答不正确。')
})
