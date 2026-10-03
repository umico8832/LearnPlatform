import { chromium, expect } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { api, login, prepareDemoFixtures } from './lib/demo-fixtures.mjs'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const learnerURL = process.env.DEMO_BASE_URL || 'http://localhost:18000'
const adminURL = process.env.DEMO_ADMIN_BASE_URL || learnerURL
const outputDir = path.resolve(repoRoot, process.env.DEMO_SCREENSHOT_DIR || 'tmp/quiet-showcase')
const viewport = { width: 1440, height: 900 }

// This workflow creates retained demo records, so it requires an explicitly selected local isolation environment.
if (process.env.DEMO_ISOLATED !== '1') throw new Error('Set DEMO_ISOLATED=1 for the isolated demo environment.')
for (const url of [learnerURL, adminURL]) {
  if (!['localhost', '127.0.0.1', '[::1]'].includes(new URL(url).hostname)) {
    throw new Error('Demo fixtures are limited to a local isolated environment.')
  }
}

const manifest = {
  capturedAt: new Date().toISOString(),
  viewport,
  complete: false,
  environment: 'isolated local backend; retained original demo questions and learner',
  aiEvidence: 'Deterministic isolation provider: session and interface evidence, not cloud teaching quality.',
  screenshots: [],
  recordings: [],
  facts: {},
}
const issues = []
let browser
let storageState

