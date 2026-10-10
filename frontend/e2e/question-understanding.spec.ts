import { expect, test, type Page } from '@playwright/test'
import { createLearnerAndLogin } from './helpers/registerLearner'

const stem = '入栈顺序为 1,2,3,4,5，下列哪个是可能的出栈序列？'

async function seededStackQuestionId(page: Page): Promise<number> {
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
      if (found) return found.id
      if (pageNum >= result.data.pages) break
    }
    throw new Error('出栈序列题种子不存在')
  }, stem)
}

async function openSeededQuestion(page: Page, label: string) {
  await createLearnerAndLogin(page.context().browser()!, page, label)
  const id = await seededStackQuestionId(page)
  await page.goto(`/questions/${id}`)
  await expect(page.getByText(stem, { exact: true })).toBeVisible()
}

test('出栈序列答错后依次理解误区、演示、正解并独立作答变式', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await openSeededQuestion(page, 'understanding-wrong')
  await page.locator('.answer-step input[value="B"]').check({ force: true })
  await page.locator('.answer-step').getByRole('button', { name: '提交' }).click()
  await expect(page.getByRole('heading', { name: '以为能越过 2 直接弹出 1' })).toBeVisible()
  await expect(page.getByText('用你选的 3,1,2,5,4 走一遍')).toBeVisible()
  await expect(page.locator('.viz').first()).toBeVisible()
  await page.getByRole('button', { name: '看正解' }).click()
  await expect(page.getByText('正确答案', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('正确选项 3,2,1,5,4：每一步都取栈顶')).toBeVisible()
  await expect(page.getByRole('heading', { name: '换一个条件，再做一次' })).toBeVisible()
  const variant = page.locator('article').filter({ hasText: '哪个不可能' })
  await variant.locator('input[value="A"]').check({ force: true })
  await variant.getByRole('button', { name: '提交' }).click()
  await expect(page.getByText('这次还不对，正确答案是 D。')).toBeVisible()
  await expect(page.getByRole('heading', { name: '这道变式的思路' })).toBeVisible()
})

test('出栈序列选对后直接开放正解和变式', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await openSeededQuestion(page, 'understanding-right')
  await page.locator('.answer-step input[value="A"]').check({ force: true })
  await page.locator('.answer-step').getByRole('button', { name: '提交' }).click()
  await expect(page.getByText('答对了。下面对一遍完整思路。')).toBeVisible()
  await expect(page.getByRole('heading', { name: '对一遍思路' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '换一个条件，再做一次' })).toBeVisible()
  await expect(page.getByRole('button', { name: '看正解' })).toHaveCount(0)
})
