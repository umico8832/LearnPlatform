import { expect, test } from '@playwright/test'
import type { CourseOverviewVO, TutorCheckResultVO } from '../src/api/course'
import type { TutorAgentRunVO } from '../src/api/tutor'
import type { TutorSessionNoteVO } from '../src/api/tutorNotes'

test('Tutor 会话复盘跨会话关联真实检查，纠正和删除不会增加学习事实', async ({ page }, testInfo) => {
  test.setTimeout(90_000)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名或邮箱').fill('testuser')
  await page.getByPlaceholder('请输入密码').fill('test123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/my-courses$/, { timeout: 15_000 })
  await page.goto('/courses')
  await page
    .locator('.course-card')
    .filter({ hasText: '408 数据结构' })
    .getByRole('button', { name: '查看课程' })
    .click()
  await expect(page.getByRole('button', { name: '查看题目' })).toBeVisible()
  const overviewResponse = page.waitForResponse((response) => /\/my-courses\/\d+\/overview$/.test(response.url()))
  const join = page.getByRole('button', { name: '加入课程库' })
  await ((await join.isVisible()) ? join : page.getByRole('button', { name: '进入课程空间' })).click()
  const before: CourseOverviewVO = (await (await overviewResponse).json()).data
  const courseUrl = page.url()
  const target = before.tutorProgress.find((item) => item.title === '线性表的定义与基本操作')!
  const sessionCreated = () =>
    page.waitForResponse(
      (response) => response.request().method() === 'POST' && /\/tutor-sessions\?/.test(response.url()),
    )
  const firstSession = sessionCreated()
  await page.goto(`${courseUrl}/tutor?knowledgePointId=${target.knowledgePointId}`)
  const originalKey = (await (await firstSession).json()).data.sessionKey
  const panel = page.locator('.agent-panel')
  const notes = panel.getByTestId('tutor-session-notes')
  const editor = notes.getByRole('region', { name: '会话复盘编辑器' })
  await notes.locator('summary').click()
  await expect(notes.getByTestId('session-note-input')).toBeEnabled()
  await notes.getByTestId('session-note-input').fill('我觉得自己全都答对了')
  const firstSave = page.waitForResponse(
    (response) => response.request().method() === 'PUT' && response.url().endsWith('/note'),
  )
  await notes.getByTestId('session-note-save').click()
  const saved: TutorSessionNoteVO = (await (await firstSave).json()).data
  expect(saved.revision).toBe(1)
  expect(saved.source.checkStatus).toBe('UNANSWERED')
  expect(saved.source.checkAnsweredAt).toBeNull()

  async function ask(expected: string) {
    const response = page.waitForResponse(
      (item) => item.request().method() === 'POST' && /\/agent-runs(?:\/[^/]+\/messages)?$/.test(item.url()),
    )
    await panel.getByTestId('agent-input').fill('E2E_READ_SESSION_NOTES')
    await panel.getByTestId('agent-submit').click()
    const run: TutorAgentRunVO = (await (await response).json()).data
    expect(run.messages.at(-1)?.content).toBe(expected)
    await expect(panel).toContainText(expected)
    await expect(notes.getByTestId('session-note-input')).toBeEnabled()
  }
  await ask('当前可用复盘：我觉得自己全都答对了 / 理解检查：UNANSWERED。')

  await page.getByRole('radiogroup').locator('label').filter({ hasText: '可以有多个前驱，只要元素值不同' }).click()
  const answer = page.waitForResponse(
    (response) => response.request().method() === 'POST' && /\/tutor-sessions\/[^/]+\/check$/.test(response.url()),
  )
  await page.getByRole('button', { name: '提交检查' }).click()
  const result: TutorCheckResultVO = (await (await answer).json()).data
  expect(result.correct).toBe(false)
  await expect(editor).toContainText('已作答，待复习')

  await page.evaluate(() => sessionStorage.clear())
  const nextSession = sessionCreated()
  await page.reload()
  expect((await (await nextSession).json()).data.sessionKey).not.toBe(originalKey)
  await notes.locator('summary').click()
  await expect(notes.getByTestId('session-note-input')).toHaveValue('')
  const original = notes.locator('article').filter({ hasText: '我觉得自己全都答对了' })
  await expect(original).toContainText('已作答，待复习')
  await ask('当前可用复盘：我觉得自己全都答对了 / 理解检查：INCORRECT。')
  await original.getByRole('button', { name: '编辑此复盘' }).click()
  await expect(notes.getByTestId('session-note-input')).toHaveValue('我觉得自己全都答对了')
  await notes.getByTestId('session-note-input').fill('需要复习线性表的前驱与后继')
  const correction = page.waitForResponse(
    (response) =>
      response.request().method() === 'PUT' && response.url().endsWith(`/tutor-sessions/${originalKey}/note`),
  )
  await notes.getByTestId('session-note-save').click()
  const corrected: TutorSessionNoteVO = (await (await correction).json()).data
  expect(corrected.revision).toBe(2)
  expect(corrected.source.checkStatus).toBe('INCORRECT')
  expect(corrected.source.checkAnsweredAt).not.toBeNull()
  await ask('当前可用复盘：需要复习线性表的前驱与后继 / 理解检查：INCORRECT。')
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: testInfo.outputPath('tutor-session-notes-desktop.png'), fullPage: true })

  const deletion = page.waitForResponse(
    (response) =>
      response.request().method() === 'DELETE' &&
      response.url().endsWith(`/tutor-sessions/${originalKey}/note?revision=2`),
  )
  await notes.getByTestId('session-note-delete').click()
  const erased: TutorSessionNoteVO = (await (await deletion).json()).data
  expect(erased.revision).toBe(3)
  expect(erased.note).toBeNull()
  expect(erased.source.checkStatus).toBe('INCORRECT')
  await ask('当前没有可用的会话复盘。')
  await page.reload()
  await notes.locator('summary').click()
  await expect(notes).toContainText('还没有保存的会话复盘')

  const finalOverview = page.waitForResponse((response) => /\/my-courses\/\d+\/overview$/.test(response.url()))
  await page.goto(courseUrl)
  const after: CourseOverviewVO = (await (await finalOverview).json()).data
  expect(after.answeredCount).toBe(before.answeredCount + 1)
  expect(after.correctCount).toBe(before.correctCount)
})
