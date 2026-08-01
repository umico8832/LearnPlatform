import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '..', '..')

const baseURL = process.env.DEMO_BASE_URL || process.env.E2E_BASE_URL || 'http://localhost:18000'
const outputDir = path.resolve(repoRoot, process.env.DEMO_SCREENSHOT_DIR || 'docs/showcase/screenshots')

const users = {
  learner: { username: 'testuser', password: 'test123' },
  admin: { username: 'admin', password: 'admin123' },
}

const learnerPages = [
  ['01-home-workbench', '/', '今日学习工作台'],
  ['02-course-center', '/courses', '选择今天要推进的课程'],
  ['03-question-bank', '/questions', '题库浏览'],
  ['04-practice-center', '/practice', '刷题练习'],
  ['05-wrong-questions', '/wrong-questions', '错题本'],
  ['06-review-plan', '/review', '智能复习'],
  ['07-exam-center', '/exams', '考试中心'],
]

const adminPages = [
  ['08-admin-dashboard', '/admin', '平台数据总览'],
  ['09-admin-questions', '/admin/questions', '题目管理'],
  ['10-admin-submissions', '/admin/submissions', '投稿管理'],
  ['11-admin-ai-usage', '/admin/ai-usage', 'AI 调用分析'],
]

async function loginAs(page, user) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名或邮箱').fill(user.username)
  await page.getByPlaceholder('请输入密码').fill(user.password)
  const loginButton = page.getByRole('button', { name: '登录' })
  await loginButton.waitFor({ state: 'visible' })
  await page.waitForFunction(() => !document.querySelector('button.auth-primary')?.disabled)
  await loginButton.click()
  await page.waitForURL('**/')
}

async function capturePage(page, browserName, [name, url, heading]) {
  const failedApiResponses = []
  const responseHandler = (response) => {
    const responseUrl = response.url()
    if (responseUrl.includes('/api/') && response.status() >= 400) {
      failedApiResponses.push(`${response.status()} ${responseUrl}`)
    }
  }

  page.on('response', responseHandler)
  await page.goto(url)
  await page.getByRole('main').getByText(heading, { exact: true }).first().waitFor({ timeout: 15000 })
  await page.waitForLoadState('networkidle').catch(() => undefined)
  await page.waitForTimeout(500)
  page.off('response', responseHandler)

  if (failedApiResponses.length > 0) {
    throw new Error(`API request failed while capturing ${url}:\n${failedApiResponses.join('\n')}`)
  }

  const filename = `${browserName}-${name}.png`
  await page.screenshot({
    path: path.join(outputDir, filename),
    fullPage: true,
    animations: 'disabled',
  })
  console.log(`saved ${filename}`)
}

async function captureGroup(browser, browserName, pages, user) {
  const context = await browser.newContext({
    baseURL,
    viewport: { width: 1440, height: 980 },
    deviceScaleFactor: 1,
  })
  const page = await context.newPage()
  await loginAs(page, user)

  for (const item of pages) {
    await capturePage(page, browserName, item)
  }

  await context.close()
}

async function main() {
  await mkdir(outputDir, { recursive: true })

  const browser = await chromium.launch()
  try {
    await captureGroup(browser, 'desktop', learnerPages, users.learner)
    await captureGroup(browser, 'desktop', adminPages, users.admin)
  } finally {
    await browser.close()
  }

  console.log(`Demo screenshots written to ${outputDir}`)
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
