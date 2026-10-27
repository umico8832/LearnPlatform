import { expect, test, type Page, type Route } from '@playwright/test'
import { createLearnerAndLogin } from './helpers/registerLearner'

type ApiEnvelope<T> = { code: number; message?: string; data: T }

type CacheSensitiveStatistics = {
  overview: { totalPractice: number }
  learningPath: { steps: Array<{ totalAttempts: number }> }
  learningDiagnosis: { totalPractice: number }
}

async function getApiData<T>(page: Page, path: string): Promise<T> {
  const result = await page.evaluate(async (requestPath) => {
    const response = await fetch(`/api${requestPath}`, {
      headers: { Authorization: `Bearer ${localStorage.getItem('learn_platform_token')}` },
    })
    return { status: response.status, body: (await response.json()) as ApiEnvelope<unknown> }
  }, path)
  expect(result.status, `读取 ${path} 失败`).toBe(200)
  expect(result.body.code, `读取 ${path} 失败：${result.body.message ?? 'unknown error'}`).toBe(0)
  return result.body.data as T
}

async function readCacheSensitiveStatistics(page: Page): Promise<CacheSensitiveStatistics> {
  const [overview, learningPath, learningDiagnosis] = await Promise.all([
    getApiData<CacheSensitiveStatistics['overview']>(page, '/statistics/overview'),
    getApiData<CacheSensitiveStatistics['learningPath']>(page, '/statistics/learning-path'),
    getApiData<CacheSensitiveStatistics['learningDiagnosis']>(page, '/statistics/learning-diagnosis'),
  ])
  return { overview, learningPath, learningDiagnosis }
}

function learningPathAttempts(snapshot: CacheSensitiveStatistics) {
  return snapshot.learningPath.steps.reduce((total, step) => total + step.totalAttempts, 0)
}

test('新学习者看到诚实的空学习诊断', async ({ browser, page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await createLearnerAndLogin(browser, page, 'learning-diagnosis')
  await page.goto('/learning-diagnosis')
  await expect(page.getByRole('heading', { name: '还没有可诊断的学习记录' })).toBeVisible()
  await expect(page.getByText('完成一次练习或复习后，这里会基于真实记录整理下一步。')).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('learning-diagnosis-empty.png') })
})

test('隔离学习者完成练习后可获取并停止 AI 学习建议', async ({ browser, page }, testInfo) => {
  test.setTimeout(90_000)
  await page.setViewportSize({ width: 1440, height: 900 })
  await createLearnerAndLogin(browser, page, 'learning-diagnosis-populated')
  await page.goto('/practice')
  const configuration = page.getByRole('region', { name: '开始练习' })
  await configuration.getByLabel('选择课程').selectOption({ label: 'Java 基础' })
  await configuration.getByText('自选条件', { exact: true }).click()
  await configuration.getByLabel('题型').selectOption('SINGLE_CHOICE')
  await configuration.getByLabel('题目数量').fill('5')
  const selected = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/practice/questions')
  await page.getByRole('button', { name: '开始刷题' }).click()
  const selection = await selected
  expect(selection.status()).toBe(200)
  const questions = (await selection.json()) as ApiEnvelope<unknown[]>
  expect(questions.code).toBe(0)
  const questionCount = questions.data.length
  expect(questionCount).toBeGreaterThan(0)
  expect(questionCount).toBeLessThanOrEqual(5)
  await expect(page).toHaveURL(/\/practice\/session/)
  for (let index = 0; index < questionCount; index += 1) {
    await page.locator('.question-card').locator('input[type="radio"], input[type="checkbox"]').first().check()
    // 先填充三个 Redis 缓存，再由真实练习写入 PracticeRecord；命中缓存后的读取必须看到新数据。
    const cachedBeforeAnswer = index === 0 ? await readCacheSensitiveStatistics(page) : undefined
    const submitted = page.waitForResponse(
      (response) => response.request().method() === 'POST' && response.url().endsWith('/api/practice/submit'),
    )
    await page.getByRole('button', { name: '提交答案' }).click()
    await submitted
    if (cachedBeforeAnswer) {
      const refreshedAfterAnswer = await readCacheSensitiveStatistics(page)
      expect(refreshedAfterAnswer.overview.totalPractice).toBe(cachedBeforeAnswer.overview.totalPractice + 1)
      expect(refreshedAfterAnswer.learningDiagnosis.totalPractice).toBe(
        cachedBeforeAnswer.learningDiagnosis.totalPractice + 1,
      )
      expect(learningPathAttempts(refreshedAfterAnswer)).toBeGreaterThan(learningPathAttempts(cachedBeforeAnswer))
    }
    await page.getByRole('button', { name: /查看结果|下一题|完成练习/ }).click()
    if (index < questionCount - 1) await expect(page.locator('.question-card')).toBeVisible()
  }
  await expect(page.getByRole('heading', { name: `${questionCount} 题已记录` })).toBeVisible()
  await page.goto('/learning-diagnosis')
  await expect(page.getByRole('heading', { name: '学习诊断', level: 1 })).toBeVisible()
  expect((await getApiData<{ totalPractice: number }>(page, '/statistics/learning-diagnosis')).totalPractice).toBe(
    questionCount,
  )
  let holdRequest!: (route: Route) => void
  const heldRequest = new Promise<Route>((resolve) => (holdRequest = resolve))
  await page.route('**/statistics/ai-advice/stream', (route) => holdRequest(route), { times: 1 })
  await page.getByRole('button', { name: '获取 AI 建议' }).click()
  const pausedRoute = await heldRequest
  await expect(page.getByRole('button', { name: '停止' })).toBeVisible()
  await page.getByRole('button', { name: '停止' }).click()
  await expect(page.getByRole('button', { name: '获取 AI 建议' })).toBeEnabled()
  await expect(page.getByRole('button', { name: '停止', exact: true })).toHaveCount(0)
  await pausedRoute.abort().catch(() => undefined)

  const generated = page.waitForResponse((response) => response.url().endsWith('/statistics/ai-advice/stream'))
  await page.getByRole('button', { name: '获取 AI 建议' }).click()
  expect((await generated).status()).toBe(200)
  const advice = page.getByRole('heading', { name: 'AI 学习建议' }).locator('xpath=ancestor::article[1]')
  await expect(advice.getByText('AI 生成', { exact: true })).toBeVisible()
  await expect(advice.locator('.prose')).not.toBeEmpty()
  await page.screenshot({ path: testInfo.outputPath('learning-diagnosis-populated.png'), fullPage: true })
  const prose = advice.locator('.prose')
  await expect(prose.getByRole('heading', { name: '下一步学习建议', level: 3 })).toBeVisible()
  await expect(prose.locator('ul > li')).toHaveCount(3)
  await expect(prose.locator('ol > li')).toHaveCount(3)
  await expect(prose.locator('strong').filter({ hasText: '先回看已做过的题' })).toBeVisible()
  await expect(prose).toContainText('记录较少时，先积累可核对的作答')
  await expect(prose).not.toContainText(/^\s*[\[{]/)
  await expect(page.getByRole('main')).not.toContainText(/answerLabels|"analysis"/)
  await expect(advice.getByRole('alert')).toHaveCount(0)
})
