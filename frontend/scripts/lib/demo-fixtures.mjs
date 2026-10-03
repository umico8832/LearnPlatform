/**
 * Demo fixture setup is intentionally restricted to local, isolated environments.
 * Callers own browser lifecycle and must not point these write operations at shared services.
 */

function requireSuccess(result, method, path) {
  if (result.status < 200 || result.status >= 300 || result.envelope?.code !== 0) {
    throw new Error(`${result.status} ${method} ${path}`)
  }
  return result.envelope.data
}

export async function api(page, path, method = 'GET', body) {
  const result = await page.evaluate(
    async ({ path, method, body }) => {
      const response = await fetch(`/api${path}`, {
        method,
        headers: {
          ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
          Authorization: `Bearer ${localStorage.getItem('learn_platform_token')}`,
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      })
      let envelope = null
      try {
        envelope = await response.json()
      } catch {
        // The caller only needs an actionable status and path when a proxy fails before JSON is available.
      }
      return { status: response.status, envelope }
    },
    { path, method, body },
  )
  return requireSuccess(result, method, path)
}

export async function login(page, baseURL, user, admin = false) {
  await page.goto(`${baseURL}${admin ? '/admin/login' : '/login'}`)
  await page.getByPlaceholder('请输入用户名或邮箱').fill(user.username)
  await page.getByPlaceholder('请输入密码').fill(user.password)
  await page.getByRole('button', { name: admin ? '登录管理系统' : '登录', exact: true }).click()
  await page.waitForURL(
    (url) => (admin ? ['/admin', '/admin/'].includes(url.pathname) : url.pathname === '/my-courses'),
    { timeout: 15_000 },
  )
}

function flattenKnowledgePoints(nodes) {
  return nodes.flatMap((node) => [node, ...flattenKnowledgePoints(node.children ?? [])])
}

function dateSuffix() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}

function fixtureQuestions(courseId, knowledgePointId) {
  return [
    {
      content: '依次将 A、B、C 压入空栈后执行一次出栈，得到的元素是？',
      answer: 'C',
      wrongAnswer: 'B',
      analysis: '栈遵循后进先出原则，最后压入的 C 最先出栈。',
      options: ['A', 'B', 'C', '无法确定'],
    },
    {
      content: '空队列依次入队 1、2、3 后执行一次出队，队首元素变为？',
      answer: '2',
      wrongAnswer: '3',
      analysis: '队列遵循先进先出原则，1 出队后，2 成为新的队首。',
      options: ['1', '2', '3', '队列为空'],
    },
    {
      content: '在长度为 n 的顺序表第 i 个位置插入元素时，为保持原有元素顺序，应先怎样移动元素？',
      answer: '从表尾开始依次后移到第 i 个位置',
      wrongAnswer: '从第 i 个位置开始依次前移',
      analysis: '从表尾向前后移可以避免覆盖尚未移动的元素，为新元素腾出第 i 个位置。',
      options: ['从表尾开始依次后移到第 i 个位置', '从第 i 个位置开始依次前移', '只移动第 i 个元素', '无需移动元素'],
    },
  ].map((question) => ({
    ...question,
    courseId,
    knowledgePointIds: [knowledgePointId],
    questionType: 'SINGLE_CHOICE',
    difficulty: 1,
    score: 5,
  }))
}

export async function prepareDemoFixtures(browser, { learnerURL, adminURL }) {
  const suffix = `${Date.now()}${Math.floor(Math.random() * 1_000_000)}`
  const user = {
    username: `demolearner${suffix}`.slice(0, 50),
    password: `Demo!${suffix}Learn`,
  }
  const adminContext = await browser.newContext()
  try {
    const admin = await adminContext.newPage()
    await login(admin, adminURL, { username: 'admin', password: 'admin123' }, true)

    const createdUser = await api(admin, '/admin/users', 'POST', {
      ...user,
      nickname: '课程学习者',
      role: 'USER',
    })
    const coursePage = await api(admin, '/admin/courses?pageNum=1&pageSize=100')
    const course = (coursePage.records ?? []).find((item) => /408\s*数据结构/.test(item.name ?? item.title ?? ''))
    if (!course) throw new Error('404 GET /admin/courses')

    const tree = await api(admin, `/knowledge-points/tree/${course.id}`)
    const knowledgePoint = flattenKnowledgePoints(tree).find((item) => /栈|队列|顺序表|线性表/.test(item.name ?? ''))
    if (!knowledgePoint) throw new Error(`404 GET /knowledge-points/tree/${course.id}`)

    const questions = []
    for (const question of fixtureQuestions(course.id, knowledgePoint.id)) {
      const created = await api(admin, '/admin/questions', 'POST', {
        content: question.content,
        questionType: question.questionType,
        courseId: question.courseId,
        difficulty: question.difficulty,
        score: question.score,
        analysis: question.analysis,
        knowledgePointIds: question.knowledgePointIds,
        options: question.options.map((content, optionIndex) => ({
          content,
          optionLabel: 'ABCD'[optionIndex],
          isCorrect: content === question.answer ? 1 : 0,
          sortOrder: optionIndex + 1,
        })),
      })
      questions.push({
        id: created.id,
        answer: 'ABCD'[question.options.indexOf(question.answer)],
        wrongAnswer: 'ABCD'[question.options.indexOf(question.wrongAnswer)],
        content: question.content,
      })
    }

    const paperTitle = `数据结构 · 基础回顾（${dateSuffix()} · ${suffix.slice(-4)}）`
    const createdPaper = await api(admin, '/admin/exam-papers', 'POST', {
      title: paperTitle,
      description: '围绕栈、队列与顺序表的三题基础回顾。',
      courseId: course.id,
      duration: 15,
      paperType: 'PRACTICE',
      questions: questions.map((question, index) => ({ questionId: question.id, sortOrder: index + 1, score: 5 })),
    })
    await api(admin, `/admin/exam-papers/${createdPaper.id}/publish`, 'POST')

    return {
      user: { ...user, userId: createdUser.id },
      course,
      knowledgePointId: knowledgePoint.id,
      questions,
      paper: { id: createdPaper.id, title: paperTitle, totalScore: 15 },
      learnerURL,
    }
  } finally {
    await adminContext.close()
  }
}
