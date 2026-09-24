import { expect, test, type Page } from '@playwright/test'

async function login(page: Page, admin = false) {
  await page.goto(admin ? '/admin/community' : '/community')
  await page.getByPlaceholder('请输入用户名或邮箱').fill(admin ? 'admin' : 'testuser')
  await page.getByPlaceholder('请输入密码').fill(admin ? 'admin123' : 'test123')
  const response = page.waitForResponse((r) => r.request().method() === 'POST' && r.url().endsWith('/api/auth/login'))
  await page.getByRole('button', { name: admin ? '登录管理系统' : '登录', exact: true }).click()
  expect((await (await response).json()).code).toBe(0)
  await expect(page).toHaveURL(
    (url) => url.pathname === (admin ? '/admin/community' : '/community') || (!admin && url.pathname === '/my-courses'),
  )
  if (!admin) await page.goto('/community')
}
async function selectSubject(page: Page, dialog: ReturnType<Page['getByRole']>) {
  await dialog.getByRole('combobox').nth(1).click()
  await page.getByRole('option', { name: '408 计算机学科专业基础', exact: true }).click()
  await dialog.getByRole('combobox', { name: '科目', exact: true }).press('ArrowDown')
  await page.getByRole('option', { name: '数据结构', exact: true }).click()
}

test('社区共建完成真实发布、回复、点赞、附件审核与题目投稿衔接', async ({ page, browser }, testInfo) => {
  test.setTimeout(150_000)
  page.setDefaultTimeout(15_000)
  await page.setViewportSize({ width: 1440, height: 900 })
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await login(page)
  await page.getByRole('button', { name: '发起讨论', exact: true }).click()
  const composer = page.getByRole('dialog', { name: '发起讨论' })
  await selectSubject(page, composer)
  await composer.getByPlaceholder('用一句话说明你想讨论或分享的内容').fill('原创测试：如何理解栈的后进先出？')
  await composer
    .getByPlaceholder('描述问题、已有思路或资料内容')
    .fill('我的理解是最后放入的元素最先取出。<img src=x onerror=alert(1)>')
  await composer.getByRole('button', { name: '发布讨论', exact: true }).click()
  await expect(page.getByRole('heading', { name: '原创测试：如何理解栈的后进先出？' })).toBeVisible()
  expect(await page.locator('.community-body img').count()).toBe(0)
  await page.getByRole('button', { name: '赞同 · 0', exact: true }).click()
  await expect(page.getByRole('button', { name: '已赞同 · 1', exact: true })).toBeVisible()
  await page.getByRole('textbox', { name: '讨论内容' }).fill('可以用叠放的书本来理解。')
  await page.getByRole('button', { name: '发表回复' }).click()
  await expect(page.getByText('可以用叠放的书本来理解。', { exact: true })).toBeVisible()
  await page.locator('.community-comment').getByRole('button', { name: '回复', exact: true }).click()
  await page.getByRole('textbox', { name: '讨论内容' }).fill('谢谢，这个类比很清楚。')
  await page.getByRole('button', { name: '发表回复' }).click()
  await expect(page.getByText('谢谢，这个类比很清楚。', { exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: '讨论 · 2', exact: true })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('community-topic.png'), fullPage: true, animations: 'disabled' })
  await page.getByRole('link', { name: '← 返回社区' }).click()
  await expect(page.getByRole('link', { name: '原创测试：如何理解栈的后进先出？' })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('community-feed.png'), fullPage: true, animations: 'disabled' })
  await page.getByRole('button', { name: '贡献资料', exact: true }).click()
  const bank = page.getByRole('dialog', { name: '贡献学习资料' })
  await selectSubject(page, bank)
  await bank.getByPlaceholder('用一句话说明你想讨论或分享的内容').fill('原创栈练习资料（隔离测试）')
  await bank.getByPlaceholder('描述问题、已有思路或资料内容').fill('请说明栈的取出顺序。参考答案：后进先出。')
  await bank.getByPlaceholder('说明原创、出处及允许分享或使用的范围').fill('本测试原创，允许本项目测试使用。')
  await bank.getByLabel('选择题库附件').setInputFiles({
    name: 'stack-notes.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('栈遵循后进先出。原创隔离测试附件。'),
  })
  await bank.getByRole('button', { name: '提交审核' }).click()
  await expect(page.getByRole('heading', { name: '原创栈练习资料（隔离测试）' })).toBeVisible()
  await expect(page.locator('.community-thread').getByText('待审核', { exact: true })).toBeVisible()
  const bankUrl = page.url()
  const adminContext = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const admin = await adminContext.newPage()
  admin.setDefaultTimeout(15_000)
  try {
    await login(admin, true)
    await admin
      .getByRole('row')
      .filter({ hasText: '原创栈练习资料（隔离测试）' })
      .getByRole('button', { name: '查看与审核' })
      .click()
    const drawer = admin.getByRole('dialog', { name: '社区内容审核' })
    await expect(drawer.getByText('stack-notes.txt', { exact: false })).toBeVisible()
    await drawer.getByPlaceholder('记录来源、使用许可与内容核验结果').fill('已核对原创来源与测试使用许可。')
    await drawer.getByRole('button', { name: '通过并公开' }).click()
    await admin.getByRole('dialog', { name: '确认审核' }).getByRole('button', { name: '确认', exact: true }).click()
    await expect(drawer.getByRole('button', { name: '整理题目' })).toBeVisible()
    await drawer.getByRole('button', { name: '整理题目' }).click()
    const question = admin.getByRole('dialog', { name: '整理为题目投稿' })
    await question.getByRole('combobox').first().press('ArrowDown')
    await admin.getByRole('option', { name: '408 数据结构', exact: true }).click()
    await question.getByPlaceholder('核对附件后填写完整题干').fill('请简述栈的取出顺序。')
    await question.getByPlaceholder('填写参考答案').fill('后进先出。')
    await question.getByRole('button', { name: '保存题目投稿' }).click()
    await expect(drawer.getByRole('heading', { name: '题目整理进度' })).toBeVisible()
    await admin.screenshot({
      path: testInfo.outputPath('community-review.png'),
      fullPage: true,
      animations: 'disabled',
    })
    await page.goto(bankUrl)
    await expect(page.locator('.community-thread').getByText('已公开', { exact: true })).toBeVisible()
    const downloadEvent = page.waitForEvent('download')
    await page.getByRole('button', { name: '下载', exact: true }).click()
    const download = await downloadEvent
    expect(download.suggestedFilename()).toBe('stack-notes.txt')
    expect(await download.failure()).toBeNull()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    expect(errors).toEqual([])
  } finally {
    await adminContext.close().catch(() => undefined)
  }
})
