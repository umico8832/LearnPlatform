import { expect, test, type Page } from '@playwright/test'
import type { CourseOverviewVO } from '../src/api/course'
import type { TutorAgentPracticeVO, TutorAgentRunVO } from '../src/api/tutor'

async function post<T>(page: Page, path: string, body: unknown): Promise<T> {
  const result = await page.evaluate(
    async ({ path, body }) => {
      const response = await fetch(`/api${path}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('learn_platform_token')}`,
        },
        body: JSON.stringify(body),
      })
      return response.json()
    },
    { path, body },
  )
  expect(result.code, `${path}: ${result.message}`).toBe(0)
  return result.data as T
}

test('Tutor Agent 推荐已批准变式题、恢复首次作答并读取真实结果', async ({ page, browser }, testInfo) => {
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
  const courseId = Number(new URL(courseUrl).pathname.split('/').at(-1))
  const target = overview.tutorProgress.find((item) => item.title === '线性表的定义与基本操作')!
  expect(target).toBeDefined()

  // 隔离环境通过真实生成和管理员批准 API 建立题目，推荐不绕过审查。
  const adminContext = await browser.newContext()
  try {
    const admin = await adminContext.newPage()
    await admin.goto(`${new URL(page.url()).origin}/admin/login`)
    await admin.getByPlaceholder('请输入用户名或邮箱').fill('admin')
    await admin.getByPlaceholder('请输入密码').fill('admin123')
    await admin.getByRole('button', { name: '登录管理系统' }).click()
    await expect(admin).not.toHaveURL(/\/login/, { timeout: 15_000 })
    const mother = await post<{ id: number }>(admin, '/admin/questions', {
      content: 'E2E_TUTOR_VARIANT：线性表中相邻元素是什么逻辑关系？',
      questionType: 'SINGLE_CHOICE',
      courseId,
      difficulty: 1,
      score: 1,
      analysis: '线性表是有序序列，相邻元素为一对一关系。',
      knowledgePointIds: [target.knowledgePointId],
      options: ['一对一', '一对多', '多对多', '没有关系'].map((content, index) => ({
        content,
        optionLabel: 'ABCD'[index],
        isCorrect: index === 0 ? 1 : 0,
        sortOrder: index + 1,
      })),
    })
    const asset = await post<{ variantQuestion: { id: number } }>(admin, '/ai/asset/generate', {
      questionId: mother.id,
      assetType: 'VARIANT',
    })
    const approved = await post<{ publishedQuestionId: number; reviewStatus: string }>(
      admin,
      `/admin/ai-variant-reviews/${asset.variantQuestion.id}`,
      { decision: 'APPROVE', reviewNote: '已核对隔离测试原创题目及答案，仅用于自动化验收。' },
    )
    expect(approved.reviewStatus).toBe('APPROVED')

    await page.goto(`${courseUrl}/tutor?knowledgePointId=${target.knowledgePointId}`)
    const panel = page.locator('.agent-panel')
    const recommendationResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' && /\/agent-runs(?:\/[^/]+\/messages)?$/.test(response.url()),
    )
    await panel.getByTestId('agent-request-practice').click()
    const recommended: TutorAgentRunVO = (await (await recommendationResponse).json()).data
    expect(recommended.messages.at(-1)?.actions).toEqual([
      { type: 'PRACTICE', questionId: approved.publishedQuestionId },
    ])
    const openResponse = page.waitForResponse(
      (response) => response.request().method() === 'GET' && response.url().endsWith('/practice'),
    )
    await panel.getByTestId('practice-open').last().click()
    const opened: TutorAgentPracticeVO = (await (await openResponse).json()).data
    expect(opened.result).toBeNull()
    expect(opened.question).not.toHaveProperty('analysis')
    expect(opened.question.options.every((option) => !('isCorrect' in option))).toBe(true)
    const practice = panel.locator('.agent-practice').last()
    const wrong = practice.getByRole('radio', { name: 'B. 可以有多个' })
    await practice.locator('label').filter({ hasText: 'B. 可以有多个' }).click()
    await expect(wrong).toBeChecked()
    const answerResponse = page.waitForResponse(
      (response) => response.request().method() === 'POST' && response.url().endsWith('/practice/answer'),
    )
    await practice.getByTestId('practice-submit').click()
    const answered: TutorAgentPracticeVO = (await (await answerResponse).json()).data
    expect(answered.result?.correct).toBe(false)
    expect(answered.result?.userAnswer).toBe('B')
    await expect(practice).toContainText('回答不正确')

    await page.reload()
    const restoreResponse = page.waitForResponse(
      (response) => response.request().method() === 'GET' && response.url().endsWith('/practice'),
    )
    await panel.getByTestId('practice-open').last().click()
    const restored: TutorAgentPracticeVO = (await (await restoreResponse).json()).data
    expect(restored.result).toEqual(answered.result)
    await expect(wrong).toBeChecked()
    await expect(wrong).toBeDisabled()
    await expect(practice.getByTestId('practice-submit')).toBeDisabled()
    await page.evaluate(() => window.scrollTo(0, 0))
    await page.screenshot({ path: testInfo.outputPath('tutor-agent-practice-desktop.png'), fullPage: true })
    const feedbackResponse = page.waitForResponse(
      (response) => response.request().method() === 'POST' && /\/agent-runs\/[^/]+\/messages$/.test(response.url()),
    )
    await practice.getByTestId('practice-follow-up').click()
    const feedback: TutorAgentRunVO = (await (await feedbackResponse).json()).data
    expect(feedback.messages.at(-1)?.content).toBe('服务端变式练习结果：回答不正确。')
    await expect(panel).toContainText('服务端变式练习结果：回答不正确。')
  } finally {
    await adminContext.close()
  }
})
