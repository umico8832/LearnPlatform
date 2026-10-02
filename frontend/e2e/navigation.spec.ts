import { expect, test } from '@playwright/test'
import type { Page, Route } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const searchApi = /\/api\/search\?/
const leaveMessage = '离开后，本页未提交的答案会丢失，考试计时仍会继续。'

async function login(page: Page) {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名或邮箱').fill('testuser')
  await page.getByPlaceholder('请输入密码').fill('test123')
  const loginResponse = page.waitForResponse(
    (response) => response.request().method() === 'POST' && response.url().endsWith('/api/auth/login'),
  )
  await page.getByRole('button', { name: '登录', exact: true }).click()
  expect((await loginResponse).status()).toBe(200)
  await expect(page).toHaveURL(/\/my-courses$/)
}

async function openSearch(page: Page) {
  const trigger = page.getByRole('button', { name: /搜索题目、课程、知识点/ })
  await trigger.click()
  const dialog = page.getByRole('dialog', { name: '全局搜索' })
  await expect(dialog.getByRole('heading', { name: '全局搜索' })).toBeVisible()
  await expect(dialog.getByRole('combobox', { name: '搜索题目、课程或知识点' })).toBeFocused()
  return { dialog, trigger }
}

async function maybeScreenshot(page: Page, name: string, fullPage = true) {
  const directory = process.env.NAVIGATION_SHOWCASE_DIR
  if (!directory) return
  await mkdir(directory, { recursive: true })
  await page.screenshot({ path: path.join(directory, `${name}.png`), fullPage })
}

test.beforeEach(async ({ page }) => {
  await login(page)
})

test('课程库导航、全局搜索和键盘结果选择保持焦点与语义', async ({ page }) => {
  const courseLink = page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '课程库' })
  await courseLink.click()
  await expect(page).toHaveURL(/\/courses$/)
  await expect(courseLink).toHaveAttribute('aria-current', 'page')
  await expect(page.getByRole('main')).toBeFocused()

  const { dialog, trigger } = await openSearch(page)
  const input = dialog.getByRole('combobox', { name: '搜索题目、课程或知识点' })
  const javaSearchResponse = page.waitForResponse(
    (response) =>
      searchApi.test(response.url()) && new URL(response.url()).searchParams.get('keyword') === 'Java' && response.ok(),
  )
  await input.fill('Java')
  const javaSearch = (await javaSearchResponse).json() as Promise<{
    data: { questions: Array<{ id: number; title: string }> }
  }>
  const selectedQuestion = (await javaSearch).data.questions[0]
  expect(selectedQuestion).toBeDefined()
  if (!selectedQuestion) throw new Error('Java 搜索没有返回题目结果')
  await expect(dialog.getByRole('listbox', { name: '搜索结果' })).toBeVisible()
  await expect(input).toHaveAttribute('aria-expanded', 'true')
  await expect(dialog.getByRole('option').first()).toBeVisible()
  await input.press('ArrowDown')
  await expect(dialog.getByRole('option').first()).toHaveAttribute('aria-selected', 'true')
  await expect(input).toHaveAttribute('aria-activedescendant', 'global-search-option-0')
  await input.press('Enter')
  await expect(page).toHaveURL(/\/questions\?/, { timeout: 15_000 })
  const selectedQuestionId = String(selectedQuestion.id)
  expect(new URL(page.url()).searchParams.get('questionId')).toBe(selectedQuestionId)
  await expect(page.locator('.question-card')).toHaveCount(1)
  await expect(page.locator('.question-card').first()).toContainText(selectedQuestion.title)

  await openSearch(page)
  await page.keyboard.press('Escape')
  await expect(trigger).toBeFocused()

  const fullQuestionsResponse = page.waitForResponse(
    (response) =>
      response.url().includes('/api/questions?') && !new URL(response.url()).searchParams.has('knowledgePointId'),
  )
  await page.goto('/questions')
  const fullQuestionTotal = (await (await fullQuestionsResponse).json()).data.total as number
  const { dialog: knowledgePointDialog } = await openSearch(page)
  const knowledgePointInput = knowledgePointDialog.getByRole('combobox', { name: '搜索题目、课程或知识点' })
  const knowledgePointSearchResponse = page.waitForResponse(
    (response) =>
      searchApi.test(response.url()) &&
      new URL(response.url()).searchParams.get('keyword') === '面向对象' &&
      response.ok(),
  )
  await knowledgePointInput.fill('面向对象')
  const knowledgePointSearch = (await knowledgePointSearchResponse).json() as Promise<{
    data: { knowledgePoints: Array<{ id: number; title: string }> }
  }>
  const selectedKnowledgePoint = (await knowledgePointSearch).data.knowledgePoints[0]
  expect(selectedKnowledgePoint).toBeDefined()
  if (!selectedKnowledgePoint) throw new Error('面向对象搜索没有返回知识点结果')
  const filteredQuestionsResponse = page.waitForResponse(
    (response) => new URL(response.url()).searchParams.get('knowledgePointId') === String(selectedKnowledgePoint.id),
  )
  await knowledgePointDialog.getByRole('option').filter({ hasText: selectedKnowledgePoint.title }).first().click()
  await expect(page).toHaveURL(/\/questions\?knowledgePointId=/)
  const filteredQuestionTotal = (await (await filteredQuestionsResponse).json()).data.total as number
  expect(filteredQuestionTotal).toBeLessThan(fullQuestionTotal)
  await expect(page.locator('.question-card')).toHaveCount(Math.min(filteredQuestionTotal, 10))
  await expect(knowledgePointDialog).toBeHidden()
  await expect(page.locator('.el-overlay:visible')).toHaveCount(0)
  await maybeScreenshot(page, 'navigation-search')
})

