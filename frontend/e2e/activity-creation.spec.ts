import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

type Course = { id: string; code: string; name: string }
type Activity = { id: string; courseId: string; instructions: string; deadline: string; status: string;
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
  const result = ((await response.json()) as Course[]).find(c => c.code === code)
  expect(result).toBeDefined()
  return result!
}
async function read(page: Page, id: string): Promise<{ activity: Activity | null; enrolledCount: number | null }> {
  const response = await page.request.get(`/api/courses/${id}/activity`)
  expect(response.status()).toBe(200)
  return response.json()
}
async function post(page: Page, id: string, body: unknown, csrf = true) {
  const token = await (await page.request.get('/api/auth/csrf')).json()
  return page.request.post(`/api/courses/${id}/activity`, {
    data: body, headers: csrf ? { [token.headerName]: token.token } : {},
  })
}
async function fill(page: Page, capacities: number[], instructions: string) {
  await page.getByLabel('Instructions', { exact: true }).fill(instructions)
  await page.getByLabel('Deadline (America/New_York)', { exact: true }).fill('2099-03-15T14:30')
  for (let i = 0; i < capacities.length; i++) {
    if (i) await page.getByRole('button', { name: 'Add group', exact: true }).click()
    await page.getByLabel(`Group ${i + 1} capacity`, { exact: true }).fill(String(capacities[i]))
  }
}
async function noOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
}

test('teacher reviews and creates once; students immediately see exact fixed groups with New York deadline independent of browser timezone', async ({ browser }, testInfo) => {
  // Multiple real sessions and screenshots need a journey budget under concurrent browser workers.
  test.slow()
  const teacher = await browser.newContext({ timezoneId: 'America/Los_Angeles', viewport: { width: 390, height: 844 } })
  const student = await browser.newContext({ timezoneId: 'Europe/London' })
  try {
    const t = await teacher.newPage(); const s = await student.newPage()
    await login(t, 'batch-teacher'); await login(s, 'batch-student-1')
    const target = await course(t, 'BATCH-101')
    // This suite requires a disposable fresh database; never delete or reset an existing activity.
    expect((await read(t, target.id)).activity).toBeNull()
    await t.goto(`/#/courses/${target.id}`)
    await t.getByRole('button', { name: 'Create activity', exact: true }).click()
    await fill(t, [2, 2, 2, 2], '  <script>plain text</script>\nWork together 😀  ')
    await t.getByLabel('Group 1 capacity', { exact: true }).fill('1')
    await t.getByRole('button', { name: 'Review activity', exact: true }).click()
    await expect(t.getByRole('button', { name: 'Confirm create activity', exact: true })).toHaveCount(0)
    expect((await read(t, target.id)).activity).toBeNull()
    await t.getByLabel('Group 1 capacity', { exact: true }).fill('2')
    await t.getByRole('button', { name: 'Review activity', exact: true }).click()
    await expect(t.getByRole('button', { name: 'Confirm create activity', exact: true })).toBeVisible()
    expect((await read(t, target.id)).activity).toBeNull()
    await noOverflow(t)
    await t.screenshot({ path: testInfo.outputPath('review-mobile.png'), fullPage: true })
    await t.getByRole('button', { name: 'Back to setup', exact: true }).click()
    await expect(t.getByLabel('Instructions', { exact: true })).toHaveValue('  <script>plain text</script>\nWork together 😀  ')
    await t.getByRole('button', { name: 'Review activity', exact: true }).click()
    let posts = 0
    t.on('request', request => { if (request.method() === 'POST' && request.url().endsWith(`/api/courses/${target.id}/activity`)) posts++ })
    await t.getByRole('button', { name: 'Confirm create activity', exact: true }).click()
    await expect(t.getByText('Activity created. It is now visible to course participants.', { exact: true })).toBeVisible()
    const saved = (await read(t, target.id)).activity!
    expect(saved.instructions).toBe('  <script>plain text</script>\nWork together 😀  ')
    expect(saved.deadline).toBe('2099-03-15T18:30:00Z')
    await expect(t.locator('time')).toHaveText(/15 Mar 2099.*14:30.*America\/New_York/)
    expect(saved.status).toBe('OPEN')
    expect(saved.groups.map(g => [g.ordinal, g.name, g.capacity])).toEqual([
      [1, 'Group 1', 2], [2, 'Group 2', 2], [3, 'Group 3', 2], [4, 'Group 4', 2],
    ])
    await t.getByRole('button', { name: 'Refresh activity', exact: true }).click()
    await t.reload()
    await expect(t.getByRole('button', { name: 'Create activity', exact: true })).toHaveCount(0)
    expect((await read(t, target.id)).activity).toEqual(saved)
    expect(posts).toBe(1)
    await s.goto(`/#/courses/${target.id}`)
    await expect(s.getByText('<script>plain text</script>\nWork together 😀', { exact: false })).toBeVisible()
    expect((await read(s, target.id)).activity).toEqual(saved)
    await expect(s.locator('time')).toHaveText(/15 Mar 2099.*14:30.*America\/New_York/)
    await expect(s.getByRole('button', { name: 'Create activity', exact: true })).toHaveCount(0)
    const denied = await post(s, target.id, { commandKey: crypto.randomUUID(), instructions: 'denied', deadline: saved.deadline, capacities: [8] })
    expect(denied.status()).toBe(403)
    expect((await read(t, target.id)).activity).toEqual(saved)
    await noOverflow(t)
    await t.screenshot({ path: testInfo.outputPath('created-mobile.png'), fullPage: true })
    await s.screenshot({ path: testInfo.outputPath('student-immediate-activity.png'), fullPage: true })
  } finally { await teacher.close(); await student.close() }
})

