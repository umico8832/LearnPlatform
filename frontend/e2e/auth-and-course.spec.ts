import { expect, test } from '@playwright/test'
import type { Locator, Page } from '@playwright/test'
import path from 'node:path'
import { readFile } from 'node:fs/promises'

async function loginAs(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名或邮箱').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  const loginButton = page.getByRole('button', { name: '登录' })
  await expect(loginButton).toBeEnabled({ timeout: 15_000 })
  await loginButton.click()

  // 登录成功后默认进入「我的课程」
  await expect(page).toHaveURL(/\/my-courses$/)
}

async function loginToAdminApp(page: Page, username: string, password: string) {
  await page.goto('/admin/subjective-reviews')
  await expect(page).toHaveURL(/\/admin\/login\?redirect=/)
  await page.getByPlaceholder('请输入用户名或邮箱').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  const loginButton = page.getByRole('button', { name: '登录管理系统' })
  await expect(loginButton).toBeEnabled({ timeout: 15_000 })
  await loginButton.click()
  await expect(page).toHaveURL(/\/admin\/subjective-reviews$/)
}

async function readCountdownSeconds(countdown: Locator) {
  const text = await countdown.textContent()
  const match = text?.match(/(\d+):(\d{2})/)
  if (!match) throw new Error(`无法解析考试倒计时：${text ?? '<empty>'}`)
  return Number(match[1]) * 60 + Number(match[2])
}

test('用户可通过真实登录流程访问课程库', async ({ page }) => {
  await loginAs(page, 'testuser', 'test123')

  await expect(page.getByRole('main').getByText('我的课程', { exact: true }).first()).toBeVisible()

  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '课程库' }).click()
  await expect(page).toHaveURL(/\/courses$/)
  await expect(page.getByRole('main').getByRole('heading', { name: '课程库' })).toBeVisible()
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
    ['/my-courses', '我的课程'],
    ['/practice', '练习'],
    ['/wrong-questions', '错题'],
    ['/review', '复习'],
    ['/exams', '考试与试卷'],
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
    ['/admin/ai-variant-reviews', 'AI 变式题审查'],
    ['/admin/ai-usage', 'AI 调用分析'],
  ] as const

  for (const [path, heading] of adminPages) {
    await page.goto(path)
    await expect(page.getByRole('main').getByRole('heading', { name: heading, exact: true })).toBeVisible()
  }

  await page.goto('/admin/')
  await expect(page.getByRole('main').getByRole('heading', { name: '平台数据总览', exact: true })).toBeVisible()
  await page.getByRole('navigation', { name: '管理导航' }).getByRole('link', { name: '试卷管理' }).click()
  await expect(page).toHaveURL(/\/admin\/exams$/)
  await expect(page.getByRole('main').getByRole('heading', { name: '试卷管理', exact: true })).toBeVisible()
  await page.getByRole('navigation', { name: '管理导航' }).getByRole('link', { name: '课程管理' }).click()
  await expect(page.getByRole('main').getByRole('heading', { name: '课程管理', exact: true })).toBeVisible()
  await page.locator('.admin-data-table').getByRole('button', { name: '知识点' }).first().click()
  await expect(page).toHaveURL(/\/admin\/knowledge-points\?courseId=/)
  await expect(page.getByRole('main').getByRole('heading', { name: '知识点管理', exact: true })).toBeVisible()
  await page.getByRole('navigation', { name: '管理导航' }).getByRole('link', { name: '题目管理' }).click()
  await expect(page.getByRole('main').getByRole('heading', { name: '题目管理', exact: true })).toBeVisible()
  for (const [path, heading] of [
    ['/admin/users', '用户管理'],
    ['/admin/submissions', '投稿管理'],
    ['/admin/ai-usage', 'AI 调用分析'],
  ] as const) {
    await page.goto(path)
    await expect(page.getByRole('main').getByRole('heading', { name: heading, exact: true })).toBeVisible()
  }

  expect(apiServerErrors, `页面加载期间出现 5xx 接口：\n${apiServerErrors.join('\n')}`).toEqual([])
  expect(consoleErrors, `页面加载期间出现 console.error：\n${consoleErrors.join('\n')}`).toEqual([])
})

