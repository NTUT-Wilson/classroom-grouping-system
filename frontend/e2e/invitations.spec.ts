import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

type Profile = { accountId: string; studentId: string; studentName: string }
type Attempt = { id: string; groupId: string; status: string; reason: string | null; sender: Profile; recipient: Profile }
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
  const response = await page.request.get(`/api/courses/${course}/invitations${query}`)
  expect(response.status()).toBe(200); return response.json()
}
async function post(page: Page, path: string, data: object) {
  return page.request.post(path, { headers: await headers(page), data: { commandKey: crypto.randomUUID(), ...data } })
}
async function join(page: Page, course: string, group: string) {
  expect((await post(page, `/api/courses/${course}/groups/${group}/join`, {})).status()).toBe(200)
}
async function application(page: Page, course: string, group: string) {
  const response = await post(page, `/api/courses/${course}/groups/${group}/applications`, {})
  expect(response.status()).toBe(200)
}
async function applications(page: Page, course: string): Promise<History> {
  const response = await page.request.get(`/api/courses/${course}/applications`)
  expect(response.status()).toBe(200); return response.json()
}
async function open(page: Page, course: string, invitations = true) {
  await page.goto(`/#/courses/${course}/requests`)
  await page.getByRole('button', { name: invitations ? 'Invitations' : 'Applications', exact: true }).click()
  const refresh = page.getByRole('button', { name: 'Refresh requests', exact: true })
  await expect(refresh).toBeEnabled(); await refresh.click(); await expect(refresh).toBeEnabled()
}
async function invite(page: Page, name: string, uncertain = false) {
  await page.getByRole('button', { name: `Invite ${name}`, exact: true }).click()
  await page.getByRole('button', { name: 'Confirm invitation', exact: true }).click()
  if (!uncertain) await expect(page.getByRole('status')).toContainText('Invitation recorded.')
}
function invitation(page: Page, name: string, group = 1) {
  return page.getByRole('article', { name: `Invitation to ${name} for Group ${group}`, exact: true }).filter({ hasText: 'PENDING' })
}

