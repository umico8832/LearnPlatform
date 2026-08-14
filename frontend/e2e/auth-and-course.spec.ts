import { expect, test } from '@playwright/test'
import type { Locator, Page } from '@playwright/test'

async function loginAs(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名或邮箱').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  const loginButton = page.getByRole('button', { name: '登录' })
  await expect(loginButton).toBeEnabled({ timeout: 15_000 })
  await loginButton.click()

  await expect(page).toHaveURL(/\/$/)
}

async function readCountdownSeconds(countdown: Locator) {
  const text = await countdown.textContent()
  const match = text?.match(/(\d+):(\d{2})/)
  if (!match) throw new Error(`无法解析考试倒计时：${text ?? '<empty>'}`)
  return Number(match[1]) * 60 + Number(match[2])
}

test('用户可通过真实登录流程访问课程列表', async ({ page }) => {
  await loginAs(page, 'testuser', 'test123')

  await expect(page.getByRole('main').getByText('今日学习工作台', { exact: true })).toBeVisible()

  await page.getByRole('menuitem', { name: '课程列表' }).click()
  await expect(page).toHaveURL(/\/courses$/)
  await expect(page.getByRole('main').getByRole('heading', { name: '课程列表' })).toBeVisible()
  await expect(page.locator('.course-card').first()).toBeVisible()
})

test('高频用户与管理页面可通过真实接口加载', async ({ page }) => {
  const apiServerErrors: string[] = []
  const consoleErrors: string[] = []

  page.on('response', (response) => {
    if (response.url().includes('/api/') && response.status() >= 500) {
      apiServerErrors.push(`${response.status()} ${response.request().method()} ${response.url()}`)
    }
  })
  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push(message.text())
    }
  })

  await loginAs(page, 'testuser', 'test123')

  const learnerPages = [
    ['/', '今日学习工作台'],
    ['/practice', '刷题练习'],
    ['/wrong-questions', '错题本'],
    ['/review', '智能复习'],
    ['/exams', '考试中心'],
  ] as const

  for (const [path, heading] of learnerPages) {
    await page.goto(path)
    await expect(page.getByRole('main').getByText(heading, { exact: true }).first()).toBeVisible()
  }

  await page.evaluate(() => localStorage.clear())
  await loginAs(page, 'admin', 'admin123')

  const adminPages = [
    ['/admin', '平台数据总览'],
    ['/admin/questions', '题目管理'],
    ['/admin/submissions', '投稿管理'],
    ['/admin/ai-usage', 'AI 调用分析'],
  ] as const

  for (const [path, heading] of adminPages) {
    await page.goto(path)
    await expect(page.getByRole('main').getByRole('heading', { name: heading, exact: true })).toBeVisible()
  }

  expect(apiServerErrors, `页面加载期间出现 5xx 接口：\n${apiServerErrors.join('\n')}`).toEqual([])
  expect(consoleErrors, `页面加载期间出现 console.error：\n${consoleErrors.join('\n')}`).toEqual([])
})

test('用户答错后可在错题本更新掌握程度并重练', async ({ page }) => {
  await loginAs(page, 'testuser', 'test123')

  await page.getByRole('menuitem', { name: '刷题练习' }).click()
  await expect(page).toHaveURL(/\/practice$/)
  const questionTypeField = page.locator('.config-card .el-form-item').filter({ hasText: '题型' })
  await questionTypeField.locator('.el-select').click()
  await page.getByRole('option', { name: '单选题' }).click()
  await page.locator('.config-card input').last().fill('1')
  await page.locator('.config-card').getByRole('button', { name: '开始刷题' }).click()
  await expect(page).toHaveURL(/\/practice\/session$/)

  // 演示题库中的三道题均将第二个选项设为错误答案，保证会进入错题闭环。
  await page.locator('.question-card .option-item').nth(1).click()
  await page.getByRole('button', { name: '提交答案' }).click()
  const resultDialog = page.getByRole('dialog')
  await expect(resultDialog).toContainText('答错了')
  await resultDialog.getByRole('button', { name: '查看结果' }).click()
  await expect(page.getByText('练习完成！')).toBeVisible()

  await page.getByRole('menuitem', { name: '错题本' }).click()
  await expect(page).toHaveURL(/\/wrong-questions$/)
  const wrongCard = page.locator('.wrong-card').first()
  await expect(wrongCard).toBeVisible()
  await wrongCard.locator('.el-radio-button').filter({ hasText: '部分掌握' }).click()
  await expect(page.getByText('掌握程度已更新')).toBeVisible()

  await page.getByRole('button', { name: '重练错题' }).click()
  await expect(page).toHaveURL(/\/practice\/session$/)
  await expect(page.getByText('错题重练', { exact: true })).toBeVisible()
})

