import { expect, test } from '@playwright/test'
import type { CourseOverviewVO } from '../src/api/course'
import type { TutorAgentRunVO } from '../src/api/tutor'
import type { TutorMemoryVO } from '../src/api/tutorMemory'
import { loginAsIsolatedTutorUser, readApiData } from './tutor-fixtures'

test('Tutor Agent 跨会话读取课程记忆，纠正和删除后使用最新设置', async ({ page }, testInfo) => {
  test.setTimeout(90_000)
  await page.setViewportSize({ width: 1440, height: 900 })
  await loginAsIsolatedTutorUser(page, testInfo, 'memory')
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
  const sessionCreated = () =>
    page.waitForResponse(
      (response) => response.request().method() === 'POST' && /\/tutor-sessions\?/.test(response.url()),
    )
  const initialSession = sessionCreated()
  const initialMemoryResponse = page.waitForResponse(
    (response) => response.request().method() === 'GET' && response.url().endsWith('/tutor-memory'),
  )
  await page.goto(`${courseUrl}/tutor?knowledgePointId=${target.knowledgePointId}`)
  const initialKey = (await readApiData<{ sessionKey: string }>(await initialSession)).sessionKey
  const panel = page.locator('.agent-panel')
  const records = panel.getByTestId('agent-records')
  const memory = panel.getByTestId('tutor-memory')
  await records.locator(':scope > summary').click()
  await memory.locator('summary').click()
  const initialMemory = await readApiData<TutorMemoryVO>(await initialMemoryResponse)
  expect(initialMemory).toEqual({ revision: 0, explanationStyle: null, goal: null })
  await expect(memory.getByTestId('memory-goal')).toBeEnabled()
  await memory.getByTestId('memory-style').selectOption('EXAMPLES')
  await memory.getByTestId('memory-goal').fill('理解栈顶变化')
  const saveResponse = page.waitForResponse(
    (response) => response.request().method() === 'PUT' && response.url().endsWith('/tutor-memory'),
  )
  await memory.getByTestId('memory-save').click()
  const saved = await readApiData<TutorMemoryVO>(await saveResponse)
  expect(saved).toEqual({ revision: initialMemory.revision + 1, explanationStyle: 'EXAMPLES', goal: '理解栈顶变化' })
  await expect(memory).toContainText('记忆已保存')

  async function askForCurrentMemory(expected: string) {
    const response = page.waitForResponse(
      (item) => item.request().method() === 'POST' && /\/agent-runs(?:\/[^/]+\/messages)?$/.test(item.url()),
    )
    await panel.getByTestId('agent-input').fill('E2E_READ_MEMORY')
    await panel.getByTestId('agent-submit').click()
    const run = await readApiData<TutorAgentRunVO>(await response)
    expect(run.messages.at(-1)?.content).toBe(expected)
    await expect(panel).toContainText(expected)
    await expect(memory.getByTestId('memory-goal')).toBeEnabled()
  }
  await askForCurrentMemory('当前保存的目标：理解栈顶变化；讲解偏好：EXAMPLES。')

  await page.evaluate(() => sessionStorage.clear())
  const newSession = sessionCreated()
  await page.reload()
  expect((await readApiData<{ sessionKey: string }>(await newSession)).sessionKey).not.toBe(initialKey)
  await records.locator(':scope > summary').click()
  await memory.locator('summary').click()
  await expect(memory.getByTestId('memory-goal')).toHaveValue('理解栈顶变化')
  await expect(memory.getByTestId('memory-style')).toHaveValue('EXAMPLES')
  await memory.getByTestId('memory-goal').fill('理解队列顺序')
  await memory.getByTestId('memory-style').selectOption('CONCISE')
  const updateResponse = page.waitForResponse(
    (response) => response.request().method() === 'PUT' && response.url().endsWith('/tutor-memory'),
  )
  await memory.getByTestId('memory-save').click()
  const updated = await readApiData<TutorMemoryVO>(await updateResponse)
  expect(updated).toEqual({ revision: saved.revision + 1, explanationStyle: 'CONCISE', goal: '理解队列顺序' })
  await expect(memory).toContainText('记忆已保存')
  await askForCurrentMemory('当前保存的目标：理解队列顺序；讲解偏好：CONCISE。')
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: testInfo.outputPath('tutor-memory-desktop.png'), fullPage: true })

  await expect(memory.getByTestId('memory-delete')).toBeEnabled()
  const deletedResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'DELETE' && response.url().endsWith(`/tutor-memory?revision=${updated.revision}`),
  )
  await memory.getByTestId('memory-delete').click()
  expect(await readApiData<TutorMemoryVO>(await deletedResponse)).toEqual({
    revision: updated.revision + 1,
    explanationStyle: null,
    goal: null,
  })
  await expect(memory.getByTestId('memory-goal')).toHaveValue('')
  await askForCurrentMemory('当前保存的目标：未设置；讲解偏好：未设置。')
  await page.reload()
  await records.locator(':scope > summary').click()
  await memory.locator('summary').click()
  await expect(memory.getByTestId('memory-goal')).toHaveValue('')
  await expect(memory.getByTestId('memory-delete')).toBeDisabled()

  const finalOverview = page.waitForResponse((response) => /\/my-courses\/\d+\/overview$/.test(response.url()))
  await page.goto(courseUrl)
  const after: CourseOverviewVO = (await (await finalOverview).json()).data
  expect(after.answeredCount).toBe(before.answeredCount)
  expect(after.correctCount).toBe(before.correctCount)
})
