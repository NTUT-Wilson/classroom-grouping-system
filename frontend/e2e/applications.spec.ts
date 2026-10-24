import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

type Profile = { accountId: string; studentId: string; studentName: string }
type Attempt = { id: string; groupId: string; status: string; reason: string | null; applicant: Profile; manager: Profile }
type History = { status: string; items: Attempt[]; nextCursor: string | null }
type Discovery = { status: string; ownGroupId: string | null; groups: { id: string; availablePlaces: number; members: Profile[] }[] }
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
async function create(page: Page, code: string, deadline: string, capacities: number[]) {
  const courses = await page.request.get('/api/courses')
  expect(courses.status()).toBe(200)
  const course = (await courses.json() as { id: string; code: string }[]).find(value => value.code === code)!
  expect(course).toBeTruthy()
  // These additive courses deliberately require a fresh disposable E2E database.
  const before = await page.request.get(`/api/courses/${course.id}/activity`)
  expect((await before.json()).activity).toBeNull()
  const result = await page.request.post(`/api/courses/${course.id}/activity`, {
    headers: await headers(page), data: { commandKey: crypto.randomUUID(), instructions: 'Applications reserve no places', deadline, capacities },
  })
  expect(result.status()).toBe(200)
  return course.id
}
async function discover(page: Page, course: string): Promise<Discovery> {
  const response = await page.request.get(`/api/courses/${course}/grouping`)
  expect(response.status()).toBe(200); return response.json()
}
async function history(page: Page, course: string, query = ''): Promise<History> {
  const response = await page.request.get(`/api/courses/${course}/applications${query}`)
  expect(response.status()).toBe(200); return response.json()
}
async function post(page: Page, path: string, data: object) {
  return page.request.post(path, { headers: await headers(page), data: { commandKey: crypto.randomUUID(), ...data } })
}
async function join(page: Page, course: string, group: string) {
  expect((await post(page, `/api/courses/${course}/groups/${group}/join`, {})).status()).toBe(200)
}
async function apply(page: Page, course: string, group: string) {
  const response = await post(page, `/api/courses/${course}/groups/${group}/applications`, {})
  expect(response.status()).toBe(200)
  return (await response.json() as History).items.find(value => value.groupId === group && value.status === 'PENDING')!
}
async function open(page: Page, course: string, view: 'groups' | 'requests') {
  await page.goto(`/#/courses/${course}/${view}`)
  await expect(page.getByRole('heading', { name: view === 'groups' ? 'Groups' : 'Requests', exact: true })).toBeVisible()
  // Reopening the same hash route preserves the mounted view. Read fresh state after another actor acts.
  const refresh = page.getByRole('button', { name: view === 'groups' ? 'Refresh groups' : 'Refresh requests', exact: true })
  await expect(refresh).toBeEnabled()
  await refresh.click()
  await expect(refresh).toBeEnabled()
}
async function applyUi(page: Page, group: number) {
  await page.getByRole('button', { name: `Apply to Group ${group}`, exact: true }).click()
  await page.getByRole('button', { name: 'Confirm application', exact: true }).click()
}
function card(page: Page, name: string) {
  return page.getByRole('article').filter({ hasText: name }).filter({ hasText: 'PENDING' })
}

