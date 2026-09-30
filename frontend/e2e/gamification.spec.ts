import { expect, test, type Browser, type Page, type TestInfo } from '@playwright/test'
import { writeFile } from 'node:fs/promises'
import path from 'node:path'

const desktop = { width: 1440, height: 900 }
const courseId = 1
const knowledgePointId = 2

type ApiEnvelope<T> = { code: number; message?: string; data: T }
type FixtureQuestion = { id: number; answer: string }
type Fixture = { questions: FixtureQuestion[]; paperId: number; paperTitle: string }
type GamificationSummary = {
  currentCombo: number
  todayAnsweredCount: number
  zoneId: string
  totalXp: number
  level: number
}
type Reward = { awardedXp: number; levelAfter: number; summary: GamificationSummary }
const showcaseDirectory = process.env.GAMIFICATION_SHOWCASE_DIR

function showcasePath(testInfo: TestInfo, name: string) {
  return showcaseDirectory ? path.join(showcaseDirectory, name) : testInfo.outputPath(name)
}

function dateInShanghai() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts()
  const get = (type: Intl.DateTimeFormatPartTypes) => parts.find((part) => part.type === type)?.value
  return `${get('year')}-${get('month')}-${get('day')}`
}

async function api<T>(page: Page, path: string, method = 'GET', body?: unknown): Promise<T> {
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
      return (await response.json()) as ApiEnvelope<unknown>
    },
    { path, method, body },
  )
  expect(result.code, `${method} ${path}: ${result.message ?? 'unknown error'}`).toBe(0)
  return result.data as T
}

async function loginAs(page: Page, username: string, password: string) {
  await page.setViewportSize(desktop)
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名或邮箱').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/my-courses$/, { timeout: 15_000 })
}

async function loginAsLearner(page: Page) {
  await loginAs(page, 'testuser', 'test123')
}

async function ensureLearnerCourse(page: Page) {
  await api(page, `/my-courses/${courseId}`, 'POST')
}

async function createFixture(browser: Browser, learner: Page, name: string, count = 4): Promise<Fixture> {
  const adminContext = await browser.newContext()
  try {
    const admin = await adminContext.newPage()
    await admin.goto(`${new URL(learner.url()).origin}/admin/login`)
    await admin.getByPlaceholder('请输入用户名或邮箱').fill('admin')
    await admin.getByPlaceholder('请输入密码').fill('admin123')
    await admin.getByRole('button', { name: '登录管理系统' }).click()
    await expect(admin).not.toHaveURL(/\/login/, { timeout: 15_000 })

    const questions: FixtureQuestion[] = []
    for (let index = 0; index < count; index += 1) {
      const created = await api<{ id: number }>(admin, '/admin/questions', 'POST', {
        content: `E2E_GAMIFICATION_${name}_${index + 1}：选择正确选项。`,
        questionType: 'SINGLE_CHOICE',
        courseId,
        difficulty: 1,
        score: 1,
        analysis: '这是隔离 E2E 题目，用于验证真实判分和奖励闭环。',
        knowledgePointIds: [knowledgePointId],
        options: ['正确答案', '干扰项 B', '干扰项 C', '干扰项 D'].map((content, optionIndex) => ({
          content,
          optionLabel: 'ABCD'[optionIndex],
          isCorrect: optionIndex === 0 ? 1 : 0,
          sortOrder: optionIndex + 1,
        })),
      })
      // 管理端详情是测试专用的受控答案来源；学习者接口不得返回 isCorrect。
      const detail = await api<{ options: { optionLabel: string; isCorrect: number }[] }>(
        admin,
        `/admin/questions/${created.id}`,
      )
      const answer = detail.options.find((option) => option.isCorrect === 1)?.optionLabel
      expect(answer, `管理员题目详情缺少正确选项：${created.id}`).toBeTruthy()
      questions.push({ id: created.id, answer: answer! })
    }

    const paperTitle = `E2E 游戏化考试 ${name}`
    const paper = await api<{ id: number }>(admin, '/admin/exam-papers', 'POST', {
      title: paperTitle,
      description: '隔离 E2E 真实考试奖励验证。',
      courseId,
      duration: 30,
      paperType: 'PRACTICE',
      questions: questions.map((question, index) => ({ questionId: question.id, sortOrder: index + 1, score: 1 })),
    })
    await api<void>(admin, `/admin/exam-papers/${paper.id}/publish`, 'POST')
    return { questions, paperId: paper.id, paperTitle }
  } finally {
    await adminContext.close()
  }
}