async function saveManifest() {
  await writeFile(path.join(outputDir, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`)
}

function watchPage(page) {
  page.on('pageerror', (error) => issues.push({ kind: 'page', message: error.message }))
  page.on('response', (response) => {
    if (new URL(response.url()).pathname.startsWith('/api/') && response.status() >= 400) {
      issues.push({ kind: 'api', status: response.status(), path: new URL(response.url()).pathname })
    }
  })
}

async function capture(page, name, description) {
  await page.evaluate(() => document.fonts.ready)
  await page.mouse.move(1430, 890)
  await page.waitForTimeout(350)
  if (issues.length) throw new Error(`Capture stopped: ${JSON.stringify(issues)}`)
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1)) {
    throw new Error(`Horizontal overflow on ${new URL(page.url()).pathname}`)
  }
  const file = `${name}.png`
  await page.screenshot({ path: path.join(outputDir, file), animations: 'disabled' })
  manifest.screenshots.push({ file, description, route: new URL(page.url()).pathname })
  await saveManifest()
  console.log(`Captured ${file}`)
}

async function recordSegment(name, run) {
  const context = await browser.newContext({
    baseURL: learnerURL,
    storageState,
    viewport,
    reducedMotion: 'no-preference',
    recordVideo: { dir: path.join(outputDir, 'raw-recordings'), size: viewport },
  })
  const page = await context.newPage()
  page.setDefaultTimeout(15000)
  watchPage(page)
  const video = page.video()
  try {
    await run(page)
    await page.waitForTimeout(1200)
  } finally {
    await context.close()
  }
  const file = `${name}.webm`
  await video.saveAs(path.join(outputDir, file))
  manifest.recordings.push({ file, motion: name === '05-profile' ? 'normal and reduced' : 'normal' })
  await saveManifest()
  console.log(`Recorded ${file}`)
}

async function writeGallery() {
  const labels = {
    '02-course-and-tutor.webm': '课程空间与 Tutor 会话',
    '03-practice-loop.webm': '练习、判分与总结',
    '04-exam-and-result.webm': '考试作答与结果',
    '05-profile.webm': '个人学习记录',
  }
  const screenshots = manifest.screenshots
    .map(
      (item) =>
        `<figure><a href="${item.file}" target="_blank" rel="noopener"><img src="${item.file}" alt="${item.description}" loading="lazy"></a><figcaption>${item.description}</figcaption></figure>`,
    )
    .join('')
  const recordings = manifest.recordings
    .map(
      (item) =>
        `<figure><video controls preload="metadata" src="${item.file}"></video><figcaption>${labels[item.file]}</figcaption></figure>`,
    )
    .join('')
  await writeFile(
    path.join(outputDir, 'index.html'),
    `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>LearnPlatform · 学习体验</title><style>*{box-sizing:border-box}body{margin:0;background:#f7f7f2;color:#252722;font:16px/1.65 system-ui,sans-serif}main{max-width:1200px;margin:auto;padding:56px 24px}h1{font-size:40px;line-height:1.2;margin:0 0 16px}h2{font-size:24px;margin:48px 0 20px}p{max-width:800px;color:#62665e}a{color:#355783}a:focus-visible{outline:3px solid #355783;outline-offset:4px}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:24px}figure{margin:0;border:1px solid #dedfd5;border-radius:12px;overflow:hidden;background:white}img,video{display:block;width:100%;height:auto}figcaption{padding:16px 20px;font-size:14px}@media(max-width:760px){.grid{grid-template-columns:1fr}h1{font-size:32px}}</style><main><h1>把学习过程安静地串起来。</h1><p>LearnPlatform 的课程空间、教学、练习、考试与个人记录。画面来自隔离环境中的真实界面、接口和服务端判分；练习题为原创演示内容，数据保留本次真实作答结果。Tutor 使用确定性隔离模型，展示会话与交互，不作为云模型教学质量的证据。</p><p>1440 × 900 · <a href="manifest.json">查看记录事实</a></p><h2>短录屏</h2><section class="grid">${recordings}</section><h2>界面截图</h2><section class="grid">${screenshots}</section></main></html>`,
  )
}

async function main() {
  await mkdir(outputDir, { recursive: true })
  browser = await chromium.launch({ headless: true })
  try {
    const publicContext = await browser.newContext({ viewport, reducedMotion: 'reduce' })
    const publicPage = await publicContext.newPage()
    watchPage(publicPage)
    await publicPage.goto(learnerURL)
    await expect(publicPage.locator('#home-title')).toBeVisible()
    await capture(publicPage, '01-home', '匿名首页')
    await publicContext.close()

    const fixture = await prepareDemoFixtures(browser, { learnerURL, adminURL })
    const context = await browser.newContext({ baseURL: learnerURL, viewport, reducedMotion: 'reduce' })
    const setup = await context.newPage()
    watchPage(setup)
    await login(setup, learnerURL, fixture.user)
    await setup.goto(`/courses/${fixture.course.id}`)
    const joined = setup.waitForResponse((response) =>
      response.url().endsWith(`/my-courses/${fixture.course.id}/overview`),
    )
    await setup.getByRole('button', { name: '加入课程库', exact: true }).click()
    const overview = (await (await joined).json()).data
    const tutorKnowledgePointId = overview.tutorProgress.find((item) => item.knowledgePointId)?.knowledgePointId
    if (!tutorKnowledgePointId) throw new Error('No available Tutor lesson for this course.')
    for (const question of fixture.questions) await api(setup, `/favorites/${question.id}`, 'POST')
    storageState = await context.storageState()
    manifest.facts = {
      courseId: fixture.course.id,
      questionIds: fixture.questions.map((item) => item.id),
      paperId: fixture.paper.id,
    }
    await context.close()

    await recordSegment('02-course-and-tutor', async (page) => {
      await page.goto(`/my-courses/${fixture.course.id}`)
      await expect(page.getByRole('heading', { name: fixture.course.name, exact: true }).first()).toBeVisible()
      await capture(page, '02-course-space', '新学习者加入课程后的真实课程空间')
      await page.waitForTimeout(2000)
      await page.goto(`/my-courses/${fixture.course.id}/tutor?knowledgePointId=${tutorKnowledgePointId}`)
      await expect(page.locator('.tutor-title')).toBeVisible()
      await capture(page, '03-tutor-lesson', '课程教学步骤与受限互动课件')
      await page.waitForTimeout(2000)
      const panel = page.locator('.agent-panel')
      await panel.scrollIntoViewIfNeeded()
      await panel.getByTestId('agent-input').fill('请用一个具体例子解释本节内容。')
      await panel.getByTestId('agent-submit').click()
      await expect(panel.locator('.agent-message')).toHaveCount(2, { timeout: 30000 })
      await expect(panel).toContainText('等待你的问题', { timeout: 30000 })
      await capture(page, '04-tutor-conversation', '隔离模型返回的真实会话；仅验证协议与界面')
    })

    await recordSegment('03-practice-loop', async (page) => {
      await page.goto('/favorites')
      await expect(page.getByRole('button', { name: '练习收藏题', exact: true })).toBeEnabled()
      await page.getByRole('spinbutton', { name: '题目数' }).fill('3')
      await page.getByRole('button', { name: '练习收藏题', exact: true }).click()
      await expect(page).toHaveURL(/\/practice\/session$/)
      await expect(page.locator('.question-card')).toBeVisible()
      await capture(page, '05-practice-question', '从真实收藏题开始练习')
      const results = []
      for (let index = 0; index < fixture.questions.length; index++) {
        const content = await page.locator('.question-content').innerText()
        const question = fixture.questions.find((item) => content.includes(item.content))
        if (!question) throw new Error('Unexpected question in isolated favorites.')
        const answer = index === 1 ? question.wrongAnswer : question.answer
        await page.getByRole('radio', { name: new RegExp(`^${answer}\\.`) }).check()
        await page.waitForTimeout(650)
        const response = page.waitForResponse(
          (item) => item.request().method() === 'POST' && item.url().endsWith('/api/practice/submit'),
        )
        await page.getByRole('button', { name: '提交答案', exact: true }).click()
        const result = await (await response).json()
        if (result.code !== 0 || result.data.correct !== (index !== 1))
          throw new Error('Practice grading did not match the submitted answer.')
        results.push({
          questionId: question.id,
          correct: result.data.correct,
          awardedXp: result.data.reward?.awardedXp,
        })
        const feedback = page.getByTestId('practice-feedback')
        await expect(feedback).toBeVisible()
        await feedback.scrollIntoViewIfNeeded()
        if (index < 2)
          await capture(
            page,
            index === 0 ? '06-practice-feedback' : '07-practice-analysis',
            index === 0 ? '服务端正确判分后的原位反馈' : '错误作答后的真实答案与解析',
          )
        await page.waitForTimeout(1000)
        await page
          .getByRole('button', { name: index === fixture.questions.length - 1 ? '查看结果' : '下一题', exact: true })
          .click()
      }
      await expect(page.locator('.gamification-summary')).toBeVisible()
      await capture(page, '08-practice-summary', '本组真实作答与学习投入总结')
      manifest.facts.practice = results
    })

    await recordSegment('04-exam-and-result', async (page) => {
      await page.goto('/exams')
      await page.getByRole('textbox', { name: '按试卷名称查找' }).fill(fixture.paper.title)
      await page.getByRole('button', { name: '查找试卷' }).click()
      const card = page.locator('.exam-card').filter({ hasText: fixture.paper.title })
      await expect(card).toHaveCount(1)
      await card.getByRole('button', { name: '考试模式', exact: true }).click()
      await expect(page).toHaveURL(/\/exams\/take\/\d+$/)
      await expect(page.locator('.question-area')).toBeVisible()
      await capture(page, '09-exam', '服务端计时的安静考试作答')
      for (let index = 0; index < fixture.questions.length; index++) {
        const area = page.locator('.question-area')
        const content = await area.innerText()
        const question = fixture.questions.find((item) => content.includes(item.content))
        if (!question) throw new Error('Unexpected question in isolated paper.')
        await area.locator(`input[type=radio][value="${question.answer}"]`).check()
        await page.waitForTimeout(650)
        if (index < fixture.questions.length - 1)
          await page.getByRole('button', { name: '下一题', exact: true }).click()
      }
      await page.locator('.take-header').getByRole('button', { name: '提交试卷', exact: true }).click()
      await page
        .getByRole('dialog', { name: '提交确认' })
        .getByRole('button', { name: '提交试卷', exact: true })
        .click()
      await expect(page).toHaveURL(/\/exams\/result\/\d+$/)
      await expect(page.locator('.score-number')).toHaveText(String(fixture.paper.totalScore))
      manifest.facts.exam = {
        recordId: Number(new URL(page.url()).pathname.split('/').at(-1)),
        score: Number(await page.locator('.score-number').innerText()),
        totalScore: fixture.paper.totalScore,
      }
      await capture(page, '10-exam-result', '真实提交后的固化考试成绩')
    })

    await recordSegment('05-profile', async (page) => {
      await page.goto('/profile')
      await expect(page.getByTestId('gamification-profile')).toBeVisible()
      await capture(page, '11-profile', '本轮学习后的真实日历、目标与等级')
      await page.emulateMedia({ reducedMotion: 'reduce' })
      await page.getByTestId('gamification-profile').scrollIntoViewIfNeeded()
      await capture(page, '12-profile-reduced-motion', '减弱动效下的真实学习记录')
      const summary = await api(page, '/gamification/summary')
      manifest.facts.learning = {
        totalXp: summary.totalXp,
        todayAnsweredCount: summary.todayAnsweredCount,
        level: summary.level,
        zoneId: summary.zoneId,
      }
    })
    await writeGallery()
    manifest.complete = true
    await saveManifest()
    console.log(`Demo evidence saved to ${outputDir}`)
  } finally {
    await browser?.close()
  }
}

main().catch(async (error) => {
  await saveManifest()
  console.error(error.message)
  process.exitCode = 1
})
