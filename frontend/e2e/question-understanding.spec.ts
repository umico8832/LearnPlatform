import { expect, test, type Page } from '@playwright/test'
import { createLearnerAndLogin } from './helpers/registerLearner'
import { prepareTurnstile } from './helpers/turnstile'

const stem = '入栈顺序为 1,2,3,4,5，下列哪个是可能的出栈序列？'

async function seededStackQuestion(page: Page): Promise<{ id: number; courseId: number }> {
  return page.evaluate(async (expectedStem) => {
    const headers = { Authorization: `Bearer ${localStorage.getItem('learn_platform_token')}` }
    const coursesResponse = await fetch('/api/courses/list', { headers })
    const courses = (await coursesResponse.json()) as { code: number; data: { id: number; name: string }[] }
    if (courses.code !== 0) throw new Error('课程种子不可用')
    const course = courses.data.find((item) => item.name === '408 数据结构')
    if (!course) throw new Error('408 数据结构课程种子不存在')
    for (let pageNum = 1; pageNum <= 20; pageNum += 1) {
      const response = await fetch(`/api/questions?courseId=${course.id}&pageNum=${pageNum}&pageSize=100`, { headers })
      const result = (await response.json()) as {
        code: number
        data: { records: { id: number; content: string }[]; pages: number }
      }
      if (result.code !== 0) throw new Error('题目种子查询失败')
      const found = result.data.records.find((item) => item.content === expectedStem)
      if (found) return { id: found.id, courseId: course.id }
      if (pageNum >= result.data.pages) break
    }
    throw new Error('出栈序列题种子不存在')
  }, stem)
}

async function openSeededQuestion(page: Page, label: string) {
  await createLearnerAndLogin(page.context().browser()!, page, label)
  const { id, courseId } = await seededStackQuestion(page)
  await page.goto(`/courses/${courseId}`)
  await page.getByRole('button', { name: '加入课程库', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`/my-courses/${courseId}$`))
  const question = page.getByRole('link').filter({ hasText: stem })
  await expect(question).toContainText('未做')
  await expect(question).toHaveAttribute('href', `/questions/${id}`)
  await question.click()
  await expect(page.getByText(stem, { exact: true })).toBeVisible()
}

test('出栈序列答错后依次理解误区、演示、正解并辅助作答变式', async ({ page }) => {
  await openSeededQuestion(page, 'understanding-wrong')
  await page.locator('.answer-step input[value="B"]').check()
  await page.locator('.answer-step').getByRole('button', { name: '提交' }).click()
  await expect(page.getByRole('heading', { name: '以为能越过 2 直接弹出 1' })).toBeVisible()
  await expect(page.getByRole('button', { name: '看正解' })).toHaveCount(0)
  for (const [name, level] of [
    ['给我一点提示', 1],
    ['再具体一点', 2],
    ['看一个相似的例子', 3],
  ] as const) {
    await page.getByRole('button', { name }).click()
    await expect(page.getByRole('heading', { name: new RegExp(`^提示 ${level}`) })).toBeVisible()
  }
  await expect(page.getByText('内容由 AI 生成，请注意甄别')).toBeVisible()
  await expect(page.getByText('用你选的 3,1,2,5,4 走一遍')).toBeVisible()
  await expect(page.locator('.viz').first()).toBeVisible()
  await page.getByRole('button', { name: '看正解' }).click()
  await expect(page.getByText('正确答案', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('正确选项 3,2,1,5,4：每一步都取栈顶')).toBeVisible()
  await expect(page.getByRole('heading', { name: '换一个条件，再做一次' })).toBeVisible()
  const variant = page.locator('article').filter({ hasText: '哪个不可能' })
  await variant.locator('input[value="A"]').check()
  const variantResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' && /\/understanding\/variants\/\d+\/attempts$/.test(response.url()),
  )
  await variant.getByRole('button', { name: '提交' }).click()
  expect((await (await variantResponse).json()).data.review.counted).toBe(false)
  await expect(page.getByText('这次还不对，正确答案是 D。')).toBeVisible()
  await expect(page.getByRole('heading', { name: '这道变式的思路' })).toBeVisible()
  await expect(page.getByText('原题用过提示，这次变式不计入复习安排。')).toBeVisible()

  // 错题页先讲为什么错，再给重做日期；重置复习卡片后当日到期。
  const id = Number(new URL(page.url()).pathname.split('/').pop())
  await page.goto(`/wrong-questions?questionId=${id}`)
  const row = page.getByRole('region', { name: '错题列表' }).getByRole('article')
  await expect(row).toHaveCount(1)
  await expect(row.getByRole('heading', { name: '以为能越过 2 直接弹出 1' })).toBeVisible()
  await expect(row.locator('.when')).not.toContainText('未排期')
  const reset = await page.evaluate(async (questionId) => {
    const response = await fetch(`/api/review/reset/${questionId}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${localStorage.getItem('learn_platform_token')}` },
    })
    return ((await response.json()) as { code: number }).code
  }, id)
  expect(reset).toBe(0)
  await page.reload()
  await expect(row.locator('.when')).toContainText('今天')
  await page.goto(`/review?questionId=${id}`)
  const due = page.getByRole('region', { name: /今天到期/ }).getByRole('article')
  await expect(due).toHaveCount(1)
  await expect(due).toContainText(stem)
  await expect(due).toContainText('首次复习')
  await expect(due.locator('.when')).toHaveText('今天')
  await due.getByRole('button', { name: '复习', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`/questions/${id}$`))
  await expect(page.getByText(stem, { exact: true })).toBeVisible()
})