async function createFreshLearner(browser: Browser, learner: Page) {
  const suffix = `${Date.now()}${Math.floor(Math.random() * 10_000)}`
  const username = `e2egame${suffix}`
  const password = 'E2eGamification!123'
  await learner.goto('/login')
  const origin = new URL(learner.url()).origin
  const adminContext = await browser.newContext()
  try {
    const admin = await adminContext.newPage()
    await admin.goto(`${origin}/admin/login`)
    await admin.getByPlaceholder('请输入用户名或邮箱').fill('admin')
    await admin.getByPlaceholder('请输入密码').fill('admin123')
    await admin.getByRole('button', { name: '登录管理系统' }).click()
    await expect(admin).not.toHaveURL(/\/login/, { timeout: 15_000 })
    await api<{ id: number }>(admin, '/admin/users', 'POST', {
      username,
      password,
      nickname: 'E2E 零状态学习者',
      role: 'USER',
    })
  } finally {
    await adminContext.close()
  }
  await loginAs(learner, username, password)
  return { username, password }
}

async function loadFavoriteQuestions(page: Page, questions: FixtureQuestion[]) {
  const learner = await api<{ id: number }>(page, '/auth/me')
  const questionData: unknown[] = []
  for (const question of questions) {
    await api<void>(page, `/favorites/${question.id}`, 'POST')
    const selected = await api<unknown[]>(page, `/practice/favorites?questionId=${question.id}`)
    expect(selected).toHaveLength(1)
    expect(selected[0]).not.toHaveProperty('analysis')
    expect(selected[0]).not.toHaveProperty('isCorrect')
    questionData.push(selected[0])
  }
  await page.evaluate(
    ({ userId, questionData }) => {
      sessionStorage.setItem('practice_questions', JSON.stringify(questionData))
      sessionStorage.setItem('practice_mode', 'favorite')
      sessionStorage.setItem('practice_user_id', String(userId))
    },
    { userId: learner.id, questionData },
  )
}

async function createPracticeCapturePage(browser: Browser, source: Page, testInfo: TestInfo) {
  const storageState = await source.context().storageState()
  const practiceSession = await source.evaluate(() =>
    ['practice_questions', 'practice_mode', 'practice_user_id'].map((key) => [key, sessionStorage.getItem(key)]),
  )
  const context = await browser.newContext({
    storageState,
    viewport: desktop,
    recordVideo: { dir: showcaseDirectory ?? testInfo.outputPath('practice-video'), size: desktop },
  })
  const page = await context.newPage()
  await page.goto('/')
  await page.evaluate((entries) => {
    entries.forEach(([key, value]) => {
      if (value !== null) sessionStorage.setItem(key, value)
    })
  }, practiceSession)
  await page.goto('/practice/session')
  return { context, page }
}

async function submitPracticeAnswer(page: Page, answer: string) {
  await page
    .locator('.question-card .option-item')
    .filter({ hasText: new RegExp(`^${answer}`) })
    .click()
  const response = page.waitForResponse(
    (item) => item.request().method() === 'POST' && item.url().endsWith('/api/practice/submit'),
  )
  await page.getByRole('button', { name: '提交答案', exact: true }).click()
  return (await response).json() as Promise<ApiEnvelope<{ correct: boolean; reward: Reward }>>
}