test('用户刷新后可继续限时考试并查看自动判分结果', async ({ page }) => {
  await loginAs(page, 'testuser', 'test123')

  await page.getByRole('menuitem', { name: '考试' }).click()
  await expect(page).toHaveURL(/\/exams$/)
  const examCard = page.locator('.exam-card').filter({ hasText: 'Java 基础入门测验' })
  await expect(examCard).toBeVisible()
  await examCard.getByRole('button', { name: '考试模式' }).click()
  await expect(page).toHaveURL(/\/exams\/take\/\d+$/)

  const takeUrl = page.url()
  const countdown = page.locator('.take-header .countdown')
  await expect(page.getByRole('heading', { name: '考试进行中' })).toBeVisible()
  await expect(page.locator('.question-area')).toBeVisible()
  await expect(countdown).toHaveText(/\d+:\d{2}/)
  const startedSeconds = await readCountdownSeconds(countdown)
  expect(startedSeconds).toBeGreaterThan(0)
  await expect.poll(() => readCountdownSeconds(countdown), { timeout: 5_000 }).toBeLessThan(startedSeconds)
  const beforeReloadSeconds = await readCountdownSeconds(countdown)

  await page.reload()
  await expect(page).toHaveURL(takeUrl)
  await expect(page.getByRole('heading', { name: '考试进行中' })).toBeVisible()
  await expect(page.locator('.question-area')).toBeVisible()
  await expect(countdown).toHaveText(/\d+:\d{2}/)
  expect(await readCountdownSeconds(countdown)).toBeLessThanOrEqual(beforeReloadSeconds)

  // 演示试卷固定包含单选、多选和判断三题，分别覆盖三种作答状态与后端判分。
  await page.locator('.question-area .option-item').filter({ hasText: 'extends' }).click()
  await page.getByRole('button', { name: '下一题' }).click()
  await page.getByRole('button', { name: /^A\s+int$/ }).click()
  await page.getByRole('button', { name: /^C\s+boolean$/ }).click()
  await page.getByRole('button', { name: '下一题' }).click()
  await page.locator('.question-area .option-item').filter({ hasText: '正确' }).click()

  await page.locator('.take-header').getByRole('button', { name: '提交试卷' }).click()
  const confirmDialog = page.getByRole('dialog', { name: '提交确认' })
  await expect(confirmDialog).toBeVisible()
  await confirmDialog.getByRole('button', { name: '确定' }).click()

  await expect(page).toHaveURL(/\/exams\/result\/\d+$/)
  await expect(page.locator('.score-number')).toHaveText('15')
  await expect(page.getByText('答题详情', { exact: true })).toBeVisible()
  await expect(page.locator('.result-tag')).toHaveCount(3)
  await expect(page.locator('.result-tag').filter({ hasText: '正确' })).toHaveCount(3)
})