test('private applications preserve rejected attempts, reserve no place, approve atomically, and reconcile lost responses without replay', async ({ browser }, testInfo) => {
  test.setTimeout(120_000)
  const contexts = await Promise.all(Array.from({ length: 6 }, (_, index) => browser.newContext(index === 3 ? { viewport: { width: 390, height: 844 } } : {})))
  try {
    const pages = await Promise.all(contexts.map(context => context.newPage()))
    const [teacher, m1, m2, applicant, rival, peer] = pages as [Page, Page, Page, Page, Page, Page]
    for (const [index, page] of pages.entries()) await login(page, index === 0 ? 'batch-teacher' : `batch-student-${index}`)
    const course = await create(teacher, 'BATCH-606', '2099-03-15T18:30:00Z', [2, 2, 2, 2])
    const groups = (await discover(m1, course)).groups
    await join(m1, course, groups[0]!.id); await join(m2, course, groups[1]!.id)
    await open(applicant, course, 'groups')
    await applicant.getByRole('button', { name: 'Apply to Group 1', exact: true }).click()
    expect((await history(applicant, course)).items).toEqual([])
    await applicant.getByRole('button', { name: 'Cancel', exact: true }).click()
    expect((await history(applicant, course)).items).toEqual([])
    await applyUi(applicant, 1)
    await expect.poll(async () => (await history(applicant, course)).items.length).toBe(1)
    expect((await discover(applicant, course)).ownGroupId).toBeNull()
    expect((await discover(applicant, course)).groups[0]!.availablePlaces).toBe(1)
    const first = (await history(applicant, course)).items[0]!
    expect((await teacher.request.get(`/api/courses/${course}/applications`)).status()).toBe(403)
    expect((await history(peer, course)).items).toEqual([])
    expect((await history(m2, course)).items).toEqual([])
    expect((await post(peer, `/api/courses/${course}/applications/${first.id}/decision`, { decision: 'APPROVE' })).status()).toBe(404)
    expect((await post(m2, `/api/courses/${course}/applications/${first.id}/decision`, { decision: 'REJECT' })).status()).toBe(404)
    await open(m1, course, 'requests')
    await m1.getByRole('button', { name: 'Reject', exact: true }).click()
    await m1.getByRole('button', { name: 'Confirm rejection', exact: true }).click()
    await expect(m1.getByRole('article')).toContainText('REJECTED')
    await open(applicant, course, 'groups')

    const submissionPath = `/api/courses/${course}/groups/${groups[0]!.id}/applications`
    let submissionPosts = 0
    await applicant.route(`**${submissionPath}`, async route => {
      submissionPosts++
      const response = await route.fetch(); expect(response.status()).toBe(200); await route.abort('failed')
    })
    await applyUi(applicant, 1)
    await expect(applicant.getByRole('alert')).toContainText('could not be confirmed')
    expect((await history(applicant, course)).items.map(value => value.status)).toEqual(['PENDING', 'REJECTED'])
    await open(applicant, course, 'requests')
    await applicant.getByRole('button', { name: 'Refresh requests', exact: true }).click()
    await applicant.reload()
    expect(submissionPosts).toBe(1)
    const firstPage = await history(applicant, course, '?limit=1')
    expect(firstPage.nextCursor).toBeTruthy()
    const older = await history(applicant, course, `?limit=1&cursor=${encodeURIComponent(firstPage.nextCursor!)}`)
    expect(older.items[0]!.id).toBe(first.id); expect(older.items[0]!.status).toBe('REJECTED')
    expect((await peer.request.get(`/api/courses/${course}/applications?limit=1&cursor=${encodeURIComponent(firstPage.nextCursor!)}`)).status()).toBe(400)
    await apply(applicant, course, groups[1]!.id)
    await apply(rival, course, groups[0]!.id)
    const accepted = (await history(applicant, course)).items.find(value => value.groupId === groups[0]!.id && value.status === 'PENDING')!
    await open(m1, course, 'requests')
    const decisionPath = `/api/courses/${course}/applications/${accepted.id}/decision`
    let decisionPosts = 0
    await m1.route(`**${decisionPath}`, async route => {
      decisionPosts++
      const response = await route.fetch(); expect(response.status()).toBe(200); await route.abort('failed')
    })
    await card(m1, 'Sam Batch 三').getByRole('button', { name: 'Approve', exact: true }).click()
    await m1.getByRole('button', { name: 'Confirm approval', exact: true }).click()
    await expect(m1.getByRole('alert')).toContainText('could not be confirmed')
    await m1.getByRole('button', { name: 'Refresh requests', exact: true }).click()
    await m1.reload(); expect(decisionPosts).toBe(1)
    const own = await history(applicant, course)
    expect(own.items.find(value => value.id === accepted.id)!.status).toBe('ACCEPTED')
    expect(own.items.find(value => value.groupId === groups[1]!.id)!.reason).toBe('Joined a group')
    expect(own.items.find(value => value.id === first.id)!.status).toBe('REJECTED')
    expect((await history(rival, course)).items[0]!.reason).toBe('Group full')
    expect((await discover(applicant, course)).ownGroupId).toBe(groups[0]!.id)
    expect((await discover(applicant, course)).groups[0]!.members).toHaveLength(2)
    expect((await history(applicant, course)).items.every(value => value.applicant.studentId === '00007003')).toBe(true)
    await open(applicant, course, 'requests')
    await expect(applicant.getByRole('button', { name: /Approve|Reject|Withdraw/ })).toHaveCount(0)
    expect(await applicant.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await applicant.screenshot({ path: testInfo.outputPath('application-history-mobile.png'), fullPage: true })
  } finally { await Promise.all(contexts.map(context => context.close())) }
})

test('real cutoff expires private history without assignment and forbids approval and rejection while profiles remain readable', async ({ browser }, testInfo) => {
  test.setTimeout(60_000)
  const contexts = await Promise.all([browser.newContext(), browser.newContext(), browser.newContext()])
  try {
    const [teacher, manager, applicant] = await Promise.all(contexts.map(context => context.newPage())) as [Page, Page, Page]
    await login(teacher, 'batch-teacher'); await login(manager, 'batch-student-1'); await login(applicant, 'batch-student-3')
    const course = await create(teacher, 'BATCH-607', new Date(Date.now() + 16_000).toISOString(), [2, 2])
    const group = (await discover(manager, course)).groups[0]!
    await join(manager, course, group.id)
    const attempt = await apply(applicant, course, group.id)
    await open(manager, course, 'requests')
    await expect(manager.getByRole('button', { name: 'Reject', exact: true })).toBeVisible()
    await expect.poll(async () => (await history(applicant, course)).status, { timeout: 25_000, intervals: [100, 250, 500] }).toBe('FINALIZING')
    for (const decision of ['APPROVE', 'REJECT']) {
      const denied = await post(manager, `/api/courses/${course}/applications/${attempt.id}/decision`, { decision })
      expect(denied.status()).toBe(409); expect((await denied.json()).code).toBe('ACTIVITY_CLOSED')
    }
    const closed = await history(applicant, course)
    expect(closed.items[0]!.status).toBe('EXPIRED')
    const writes: string[] = []
    manager.on('request', request => { if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method()) && request.url().includes('/api/')) writes.push(request.url()) })
    await manager.getByRole('button', { name: 'Refresh requests', exact: true }).click()
    await expect(manager.getByRole('heading', { name: 'Grouping is finalizing', exact: true })).toBeVisible()
    await expect(manager.getByRole('article')).toContainText('EXPIRED')
    await expect(manager.getByRole('button', { name: /Approve|Reject/ })).toHaveCount(0)
    await manager.reload(); expect(writes).toEqual([])
    expect(await history(applicant, course)).toEqual(closed)
    expect((await discover(applicant, course)).groups).toEqual([])
    expect((await discover(applicant, course)).ownGroupId).toBeNull()
    await applicant.goto(`/#/courses/${course}/classmates`)
    await applicant.getByRole('button', { name: 'View profile 陳 Batch 一', exact: true }).click()
    await expect(applicant.getByRole('dialog', { name: '陳 Batch 一', exact: true })).toContainText('00007001')
    await applicant.screenshot({ path: testInfo.outputPath('application-cutoff-profile.png'), fullPage: true })
  } finally { await Promise.all(contexts.map(context => context.close())) }
})
