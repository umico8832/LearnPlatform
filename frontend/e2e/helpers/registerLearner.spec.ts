import { expect, test } from '@playwright/test'
import { createLearnerAndLogin } from './registerLearner'

test('管理员可创建唯一学习者并通过学习端登录', async ({ browser, page }) => {
  const learner = await createLearnerAndLogin(browser, page, 'helper')
  await expect(page.getByRole('main')).toContainText('我的课程')
  expect(learner.username).toMatch(/^e2ehelper\d+$/)
})
