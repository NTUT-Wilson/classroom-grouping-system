import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

type Course = { id: string; code: string }
type Discovery = { status: string; ownGroupId: string | null; groups: {
  id: string; name: string; capacity: number; availablePlaces: number;
  manager: { accountId: string; studentId: string; studentName: string; aboutMe: string | null } | null;
  members: { accountId: string; studentId: string; studentName: string; aboutMe: string | null }[] }[] }
async function login(page: Page, username: string) {
  const password = process.env.BATCH_FIXTURE_PASSWORD
  if (!password) throw new Error('Enable the opt-in batch fixture and set BATCH_FIXTURE_PASSWORD')
  await page.goto('/')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByText(username, { exact: true })).toBeVisible()
}
async function headers(page: Page) {
  const response = await page.request.get('/api/auth/csrf'); expect(response.status()).toBe(200)
  const token = await response.json() as { headerName: string; token: string }
  return { [token.headerName]: token.token }
}
async function course(page: Page, code: string) {
  const response = await page.request.get('/api/courses'); expect(response.status()).toBe(200)
  const found = ((await response.json()) as Course[]).find(value => value.code === code)
  if (!found) throw new Error(`Missing isolated synthetic course ${code}`)
  return found
}
async function create(page: Page, id: string, deadline: string, capacities: number[]) {
  // Never replace history: these additive fixture courses require a fresh disposable E2E database.
  const before = await page.request.get(`/api/courses/${id}/activity`)
  expect(before.status()).toBe(200); expect((await before.json()).activity).toBeNull()
  const response = await page.request.post(`/api/courses/${id}/activity`, {
    headers: await headers(page), data: { commandKey: crypto.randomUUID(), instructions: 'Choose your group deliberately', deadline, capacities },
  })
  expect(response.status()).toBe(200)
}
async function discover(page: Page, id: string): Promise<Discovery> {
  const response = await page.request.get(`/api/courses/${id}/grouping`)
  expect(response.status()).toBe(200); return response.json()
}
async function join(page: Page, id: string, group: string) {
  return page.request.post(`/api/courses/${id}/groups/${group}/join`, {
    headers: await headers(page), data: { commandKey: crypto.randomUUID() },
  })
}
async function openGroups(page: Page, id: string) {
  await page.goto(`/#/courses/${id}`)
  await page.getByRole('link', { name: 'Groups', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Groups', exact: true })).toBeVisible()
}

test('deliberate joining shows profiles, loses a stale empty-group race safely, and reconciles a lost committed response without replay', async ({ browser }, testInfo) => {
  test.setTimeout(120_000)
  const contexts = await Promise.all([browser.newContext({ viewport: { width: 390, height: 844 } }),
    browser.newContext(), browser.newContext(), browser.newContext(), browser.newContext()])
  try {
    const [t, manager, stale, rival, uncertain] = await Promise.all(contexts.map(context => context.newPage()))
    await login(t!, 'batch-teacher'); await login(manager!, 'batch-student-1'); await login(stale!, 'batch-student-2')
    await login(rival!, 'batch-student-3'); await login(uncertain!, 'batch-student-4')
    const target = await course(t!, 'BATCH-505')
    await create(t!, target.id, '2099-03-15T18:30:00Z', [2, 2, 2, 2])
    const introduction = '<script>window.groupInjected=true</script> & teamwork 😀'
    const savedProfile = await manager!.request.put(`/api/courses/${target.id}/about-me`, {
      headers: await headers(manager!), data: { aboutMe: introduction },
    })
    expect(savedProfile.status()).toBe(200)
    const groups = (await discover(manager!, target.id)).groups
    await openGroups(manager!, target.id)
    await manager!.getByRole('button', { name: 'Join Group 1', exact: true }).click()
    await expect(manager!.getByRole('dialog', { name: 'Join Group 1?', exact: true })).toBeVisible()
    expect((await discover(manager!, target.id)).ownGroupId).toBeNull()
    await manager!.getByRole('button', { name: 'Cancel', exact: true }).click()
    expect((await discover(manager!, target.id)).ownGroupId).toBeNull()
    await manager!.getByRole('button', { name: 'Join Group 1', exact: true }).click()
    await manager!.getByRole('button', { name: 'Confirm join', exact: true }).click()
    await expect(manager!.getByText('You are the group manager and a member of Group 1.', { exact: true })).toBeVisible()
    const committed = await discover(manager!, target.id)
    expect(committed.ownGroupId).toBe(groups[0]!.id)
    expect(committed.groups[0]!.members).toHaveLength(1)
    expect(committed.groups[0]!.manager).toEqual(committed.groups[0]!.members[0])
    expect(committed.groups[0]!.availablePlaces).toBe(1)
    await expect(manager!.getByRole('region', { name: 'Group 1', exact: true })).toContainText('00007001')
    await expect(manager!.getByRole('region', { name: 'Group 1', exact: true })).toContainText('陳 Batch 一')
    await expect(manager!.getByRole('region', { name: 'Group 1', exact: true })).toContainText(introduction)
    expect(await manager!.evaluate(() => 'groupInjected' in window)).toBe(false)
    expect(await manager!.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await manager!.screenshot({ path: testInfo.outputPath('group-manager-mobile.png'), fullPage: true })

    await openGroups(stale!, target.id)
    await expect(stale!.getByRole('region', { name: 'Group 1', exact: true })).toContainText(introduction)
    await stale!.getByRole('button', { name: 'Join Group 2', exact: true }).click()
    expect((await join(rival!, target.id, groups[1]!.id)).status()).toBe(200)
    let stalePosts = 0
    let automaticApplications = 0
    stale!.on('request', request => {
      if (request.method() === 'POST' && request.url().includes('/api/courses/')) stalePosts++
      if (request.method() === 'POST' && /\/groups\/[^/]+\/applications$/.test(request.url())) automaticApplications++
    })
    await stale!.getByRole('button', { name: 'Confirm join', exact: true }).click()
    await expect(stale!.getByRole('alert')).toContainText('This group is no longer empty')
    await expect(stale!.getByRole('button', { name: 'Join Group 2', exact: true })).toHaveCount(0)
    const lost = await discover(stale!, target.id)
    expect(lost.ownGroupId).toBeNull(); expect(lost.groups[1]!.members).toHaveLength(1)
    expect(lost.groups[1]!.manager!.studentId).toBe('00007003')
    await stale!.getByRole('button', { name: 'Refresh groups', exact: true }).click()
    expect(stalePosts).toBe(1)
    await expect(stale!.getByRole('button', { name: 'Apply to Group 2', exact: true })).toBeVisible()
    expect(automaticApplications).toBe(0)
    const privateHistory = await stale!.request.get(`/api/courses/${target.id}/applications`)
    expect(privateHistory.status()).toBe(200); expect((await privateHistory.json()).items).toEqual([])
    await expect(stale!.getByRole('button', { name: /Invite|Leave|Remove/ })).toHaveCount(0)

    await openGroups(uncertain!, target.id)
    const path = `/api/courses/${target.id}/groups/${groups[2]!.id}/join`
    let posts = 0
    await uncertain!.route(`**${path}`, async route => {
      posts++
      const response = await route.fetch(); expect(response.status()).toBe(200)
      await route.abort('failed')
    })
    await uncertain!.getByRole('button', { name: 'Join Group 3', exact: true }).click()
    await uncertain!.getByRole('button', { name: 'Confirm join', exact: true }).click()
    await expect(uncertain!.getByRole('alert')).toContainText('Check current groups before continuing')
    expect((await discover(uncertain!, target.id)).ownGroupId).toBe(groups[2]!.id)
    await expect(uncertain!.getByRole('button', { name: 'Confirm join', exact: true })).toHaveCount(0)
    await uncertain!.getByRole('button', { name: 'Check current groups', exact: true }).click()
    await expect(uncertain!.getByText('You are the group manager and a member of Group 3.', { exact: true })).toBeVisible()
    await uncertain!.getByRole('button', { name: 'Refresh groups', exact: true }).click()
    await uncertain!.reload()
    await expect(uncertain!.getByText('You are the group manager and a member of Group 3.', { exact: true })).toBeVisible()
    expect(posts).toBe(1)
    expect((await discover(uncertain!, target.id)).groups[2]!.members).toHaveLength(1)
    await uncertain!.screenshot({ path: testInfo.outputPath('group-reconciled.png'), fullPage: true })
    const teacherRead = await t!.request.get(`/api/courses/${target.id}/grouping`)
    expect(teacherRead.status()).toBe(403)
    expect((await join(t!, target.id, groups[3]!.id)).status()).toBe(403)
  } finally { await Promise.all(contexts.map(context => context.close())) }
})

test('real deadline closes discovery and joining while course profiles remain available', async ({ browser }, testInfo) => {
  test.setTimeout(60_000)
  const teacher = await browser.newContext(); const student = await browser.newContext()
  try {
    const t = await teacher.newPage(); const s = await student.newPage()
    await login(t, 'batch-teacher'); await login(s, 'batch-student-1')
    const target = await course(t, 'BATCH-506')
    await create(t, target.id, new Date(Date.now() + 12_000).toISOString(), [2])
    const group = (await discover(s, target.id)).groups[0]!
    expect((await join(s, target.id, group.id)).status()).toBe(200)
    await openGroups(s, target.id)
    await expect(s.getByText('You are the group manager and a member of Group 1.', { exact: true })).toBeVisible()
    await expect.poll(async () => (await discover(s, target.id)).status, { timeout: 25_000, intervals: [100, 250, 500] }).toBe('FINALIZING')
    const denied = await join(s, target.id, group.id)
    expect(denied.status()).toBe(409); expect((await denied.json()).code).toBe('ACTIVITY_CLOSED')
    const closed = await discover(s, target.id)
    expect(closed.ownGroupId).toBeNull(); expect(closed.groups).toEqual([])
    const mutations: string[] = []
    s.on('request', request => { if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method()) && request.url().includes('/api/')) mutations.push(request.url()) })
    await s.getByRole('button', { name: 'Refresh groups', exact: true }).click()
    await expect(s.getByRole('button', { name: /^Join Group/ })).toHaveCount(0)
    await expect(s.getByRole('heading', { name: 'Grouping is finalizing', exact: true })).toBeVisible()
    await expect(s.getByRole('region', { name: 'Group 1', exact: true })).toHaveCount(0)
    await expect(s.getByText('You are the group manager and a member of Group 1.', { exact: true })).toHaveCount(0)
    await s.reload(); expect(await discover(s, target.id)).toEqual(closed); expect(mutations).toEqual([])
    await s.goto(`/#/courses/${target.id}/classmates`)
    await s.getByRole('button', { name: 'View profile Alex <Batch> & 二', exact: true }).click()
    await expect(s.getByRole('dialog', { name: 'Alex <Batch> & 二', exact: true })).toContainText('00007002')
    await s.screenshot({ path: testInfo.outputPath('group-profile-after-cutoff.png'), fullPage: true })
  } finally { await teacher.close(); await student.close() }
})
