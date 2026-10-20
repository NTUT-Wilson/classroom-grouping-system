import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

type Course = { id: string; code: string }
type Activity = { id: string; instructions: string; deadline: string; status: string;
  groups: { id: string; ordinal: number; name: string; capacity: number }[] }
async function login(page: Page, username: string) {
  const password = process.env.BATCH_FIXTURE_PASSWORD
  if (!password) throw new Error('Enable the opt-in batch fixture and set BATCH_FIXTURE_PASSWORD')
  await page.goto('/')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByText(username, { exact: true })).toBeVisible()
}
async function course(page: Page, code: string) {
  const response = await page.request.get('/api/courses')
  expect(response.status()).toBe(200)
  const found = ((await response.json()) as Course[]).find(value => value.code === code)
  if (!found) throw new Error(`Missing isolated synthetic course ${code}`)
  return found
}
async function headers(page: Page) {
  const response = await page.request.get('/api/auth/csrf')
  expect(response.status()).toBe(200)
  const token = await response.json() as { headerName: string; token: string }
  return { [token.headerName]: token.token }
}
async function read(page: Page, id: string): Promise<Activity | null> {
  const response = await page.request.get(`/api/courses/${id}/activity`)
  expect(response.status()).toBe(200)
  return (await response.json()).activity
}
async function create(page: Page, id: string, deadline: string) {
  // This suite requires a fresh disposable database. Creation UI is covered separately.
  expect(await read(page, id)).toBeNull()
  const response = await page.request.post(`/api/courses/${id}/activity`, {
    headers: await headers(page),
    data: { commandKey: crypto.randomUUID(), instructions: 'Original settings activity', deadline, capacities: [2] },
  })
  expect(response.status()).toBe(200)
  return (await response.json()).activity as Activity
}