test('lost committed creation response reconciles by reads without replay and invalid or denied requests leave courses unchanged', async ({ browser }, testInfo) => {
  const teacher = await browser.newContext({ timezoneId: 'Pacific/Honolulu' })
  const outsider = await browser.newContext()
  try {
    const t = await teacher.newPage(); const o = await outsider.newPage()
    await login(t, 'batch-teacher'); await login(o, 'batch-student-8')
    const target = await course(t, 'BATCH-202'); const empty = await course(t, 'BATCH-303')
    expect((await read(t, target.id)).activity).toBeNull()
    const body = { commandKey: crypto.randomUUID(), instructions: 'Validation', deadline: '2099-03-15T18:30:00Z', capacities: [2] }
    expect((await post(t, target.id, body, false)).status()).toBe(403)
    // The student role is rejected before teacher-only course authorization.
    expect((await post(o, target.id, body)).status()).toBe(403)
    expect((await o.request.get(`/api/courses/${target.id}/activity`)).status()).toBe(404)
    for (const capacities of [[], [1, 1], [2.5], [3], ['2']]) {
      expect((await post(t, target.id, { ...body, commandKey: crypto.randomUUID(), capacities })).status()).toBe(400)
      expect((await read(t, target.id)).activity).toBeNull()
    }
    expect((await post(t, empty.id, body)).status()).toBe(400)
    expect((await read(t, empty.id)).activity).toBeNull()
    await t.goto(`/#/courses/${empty.id}`)
    await expect(t.getByRole('button', { name: 'Create activity', exact: true })).toBeDisabled()
    await t.goto(`/#/courses/${target.id}`)
    await t.getByRole('button', { name: 'Create activity', exact: true }).click()
    await fill(t, [2], 'Lost response was committed')
    await t.getByRole('button', { name: 'Review activity', exact: true }).click()
    let posts = 0
    await t.route(`**/api/courses/${target.id}/activity`, async route => {
      if (route.request().method() !== 'POST') { await route.continue(); return }
      posts++
      const committed = await route.fetch()
      expect(committed.status()).toBe(200)
      await route.abort('failed')
    })
    await t.getByRole('button', { name: 'Confirm create activity', exact: true }).click()
    await expect(t.getByRole('button', { name: 'Check activity', exact: true })).toBeVisible()
    await expect(t.getByRole('button', { name: 'Confirm create activity', exact: true })).toHaveCount(0)
    await t.screenshot({ path: testInfo.outputPath('uncertain-created-response.png'), fullPage: true })
    await t.getByRole('button', { name: 'Check activity', exact: true }).click()
    await expect(t.getByText('Lost response was committed', { exact: true })).toBeVisible()
    const saved = (await read(t, target.id)).activity!
    await t.getByRole('button', { name: 'Refresh activity', exact: true }).click()
    await t.reload()
    expect((await read(t, target.id)).activity).toEqual(saved)
    expect(posts).toBe(1)
    expect((await read(t, empty.id)).activity).toBeNull()
  } finally { await teacher.close(); await outsider.close() }
})