test('出栈序列选对后直接开放正解和变式', async ({ page }) => {
  await openSeededQuestion(page, 'understanding-right')
  await page.locator('.answer-step input[value="A"]').check()
  await page.locator('.answer-step').getByRole('button', { name: '提交' }).click()
  await expect(page.getByText('答对了。下面对一遍完整思路。')).toBeVisible()
  await expect(page.getByRole('heading', { name: '对一遍思路' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '换一个条件，再做一次' })).toBeVisible()
  await expect(page.getByRole('button', { name: '看正解' })).toHaveCount(0)
  const variant = page.locator('article').filter({ hasText: '哪个不可能' })
  await variant.locator('input[value="A"]').check()
  const response = page.waitForResponse(
    (item) => item.request().method() === 'POST' && /\/understanding\/variants\/\d+\/attempts$/.test(item.url()),
  )
  await variant.getByRole('button', { name: '提交' }).click()
  expect((await (await response).json()).data.review.counted).toBe(true)
  await expect(page.getByText('这次还不对，正确答案是 D。')).toBeVisible()
  await expect(page.getByText('这次独立作答已计入复习安排。')).toBeVisible()
})

test('管理员预览三级提示，且预览不留下错题', async ({ page }) => {
  await prepareTurnstile(page)
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名或邮箱').fill('admin')
  await page.getByPlaceholder('请输入密码').fill('admin123')
  const loginButton = page.getByRole('button', { name: '登录', exact: true })
  await expect(loginButton).toBeEnabled({ timeout: 15_000 })
  await loginButton.click()
  await expect(page).toHaveURL(/\/my-courses$/)
  const { id } = await seededStackQuestion(page)

  await page.goto(`/questions/${id}?preview=1`)
  await expect(page.getByText(stem, { exact: true })).toBeVisible()
  await expect(page.getByText('预览 · 含待审核内容')).toBeVisible()
  await page.locator('.answer-step input[value="B"]').check()
  await page.locator('.answer-step').getByRole('button', { name: '提交' }).click()
  await expect(page.getByRole('heading', { name: '以为能越过 2 直接弹出 1' })).toBeVisible()
  await expect(page.getByRole('button', { name: '看正解' })).toHaveCount(0)
  for (const [name, level] of [
    ['给我一点提示', 1],
    ['再具体一点', 2],
    ['看一个相似的例子', 3],
  ] as const) {
    await page.getByRole('button', { name }).click()
    await expect(page.getByRole('heading', { name: new RegExp(`^提示 ${level}`) })).toBeVisible()
  }
  await expect(page.getByText('内容由 AI 生成，请注意甄别')).toBeVisible()
  await expect(page.locator('.viz').first()).toBeVisible()
  await page.getByRole('button', { name: '看正解' }).click()
  await expect(page.getByText('正确选项 3,2,1,5,4：每一步都取栈顶')).toBeVisible()

  await page.goto(`/wrong-questions?questionId=${id}`)
  await expect(page.getByRole('heading', { name: '这个范围里没有错题' })).toBeVisible()
})
