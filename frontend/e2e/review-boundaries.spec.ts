import { expect, test, type Page } from '@playwright/test'

async function login(page: Page, account: string, password: string) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名或邮箱').fill(account)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/my-courses$/, { timeout: 15_000 })
}

async function logout(page: Page) {
  await page.goto('/my-courses')
  await page.getByRole('button', { name: '用户菜单' }).click()
  await page.getByText('退出登录', { exact: true }).click()
  await expect(page).toHaveURL(/\/login/)
}

async function api<T>(page: Page, url: string, method = 'GET', data?: unknown): Promise<{ code: number; data: T }> {
  return page.evaluate(
    async ({ url, method, data }) => {
      const response = await fetch(`/api${url}`, {
        method,
        headers: {
          Authorization: `Bearer ${localStorage.getItem('learn_platform_token')}`,
          'Content-Type': 'application/json',
        },
        body: data === undefined ? undefined : JSON.stringify(data),
      })
      return response.json()
    },
    { url, method, data },
  )
}

test('审查修复：私有收藏练习刷新恢复，退出和切换账号后不泄露题面', async ({ page }) => {
  await login(page, 'testuser', 'test123')
  const courses = await api<{ id: number; name: string }[]>(page, '/courses/list')
  const course = courses.data.find((item) => item.name === '408 数据结构')!
  const title = `私有练习隔离 ${Date.now()}`
  const questionText = `${title}：后进先出的数据结构是？`
  const source = {
    title,
    courseId: course.id,
    duration: 60,
    sourceName: 'private-cache-regression.md',
    sourceFormat: 'MARKDOWN',
    content: `## 1. 单选题\n**题干**: ${questionText}\n**选项**:\n- A. 栈\n- B. 队列\n**答案**: A\n**解析**: 栈遵循 LIFO。\n**分值**: 2`,
  }
  const preview = await api<{ contentHash: string }>(page, '/exam/private-papers/import/preview', 'POST', source)
  expect(preview.code).toBe(0)
  const imported = await api<{ questions: { questionId: number }[] }>(
    page,
    '/exam/private-papers/import/confirm',
    'POST',
    { ...source, expectedContentHash: preview.data.contentHash, confirmed: true },
  )
  expect(imported.code).toBe(0)
  const questionId = imported.data.questions[0].questionId
  expect((await api(page, `/favorites/${questionId}`, 'POST')).code).toBe(0)
  await page.goto('/favorites')
  await page
    .getByRole('row')
    .filter({ hasText: questionText })
    .getByRole('button', { name: '练习', exact: true })
    .click()
  await expect(page).toHaveURL(/\/practice\/session$/)
  await expect(page.getByText(questionText, { exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByText(questionText, { exact: true })).toBeVisible()
  const oldCache = await page.evaluate(() =>
    Object.fromEntries(
      ['practice_questions', 'practice_mode', 'practice_user_id'].map((key) => [key, sessionStorage.getItem(key)]),
    ),
  )
  await logout(page)
  expect(await page.evaluate(() => sessionStorage.getItem('practice_questions'))).toBeNull()
  await login(page, 'admin', 'admin123')
  expect((await api(page, `/questions/${questionId}`)).code).not.toBe(0)
  await page.evaluate((cache) => {
    for (const [key, value] of Object.entries(cache)) {
      if (value !== null) sessionStorage.setItem(key, value)
    }
  }, oldCache)
  await page.goto('/practice/session')
  await expect(page).toHaveURL(/\/practice$/)
  await expect(page.getByText(questionText, { exact: true })).toHaveCount(0)
  expect(await page.evaluate(() => sessionStorage.getItem('practice_questions'))).toBeNull()
})

test('审查修复：题目归属校验、作答前答案隔离与提交后判分', async ({ page }) => {
  await login(page, 'admin', 'admin123')
  const courses = await api<{ id: number; name: string }[]>(page, '/courses/list')
  const course = courses.data.find((item) => item.name === 'Java 基础')!
  const title = `填空隔离 ${Date.now()}`
  const question = {
    courseId: course.id,
    content: `${title}：Java 虚拟机的英文缩写是？`,
    questionType: 'FILL_BLANK',
    difficulty: 1,
    score: 2,
    analysis: 'Java Virtual Machine。',
    knowledgePointIds: [],
    options: [{ content: 'JVM', optionLabel: '1', isCorrect: 1, sortOrder: 0 }],
  }
  const created = await api<{ id: number }>(page, '/admin/questions', 'POST', question)
  expect(created.code).toBe(0)
  const id = created.data.id
  expect((await api(page, `/admin/questions/${id}`, 'PUT', { ...question, courseId: 999999999 })).code).not.toBe(0)
  const otherCourse = courses.data.find((item) => item.name === '408 数据结构')!
  const points = await api<{ id: number }[]>(page, `/knowledge-points/tree/${otherCourse.id}`)
  expect(points.data.length).toBeGreaterThan(0)
  expect(
    (
      await api(page, `/admin/questions/${id}`, 'PUT', {
        ...question,
        knowledgePointIds: [points.data[0].id],
      })
    ).code,
  ).not.toBe(0)
  const unchanged = await api<{ courseId: number; knowledgePointIds: number[] }>(page, `/admin/questions/${id}`)
  expect(unchanged.data).toMatchObject({ courseId: course.id, knowledgePointIds: [] })
  const paper = await api<{ id: number }>(page, '/admin/exam-papers', 'POST', {
    title,
    courseId: course.id,
    duration: 60,
    status: 1,
    questions: [{ questionId: id, score: 2, sortOrder: 1 }],
  })
  expect(paper.code).toBe(0)
  await logout(page)
  await login(page, 'testuser', 'test123')
  const detail = await api<{ options: unknown[]; analysis?: string }>(page, `/questions/${id}`)
  expect(detail.code).toBe(0)
  expect(detail.data.options).toEqual([])
  expect(detail.data.analysis).toBeUndefined()
  const preview = await api<{ questions: { options: unknown[] }[] }>(page, `/exam/papers/${paper.data.id}`)
  expect(preview.data.questions[0].options).toEqual([])
  const result = await api<{ correct: boolean; correctAnswer: string }>(page, '/practice/submit', 'POST', {
    questionId: id,
    userAnswer: 'JVM',
    answerTime: 5,
  })
  expect(result.code).toBe(0)
  expect(result.data).toMatchObject({ correct: true, correctAnswer: 'JVM' })
})
