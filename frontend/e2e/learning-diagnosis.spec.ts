import { expect, test } from '@playwright/test'
import { createLearnerAndLogin } from './helpers/registerLearner'

test('新学习者看到诚实的空学习诊断', async ({ browser, page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await createLearnerAndLogin(browser, page, 'learning-diagnosis')
  await page.goto('/learning-diagnosis')
  await expect(page.getByRole('heading', { name: '还没有可诊断的学习记录' })).toBeVisible()
  await expect(page.getByText('完成一次练习或复习后，这里会基于真实记录整理下一步。')).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('learning-diagnosis-empty.png') })
})

test('隔离学习者完成练习后可获取并停止 AI 学习建议', async ({ browser, page }, testInfo) => {
  test.setTimeout(90_000)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await createLearnerAndLogin(browser, page, 'learning-diagnosis-populated')
  await page.goto('/practice')
  await page.locator('.config-card .el-select').first().click()
  await page.getByRole('option', { name: 'Java 基础' }).click()
  await page.locator('.config-card input').last().fill('5')
  await page.getByRole('button', { name: '开始刷题' }).click()
  await expect(page).toHaveURL(/\/practice\/session/)
  for (let index = 0; index < 5; index += 1) {
    await page.locator('.question-card').locator('input[type="radio"], input[type="checkbox"]').first().check()
    await page.getByRole('button', { name: '提交答案' }).click()
    await page.getByRole('button', { name: /查看结果|下一题|完成练习/ }).click()
    if (index < 4) await expect(page.locator('.question-card')).toBeVisible()
  }
  await page.goto('/learning-diagnosis')
  await expect(page.getByRole('heading', { name: '下一步从最需要处理的内容开始' })).toBeVisible()
  await page.getByRole('button', { name: '获取 AI 建议' }).click()
  await expect(page.getByRole('button', { name: '停止' })).toBeVisible()
  await page.getByRole('button', { name: '停止' }).click()
  await expect(page.getByRole('button', { name: '获取 AI 建议' })).toBeEnabled()
  await page.screenshot({ path: testInfo.outputPath('learning-diagnosis-populated.png'), fullPage: true })
})