test('搜索错误重试、迟到响应和空结果不会混淆状态', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  let abortFirstSearch = true
  await page.route(searchApi, async (route) => {
    if (abortFirstSearch) {
      abortFirstSearch = false
      await route.abort('failed')
      return
    }
    await route.continue()
  })
  const { dialog } = await openSearch(page)
  const input = dialog.getByRole('combobox', { name: '搜索题目、课程或知识点' })
  await input.fill('Java')
  await expect(dialog.getByText('搜索暂时无法完成，请重试')).toBeVisible()
  await maybeScreenshot(page, 'navigation-search-error-reduced-motion')
  await dialog.getByRole('button', { name: '重试' }).click()
  await expect(dialog.getByRole('option').first()).toBeVisible()
  await page.unroute(searchApi)

  let releaseJava!: () => void
  let javaRequestSeen = false
  let lateJavaResponseReturned = false
  await page.route(searchApi, async (route: Route) => {
    const keyword = new URL(route.request().url()).searchParams.get('keyword')
    if (keyword !== 'Java' || javaRequestSeen) {
      await route.continue()
      return
    }
    const response = await route.fetch()
    await new Promise<void>((resolve) => {
      releaseJava = resolve
      javaRequestSeen = true
    })
    await route.fulfill({ response })
    lateJavaResponseReturned = true
  })
  await input.fill('')
  await input.fill('Java')
  await expect(dialog.locator('.search-state')).toBeVisible()
  await maybeScreenshot(page, 'navigation-search-loading-reduced-motion')
  await expect.poll(() => javaRequestSeen, { timeout: 15_000 }).toBe(true)
  await input.fill('408')
  await expect(dialog.getByRole('option').first()).toBeVisible()
  await expect(input).toHaveValue('408')
  const resultsAfterNewQuery = await dialog.getByRole('listbox', { name: '搜索结果' }).innerText()
  releaseJava()
  await expect.poll(() => lateJavaResponseReturned).toBe(true)
  await expect(input).toHaveValue('408')
  const normalizeText = (value: string) => value.replace(/\s+/g, ' ').trim()
  await expect
    .poll(async () => normalizeText(await dialog.getByRole('listbox', { name: '搜索结果' }).innerText()))
    .toBe(normalizeText(resultsAfterNewQuery))
  await expect(dialog.getByRole('option').first()).toBeVisible()
  await page.unroute(searchApi)

  await input.fill('不存在的唯一导航搜索词_20261002')
  await expect(dialog.getByText('未找到匹配结果')).toBeVisible()
  await expect(dialog.getByRole('option')).toHaveCount(0)
  await expect(input).toHaveAttribute('aria-expanded', 'true')
  await maybeScreenshot(page, 'navigation-search-empty-reduced-motion')
})

test('课程空间返回恢复滚动位置和面包屑', async ({ page }) => {
  await page.goto('/my-courses/6')
  await expect(page).toHaveURL(/\/my-courses\/6$/)
  await expect(page.getByRole('heading', { name: '408 数据结构', exact: true })).toBeVisible()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollHeight)).toBeGreaterThan(2000)
  const courseBreadcrumb = page.locator('.route-context').getByRole('link', { name: '我的课程' })
  await expect(courseBreadcrumb).toBeVisible()
  await page.evaluate(() => window.scrollTo(0, 1500))
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(1500)

  await courseBreadcrumb.click()
  await expect(page).toHaveURL(/\/my-courses$/)
  await page.goBack()
  await expect(page).toHaveURL(/\/my-courses\/6$/)
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(1500)
})

test('考试离开确认保留作答，正式提交仅进入结果页', async ({ page }) => {
  await page.goto('/exams')
  const examCard = page.locator('.exam-card').first()
  await expect(examCard).toBeVisible()
  await examCard.getByRole('button', { name: '考试模式' }).click()
  await expect(page).toHaveURL(/\/exams\/take\/\d+$/)
  await expect(page.getByRole('heading', { name: '考试进行中' })).toBeVisible()
  const takeUrl = page.url()

  const selectedOption = page.locator('.question-card').getByRole('radio').first()
  await selectedOption.check()
  await expect(selectedOption).toBeChecked()

  await page.getByRole('button', { name: '返回' }).click()
  const leaveDialog = page.getByRole('dialog', { name: '离开考试' })
  await expect(leaveDialog).toBeVisible()
  await expect(leaveDialog).toContainText(leaveMessage)
  await page.waitForTimeout(350)
  await maybeScreenshot(page, 'navigation-exam-leave-confirm', false)
  await leaveDialog.getByRole('button', { name: '继续考试' }).click()
  await expect(page).toHaveURL(takeUrl)
  await expect(selectedOption).toBeChecked()
  await expect(page).toHaveTitle(/考试中 · LearnPlatform/)

  await page.getByRole('button', { name: '提交试卷' }).first().click()
  const submitDialog = page.getByRole('dialog', { name: '提交确认' })
  await expect(submitDialog).toBeVisible()
  await expect(submitDialog).toContainText('确定提交试卷？提交后不可修改')
  await page.waitForTimeout(350)
  await maybeScreenshot(page, 'navigation-exam-submit-confirm', false)
  await submitDialog.getByRole('button', { name: '提交试卷' }).click()
  await expect(page).toHaveURL(/\/exams\/result\/\d+$/, { timeout: 15_000 })
  await expect(page.getByRole('dialog', { name: '离开考试' })).toHaveCount(0)
  await maybeScreenshot(page, 'navigation-exam-result')
})
