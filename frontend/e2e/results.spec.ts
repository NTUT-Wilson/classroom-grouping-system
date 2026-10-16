import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

type Result = { activityId: string | null; status: string; groups: { id: string; ordinal: number; managerAccountId: string | null; capacity: number; members: { accountId: string; studentId: string; studentName: string; aboutMe?: string }[] }[] }
async function login(page: Page, username: string) {
  const password = process.env.BATCH_FIXTURE_PASSWORD
  if (!password) throw new Error('Enable opt-in batch fixture and set BATCH_FIXTURE_PASSWORD')
  await page.goto('/'); await page.getByLabel('Username').fill(username); await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByText(username, { exact: true })).toBeVisible()
}
async function csrf(page: Page) {
  const response = await page.request.get('/api/auth/csrf'); expect(response.status()).toBe(200)
  const token = await response.json() as { headerName: string; token: string }; return { [token.headerName]: token.token }
}
async function post(page: Page, path: string, data: object) { return page.request.post(path, { headers: await csrf(page), data: { commandKey: crypto.randomUUID(), ...data } }) }
async function result(page: Page, course: string, teacher = false): Promise<Result> {
  const response = await page.request.get(`/api/courses/${course}/results/${teacher ? 'roster' : 'my-group'}`)
  expect(response.status()).toBe(200); return response.json()
}
test('complete final roster and own group remain private, read-only and available after profile/instruction edits', async ({ browser, request }, testInfo) => {
  test.setTimeout(120_000)
  const secret = process.env.SCHEDULER_SECRET
  if (!secret) throw new Error('Set matching server/test-process SCHEDULER_SECRET for production due-work E2E')
  const contexts = await Promise.all([browser.newContext(), browser.newContext(), browser.newContext(), browser.newContext({ viewport: { width: 390, height: 844 } })])
  try {
    const [teacher, manager, applicant, assigned] = await Promise.all(contexts.map(context => context.newPage()))
    for (const [page, username] of [[teacher, 'batch-teacher'], [manager, 'batch-student-1'], [applicant, 'batch-student-2'], [assigned, 'batch-student-8']] as const) await login(page, username)
    const courses = await (await teacher.request.get('/api/courses')).json() as { id: string; code: string }[]
    const course = courses.find(value => value.code === 'BATCH-1001')!.id
    const cutoff = courses.find(value => value.code === 'BATCH-1002')!.id
    expect(await result(teacher, course, true)).toEqual({ activityId: null, status: 'ABSENT', groups: [] })
    expect((await assigned.request.get(`/api/courses/${cutoff}/results/my-group`)).status()).toBe(404)
    expect((await teacher.request.get(`/api/courses/${course}/results/my-group`)).status()).toBe(403)
    expect((await manager.request.get(`/api/courses/${course}/results/roster`)).status()).toBe(403)
    expect((await manager.request.get('/api/courses/00000000-0000-0000-0000-000000000000/results/my-group')).status()).toBe(404)
    const deadline = new Date(Date.now() + 16_000).toISOString()
    expect((await post(teacher, `/api/courses/${course}/activity`, { instructions: 'Synthetic final results <plain text>', deadline, capacities: [2, 2, 2, 2] })).status()).toBe(200)
    const discovery = await (await manager.request.get(`/api/courses/${course}/grouping`)).json() as { groups: { id: string }[] }
    const group = discovery.groups[0]!.id
    expect((await post(manager, `/api/courses/${course}/groups/${group}/join`, {})).status()).toBe(200)
    expect((await post(applicant, `/api/courses/${course}/groups/${group}/applications`, {})).status()).toBe(200)
    const before = await result(teacher, course, true); expect(before.status).toBe('OPEN'); expect(before.groups).toEqual([])
    await teacher.goto(`/#/courses/${course}/results`)
    await expect(teacher.getByRole('heading', { name: 'Results come after the deadline' })).toBeVisible()
    await expect.poll(async () => (await result(teacher, course, true)).status, { timeout: 25_000 }).toBe('FINALIZING')
    await teacher.getByRole('button', { name: 'Refresh results' }).click()
    await expect(teacher.getByRole('heading', { name: 'Grouping is finalizing' })).toBeVisible()
    expect((await result(teacher, course, true)).groups).toEqual([])
    // This Node-only machine request uses the production endpoint; no secret enters a browser context.
    expect((await request.post('/internal/scheduler/tick', { headers: { Authorization: `Bearer ${secret}` } })).status()).toBe(204)
    await expect.poll(async () => (await result(teacher, course, true)).status).toBe('FINALIZED')
    const roster = await result(teacher, course, true)
    expect(roster.groups.map(value => value.ordinal)).toEqual([1, 2, 3, 4])
    const members = roster.groups.flatMap(value => value.members)
    expect(members).toHaveLength(8); expect(new Set(members.map(value => value.accountId)).size).toBe(8)
    expect(members.every(value => /^0000700[1-8]$/.test(value.studentId))).toBe(true)
    expect(members.every(value => !('aboutMe' in value))).toBe(true)
    expect(roster.groups.filter(value => value.managerAccountId === null)).toHaveLength(3)
    expect(roster.groups.find(value => value.id === group)!.managerAccountId).toBeTruthy()
    const own = await result(assigned, course); expect(own.groups).toHaveLength(1)
    expect(own.groups[0]!.members.every(value => 'aboutMe' in value)).toBe(true)
    const snapshot = roster.groups.map(value => ({ id: value.id, manager: value.managerAccountId, members: value.members.map(member => member.accountId) }))
    await teacher.getByRole('button', { name: 'Refresh results' }).click()
    await expect(teacher.getByText('00007001', { exact: false })).toBeVisible()
    await expect(teacher.getByRole('button', { name: /CSV|assign|retry/i })).toHaveCount(0)
    await assigned.goto(`/#/courses/${course}/groups`); await assigned.reload()
    await expect(assigned.getByRole('heading', { name: 'My final group' })).toBeVisible()
    await expect(assigned.getByRole('region', { name: /^Group \d+$/ })).toHaveCount(1)
    await expect(assigned.getByRole('button', { name: /join|apply|invite/i })).toHaveCount(0)
    expect(await assigned.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await assigned.screenshot({ path: testInfo.outputPath('own-final-group-mobile.png'), fullPage: true })
    await teacher.screenshot({ path: testInfo.outputPath('final-roster-desktop.png'), fullPage: true })
    await manager.goto(`/#/courses/${course}/groups`); await manager.reload()
    await expect(manager.getByRole('heading', { name: 'My final group' })).toBeVisible()
    await expect(manager.getByRole('region', { name: /^Group \d+$/ })).toHaveCount(1)
    const managerGroup = manager.getByRole('region', { name: 'Group 1', exact: true })
    await expect(managerGroup).toBeVisible()
    await expect(managerGroup.getByText('Student ID · 00007001', { exact: true })).toBeVisible()
    await expect(managerGroup.getByText('Manager', { exact: true })).toBeVisible()
    await expect(manager.getByRole('heading', { name: /^Group [234]$/, exact: true })).toHaveCount(0)
    await expect(manager.getByRole('button', { name: /join|apply|invite|assign/i })).toHaveCount(0)
    const applicantOwn = await result(applicant, course)
    expect(applicantOwn.status).toBe('FINALIZED')
    expect(applicantOwn.groups).toHaveLength(1)
    expect(applicantOwn.groups[0]!.members.some(member => member.studentId === '00007002')).toBe(true)
    expect(applicantOwn.groups[0]!.members.every(member => 'aboutMe' in member)).toBe(true)
    const closed = await (await applicant.request.get(`/api/courses/${course}/applications`)).json()
    expect(closed.status).toBe('FINALIZED'); expect(closed.items[0].status).toBe('EXPIRED')
    expect(JSON.stringify(closed)).not.toContain('members')
    expect((await (await assigned.request.get(`/api/courses/${course}/grouping`)).json()).groups).toEqual([])
    const peers = await (await assigned.request.get(`/api/courses/${course}/classmates`)).json()
    expect(JSON.stringify(peers)).not.toContain('groupId')
    await assigned.goto(`/#/courses/${course}/classmates`)
    await assigned.getByLabel('About me', { exact: true }).fill('Finalized course introduction <literal> 👩🏽‍💻')
    await assigned.getByRole('button', { name: 'Save introduction', exact: true }).click()
    await expect(assigned.getByRole('status')).toContainText('saved')
    expect((await result(assigned, course)).groups[0]!.members.some(value => value.aboutMe === 'Finalized course introduction <literal> 👩🏽‍💻')).toBe(true)
    await teacher.goto(`/#/courses/${course}`)
    await teacher.getByRole('button', { name: 'Edit instructions' }).click()
    await teacher.getByLabel('Instructions', { exact: true }).fill('Instructions amended after finalization')
    await teacher.getByRole('button', { name: 'Save instructions', exact: true }).click()
    await expect(teacher.getByRole('status').filter({ hasText: /^Instructions saved\.$/ })).toHaveText('Instructions saved.')
    await expect(teacher.getByRole('button', { name: 'Extend deadline' })).toHaveCount(0)
    expect((await result(teacher, course, true)).groups.map(value => ({ id: value.id, manager: value.managerAccountId, members: value.members.map(member => member.accountId) }))).toEqual(snapshot)
    await teacher.goto(`/#/courses/${course}/results`)
    await expect(teacher.getByText('Student ID · 00007001')).toBeVisible()
    await teacher.route(`**/api/courses/${course}/results/roster`, route => route.abort('failed'))
    await teacher.getByRole('button', { name: 'Refresh results' }).click()
    await expect(teacher.getByRole('alert')).toContainText('complete result could not be loaded')
    await expect(teacher.getByText('Student ID · 00007001')).toHaveCount(0)
    await teacher.unroute(`**/api/courses/${course}/results/roster`)
    await teacher.getByRole('button', { name: 'Refresh results' }).click()
    await expect(teacher.getByText('Student ID · 00007001')).toBeVisible()
    expect((await teacher.request.post('/api/auth/logout', { headers: await csrf(teacher) })).status()).toBe(204)
    await teacher.getByRole('button', { name: 'Refresh results' }).click()
    await expect(teacher.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible()
    await expect(teacher.getByText('Student ID · 00007001')).toHaveCount(0)
  } finally { await Promise.all(contexts.map(context => context.close())) }
})

