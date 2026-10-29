import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

type Person = { accountId: string; studentName: string; studentId: string }
type Group = { id: string; name: string; capacity: number; availablePlaces: number; manager: Person | null; members: Person[] }
type Discovery = { ownGroupId: string | null; groups: Group[] }
type Attempt = { id: string; status: string; reason: string | null; manager?: Person; sender?: Person; applicant?: Person; recipient?: Person }
type History = { items: Attempt[]; nextCursor: string | null }

async function login(page: Page, username: string) {
  const password = process.env.BATCH_FIXTURE_PASSWORD
  if (!password) throw new Error('Enable batch fixtures and set BATCH_FIXTURE_PASSWORD in server and test processes')
  await page.goto('/')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByText(username, { exact: true })).toBeVisible()
}
async function read<T>(page: Page, path: string): Promise<T> {
  const response = await page.request.get(path)
  expect(response.status()).toBe(200)
  return response.json()
}
async function post(page: Page, path: string, data: object = {}, key: string = crypto.randomUUID()) {
  const csrf = await read<{ headerName: string; token: string }>(page, '/api/auth/csrf')
  return page.request.post(path, { headers: { [csrf.headerName]: csrf.token }, data: { commandKey: key, ...data } })
}
async function command(page: Page, path: string, data: object = {}, key: string = crypto.randomUUID()) {
  const response = await post(page, path, data, key)
  expect(response.status()).toBe(200)
  return response
}
async function groups(page: Page, course: string) {
  await page.goto(`/#/courses/${course}/groups`)
  await expect(page.getByRole('button', { name: 'Refresh groups', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: 'Refresh groups', exact: true }).click()
  await expect(page.getByRole('heading', { name: /Your membership|You are ungrouped/ })).toBeVisible()
}
async function requests(page: Page, course: string, view: 'Applications' | 'Invitations' = 'Applications') {
  await page.goto(`/#/courses/${course}/requests`)
  await page.getByRole('button', { name: view, exact: true }).click()
  const refresh = page.getByRole('button', { name: 'Refresh requests', exact: true })
  await expect(refresh).toBeEnabled(); await refresh.click(); await expect(refresh).toBeEnabled()
}

test('departure, succession, transfer and empty reuse preserve private request history and reconcile committed lost responses', async ({ browser }, testInfo) => {
  test.setTimeout(180_000)
  const contexts = await Promise.all(Array.from({ length: 7 }, () => browser.newContext({ viewport: { width: 390, height: 844 } })))
  try {
    const pages = await Promise.all(contexts.map(context => context.newPage()))
    const [teacher, alex, blair, casey, devon, ellis, frankie] = pages as [Page, Page, Page, Page, Page, Page, Page]
    for (const [index, page] of pages.entries()) await login(page, index ? `v2-student-${index}` : 'v2-teacher')
    const courses = await read<{ id: string; code: string }[]>(teacher, '/api/courses')
    const course = courses.find(value => value.code === 'V2-101')!.id
    const base = `/api/courses/${course}`
    // This file owns V2-101 and requires a disposable fresh database; never reset an activity.
    expect((await read<{ activity: unknown }>(teacher, `${base}/activity`)).activity).toBeNull()
    await command(teacher, `${base}/activity`, { instructions: 'Synthetic lifecycle exercise', deadline: '2099-07-15T14:00:00Z', capacities: [4, 4] })
    const discover = (page = alex) => read<Discovery>(page, `${base}/grouping`)
    const history = (page: Page, kind = 'applications', query = '') => read<History>(page, `${base}/${kind}${query}`)
    const group = (await discover()).groups[0]!
    const path = `${base}/groups/${group.id}`
    const roster = await read<Person[]>(alex, `${base}/classmates`)
    const person = (name: string) => roster.find(value => value.studentName === `${name} Fixture`)!
    await command(alex, `${path}/join`)
    async function apply(page: Page) {
      await command(page, `${path}/applications`)
      return (await history(page)).items.find(value => value.status === 'PENDING')!
    }
    for (const page of [blair, casey]) {
      const attempt = await apply(page)
      await command(alex, `${base}/applications/${attempt.id}/decision`, { decision: 'APPROVE' })
    }
    const rejected = await apply(devon)
    const decisionKey = crypto.randomUUID()
    await command(alex, `${base}/applications/${rejected.id}/decision`, { decision: 'REJECT' }, decisionKey)
    const pending = await apply(devon)
    const inviteKey = crypto.randomUUID()
    await command(alex, `${path}/invitations`, { recipientId: person('Ellis').accountId }, inviteKey)
    const invitation = (await history(ellis, 'invitations')).items[0]!
    // Two invitation pages provide an authority-sensitive cursor as well as completed history.
    await command(ellis, `${base}/invitations/${invitation.id}/decision`, { decision: 'REJECT' })
    await command(alex, `${path}/invitations`, { recipientId: person('Ellis').accountId })
    const pendingInvitation = (await history(ellis, 'invitations')).items.find(value => value.status === 'PENDING')!
    await command(alex, `${path}/invitations`, { recipientId: person('Devon').accountId })
    const inheritedRecipientInvitation = (await history(devon, 'invitations')).items[0]!
    const applicationCursor = (await history(alex, 'applications', '?limit=1')).nextCursor!
    const invitationCursor = (await history(alex, 'invitations', '?limit=1')).nextCursor!
    expect(applicationCursor).toBeTruthy(); expect(invitationCursor).toBeTruthy()

    // Cancel and Escape restore focus and issue no write; keyboard confirmation makes one explicit command.
    await groups(alex, course)
    const transfer = alex.getByRole('button', { name: 'Transfer manager role to Blair Fixture', exact: true })
    await transfer.focus(); await alex.keyboard.press('Enter')
    await expect(alex.getByRole('dialog', { name: 'Transfer manager role?', exact: true })).toContainText('00008002')
    await expect(alex.getByRole('button', { name: 'Cancel', exact: true })).toBeFocused()
    await alex.keyboard.press('Escape'); await expect(transfer).toBeFocused()
    expect((await discover()).groups[0]!.manager?.accountId).toBe(person('Alex').accountId)
    let transfers = 0
    let transferBody: { commandKey: string; targetMemberId: string } | undefined
    await alex.route(`**${path}/manager-transfer`, async route => {
      transfers++; transferBody = route.request().postDataJSON()
      const response = await route.fetch(); expect(response.status()).toBe(200); await route.abort('failed')
    })
    await alex.keyboard.press('Enter')
    await alex.getByRole('button', { name: 'Confirm transfer', exact: true }).click()
    await expect(alex.getByRole('alert')).toContainText('could not be confirmed')
    await expect(alex.getByRole('button', { name: 'Leave group', exact: true })).toHaveCount(0)
    await alex.getByRole('button', { name: 'Check current groups', exact: true }).click()
    await expect(alex.getByText('You are a member of Group 1.', { exact: true })).toBeVisible()
    await alex.reload(); await expect(alex.getByRole('button', { name: 'Leave group', exact: true })).toBeVisible()
    expect(transfers).toBe(1); await alex.unroute(`**${path}/manager-transfer`)
    expect((await discover()).groups[0]!.members).toHaveLength(3)
    expect((await discover()).groups[0]!.manager?.accountId).toBe(person('Blair').accountId)
    // Receipt replay returns current discovery and cannot transfer a second time.
    await command(alex, `${path}/manager-transfer`, { targetMemberId: transferBody!.targetMemberId }, transferBody!.commandKey)
    expect((await discover()).groups[0]!.manager?.accountId).toBe(person('Blair').accountId)
    for (const [kind, cursor] of [['applications', applicationCursor], ['invitations', invitationCursor]]) {
      expect((await history(alex, kind)).items).toEqual([])
      expect((await history(alex, kind, `?limit=1&cursor=${encodeURIComponent(cursor!)}`)).items).toEqual([])
    }
    expect((await post(alex, `${base}/applications/${pending.id}/decision`, { decision: 'APPROVE' })).status()).toBe(404)
    expect((await post(alex, `${path}/invitations`, { recipientId: person('Frankie').accountId })).status()).toBe(404)
    expect((await (await command(alex, `${base}/applications/${rejected.id}/decision`, { decision: 'REJECT' }, decisionKey)).json()).items).toEqual([])
    expect((await (await command(alex, `${path}/invitations`, { recipientId: person('Ellis').accountId }, inviteKey)).json()).items).toEqual([])
    await requests(alex, course)
    await expect(alex.getByRole('article')).toHaveCount(0)
    await requests(alex, course, 'Invitations')
    await expect(alex.getByRole('article')).toHaveCount(0)
    await expect(alex.getByRole('heading', { name: 'No invitations yet', exact: true })).toBeVisible()
    // A recipient can still answer an invitation sent by the former manager. Rejection leaves the application independent.
    await requests(devon, course, 'Invitations')
    const recipientCard = devon.getByRole('article', { name: 'Invitation to Devon Fixture for Group 1', exact: true })
    await expect(recipientCard).toContainText('Original sender · Alex Fixture')
    await recipientCard.getByRole('button', { name: 'Reject invitation', exact: true }).click()
    await devon.getByRole('button', { name: 'Confirm rejection', exact: true }).click()
    await expect(devon.getByRole('status')).toContainText('Invitation response confirmed.')
    expect((await history(devon, 'invitations')).items.find(value => value.id === inheritedRecipientInvitation.id)).toMatchObject({
      status: 'REJECTED', sender: { accountId: person('Alex').accountId }, recipient: { accountId: person('Devon').accountId },
    })
    expect((await history(devon)).items.find(value => value.id === pending.id)!.status).toBe('PENDING')
    expect((await history(alex, 'invitations')).items).toEqual([])
    await requests(blair, course)
    const inherited = blair.getByRole('article').filter({ hasText: 'PENDING' })
    await expect(inherited).toContainText('Manager at submission · Alex Fixture')
    await inherited.getByRole('button', { name: 'Reject', exact: true }).click()
    await blair.getByRole('button', { name: 'Confirm rejection', exact: true }).click()
    await expect(blair.getByRole('status')).toContainText('Application response confirmed.')
    expect((await history(devon)).items.find(value => value.id === pending.id)!.status).toBe('REJECTED')
    const remainingPending = await apply(devon)
    await requests(blair, course, 'Invitations')
    await expect(blair.getByRole('article', { name: 'Invitation to Ellis Fixture for Group 1', exact: true }).filter({ hasText: 'PENDING' })).toContainText('Original sender · Alex Fixture')
    await expect(blair.getByRole('article', { name: 'Invitation to Devon Fixture for Group 1', exact: true })).toContainText('REJECTED')
    expect((await history(blair, 'invitations')).items.find(value => value.id === inheritedRecipientInvitation.id)).toMatchObject({
      status: 'REJECTED', sender: { accountId: person('Alex').accountId },
    })
    await expect(blair.getByRole('button', { name: 'Invite Frankie Fixture', exact: true })).toBeVisible()
    await expect(blair.getByRole('button', { name: /Accept invitation|Reject invitation/ })).toHaveCount(0)

    // Ordinary departure frees capacity; return uses a new application and does not restore a role.
    await groups(casey, course)
    const leave = casey.getByRole('button', { name: 'Leave group', exact: true })
    await leave.click(); await casey.getByRole('button', { name: 'Cancel', exact: true }).click(); await expect(leave).toBeFocused()
    await leave.click(); await casey.getByRole('button', { name: 'Confirm departure', exact: true }).click()
    await expect(casey.getByRole('heading', { name: 'You are ungrouped', exact: true })).toBeVisible()
    expect((await discover()).groups[0]!.manager?.accountId).toBe(person('Blair').accountId)
    expect((await discover()).groups[0]!.availablePlaces).toBe(2)
    await casey.getByRole('button', { name: 'Apply to Group 1', exact: true }).click()
    await casey.getByRole('button', { name: 'Confirm application', exact: true }).click()
    await expect(casey.getByRole('status')).toContainText('Application recorded.')
    const returning = (await history(casey)).items.find(value => value.status === 'PENDING')!
    await command(blair, `${base}/applications/${returning.id}/decision`, { decision: 'APPROVE' })
    expect((await discover(casey)).ownGroupId).toBe(group.id)
    expect((await history(casey)).items.some(value => value.status === 'ACCEPTED')).toBe(true)

    // Manager departure with two eligible successors preserves pending work; then the sole successor is deterministic.
    await groups(blair, course)
    await blair.getByRole('button', { name: 'Leave group', exact: true }).click()
    await blair.getByRole('button', { name: 'Confirm departure', exact: true }).click()
    await expect(blair.getByRole('heading', { name: 'You are ungrouped', exact: true })).toBeVisible()
    const successor = (await discover()).groups[0]!.manager!.accountId
    expect([person('Alex').accountId, person('Casey').accountId]).toContain(successor)
    expect((await history(devon)).items.find(value => value.id === remainingPending.id)!.status).toBe('PENDING')
    expect((await history(ellis, 'invitations')).items.find(value => value.id === pendingInvitation.id)!.status).toBe('PENDING')
    const formerManagerHistory = (await history(blair)).items
    expect(formerManagerHistory).toHaveLength(1)
    expect(formerManagerHistory[0]).toMatchObject({ status: 'ACCEPTED', applicant: { accountId: person('Blair').accountId } })
    expect(formerManagerHistory.some(value => value.id === remainingPending.id)).toBe(false)
    const successorPage = successor === person('Alex').accountId ? alex : casey
    const lastPage = successorPage === alex ? casey : alex
    const lastId = successorPage === alex ? person('Casey').accountId : person('Alex').accountId
    await groups(successorPage, course)
    await successorPage.getByRole('button', { name: 'Leave group', exact: true }).click()
    await successorPage.getByRole('button', { name: 'Confirm departure', exact: true }).click()
    await expect(successorPage.getByRole('heading', { name: 'You are ungrouped', exact: true })).toBeVisible()
    expect((await discover()).groups[0]!.manager?.accountId).toBe(lastId)
    await groups(lastPage, course)
    let departures = 0
    let departureKey = ''
    await lastPage.route(`**${path}/leave`, async route => {
      departures++; departureKey = route.request().postDataJSON().commandKey
      const response = await route.fetch(); expect(response.status()).toBe(200); await route.abort('failed')
    })
    await lastPage.getByRole('button', { name: 'Leave group', exact: true }).click()
    await lastPage.getByRole('button', { name: 'Confirm departure', exact: true }).click()
    await expect(lastPage.getByRole('alert')).toContainText('could not be confirmed')
    await lastPage.getByRole('button', { name: 'Check current groups', exact: true }).click()
    await expect(lastPage.getByRole('button', { name: 'Join Group 1', exact: true })).toBeVisible()
    await lastPage.reload(); await expect(lastPage.getByRole('button', { name: 'Join Group 1', exact: true })).toBeVisible()
    expect(departures).toBe(1); await lastPage.unroute(`**${path}/leave`)
    const empty = (await discover()).groups[0]!
    expect(empty).toMatchObject({ id: group.id, name: group.name, capacity: 4, availablePlaces: 4, manager: null, members: [] })
    expect((await history(devon)).items.find(value => value.id === remainingPending.id)).toMatchObject({ status: 'CANCELED', reason: 'Group empty' })
    expect((await history(devon)).items.find(value => value.id === rejected.id)!.status).toBe('REJECTED')
    expect((await history(ellis, 'invitations')).items.find(value => value.id === pendingInvitation.id)).toMatchObject({ status: 'CANCELED', reason: 'Group empty' })
    expect((await history(ellis, 'invitations')).items.find(value => value.id === invitation.id)!.status).toBe('REJECTED')

    await groups(frankie, course)
    await frankie.getByRole('button', { name: 'Join Group 1', exact: true }).click()
    await frankie.getByRole('button', { name: 'Confirm join', exact: true }).click()
    await expect(frankie.getByRole('status')).toContainText('Your join was confirmed.')
    expect((await discover()).groups[0]!.manager?.accountId).toBe(person('Frankie').accountId)
    await command(lastPage, `${path}/leave`, {}, departureKey)
    expect((await discover()).groups[0]!.manager?.accountId).toBe(person('Frankie').accountId)
    await requests(frankie, course)
    await expect(frankie.getByRole('article').filter({ hasText: 'Group empty' })).toHaveCount(1)
    await expect(frankie.getByRole('button', { name: /Approve|Reject/ })).toHaveCount(0)
    expect((await history(frankie)).items.find(value => value.id === remainingPending.id)!.status).toBe('CANCELED')
    expect(await frankie.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await frankie.screenshot({ path: testInfo.outputPath('lifecycle-inherited-history-mobile.png'), fullPage: true })
    await groups(frankie, course)
    expect(await frankie.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await frankie.screenshot({ path: testInfo.outputPath('lifecycle-reused-group-mobile.png'), fullPage: true })
  } finally { await Promise.all(contexts.map(context => context.close())) }
})