test('private invitations coexist with applications, retain rejection history, and atomically reconcile lost committed responses', async ({ browser }, testInfo) => {
  test.setTimeout(150_000)
  const contexts = await Promise.all(Array.from({ length: 7 }, (_, index) => browser.newContext(index === 3 ? { viewport: { width: 390, height: 844 } } : {})))
  try {
    const pages = await Promise.all(contexts.map(context => context.newPage()))
    const [teacher, m1, m2, recipient, second, rival, peer] = pages as [Page, Page, Page, Page, Page, Page, Page]
    for (const [index, page] of pages.entries()) await login(page, index === 0 ? 'batch-teacher' : `batch-student-${index}`)
    const course = await create(teacher, 'BATCH-707', '2099-03-15T18:30:00Z', [3, 3, 2])
    const groups = (await discover(m1, course)).groups
    await join(m1, course, groups[0]!.id); await join(m2, course, groups[1]!.id)
    await application(recipient, course, groups[0]!.id)
    await open(m1, course)
    await m1.getByRole('button', { name: 'Invite Sam Batch 三', exact: true }).click()
    expect((await history(m1, course)).items).toEqual([])
    await m1.getByRole('button', { name: 'Cancel', exact: true }).click()
    await invite(m1, 'Sam Batch 三')
    const rejected = (await history(recipient, course)).items[0]!
    expect((await discover(recipient, course)).groups[0]!.availablePlaces).toBe(2)
    expect((await discover(recipient, course)).ownGroupId).toBeNull()
    expect((await teacher.request.get(`/api/courses/${course}/invitations`)).status()).toBe(403)
    expect((await history(peer, course)).items).toEqual([])
    expect((await history(m2, course)).items).toEqual([])
    for (const page of [peer, m2, m1]) expect((await post(page, `/api/courses/${course}/invitations/${rejected.id}/decision`, { decision: 'ACCEPT' })).status()).toBe(404)
    expect((await post(teacher, `/api/courses/${course}/groups/${groups[0]!.id}/invitations`, { recipientId: rejected.recipient.accountId })).status()).toBe(403)
    expect((await post(peer, `/api/courses/${course}/groups/${groups[0]!.id}/invitations`, { recipientId: rejected.recipient.accountId })).status()).toBe(404)
    await open(recipient, course)
    await invitation(recipient, 'Sam Batch 三').getByRole('button', { name: 'Reject invitation', exact: true }).click()
    await recipient.getByRole('button', { name: 'Confirm rejection', exact: true }).click()
    await expect(recipient.getByRole('article')).toContainText('REJECTED')
    expect((await applications(recipient, course)).items[0]!.status).toBe('PENDING')
    await open(m1, course)
    const sendPath = `/api/courses/${course}/groups/${groups[0]!.id}/invitations`
    let sendPosts = 0
    await m1.route(`**${sendPath}`, async route => { sendPosts++; const response = await route.fetch(); expect(response.status()).toBe(200); await route.abort('failed') })
    await invite(m1, 'Sam Batch 三', true)
    await expect(m1.getByRole('alert')).toContainText('could not be confirmed')
    await open(m1, course); await m1.reload(); expect(sendPosts).toBe(1)
    await m1.unroute(`**${sendPath}`)
    expect((await history(recipient, course)).items.map(item => item.status)).toEqual(['PENDING', 'REJECTED'])
    const firstPage = await history(recipient, course, '?limit=1')
    expect(firstPage.nextCursor).toBeTruthy()
    const cursor = encodeURIComponent(firstPage.nextCursor!)
    expect((await history(recipient, course, `?limit=1&cursor=${cursor}`)).items[0]!.id).toBe(rejected.id)
    expect((await peer.request.get(`/api/courses/${course}/invitations?limit=1&cursor=${cursor}`)).status()).toBe(400)
    await open(m1, course, false)
    await m1.getByRole('button', { name: 'Reject', exact: true }).click(); await m1.getByRole('button', { name: 'Confirm rejection', exact: true }).click()
    await expect(m1.getByRole('status')).toContainText('Application response confirmed.')
    expect((await history(recipient, course)).items[0]!.status).toBe('PENDING')
    await application(recipient, course, groups[1]!.id)
    await open(m2, course); await invite(m2, 'Sam Batch 三')
    await open(m1, course); await invite(m1, 'Riley Batch 四'); await invite(m1, 'Morgan Batch 五')
    await application(second, course, groups[0]!.id); await application(rival, course, groups[0]!.id)
    await open(recipient, course)
    const accepted = (await history(recipient, course)).items.find(item => item.groupId === groups[0]!.id && item.status === 'PENDING')!
    const acceptPath = `/api/courses/${course}/invitations/${accepted.id}/decision`
    let acceptPosts = 0
    await recipient.route(`**${acceptPath}`, async route => { acceptPosts++; const response = await route.fetch(); expect(response.status()).toBe(200); await route.abort('failed') })
    await invitation(recipient, 'Sam Batch 三').getByRole('button', { name: 'Accept invitation', exact: true }).click()
    await recipient.getByRole('button', { name: 'Confirm acceptance', exact: true }).click()
    await expect(recipient.getByRole('alert')).toContainText('could not be confirmed')
    await open(recipient, course); await recipient.reload(); expect(acceptPosts).toBe(1)
    expect((await history(recipient, course)).items.find(item => item.id === accepted.id)!.status).toBe('ACCEPTED')
    expect((await history(recipient, course)).items.find(item => item.groupId === groups[1]!.id)!.reason).toBe('Joined a group')
    expect((await applications(recipient, course)).items.find(item => item.groupId === groups[1]!.id)!.reason).toBe('Joined a group')
    expect((await history(recipient, course)).items.find(item => item.id === rejected.id)!.status).toBe('REJECTED')
    await open(second, course)
    await invitation(second, 'Riley Batch 四').getByRole('button', { name: 'Accept invitation', exact: true }).click(); await second.getByRole('button', { name: 'Confirm acceptance', exact: true }).click()
    await expect(second.getByRole('status')).toContainText('Invitation response confirmed.')
    expect((await history(rival, course)).items[0]!.reason).toBe('Group full')
    expect((await applications(rival, course)).items[0]!.reason).toBe('Group full')
    expect((await applications(second, course)).items[0]!.reason).toBe('Joined a group')
    expect((await discover(recipient, course)).groups[0]!.members).toHaveLength(3)
    await open(recipient, course)
    await expect(recipient.getByRole('button', { name: /Accept invitation|Reject invitation|Withdraw|Cancel invitation/ })).toHaveCount(0)
    expect(await recipient.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await recipient.screenshot({ path: testInfo.outputPath('invitation-history-mobile.png'), fullPage: true })
  } finally { await Promise.all(contexts.map(context => context.close())) }
})

test('actual cutoff expires invitations without assignments, blocks acceptance and rejection, and preserves profile access', async ({ browser }, testInfo) => {
  test.setTimeout(60_000)
  const contexts = await Promise.all(Array.from({ length: 3 }, () => browser.newContext()))
  try {
    const [teacher, manager, recipient] = await Promise.all(contexts.map(context => context.newPage())) as [Page, Page, Page]
    await login(teacher, 'batch-teacher'); await login(manager, 'batch-student-1'); await login(recipient, 'batch-student-3')
    const course = await create(teacher, 'BATCH-708', new Date(Date.now() + 16_000).toISOString(), [2, 2])
    const group = (await discover(manager, course)).groups[0]!
    await join(manager, course, group.id); await open(manager, course); await invite(manager, 'Sam Batch 三')
    const attempt = (await history(recipient, course)).items[0]!
    await open(recipient, course)
    await expect(recipient.getByRole('button', { name: 'Reject invitation', exact: true })).toBeVisible()
    await expect.poll(async () => (await history(recipient, course)).status, { timeout: 25_000, intervals: [100, 250, 500] }).toBe('FINALIZING')
    for (const decision of ['ACCEPT', 'REJECT']) {
      const denied = await post(recipient, `/api/courses/${course}/invitations/${attempt.id}/decision`, { decision })
      expect(denied.status()).toBe(409); expect((await denied.json()).code).toBe('ACTIVITY_CLOSED')
    }
    const closed = await history(recipient, course); expect(closed.items[0]!.status).toBe('EXPIRED')
    const writes: string[] = []
    recipient.on('request', request => { if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method()) && request.url().includes('/api/')) writes.push(request.url()) })
    await open(recipient, course)
    await expect(recipient.getByRole('heading', { name: 'Grouping is finalizing', exact: true })).toBeVisible()
    await expect(recipient.getByRole('article')).toContainText('EXPIRED')
    await expect(recipient.getByRole('button', { name: /Accept invitation|Reject invitation/ })).toHaveCount(0)
    await recipient.reload(); expect(writes).toEqual([])
    expect(await history(recipient, course)).toEqual(closed)
    expect((await discover(recipient, course)).groups).toEqual([]); expect((await discover(recipient, course)).ownGroupId).toBeNull()
    await recipient.goto(`/#/courses/${course}/classmates`)
    await recipient.getByRole('button', { name: 'View profile 陳 Batch 一', exact: true }).click()
    await expect(recipient.getByRole('dialog', { name: '陳 Batch 一', exact: true })).toContainText('00007001')
    await recipient.screenshot({ path: testInfo.outputPath('invitation-cutoff-profile.png'), fullPage: true })
  } finally { await Promise.all(contexts.map(context => context.close())) }
})
