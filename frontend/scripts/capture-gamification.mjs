import { chromium } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const frontendDirectory = path.resolve(scriptDirectory, '..')
const outputDirectory = path.resolve(
  frontendDirectory,
  process.env.GAMIFICATION_SCREENSHOT_DIR || '../tmp/gamification-showcase',
)
const baseUrl = process.env.DEMO_BASE_URL || 'http://localhost:18000'
const courseId = 1
const knowledgePointId = 2

async function api(page, pathName, method = 'GET', body) {
  const result = await page.evaluate(
    async ({ pathName, method, body }) => {
      const response = await fetch(`/api${pathName}`, {
        method,
        headers: {
          ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
          Authorization: `Bearer ${localStorage.getItem('learn_platform_token')}`,
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      })
      return response.json()
    },
    { pathName, method, body },
  )
  if (result.code !== 0) throw new Error(`${method} ${pathName}: ${result.message || 'unknown error'}`)
  return result.data
}

async function login(page, username, password, admin = false) {
  await page.goto(`${baseUrl}${admin ? '/admin/login' : '/login'}`)
  await page.getByPlaceholder('请输入用户名或邮箱').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: admin ? '登录管理系统' : '登录', exact: true }).click()
  await page.waitForURL(admin ? /\/admin(\/|$)/ : /\/my-courses$/)
}

async function createShowcaseQuestions(browser) {
  const adminContext = await browser.newContext()
  try {
    const admin = await adminContext.newPage()
    await login(admin, 'admin', 'admin123', true)
    const questions = []
    const prefix = `SHOWCASE_GAMIFICATION_${Date.now()}`
    for (let index = 0; index < 4; index += 1) {
      const created = await api(admin, '/admin/questions', 'POST', {
        content: `${prefix}_${index + 1}：选择正确选项。`,
        questionType: 'SINGLE_CHOICE',
        courseId,
        difficulty: 1,
        score: 1,
        analysis: '真实接口创建的展示题目。',
        knowledgePointIds: [knowledgePointId],
        options: ['正确答案', '干扰项 B', '干扰项 C', '干扰项 D'].map((content, optionIndex) => ({
          content,
          optionLabel: 'ABCD'[optionIndex],
          isCorrect: optionIndex === 0 ? 1 : 0,
          sortOrder: optionIndex + 1,
        })),
      })
      const detail = await api(admin, `/admin/questions/${created.id}`)
      const answer = detail.options.find((option) => option.isCorrect === 1)?.optionLabel
      if (!answer) throw new Error(`题目 ${created.id} 没有正确答案`)
      questions.push({ id: created.id, answer })
    }
    return questions
  } finally {
    await adminContext.close()
  }
}

async function preparePracticeSession(page, questions) {
  const learner = await api(page, '/auth/me')
  await api(page, `/my-courses/${courseId}`, 'POST')
  const practiceQuestions = []
  for (const question of questions) {
    await api(page, `/favorites/${question.id}`, 'POST')
    const selected = await api(page, `/practice/favorites?questionId=${question.id}`)
    if (selected.length !== 1 || 'isCorrect' in selected[0]) throw new Error('练习接口数据不符合答案隔离约束')
    practiceQuestions.push(selected[0])
  }
  await page.evaluate(
    ({ userId, practiceQuestions }) => {
      sessionStorage.setItem('practice_questions', JSON.stringify(practiceQuestions))
      sessionStorage.setItem('practice_mode', 'favorite')
      sessionStorage.setItem('practice_user_id', String(userId))
    },
    { userId: learner.id, practiceQuestions },
  )
}

async function answerPracticeQuestion(page, answer) {
  await page
    .locator('.question-card .option-item')
    .filter({ hasText: new RegExp(`^${answer}`) })
    .click()
  const response = page.waitForResponse(
    (item) => item.request().method() === 'POST' && item.url().endsWith('/api/practice/submit'),
  )
  await page.getByRole('button', { name: '提交答案', exact: true }).click()
  const result = await (await response).json()
  if (result.code !== 0 || !result.data?.reward) throw new Error('练习提交没有返回真实奖励')
  return result.data
}

await mkdir(outputDirectory, { recursive: true })
const browser = await chromium.launch({ headless: true })
try {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    recordVideo: { dir: outputDirectory, size: { width: 1440, height: 900 } },
  })
  const page = await context.newPage()
  await login(page, 'testuser', 'test123')
  const questions = await createShowcaseQuestions(browser)
  await preparePracticeSession(page, questions)
  await page.goto(`${baseUrl}/practice/session`)
  for (let index = 0; index < questions.length; index += 1) {
    const result = await answerPracticeQuestion(page, index === 3 ? 'B' : questions[index].answer)
    if (index === 0)
      await page.screenshot({ path: path.join(outputDirectory, 'practice-correct-reward.png'), fullPage: true })
    if (index === 3)
      await page.screenshot({ path: path.join(outputDirectory, 'practice-wrong-feedback.png'), fullPage: true })
    const notice = page.locator('.gamification-feedback-host')
    if ((await notice.count()) > 0)
      await page.screenshot({
        path: path.join(outputDirectory, `reward-highlight-${result.reward.eventId}.png`),
        fullPage: true,
      })
    await page.getByRole('button', { name: index === 3 ? '查看结果' : '下一题', exact: true }).click()
  }
  await page.locator('.gamification-summary').waitFor({ state: 'visible', timeout: 15_000 })
  await page.screenshot({ path: path.join(outputDirectory, 'practice-summary.png'), fullPage: true })
  await page.goto(`${baseUrl}/profile`)
  await page.locator('[data-testid="gamification-profile"]').waitFor({ state: 'visible', timeout: 15_000 })
  await page.screenshot({ path: path.join(outputDirectory, 'profile-gamification.png'), fullPage: true })
  const video = page.video()
  await page.close()
  if (video) await video.saveAs(path.join(outputDirectory, 'gamification-practice-loop.webm'))
  await context.close()
  console.log(`Gamification screenshots and recording written to ${outputDirectory}`)
} finally {
  await browser.close()
}