test('用户答错后可在错题本更新掌握程度并重练', async ({ page }) => {
  await loginAs(page, 'testuser', 'test123')

  await page.goto('/practice')
  await expect(page).toHaveURL(/\/practice$/)
  const courseField = page.locator('.config-card .el-form-item').filter({ hasText: '选择课程' })
  await courseField.locator('.el-select').click()
  await page.getByRole('option', { name: 'Java 基础' }).click()
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

  await page.goto('/wrong-questions')
  await expect(page).toHaveURL(/\/wrong-questions$/)
  const wrongCard = page.locator('.wrong-card').first()
  await expect(wrongCard).toBeVisible()

  // 测评复盘可深链按知识点筛选错题（Java 基础演示题关联“面向对象”知识点）
  await page.goto('/wrong-questions?courseId=1&knowledgePointId=2&knowledgePointName=面向对象')
  await expect(page.locator('.kp-filter-chip')).toContainText('知识点：面向对象')
  await expect(page.locator('.wrong-card')).toHaveCount(1)
  await expect(page.locator('.wrong-card')).toContainText('Java 中用于定义类继承关系的关键字是？')

  const filteredCard = page.locator('.wrong-card').first()
  await filteredCard.locator('.el-radio-button').filter({ hasText: '部分掌握' }).click()
  await expect(page.getByText('掌握程度已更新')).toBeVisible()

  await page.getByRole('button', { name: '重练错题' }).click()
  await expect(page).toHaveURL(/\/practice\/session$/)
  await expect(page.getByText('错题重练', { exact: true })).toBeVisible()
})

