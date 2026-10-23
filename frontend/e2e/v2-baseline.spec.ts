import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

async function login(page: Page, username: string) {
  const password = process.env.BATCH_FIXTURE_PASSWORD
  if (!password) throw new Error('Enable batch fixtures and set BATCH_FIXTURE_PASSWORD server-side and in the test process')
  await page.goto('/')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByText(username, { exact: true })).toBeVisible()
}

test('New York winter creation, summer extension and DST rejection preserve real saved instants across device timezones', async ({ browser }, testInfo) => {
  test.slow()
  const teacher = await browser.newContext({ timezoneId: 'Pacific/Honolulu', viewport: { width: 390, height: 844 } })
  const student = await browser.newContext({ timezoneId: 'Asia/Taipei' })
  try {
    const t = await teacher.newPage(); const s = await student.newPage()
    await login(t, 'v2-teacher'); await login(s, 'v2-student-1')
    const courses = await t.request.get('/api/courses')
    expect(courses.status()).toBe(200)
    const target = ((await courses.json()) as { id: string; code: string }[]).find(course => course.code === 'V2-404')
    if (!target) throw new Error('Missing isolated V2-404 fixture')
    const path = `/api/courses/${target.id}/activity`
    const read = async () => {
      const response = await t.request.get(path)
      expect(response.status()).toBe(200)
      return (await response.json()).activity as { deadline: string; instructions: string; groups: unknown[] } | null
    }
    expect(await read()).toBeNull() // Fresh disposable database; never reset an existing activity.
    await t.goto(`/#/courses/${target.id}`)
    await t.getByRole('button', { name: 'Create activity', exact: true }).click()
    await t.getByLabel('Instructions', { exact: true }).fill('Synthetic English timezone acceptance exercise')
    await t.getByLabel('Group 1 capacity', { exact: true }).fill('8')
    let writes = 0
    t.on('request', request => { if (request.method() === 'POST' && request.url().includes(path)) writes++ })
    // 2099 March 8 is the spring gap; November 1 is the repeated fall hour.
    for (const invalid of ['2099-03-08T02:30', '2099-11-01T01:30']) {
      await t.getByLabel('Deadline (America/New_York)', { exact: true }).fill(invalid)
      await t.getByRole('button', { name: 'Review activity', exact: true }).click()
      await expect(t.getByRole('alert')).toContainText('valid, unambiguous deadline')
      await expect(t.getByRole('button', { name: 'Confirm create activity', exact: true })).toHaveCount(0)
      expect(await read()).toBeNull()
    }
    expect(writes).toBe(0)
    await t.getByLabel('Deadline (America/New_York)', { exact: true }).fill('2099-01-15T10:00')
    await t.getByRole('button', { name: 'Review activity', exact: true }).click()
    await t.getByRole('button', { name: 'Confirm create activity', exact: true }).click()
    await expect(t.getByText('Activity created. It is now visible to course participants.', { exact: true })).toBeVisible()
    const winter = (await read())!
    expect(winter.deadline).toBe('2099-01-15T15:00:00Z')
    await expect(t.locator('time')).toHaveText(/15 Jan 2099.*10:00.*America\/New_York/)
    await t.reload()
    expect(await read()).toEqual(winter)
    await t.getByRole('button', { name: 'Extend deadline', exact: true }).click()
    await expect(t.getByLabel('New deadline (America/New_York)', { exact: true })).toHaveValue('2099-01-15T10:00')
    for (const invalid of ['2099-03-08T02:30', '2099-11-01T01:30']) {
      await t.getByLabel('New deadline (America/New_York)', { exact: true }).fill(invalid)
      await t.getByRole('button', { name: 'Save deadline', exact: true }).click()
      await expect(t.getByRole('alert')).toContainText('valid, unambiguous deadline')
      expect(await read()).toEqual(winter)
    }
    expect(writes).toBe(1)
    await t.getByLabel('New deadline (America/New_York)', { exact: true }).fill('2099-07-15T10:00')
    await t.getByRole('button', { name: 'Save deadline', exact: true }).click()
    await expect(t.getByText('Deadline extended.', { exact: true })).toBeVisible()
    const summer = (await read())!
    expect(summer).toEqual({ ...winter, deadline: '2099-07-15T14:00:00Z' })
    expect(writes).toBe(2)
    await expect(t.locator('time')).toHaveText(/15 Jul 2099.*10:00.*America\/New_York/)
    await s.goto(`/#/courses/${target.id}`)
    await expect(s.locator('time')).toHaveText(/15 Jul 2099.*10:00.*America\/New_York/)
    const studentResponse = await s.request.get(path)
    expect(studentResponse.status()).toBe(200)
    expect((await studentResponse.json()).activity).toEqual(summer)
    expect(await t.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await t.screenshot({ path: testInfo.outputPath('new-york-summer-mobile.png'), fullPage: true })
  } finally { await teacher.close(); await student.close() }
})