test('settings edits reconcile lost responses, extend only later, and preserve instructions and profiles across a real cutoff', async ({ browser }, testInfo) => {
  // Includes a real near-future deadline, independent sessions, and mobile screenshots.
  test.setTimeout(120_000)
  const teacher = await browser.newContext({ timezoneId: 'America/Los_Angeles', viewport: { width: 390, height: 844 } })
  const student = await browser.newContext({ timezoneId: 'Europe/London' })
  try {
    const t = await teacher.newPage(); const s = await student.newPage()
    await login(t, 'batch-teacher'); await login(s, 'batch-student-1')
    const settings = await course(t, 'BATCH-404'); const cutoff = await course(t, 'BATCH-405')
    const original = await create(t, settings.id, '2099-03-15T18:30:00Z')
    await t.goto(`/#/courses/${settings.id}`)
    await t.getByRole('button', { name: 'Edit instructions', exact: true }).click()
    const text = '  Revised 😀\n<script>window.settingsInjected=true</script> & plain text  '
    await t.getByLabel('Instructions', { exact: true }).fill(text)
    await t.getByRole('button', { name: 'Save instructions', exact: true }).click()
    await expect(t.getByText('Instructions saved.', { exact: true })).toBeVisible()
    const edited = (await read(t, settings.id))!
    expect(edited.instructions).toBe(text); expect(edited.groups).toEqual(original.groups)
    expect(edited.deadline).toBe(original.deadline)
    await s.goto(`/#/courses/${settings.id}`)
    await expect(s.locator('.activity-instructions')).toHaveText(text)
    expect(await s.evaluate(() => 'settingsInjected' in window)).toBe(false)
    await expect(s.getByRole('button', { name: 'Edit instructions', exact: true })).toHaveCount(0)
    await expect(s.getByRole('button', { name: 'Extend deadline', exact: true })).toHaveCount(0)

    await t.getByRole('button', { name: 'Extend deadline', exact: true }).click()
    for (const invalid of ['2099-03-15T14:30', '2099-03-15T14:29']) {
      await t.getByLabel('New deadline (America/New_York)', { exact: true }).fill(invalid)
      await t.getByRole('button', { name: 'Save deadline', exact: true }).click()
      await expect(t.getByRole('alert')).toContainText('Choose a deadline later than the current deadline.')
      expect((await read(t, settings.id))!.deadline).toBe(original.deadline)
    }
    await t.getByLabel('New deadline (America/New_York)', { exact: true }).fill('2099-03-15T14:31')
    await t.getByRole('button', { name: 'Save deadline', exact: true }).click()
    await expect(t.getByText('Deadline extended.', { exact: true })).toBeVisible()
    await expect(t.locator('time')).toHaveText(/15 Mar 2099.*14:31.*America\/New_York/)
    const extended = (await read(t, settings.id))!
    expect(extended.deadline).toBe('2099-03-15T18:31:00Z'); expect(extended.instructions).toBe(text)
    expect(extended.groups).toEqual(original.groups)

    const path = `/api/courses/${settings.id}/activity/instructions`
    let puts = 0
    await t.route(`**${path}`, async route => {
      if (route.request().method() !== 'PUT') { await route.continue(); return }
      puts++
      const committed = await route.fetch()
      expect(committed.status()).toBe(200)
      await route.abort('failed')
    })
    await t.getByRole('button', { name: 'Edit instructions', exact: true }).click()
    await t.getByLabel('Instructions', { exact: true }).fill('Committed despite lost response')
    await t.getByRole('button', { name: 'Save instructions', exact: true }).click()
    await expect(t.getByRole('alert')).toContainText('Save could not be confirmed.')
    await expect(t.getByRole('button', { name: 'Save instructions', exact: true })).toBeDisabled()
    expect((await read(t, settings.id))!.instructions).toBe('Committed despite lost response')
    await t.screenshot({ path: testInfo.outputPath('settings-uncertain-mobile.png'), fullPage: true })
    await t.getByRole('button', { name: 'Check activity', exact: true }).click()
    await expect(t.getByRole('button', { name: 'Review preserved draft', exact: true })).toBeVisible()
    await expect(t.getByLabel('Instructions', { exact: true })).toHaveValue('Committed despite lost response')
    await expect(t.locator('.settings-current')).toContainText('Committed despite lost response')
    expect(puts).toBe(1)
    await t.getByRole('button', { name: 'Cancel editing', exact: true }).click()
    await t.getByRole('button', { name: 'Refresh activity', exact: true }).click()
    await t.reload()
    expect(puts).toBe(1)
    expect((await read(t, settings.id))!.groups).toEqual(original.groups)
    await t.unroute(`**${path}`)

    // Start a separate real cutoff only now, keeping the main editing story independent of timing.
    const nearDeadline = new Date(Date.now() + 8_000).toISOString()
    const near = await create(t, cutoff.id, nearDeadline)
    await t.goto(`/#/courses/${cutoff.id}`)
    await expect(t.getByRole('button', { name: 'Edit instructions', exact: true })).toBeVisible()
    await expect.poll(async () => (await read(t, cutoff.id))!.status, { timeout: 20_000, intervals: [100, 250, 500] }).toBe('FINALIZING')
    const denied = await t.request.post(`/api/courses/${cutoff.id}/activity/deadline`, {
      headers: await headers(t), data: { commandKey: crypto.randomUUID(), deadline: '2099-03-15T18:30:00Z' },
    })
    expect(denied.status()).toBe(409)
    expect((await denied.json()).code).toBe('ACTIVITY_CLOSED')
    await t.getByRole('button', { name: 'Refresh activity', exact: true }).click()
    await expect(t.getByRole('button', { name: 'Extend deadline', exact: true })).toHaveCount(0)
    await expect(t.getByText('Grouping is closed. Final results are not available. Refresh to check current activity status.', { exact: true })).toBeVisible()
    await t.getByRole('button', { name: 'Edit instructions', exact: true }).click()
    await t.getByLabel('Instructions', { exact: true }).fill('Instructions remain editable after the actual cutoff 😀')
    await t.getByRole('button', { name: 'Save instructions', exact: true }).click()
    await expect(t.getByText('Instructions saved.', { exact: true })).toBeVisible()
    const closed = (await read(t, cutoff.id))!
    expect(closed.status).toBe('FINALIZING'); expect(closed.deadline).toBe(near.deadline)
    expect(closed.groups).toEqual(near.groups)
    await s.goto(`/#/courses/${cutoff.id}`)
    await expect(s.locator('.activity-instructions')).toHaveText(closed.instructions)
    await s.goto(`/#/courses/${cutoff.id}/classmates`)
    await expect(s.getByLabel('About me', { exact: true })).toBeVisible()
    await s.getByLabel('About me', { exact: true }).fill('My course introduction still saves after cutoff')
    await s.getByRole('button', { name: 'Save introduction', exact: true }).click()
    await expect(s.getByText('Your About me has been saved for this course.', { exact: true })).toBeVisible()
    await s.getByRole('button', { name: 'View profile Alex <Batch> & 二', exact: true }).click()
    await expect(s.getByRole('dialog', { name: 'Alex <Batch> & 二', exact: true })).toContainText('00007002')
    await s.getByRole('button', { name: 'Close profile', exact: true }).click()
    const refreshMutations: string[] = []
    t.on('request', request => {
      if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method()) && request.url().includes('/api/')) refreshMutations.push(request.url())
    })
    await t.getByRole('button', { name: 'Refresh activity', exact: true }).click()
    await t.reload()
    await expect(t.locator('.activity-instructions')).toHaveText(closed.instructions)
    expect(await read(t, cutoff.id)).toEqual(closed)
    expect(refreshMutations).toEqual([])
    expect(await t.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await t.screenshot({ path: testInfo.outputPath('settings-closed-mobile.png'), fullPage: true })
    await s.screenshot({ path: testInfo.outputPath('settings-profile-after-cutoff.png'), fullPage: true })
  } finally { await teacher.close(); await student.close() }
})