test('用户刷新后可继续限时考试并查看自动判分结果', async ({ page }) => {
  await loginAs(page, 'testuser', 'test123')

  await page.goto('/exams')
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

test('用户可预览确认结构化私有试卷并隔离给其他账号', async ({ page }) => {
  const paperTitle = `E2E 私有试卷 ${Date.now()}`
  await loginAs(page, 'testuser', 'test123')
  await page.goto('/exams')
  await page.getByRole('button', { name: '导入私有试卷' }).click()
  const dialog = page.getByRole('dialog', { name: '导入私有试卷' })
  await expect(dialog).toContainText('原文件存储：0 KB / 100 MB')
  await dialog.getByRole('textbox', { name: '试卷标题' }).fill(paperTitle)
  await dialog.getByRole('combobox', { name: '所属课程' }).click()
  await page.getByRole('option', { name: '408 数据结构' }).click()
  await dialog.getByRole('textbox', { name: '原始资料名称' }).fill('e2e-private.md')
  await dialog.getByRole('textbox', { name: '原始内容' }).fill(`## 1. 单选题
**题干**: 栈遵循哪种访问顺序？
**选项**:
- A. 先进先出
- B. 先进后出
**答案**: B
**解析**: 栈遵循 LIFO。
**分值**: 2`)
  await dialog.getByRole('button', { name: '解析并预览' }).click()
  await expect(dialog).toContainText('1 题 · 2 分 · 60 分钟')
  await expect(dialog).toContainText('确认答案：B')
  await dialog.getByRole('button', { name: '确认导入' }).click()
  await expect(page.getByText('私有试卷已导入')).toBeVisible()

  const privateCard = page.locator('.exam-card').filter({ hasText: paperTitle })
  await expect(privateCard).toContainText('我的私有试卷')
  await expect(privateCard).toContainText('仅你可见 · 已确认导入')
  await privateCard.getByRole('button', { name: '查看原始资料' }).click()
  const sourceDialog = page.getByRole('dialog', { name: '私有试卷原始资料' })
  await expect(sourceDialog).toContainText('e2e-private.md')
  await expect(sourceDialog).toContainText('栈遵循哪种访问顺序？')
  await page.keyboard.press('Escape')
  await expect(sourceDialog).toBeHidden()

  await privateCard.getByRole('button', { name: '考试模式' }).click()
  await expect(page).toHaveURL(/\/exams\/take\/\d+$/)
  await page.locator('.question-area .option-item').filter({ hasText: '先进后出' }).click()
  await page.locator('.take-header').getByRole('button', { name: '提交试卷' }).click()
  await page.getByRole('dialog', { name: '提交确认' }).getByRole('button', { name: '确定' }).click()
  await expect(page).toHaveURL(/\/exams\/result\/\d+$/)
  await expect(page.locator('.score-number')).toHaveText('2')

  await page.goto('/exams')
  const referencedCard = page.locator('.exam-card').filter({ hasText: paperTitle })
  await referencedCard.getByRole('button', { name: '删除试卷' }).click()
  await page.getByRole('dialog', { name: '删除私有试卷' }).getByRole('button', { name: '确认删除' }).click()
  await expect(page.getByText('私有试卷已有考试、学习记录或衍生内容，不能删除')).toBeVisible()

  await page.evaluate(() => localStorage.clear())
  await loginAs(page, 'admin', 'admin123')
  await page.goto('/exams')
  await expect(page.getByText(paperTitle, { exact: true })).toHaveCount(0)
})

test('无答案私有题必须经过AI建议与逐题人工复核才可启用', async ({ page }) => {
  const paperTitle = `E2E AI复核试卷 ${Date.now()}`
  await loginAs(page, 'testuser', 'test123')
  await page.goto('/exams')
  await page.getByRole('button', { name: '导入私有试卷' }).click()
  let dialog = page.getByRole('dialog', { name: '导入私有试卷' })
  await dialog.getByRole('textbox', { name: '试卷标题' }).fill(paperTitle)
  await dialog.getByRole('combobox', { name: '所属课程' }).click()
  await page.getByRole('option', { name: '408 数据结构' }).click()
  await dialog.getByRole('textbox', { name: '原始资料名称' }).fill('e2e-answerless.md')
  await dialog.getByRole('textbox', { name: '原始内容' }).fill(`## 1. 单选题
**题干**: 先进后出的数据结构是？
**选项**:
- A. 栈
- B. 队列
**分值**: 2`)
  await dialog.getByRole('button', { name: '解析并预览' }).click()
  await expect(dialog).toContainText('未提供可靠答案')
  await expect(dialog.getByRole('button', { name: '确认导入' })).toHaveCount(0)
  await dialog.getByRole('button', { name: '创建 AI 补全草稿' }).click()
  await expect(page.getByText('草稿已保存，请逐题生成并复核答案')).toBeVisible()

  // 草稿关闭后仍能从服务端恢复，且尚未出现在可考试试卷列表。
  await dialog.getByRole('button', { name: '取消' }).click()
  await expect(page.getByText(paperTitle, { exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '导入私有试卷' }).click()
  dialog = page.getByRole('dialog', { name: '导入私有试卷' })
  await dialog.getByRole('button', { name: new RegExp(paperTitle) }).click()
  await expect(dialog).toContainText('0/1 题已人工复核')

  await dialog.getByRole('button', { name: '生成 AI 答案与解析' }).click()
  await expect(dialog).toContainText('AI 建议：A · 栈遵循后进先出的访问顺序。')
  await expect(dialog.getByRole('button', { name: '确认启用试卷' })).toHaveCount(0)
  await dialog.getByRole('checkbox', { name: 'A' }).check()
  await dialog.getByRole('textbox', { name: '人工确认解析' }).fill('人工复核：栈遵循后进先出。')
  await dialog.getByRole('button', { name: '确认本题' }).click()
  await expect(dialog).toContainText('1/1 题已人工复核')
  await dialog.getByRole('button', { name: '确认启用试卷' }).click()
  await expect(page.getByText('私有试卷已人工确认并启用')).toBeVisible()

  const card = page.locator('.exam-card').filter({ hasText: paperTitle })
  await expect(card).toContainText('可参加')
  await card.getByRole('button', { name: '考试模式' }).click()
  await page.locator('.question-area .option-item').filter({ hasText: '栈' }).click()
  await page.locator('.take-header').getByRole('button', { name: '提交试卷' }).click()
  await page.getByRole('dialog', { name: '提交确认' }).getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.score-number')).toHaveText('2')
})

test('用户可删除未引用的私有试卷和未确认草稿', async ({ page }) => {
  const suffix = Date.now()
  const paperTitle = `E2E 待删除试卷 ${suffix}`
  const draftTitle = `E2E 待删除草稿 ${suffix}`
  await loginAs(page, 'testuser', 'test123')
  await page.goto('/exams')
  await page.getByRole('button', { name: '导入私有试卷' }).click()
  let dialog = page.getByRole('dialog', { name: '导入私有试卷' })
  await dialog.getByRole('textbox', { name: '试卷标题' }).fill(paperTitle)
  await dialog.getByRole('combobox', { name: '所属课程' }).click()
  await page.getByRole('option', { name: '408 数据结构' }).click()
  await dialog.getByRole('textbox', { name: '原始资料名称' }).fill('delete-paper.md')
  await dialog.getByRole('textbox', { name: '原始内容' }).fill(`## 1. 单选题
**题干**: 待删除试卷题目？
**选项**:
- A. 是
- B. 否
**答案**: A
**解析**: 删除前未产生学习事实。
**分值**: 1`)
  await dialog.getByRole('button', { name: '解析并预览' }).click()
  await dialog.getByRole('button', { name: '确认导入' }).click()
  const paperCard = page.locator('.exam-card').filter({ hasText: paperTitle })
  await paperCard.getByRole('button', { name: '删除试卷' }).click()
  await page.getByRole('dialog', { name: '删除私有试卷' }).getByRole('button', { name: '确认删除' }).click()
  await expect(page.getByText('私有试卷已删除')).toBeVisible()
  await expect(page.getByText(paperTitle, { exact: true })).toHaveCount(0)

  await page.getByRole('button', { name: '导入私有试卷' }).click()
  dialog = page.getByRole('dialog', { name: '导入私有试卷' })
  await dialog.getByRole('textbox', { name: '试卷标题' }).fill(draftTitle)
  await dialog.getByRole('combobox', { name: '所属课程' }).click()
  await page.getByRole('option', { name: '408 数据结构' }).click()
  await dialog.getByRole('textbox', { name: '原始资料名称' }).fill('delete-draft.md')
  await dialog.getByRole('textbox', { name: '原始内容' }).fill(`## 1. 单选题
**题干**: 待删除草稿题目？
**选项**:
- A. 是
- B. 否
**分值**: 1`)
  await dialog.getByRole('button', { name: '解析并预览' }).click()
  await dialog.getByRole('button', { name: '创建 AI 补全草稿' }).click()
  await dialog.getByRole('button', { name: '返回导入' }).click()
  const draftItem = dialog.locator('.draft-list-item').filter({ hasText: draftTitle })
  await draftItem.getByRole('button', { name: '删除草稿', exact: true }).click()
  await page.getByRole('dialog', { name: '删除私有试卷草稿' }).getByRole('button', { name: '确认删除' }).click()
  await expect(page.getByText('私有试卷草稿已删除')).toBeVisible()
  await expect(dialog.getByText(draftTitle)).toHaveCount(0)
})

test('用户可上传文本型PDF并沿用预览确认与来源追溯', async ({ page }) => {
  const paperTitle = `E2E PDF 私有试卷 ${Date.now()}`
  await loginAs(page, 'testuser', 'test123')
  await page.goto('/exams')
  await page.getByRole('button', { name: '导入私有试卷' }).click()
  const dialog = page.getByRole('dialog', { name: '导入私有试卷' })
  await dialog.getByRole('textbox', { name: '试卷标题' }).fill(paperTitle)
  await dialog.getByRole('combobox', { name: '所属课程' }).click()
  await page.getByRole('option', { name: '408 数据结构' }).click()
  await dialog.getByRole('combobox', { name: '格式' }).focus()
  await page.keyboard.press('ArrowDown')
  await page.getByRole('option', { name: '文本型 PDF' }).click()
  await dialog.locator('input[type="file"]').setInputFiles(path.resolve('e2e/fixtures/private-exam-text.pdf'))
  await expect(dialog.getByRole('textbox', { name: '原始资料名称' })).toHaveValue('private-exam-text.pdf')
  await dialog.getByRole('button', { name: '解析并预览' }).click()
  await expect(dialog).toContainText('PDF中的栈遵循哪种访问顺序？')
  await expect(dialog).toContainText('确认答案：B')
  await dialog.getByRole('button', { name: '确认导入' }).click()
  await expect(page.getByText('私有试卷已导入')).toBeVisible()

  const card = page.locator('.exam-card').filter({ hasText: paperTitle })
  await card.getByRole('button', { name: '查看原始资料' }).click()
  const sourceDialog = page.getByRole('dialog', { name: '私有试卷原始资料' })
  await expect(sourceDialog).toContainText('private-exam-text.pdf · PDF')
  await expect(sourceDialog).toContainText('题干：PDF中的栈遵循哪种访问顺序？')
  await page.keyboard.press('Escape')

  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: '导入私有试卷' }).click()
  const reopenedImportDialog = page.getByRole('dialog', { name: '导入私有试卷' })
  await reopenedImportDialog.getByRole('button', { name: '查看明细' }).click()
  const storageDialog = page.getByRole('dialog', { name: '我的原文件存储' })
  await expect(storageDialog).toContainText('private-exam-text.pdf')
  await expect(storageDialog).toContainText(`关联试卷：${paperTitle}`)
  await expect(storageDialog).not.toContainText('application/pdf')
  expect(await storageDialog.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(true)
  await storageDialog.getByRole('button', { name: '删除关联内容' }).click()
  await page.getByRole('dialog', { name: '删除关联内容' }).getByRole('button', { name: '确认删除' }).click()
  await expect(page.getByText('私有试卷及其原文件已删除')).toBeVisible()
  await expect(storageDialog).not.toContainText(paperTitle)
})

test('用户可上传有限DOCX并提取段落表格进入同一确认闭环', async ({ page }) => {
  const paperTitle = `E2E DOCX 私有试卷 ${Date.now()}`
  await loginAs(page, 'testuser', 'test123')
  await page.goto('/exams')
  await page.getByRole('button', { name: '导入私有试卷' }).click()
  const dialog = page.getByRole('dialog', { name: '导入私有试卷' })
  await dialog.getByRole('textbox', { name: '试卷标题' }).fill(paperTitle)
  await dialog.getByRole('combobox', { name: '所属课程' }).click()
  await page.getByRole('option', { name: '408 数据结构' }).click()
  await dialog.getByRole('combobox', { name: '格式' }).focus()
  await page.keyboard.press('ArrowDown')
  await page.getByRole('option', { name: '有限 DOCX' }).click()
  await dialog.locator('input[type="file"]').setInputFiles(path.resolve('e2e/fixtures/private-exam-text.docx'))
  await expect(dialog.getByRole('textbox', { name: '原始资料名称' })).toHaveValue('private-exam-text.docx')
  await dialog.getByRole('button', { name: '解析并预览' }).click()
  await expect(dialog).toContainText('Which access order does a queue follow?')
  await expect(dialog).toContainText('确认答案：A')
  await dialog.getByRole('button', { name: '确认导入' }).click()
  await expect(page.getByText('私有试卷已导入')).toBeVisible()

  const card = page.locator('.exam-card').filter({ hasText: paperTitle })
  await card.getByRole('button', { name: '查看原始资料' }).click()
  const sourceDialog = page.getByRole('dialog', { name: '私有试卷原始资料' })
  await expect(sourceDialog).toContainText('private-exam-text.docx · DOCX')
  await expect(sourceDialog).toContainText('A. First in, first out')
  const downloadPromise = page.waitForEvent('download')
  await sourceDialog.getByRole('button', { name: '下载原文件' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('private-exam-text.docx')
  expect(await readFile(await download.path())).toEqual(
    await readFile(path.resolve('e2e/fixtures/private-exam-text.docx')),
  )
  await page.keyboard.press('Escape')
  await card.getByRole('button', { name: '删除试卷' }).click()
  await page.getByRole('dialog', { name: '删除私有试卷' }).getByRole('button', { name: '确认删除' }).click()
  await expect(page.getByText('私有试卷已删除')).toBeVisible()
})

test('用户可完成2026真题学习与限时考试并复盘可信来源', async ({ page }) => {
  await loginAs(page, 'testuser', 'test123')

  await page.goto('/courses')
  const courseCard = page.locator('.course-card').filter({ hasText: '408 数据结构' })
  await expect(courseCard).toBeVisible()
  await courseCard.getByRole('button', { name: '查看课程' }).click()
  // 页面切换过渡期间旧页面内容仍在 DOM（旧卡片标题会命中 heading 断言），
  // 因此先等详情页独有的按钮出现，再判断「加入课程库 / 进入课程空间」分支。
  await expect(page).toHaveURL(/\/courses\/\d+$/)
  await expect(page.getByRole('button', { name: '查看题目' })).toBeVisible()
  const joinButton = page.getByRole('button', { name: '加入课程库' })
  if (await joinButton.isVisible()) {
    await joinButton.click()
    await expect(page).toHaveURL(/\/my-courses\/\d+$/)
  } else {
    const overviewButton = page.getByRole('button', { name: '进入课程空间' })
    await expect(overviewButton).toBeVisible()
    await overviewButton.click()
  }

  await page.getByRole('button', { name: '阶段测评' }).click()
  const setupDialog = page.getByRole('dialog', { name: '开始阶段测评' })
  await expect(setupDialog).toContainText('课程整体测评')
  await setupDialog.getByRole('button', { name: '开始测评' }).click()
  const assessmentDialog = page.getByRole('dialog', { name: '课程阶段测评' })
  await expect(assessmentDialog).toContainText(/按当前错题|确定性课程题序/)
  await expect(assessmentDialog).toContainText('范围：课程整体')
  await expect(assessmentDialog).toContainText('题源构成：')
  const assessmentQuestions = await assessmentDialog.locator('.assessment-question').all()
  expect(assessmentQuestions.length).toBeGreaterThan(0)
  for (const question of assessmentQuestions) {
    const checkboxes = question.locator('.el-checkbox')
    if ((await checkboxes.count()) > 0) await checkboxes.first().click()
    else await question.locator('.el-radio').first().click()
  }
  await assessmentDialog.getByRole('button', { name: '提交测评' }).click()
  await expect(assessmentDialog).toContainText(/答对 \d+ \/ \d+ 题/)
  await assessmentDialog.getByRole('button', { name: '关闭', exact: true }).click()

  // 课程总览最近测评摘要直接展示各知识点题数与正误数
  await expect(page.locator('.activity-panel')).toContainText('知识点：')

  // 限定已审查知识点范围发起新一轮测评，并确认范围随会话固化展示
  await page.getByRole('button', { name: '阶段测评' }).click()
  const scopedSetupDialog = page.getByRole('dialog', { name: '开始阶段测评' })
  await scopedSetupDialog.locator('.el-select').click()
  await page.getByRole('option', { name: '顺序表的插入与删除' }).click()
  await scopedSetupDialog.getByRole('button', { name: '开始测评' }).click()
  await expect(assessmentDialog).toContainText('范围：顺序表的插入与删除')
  const scopedQuestions = await assessmentDialog.locator('.assessment-question').all()
  expect(scopedQuestions.length).toBeGreaterThan(0)
  for (const question of scopedQuestions) {
    const checkboxes = question.locator('.el-checkbox')
    if ((await checkboxes.count()) > 0) await checkboxes.first().click()
    else await question.locator('.el-radio').first().click()
  }
  await assessmentDialog.getByRole('button', { name: '提交测评' }).click()
  await expect(assessmentDialog).toContainText(/答对 \d+ \/ \d+ 题/)
  await assessmentDialog.getByRole('button', { name: '关闭', exact: true }).click()

  await page.getByRole('button', { name: '测评历史' }).click()
  const historyDialog = page.getByRole('dialog', { name: '阶段测评历史' })
  await expect(historyDialog.locator('.el-select').first()).toBeVisible()
  await expect(historyDialog).toContainText(/答对 \d+ \/ \d+ 题/)
  await expect(historyDialog).toContainText('题源：')
  await historyDialog.getByRole('button', { name: '查看复盘' }).first().click()
  await expect(assessmentDialog).toContainText(/答对 \d+ \/ \d+ 题/)
  await expect(assessmentDialog).toContainText('知识点：顺序表的插入与删除')
  await expect(assessmentDialog).toContainText('按知识点统计')
  await assessmentDialog.getByRole('button', { name: '关闭', exact: true }).click()

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
  await loginToAdminApp(page, 'admin', 'admin123')
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