test.describe('轻游戏化学习闭环', () => {
  test('收藏的四道真实题目按 3 对 1 错结算 XP、连击、热力与持久目标', async ({ page, browser }, testInfo) => {
    test.setTimeout(90_000)
    await loginAsLearner(page)
    await ensureLearnerCourse(page)
    const fixture = await createFixture(browser, page, `practice-${Date.now()}`)
    await loadFavoriteQuestions(page, fixture.questions)
    const { context: captureContext, page: capturePage } = await createPracticeCapturePage(browser, page, testInfo)
    await expect(capturePage.getByText('收藏练习', { exact: true })).toBeVisible()

    let expectedCombo = (await api<GamificationSummary>(capturePage, '/gamification/summary')).currentCombo
    for (let index = 0; index < fixture.questions.length; index += 1) {
      const answer = index === fixture.questions.length - 1 ? 'B' : fixture.questions[index].answer
      const result = await submitPracticeAnswer(capturePage, answer)
      expect(result.data.correct).toBe(index < fixture.questions.length - 1)
      expectedCombo = result.data.correct ? expectedCombo + 1 : 0
      expect(result.data.reward.summary.currentCombo).toBe(expectedCombo)
      await expect(capturePage.getByTestId('gamification-xp-float')).toContainText('+')
      await expect(capturePage.getByTestId('gamification-combo')).toContainText(`连续答对 ${expectedCombo}`)
      if (index === 0) await expect(capturePage.getByText('成就已解锁', { exact: true })).toBeVisible()
      if (index === 0) {
        await expect(capturePage.locator('.el-dialog')).toBeVisible()
        await capturePage.screenshot({
          path: showcasePath(testInfo, 'practice-correct-reward.png'),
          fullPage: true,
          animations: 'disabled',
        })
      }
      if (index === fixture.questions.length - 1)
        await capturePage.screenshot({ path: showcasePath(testInfo, 'practice-wrong-feedback.png'), fullPage: true })
      await capturePage.getByRole('button', { name: index === 3 ? '查看结果' : '下一题', exact: true }).click()
    }

    const summary = capturePage.locator('.gamification-summary')
    await expect(summary).toBeVisible()
    await expect(summary).toContainText('获得经验')
    await expect(summary).toContainText('本组期间最高连续答对')
    await expect(summary.locator('.gamification-summary__rate')).toHaveAttribute('aria-label', '正确率 75%')
    await expect(capturePage.locator('canvas')).toHaveCount(0)
    await capturePage.screenshot({ path: showcasePath(testInfo, 'practice-summary.png'), fullPage: true })
    await capturePage.setViewportSize({ width: 1280, height: 900 })
    expect(
      await capturePage.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth),
    ).toBe(true)

    await capturePage.setViewportSize(desktop)
    await capturePage.goto('/profile')
    const profile = capturePage.getByTestId('gamification-profile')
    await expect(profile).toBeVisible()
    await expect(capturePage.getByTestId('gamification-achievements')).toContainText('成就')
    await expect(capturePage.getByTestId('gamification-heatmap')).toBeVisible()
    const profileSummary = await api<GamificationSummary>(capturePage, '/gamification/summary')
    expect(profileSummary.zoneId).toBe('Asia/Shanghai')
    expect(profileSummary.todayAnsweredCount).toBeGreaterThan(0)
    const shanghaiToday = dateInShanghai()
    const todayHeatmap = capturePage.getByRole('button', {
      name: new RegExp(`^${shanghaiToday}：${profileSummary.todayAnsweredCount} 题，`),
    })
    await expect(todayHeatmap).toHaveAttribute('data-active', 'true')
    await profile.getByTestId('gamification-daily-goal').locator('input').fill('7')
    await profile.getByRole('button', { name: '保存目标', exact: true }).click()
    await capturePage.reload()
    await expect(capturePage.getByTestId('gamification-daily-goal').locator('input')).toHaveValue('7')
    await expect(capturePage.getByTestId('gamification-heatmap').locator('[data-active="true"]')).not.toHaveCount(0)
    await capturePage.screenshot({ path: showcasePath(testInfo, 'profile-gamification.png'), fullPage: true })
    await capturePage.setViewportSize({ width: 1280, height: 900 })
    expect(
      await capturePage.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth),
    ).toBe(true)
    const video = capturePage.video()
    await captureContext.close()
    if (video) await video.saveAs(showcasePath(testInfo, 'gamification-practice-loop.webm'))
  })

  test('减少动态时仍保留真实判分信息且不渲染彩纸画布', async ({ page, browser }) => {
    test.setTimeout(90_000)
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await loginAsLearner(page)
    await ensureLearnerCourse(page)
    const fixture = await createFixture(browser, page, `reduced-motion-${Date.now()}`)
    await loadFavoriteQuestions(page, [fixture.questions[0]])
    await page.goto('/practice/session')
    const result = await submitPracticeAnswer(page, fixture.questions[0].answer)
    expect(result.data.correct).toBe(true)
    await expect(page.getByText('答对了！', { exact: true })).toBeVisible()
    await expect(page.getByTestId('gamification-xp-float')).toContainText('+')
    await expect(page.locator('canvas')).toHaveCount(0)
  })

  test('复习提交使用服务端 correct 字段，并以真实判分展示奖励', async ({ page, browser }) => {
    test.setTimeout(90_000)
    await loginAsLearner(page)
    await ensureLearnerCourse(page)
    const fixture = await createFixture(browser, page, `review-${Date.now()}`)
    const question = fixture.questions[0]
    await api<void>(page, `/review/add/${question.id}`, 'POST')
    await page.goto(`/review?questionId=${question.id}`)
    // 带 questionId 的路由会在 ReviewView.onMounted 自动 startReview；再次点击会并发
    // loadDueCards，并由较晚的 start() 重置刚填入的 userAnswer。
    const answerInput = page.getByPlaceholder('输入你的答案...')
    await expect(answerInput).toBeVisible()
    const response = page.waitForResponse(
      (item) => item.request().method() === 'POST' && item.url().endsWith('/api/review/submit'),
    )
    const submit = page.getByRole('button', { name: '提交答案', exact: true })
    await answerInput.fill(question.answer)
    await expect(answerInput).toHaveValue(question.answer)
    await expect(submit).toBeEnabled()
    await submit.click()
    const submitted = (await response).json() as Promise<ApiEnvelope<{ correct: boolean; reward: Reward }>>
    expect((await submitted).data.correct).toBe(true)
    await expect(page.getByText('回答正确！', { exact: true })).toBeVisible()
    await expect(page.getByTestId('gamification-combo')).toContainText('连续答对')
  })

  test('考试作答全程静默，交卷后的结果页展示 submissionReward', async ({ page, browser }) => {
    test.setTimeout(90_000)
    await loginAsLearner(page)
    await ensureLearnerCourse(page)
    const fixture = await createFixture(browser, page, `exam-${Date.now()}`)
    await page.goto('/exams')
    const card = page.locator('.exam-card').filter({ hasText: fixture.paperTitle })
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: '考试模式', exact: true }).click()
    await expect(page).toHaveURL(/\/exams\/take\/\d+$/)

    for (let index = 0; index < fixture.questions.length; index += 1) {
      await page
        .locator('.question-area .option-item')
        .filter({ hasText: new RegExp(`^${fixture.questions[index].answer}`) })
        .click()
      await expect(page.locator('.gamification-feedback-host')).toHaveCount(0)
      await expect(page.getByTestId('gamification-xp-float')).toHaveCount(0)
      if (index < fixture.questions.length - 1) await page.getByRole('button', { name: '下一题', exact: true }).click()
    }
    await page.getByRole('button', { name: '提交试卷', exact: true }).last().click()
    await page.getByRole('dialog', { name: '提交确认' }).getByRole('button', { name: '确定', exact: true }).click()
    await expect(page).toHaveURL(/\/exams\/result\/\d+$/)
    await expect(page.getByTestId('gamification-exam-reward')).toContainText('本次学习奖励')
  })
})

