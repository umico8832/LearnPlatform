import { expect, test } from '@playwright/test'
import type { Locator, Page, Response } from '@playwright/test'

type DraftQuestion = {
  sortOrder: number
  finalAnswerLabels: string[]
  finalAnalysis: string | null
  reviewStatus: 'PENDING' | 'REVIEWED'
}

type PrivateExamDraftResponse = {
  code: number
  data: {
    status: 'DRAFT' | 'AI_GENERATED' | 'REVIEWING' | 'READY' | 'CONFIRMED'
    reviewedQuestionCount: number
    questionCount: number
    questions: DraftQuestion[]
  }
}

async function loginAs(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名或邮箱').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
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
}

async function reviewResponse(response: Response): Promise<PrivateExamDraftResponse> {
  expect(response.status()).toBe(200)
  const body = (await response.json()) as PrivateExamDraftResponse
  expect(body.code).toBe(0)
  return body
}

async function selectAnswer(question: Locator, from: string, to: string) {
  const option = (label: string) =>
    question.locator('.el-checkbox__label').filter({ hasText: new RegExp(`^${label}$`) })
  await option(from).click()
  await option(to).click()
}

test('用户复核两道无答案私有题后可启用，未提交编辑不会因另一题确认而丢失', async ({ page }, testInfo) => {
  const paperTitle = `E2E 双题人工复核 ${Date.now()}`
  const firstAnalysis = '人工确认：栈按后进先出顺序访问。'
  const secondAnalysis = '人工确认：队列按先进先出顺序访问。'

  await loginAs(page, 'testuser', 'test123')
  await page.goto('/exams')
  await page.getByRole('button', { name: '导入私有试卷' }).click()
  const dialog = page.getByRole('dialog', { name: '导入私有试卷' })

  await dialog.getByRole('textbox', { name: '试卷标题' }).fill(paperTitle)
  await dialog.getByRole('combobox', { name: '所属课程' }).click()
  await page.getByRole('option', { name: '408 数据结构' }).click()
  await dialog.getByRole('textbox', { name: '原始资料名称' }).fill('e2e-two-question-review.md')
  await dialog.getByRole('textbox', { name: '原始内容' }).fill(`## 1. 单选题
**题干**: 后进先出的数据结构是？
**选项**:
- A. 队列
- B. 栈
**分值**: 2

## 2. 单选题
**题干**: 先进先出的数据结构是？
**选项**:
- A. 栈
- B. 队列
**分值**: 2`)
  await dialog.getByRole('button', { name: '解析并预览' }).click()
  await expect(dialog).toContainText('未提供可靠答案')
  await dialog.getByRole('button', { name: '创建 AI 补全草稿' }).click()
  await expect(dialog).toContainText('0/2 题已人工复核')

  const questions = dialog.locator('.draft-question')
  await expect(questions).toHaveCount(2)

  for (const question of await questions.all()) {
    await question.getByRole('button', { name: '生成 AI 答案与解析' }).click()
  }
  await expect(questions.nth(0)).toContainText('AI 建议：A · 栈遵循后进先出的访问顺序。')
  await expect(questions.nth(1)).toContainText('AI 建议：A · 栈遵循后进先出的访问顺序。')

  expect(await dialog.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('review-desktop.png') })
  await page.setViewportSize({ width: 375, height: 844 })
  expect(await dialog.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(true)

  const firstQuestion = questions.nth(0)
  const secondQuestion = questions.nth(1)
  await selectAnswer(firstQuestion, 'A', 'B')
  await firstQuestion.getByRole('textbox', { name: '第1题人工确认解析' }).fill(firstAnalysis)
  await selectAnswer(secondQuestion, 'A', 'B')
  const secondAnalysisInput = secondQuestion.getByRole('textbox', { name: '第2题人工确认解析' })
  await page.keyboard.press('Tab')
  await expect(secondAnalysisInput).toBeFocused()
  await secondAnalysisInput.fill(secondAnalysis)

  const firstReview = page.waitForResponse(
    (response) =>
      response.request().method() === 'PUT' &&
      /\/api\/exam\/private-papers\/drafts\/\d+\/questions\/\d+\/review$/.test(response.url()),
  )
  await firstQuestion.getByRole('button', { name: '确认本题' }).click()
  const firstReviewedDraft = await reviewResponse(await firstReview)
  const firstReviewedQuestion = firstReviewedDraft.data.questions.find((question) => question.sortOrder === 1)
  const pendingSecondQuestion = firstReviewedDraft.data.questions.find((question) => question.sortOrder === 2)
  expect(firstReviewedQuestion).toMatchObject({
    finalAnswerLabels: ['B'],
    finalAnalysis: firstAnalysis,
    reviewStatus: 'REVIEWED',
  })
  expect(pendingSecondQuestion?.reviewStatus).toBe('PENDING')

  await expect(secondQuestion.getByRole('checkbox', { name: 'B' })).toBeChecked()
  await expect(secondAnalysisInput).toHaveValue(secondAnalysis)
  await secondQuestion.scrollIntoViewIfNeeded()
  await page.screenshot({ path: testInfo.outputPath('review-mobile.png') })

  const secondReview = page.waitForResponse(
    (response) =>
      response.request().method() === 'PUT' &&
      /\/api\/exam\/private-papers\/drafts\/\d+\/questions\/\d+\/review$/.test(response.url()),
  )
  await secondQuestion.getByRole('button', { name: '确认本题' }).click()
  const readyDraft = await reviewResponse(await secondReview)
  expect(readyDraft.data.status).toBe('READY')
  expect(readyDraft.data.reviewedQuestionCount).toBe(2)
  expect(readyDraft.data.questionCount).toBe(2)
  expect(readyDraft.data.questions).toEqual(
    expect.arrayContaining([
      expect.objectContaining({
        sortOrder: 1,
        finalAnswerLabels: ['B'],
        finalAnalysis: firstAnalysis,
        reviewStatus: 'REVIEWED',
      }),
      expect.objectContaining({
        sortOrder: 2,
        finalAnswerLabels: ['B'],
        finalAnalysis: secondAnalysis,
        reviewStatus: 'REVIEWED',
      }),
    ]),
  )

  const enableResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      /\/api\/exam\/private-papers\/drafts\/\d+\/confirm$/.test(response.url()),
  )
  await dialog.getByRole('button', { name: '确认启用试卷' }).click()
  const enabled = await enableResponse
  expect(enabled.status()).toBe(200)
  expect((await enabled.json()) as { code: number; data: { id: number } }).toMatchObject({
    code: 0,
    data: { id: expect.any(Number) },
  })
  await expect(page.getByText('私有试卷已人工确认并启用')).toBeVisible()
})