test('用户可完成2026真题学习与限时考试并复盘可信来源', async ({ page }) => {
  await loginAs(page, 'testuser', 'test123')

  await page.goto('/courses')
  const courseCard = page.locator('.course-card').filter({ hasText: '408 数据结构' })
  await expect(courseCard).toBeVisible()
  await courseCard.getByRole('button', { name: '课程详情' }).click()
  await expect(page.getByRole('heading', { name: '408 数据结构' })).toBeVisible()
  const joinButton = page.getByRole('button', { name: '加入课程库' })
  if (await joinButton.isVisible()) {
    await joinButton.click()
    await expect(page).toHaveURL(/\/my-courses\/\d+$/)
  } else {
    await expect(page.getByRole('button', { name: '进入课程总览' })).toBeVisible()
  }

  await page.goto('/exams')
  const officialCard = page.locator('.exam-card').filter({ hasText: '2026 年 408 真题·数据结构选择题' })
  await expect(officialCard).toBeVisible()
  await expect(officialCard).toContainText('官方原题')
  await expect(officialCard).toContainText('2026 · 全国硕士研究生招生考试计算机学科专业基础')
  await expect(officialCard).toContainText('https://csgraduates.com/study_methods/408quiz/2026/')
  await expect(officialCard).toContainText('11 题')
  await expect(officialCard).toContainText('22 分')

  await officialCard.getByRole('button', { name: '学习模式' }).click()
  await expect(page).toHaveURL(/\/exams\/learn\/\d+$/)
  await expect(page.getByRole('heading', { name: '2026 年 408 真题·数据结构选择题' })).toBeVisible()
  await expect(page.locator('.learning-header')).toContainText(
    '2026 · 全国硕士研究生招生考试计算机学科专业基础综合 · 来源：https://csgraduates.com/study_methods/408quiz/2026/',
  )
  await expect(page.locator('.question-meta')).toContainText('第1题')
  await expect(page.locator('.question-section')).toHaveText('一、单项选择题（数据结构）')
  await expect(page.getByRole('button', { name: 'AI 深度解析' })).toBeDisabled()

  for (let questionNumber = 1; questionNumber <= 11; questionNumber += 1) {
    if (questionNumber > 1) {
      await page
        .locator('.sheet-item')
        .filter({ hasText: `第${questionNumber}题` })
        .click()
    }
    await page.locator('.question-card .option-item').first().click()
    await page.getByRole('button', { name: '提交答案' }).click()
    await expect(page.locator('.answer-result')).toBeVisible()
  }
  await expect(page.getByRole('button', { name: 'AI 深度解析' })).toBeEnabled()
  await page.getByRole('button', { name: '完成本轮学习' }).click()
  await expect(page.locator('.learning-header')).toContainText('本轮已完成')

  await page.goto('/exams')
  const examCard = page.locator('.exam-card').filter({ hasText: '2026 年 408 真题·数据结构选择题' })
  await examCard.getByRole('button', { name: '考试模式' }).click()
  await expect(page).toHaveURL(/\/exams\/take\/\d+$/)
  await expect(page.locator('.question-area')).toBeVisible()
  for (let questionNumber = 1; questionNumber <= 11; questionNumber += 1) {
    await page.locator('.question-area .option-item').first().click()
    if (questionNumber < 11) {
      await page.getByRole('button', { name: '下一题' }).click()
    }
  }
  await page.locator('.take-header').getByRole('button', { name: '提交试卷' }).click()
  const confirmDialog = page.getByRole('dialog', { name: '提交确认' })
  await confirmDialog.getByRole('button', { name: '确定' }).click()

  await expect(page).toHaveURL(/\/exams\/result\/\d+$/)
  await expect(page.getByRole('heading', { name: '2026 年 408 真题·数据结构选择题' })).toBeVisible()
  await expect(page.locator('.score-number')).toHaveText('4')
  await expect(page.locator('.source-panel')).toContainText('来源已核验')
  await expect(page.locator('.source-panel')).toContainText('2026 · 全国硕士研究生招生考试计算机学科专业基础')
  await expect(page.locator('.source-panel')).toContainText('https://csgraduates.com/study_methods/408quiz/2026/')
  await expect(page.locator('.result-tag')).toHaveCount(11)
  await expect(page.locator('.answer-item').first()).toContainText('第1题')
  await expect(page.locator('.answer-item').first()).toContainText('一、单项选择题（数据结构）')
})