test.describe('轻游戏化追加验收', () => {
  test('真实原创题累计跨越 Lv.2，庆祝可关闭并继续作答', async ({ page, browser }, testInfo) => {
    test.setTimeout(120_000)
    await createFreshLearner(browser, page)
    await ensureLearnerCourse(page)
    const fixture = await createFixture(browser, page, `level-${Date.now()}`, 10)
    await loadFavoriteQuestions(page, fixture.questions)
    const { context: captureContext, page: capturePage } = await createPracticeCapturePage(browser, page, testInfo)
    let levelReward: Reward | undefined
    try {
      for (let index = 0; index < fixture.questions.length; index += 1) {
        const result = await submitPracticeAnswer(capturePage, fixture.questions[index].answer)
        expect(result.data.correct).toBe(true)
        if (result.data.reward.levelAfter >= 2) levelReward = result.data.reward
        const achievement = capturePage.locator('.gamification-achievement')
        if (!result.data.reward.leveledUp && (await achievement.isVisible().catch(() => false))) {
          await capturePage.keyboard.press('Escape')
          await expect(achievement).toHaveCount(0)
        }
        await capturePage
          .getByRole('button', { name: index === fixture.questions.length - 1 ? '查看结果' : '下一题' })
          .click()
      }
      expect(levelReward, '真实练习奖励应跨越 Lv.2').toBeTruthy()
      expect(levelReward!.summary.totalXp).toBeGreaterThanOrEqual(100)
      await expect(capturePage.locator('.gamification-level')).toBeVisible({ timeout: 15_000 })
      const frames = await capturePage.evaluate(
        () =>
          new Promise<number[]>((resolve) => {
            const samples: number[] = []
            const next = (time: number) => {
              samples.push(time)
              if (samples.length === 60) resolve(samples)
              else requestAnimationFrame(next)
            }
            requestAnimationFrame(next)
          }),
      )
      expect(frames).toHaveLength(60)
      expect(frames.slice(1).every((time, index) => time > frames[index])).toBe(true)
      const intervals = frames
        .slice(1)
        .map((time, index) => time - frames[index])
        .sort((a, b) => a - b)
      await writeFile(
        showcasePath(testInfo, 'level-up-raf.json'),
        `${JSON.stringify(
          {
            samples: frames.length,
            p95Ms: intervals[Math.floor(intervals.length * 0.95)],
            maxMs: intervals.at(-1),
          },
          null,
          2,
        )}\n`,
      )
      const levelHeadingColors = await capturePage.evaluate(() => {
        const heading = document.querySelector('.gamification-level h2')
        if (!(heading instanceof HTMLElement)) return null
        const probe = document.createElement('span')
        probe.style.color = 'var(--lp-on-primary)'
        document.body.append(probe)
        const expected = getComputedStyle(probe).color
        probe.remove()
        return { actual: getComputedStyle(heading).color, expected }
      })
      expect(levelHeadingColors).not.toBeNull()
      expect(levelHeadingColors!.actual).toBe(levelHeadingColors!.expected)
      await capturePage.screenshot({
        path: showcasePath(testInfo, 'level-up-celebration.png'),
        fullPage: true,
        animations: 'disabled',
      })
      await capturePage.waitForTimeout(5_200)
      await capturePage.getByRole('button', { name: '收下这份鼓励', exact: true }).click()
      await expect(capturePage.locator('.gamification-level')).toHaveCount(0)
      const queuedAchievement = capturePage.locator('.gamification-achievement')
      for (let dismissals = 0; dismissals < 12; dismissals += 1) {
        await queuedAchievement.waitFor({ state: 'visible', timeout: 500 }).catch(() => undefined)
        if (!(await queuedAchievement.isVisible().catch(() => false))) break
        await capturePage.keyboard.press('Escape')
        await capturePage.waitForTimeout(250)
      }
      await expect(queuedAchievement).toBeHidden()
      await expect(capturePage.locator('.gamification-summary')).toBeVisible()
      await capturePage.goto('/profile')
      const profile = capturePage.getByTestId('gamification-profile')
      await expect(profile.getByTestId('gamification-achievements')).toBeVisible()
      await expect(profile).toContainText('累计 100 经验')
      await capturePage.screenshot({
        path: showcasePath(testInfo, 'profile-gamification-100xp.png'),
        fullPage: true,
        animations: 'disabled',
      })
    } finally {
      const video = capturePage.video()
      await captureContext.close()
      if (video) await video.saveAs(showcasePath(testInfo, 'gamification-level-up.webm'))
    }
  })

  test('重复提交只发出一次，Profile 失败后重试，并呈现新账号零状态', async ({ page, browser }, testInfo) => {
    test.setTimeout(120_000)
    await createFreshLearner(browser, page)
    await ensureLearnerCourse(page)
    await page.goto('/profile')
    const profile = page.getByTestId('gamification-profile')
    await expect(profile).toContainText('累计 0 经验')
    await expect(profile.getByTestId('gamification-heatmap')).toContainText('完成一次作答，点亮第一个学习日。')
    await page.screenshot({
      path: showcasePath(testInfo, 'profile-zero-account.png'),
      fullPage: true,
      animations: 'disabled',
    })

    let achievementRequests = 0
    await page.route('**/api/gamification/achievements', async (route) => {
      achievementRequests += 1
      if (achievementRequests === 1) await route.abort('failed')
      else await route.continue()
    })
    await page.reload()
    await expect(profile).toContainText('成就与学习日历暂时无法加载')
    await profile.getByRole('button', { name: '重新加载', exact: true }).click()
    await expect(profile.getByTestId('gamification-achievements')).toBeVisible()
    expect(achievementRequests).toBeGreaterThanOrEqual(2)
    await page.screenshot({
      path: showcasePath(testInfo, 'profile-gamification-final.png'),
      fullPage: true,
      animations: 'disabled',
    })
    await page.unroute('**/api/gamification/achievements')

    for (const [path, heading] of [
      ['/my-courses', '我的课程'],
      [`/my-courses/${courseId}`, 'Java 基础'],
      ['/learning-diagnosis', '学习诊断'],
    ]) {
      await page.goto(path)
      if (path === '/learning-diagnosis') await expect(page.locator('.page-title')).toContainText(heading)
      else await expect(page.getByRole('main').getByText(heading, { exact: true }).first()).toBeVisible()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth),
      ).toBe(true)
    }

    const fixture = await createFixture(browser, page, `repeat-${Date.now()}`, 1)
    await loadFavoriteQuestions(page, fixture.questions)
    await page.goto('/practice/session')
    await page.locator('.question-card .option-item').filter({ hasText: /^A/ }).click()
    let submitRequests = 0
    page.on('request', (request) => {
      if (request.method() === 'POST' && request.url().endsWith('/api/practice/submit')) submitRequests += 1
    })
    const submit = page.getByRole('button', { name: '提交答案', exact: true })
    await submit.evaluate((button: HTMLButtonElement) => {
      button.click()
      button.click()
    })
    await expect(page.getByText('答对了！', { exact: true })).toBeVisible()
    await expect.poll(() => submitRequests, { timeout: 5_000 }).toBe(1)
    await page.screenshot({
      path: showcasePath(testInfo, 'practice-repeat-submit.png'),
      fullPage: true,
      animations: 'disabled',
    })
  })

  test('答错后在复习答对，错题列表会移除该真实题目', async ({ page, browser }) => {
    test.setTimeout(90_000)
    await loginAsLearner(page)
    await ensureLearnerCourse(page)
    const fixture = await createFixture(browser, page, `wrong-review-${Date.now()}`, 1)
    const question = fixture.questions[0]
    await loadFavoriteQuestions(page, [question])
    await page.goto('/practice/session')
    const wrong = await submitPracticeAnswer(page, 'B')
    expect(wrong.data.correct).toBe(false)
    await page.getByRole('button', { name: '查看结果', exact: true }).click()
    await page.goto(`/wrong-questions?questionId=${question.id}`)
    await expect(page.locator('.wrong-card')).toHaveCount(1)
    await expect(page.locator('.wrong-card')).toContainText(`E2E_GAMIFICATION_wrong-review-`)

    await api<void>(page, `/review/add/${question.id}`, 'POST')
    await page.goto(`/review?questionId=${question.id}`)
    const answer = page.getByPlaceholder('输入你的答案...')
    await expect(answer).toBeVisible()
    await answer.fill(question.answer)
    const submitted = page.waitForResponse(
      (item) => item.request().method() === 'POST' && item.url().endsWith('/api/review/submit'),
    )
    await page.getByRole('button', { name: '提交答案', exact: true }).click()
    expect((await (await submitted).json()) as ApiEnvelope<{ correct: boolean }>).toMatchObject({
      data: { correct: true },
    })
    await expect(page.getByText('回答正确！', { exact: true })).toBeVisible()
    await page.goto(`/wrong-questions?questionId=${question.id}`)
    await expect(page.locator('.wrong-card')).toHaveCount(0)
    await expect(page.getByText('暂无错题', { exact: true })).toBeVisible()
  })
})