test('2026主观题提交后由管理员按评分点批阅并固化总分', async ({ page }) => {
  await loginAs(page, 'testuser', 'test123')
  await page.goto('/exams')
  const paperCard = page.locator('.exam-card').filter({ hasText: '2026 年 408 真题·数据结构部分' })
  await expect(paperCard).toContainText('13 题')
  await expect(paperCard).toContainText('45 分')
  await paperCard.getByRole('button', { name: '考试模式' }).click()

  for (let questionNumber = 1; questionNumber <= 13; questionNumber += 1) {
    if (questionNumber <= 11) {
      await page.locator('.question-area .option-item').first().click()
    } else {
      await page.locator('.question-area textarea').fill(`第${questionNumber === 12 ? 41 : 42}题 E2E 分步作答`)
    }
    if (questionNumber < 13) {
      await page.getByRole('button', { name: '下一题' }).click()
    }
  }
  await page.locator('.take-header').getByRole('button', { name: '提交试卷' }).click()
  await page.getByRole('dialog', { name: '提交确认' }).getByRole('button', { name: '确定' }).click()

  await expect(page).toHaveURL(/\/exams\/result\/\d+$/)
  const resultUrl = page.url()
  await expect(page.getByText('当前分数为暂定分')).toBeVisible()
  await expect(page.locator('.result-tag').filter({ hasText: '待人工批阅' })).toHaveCount(2)
  await expect(page.locator('.answer-item').filter({ hasText: '第41题' })).not.toContainText('正确答案')

  await page.evaluate(() => localStorage.clear())
  await loginAs(page, 'admin', 'admin123')
  await page.goto('/admin/subjective-reviews')
  await expect(page.getByRole('heading', { name: '主观题批阅', level: 1 })).toBeVisible()

  for (let reviewIndex = 0; reviewIndex < 2; reviewIndex += 1) {
    await page.locator('.el-table__row').first().getByRole('button', { name: '开始批阅' }).click()
    const drawer = page.getByRole('dialog', { name: '按评分点批阅' })
    const scoreInputs = drawer.locator('.rubric-point .el-input-number input')
    const pointCount = await scoreInputs.count()
    const scores = pointCount === 3 ? [4, 8, 1] : [2, 2, 2, 4]
    await expect(scoreInputs).toHaveCount(scores.length)
    for (let index = 0; index < scores.length; index += 1) {
      await scoreInputs.nth(index).fill(String(scores[index]))
    }
    await drawer.getByRole('textbox', { name: '总体批阅意见' }).fill('E2E 按评分点复核完成')
    await drawer.getByRole('button', { name: '确认并完成批阅' }).click()
    await expect(page.getByText('批阅已保存，考试成绩已重新计算')).toBeVisible()
  }
  await expect(page.getByText('当前没有待批阅答案')).toBeVisible()

  await page.evaluate(() => localStorage.clear())
  await loginAs(page, 'testuser', 'test123')
  await page.goto(resultUrl)
  await expect(page.locator('.score-number')).toHaveText('27')
  await expect(page.getByText('当前分数为暂定分')).toHaveCount(0)
  await expect(page.locator('.answer-item').filter({ hasText: '第41题' })).toContainText('E2E 按评分点复核完成')
  await expect(page.locator('.result-tag')).toHaveCount(13)
})

test('用户投稿可由管理员审核并入库', async ({ page }) => {
  const questionContent = `E2E 投稿题目 ${Date.now()}`

  await loginAs(page, 'testuser', 'test123')

  await page.goto('/submit')
  await expect(page.getByRole('main').getByText('题目投稿', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '投稿新题目' }).click()
  const submissionDialog = page.getByRole('dialog', { name: '投稿新题目' })
  const courseSelect = submissionDialog.getByRole('combobox', { name: /所属课程/ })
  await courseSelect.click()
  await page.getByRole('option', { name: 'Java 基础' }).click()
  await courseSelect.press('Escape')
  const questionTypeSelect = submissionDialog.getByRole('combobox', { name: /题型/ })
  await questionTypeSelect.press('Enter')
  await page.getByRole('option', { name: '单选题' }).click()
  await submissionDialog.getByPlaceholder('输入题目内容，支持 Markdown').fill(questionContent)
  await submissionDialog.getByPlaceholder('选项 A').fill('正确选项')
  await submissionDialog.getByPlaceholder('选项 B').fill('错误选项')
  await submissionDialog.locator('.option-row').first().getByText('正确答案', { exact: true }).click()
  await submissionDialog.getByRole('button', { name: '提交投稿' }).click()
  await expect(page.getByText('投稿提交成功，等待管理员审核')).toBeVisible()

  await page.evaluate(() => localStorage.clear())
  await loginAs(page, 'admin', 'admin123')

  await page.goto('/admin/submissions')
  await expect(page.getByRole('main').getByText('投稿管理', { exact: true })).toBeVisible()
  await page.getByPlaceholder('搜索题干关键词').fill(questionContent)
  await page.getByRole('main').getByRole('button', { name: '搜索' }).click()
  const submissionRow = page.locator('.el-table__row').filter({ hasText: questionContent })
  await expect(submissionRow).toHaveCount(1)
  await submissionRow.getByRole('button', { name: '通过' }).click()
  const reviewDialog = page.getByRole('dialog', { name: '通过投稿' })
  await reviewDialog.getByPlaceholder('审核通过意见（可选）').fill('E2E 审核通过')
  await reviewDialog.getByRole('button', { name: '确认通过' }).click()
  await expect(submissionRow).toContainText('已通过')

  await submissionRow.getByRole('button', { name: '入库' }).click()
  const importDialog = page.getByRole('dialog', { name: '确认入库' })
  await expect(importDialog).toContainText('入库为正式题目')
  await importDialog.getByRole('button', { name: '确定' }).click()
  await expect(page.getByText('入库成功')).toBeVisible()
  await expect(submissionRow).toContainText('已入库')
})
