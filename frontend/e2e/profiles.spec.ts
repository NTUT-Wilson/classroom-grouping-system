import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

type Course = { id: string; code: string; name: string }
type Profile = { accountId: string; studentId: string; studentName: string; aboutMe: string }
function fixturePassword() {
  const value = process.env.BATCH_FIXTURE_PASSWORD
  if (!value) throw new Error('Enable app.batch-fixture.enabled on the backend and supply BATCH_FIXTURE_PASSWORD to the backend and Playwright')
  return value
}
async function login(page: Page, username: string) {
  await page.goto('/')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(fixturePassword())
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByText(username, { exact: true })).toBeVisible()
}
async function course(page: Page, code: string) {
  const response = await page.request.get('/api/courses')
  expect(response.status()).toBe(200)
  const found = ((await response.json()) as Course[]).find(value => value.code === code)
  if (!found) throw new Error(`Missing opt-in synthetic course ${code}`)
  return found
}
function ownUrl(id: string) { return `/api/courses/${id}/about-me` }
async function own(page: Page, id: string): Promise<Profile> {
  const response = await page.request.get(ownUrl(id))
  expect(response.status()).toBe(200)
  return response.json() as Promise<Profile>
}
async function csrfHeaders(page: Page) {
  const response = await page.request.get('/api/auth/csrf')
  expect(response.status()).toBe(200)
  const csrf = await response.json() as { headerName: string; token: string }
  return { [csrf.headerName]: csrf.token }
}
async function restore(page: Page, id: string, aboutMe: string) {
  const response = await page.request.put(ownUrl(id), { headers: await csrfHeaders(page), data: { aboutMe } })
  expect(response.status()).toBe(200)
}
async function classmates(page: Page, id: string): Promise<Profile[]> {
  const response = await page.request.get(`/api/courses/${id}/classmates`)
  expect(response.status()).toBe(200)
  return response.json() as Promise<Profile[]>
}
async function visitProfiles(page: Page, id: string) {
  await page.goto(`/#/courses/${id}/classmates`)
  await expect(page.getByRole('heading', { name: 'Classmates', exact: true })).toBeVisible()
  await expect(page.getByLabel('About me', { exact: true })).toBeVisible()
}

test('own introduction persists, peers read literal Unicode and HTML, courses remain isolated', async ({ browser }) => {
  const writerContext = await browser.newContext(); const peerContext = await browser.newContext()
  const writer = await writerContext.newPage(); const peer = await peerContext.newPage()
  let id: string | undefined; let before: string | undefined
  try {
    await login(writer, 'batch-student-1'); await login(peer, 'batch-student-2')
    const first = await course(writer, 'BATCH-101'); const second = await course(writer, 'BATCH-202')
    id = first.id; before = (await own(writer, id)).aboutMe
    const separateBefore = (await own(writer, second.id)).aboutMe
    const introduction = `你好 👩🏽‍💻 café é
<script>window.profileInjected=true</script> <b>plain & exact</b>`
    await visitProfiles(writer, id)
    await writer.getByLabel('About me', { exact: true }).fill(introduction)
    await writer.getByRole('button', { name: 'Save introduction', exact: true }).click()
    await expect(writer.getByText('Your About me has been saved for this course.', { exact: true })).toBeVisible()
    await writer.reload()
    await expect(writer.getByLabel('About me', { exact: true })).toHaveValue(introduction)
    expect((await own(writer, second.id)).aboutMe).toBe(separateBefore)
    const profiles = await classmates(peer, id)
    expect(profiles).toHaveLength(8)
    const saved = profiles.find(value => value.studentId === '00007001')!
    expect(saved).toEqual({ accountId: expect.any(String), studentId: '00007001', studentName: '陳 Batch 一', aboutMe: introduction })
    expect(profiles.find(value => value.studentId === '00007002')?.studentName).toBe('Alex <Batch> & 二')
    expect(await classmates(peer, second.id)).toHaveLength(2)
    await visitProfiles(peer, id)
    await peer.getByRole('button', { name: 'View profile 陳 Batch 一', exact: true }).click()
    const dialog = peer.getByRole('dialog', { name: '陳 Batch 一', exact: true })
    await expect(dialog).toContainText('00007001')
    await expect(dialog).toContainText(introduction)
    expect(await peer.evaluate(() => 'profileInjected' in window)).toBe(false)
    expect(await dialog.locator('script, b').count()).toBe(0)
    await dialog.getByRole('button', { name: 'Close profile' }).click()
    await writer.getByLabel('About me', { exact: true }).fill('')
    await writer.getByRole('button', { name: 'Save introduction', exact: true }).click()
    await expect.poll(async () => (await own(writer, first.id)).aboutMe).toBe('')
  } finally {
    if (id && before !== undefined) await restore(writer, id, before)
    await writerContext.close(); await peerContext.close()
  }
})

test('server rejects peer writes, teacher access, missing CSRF, oversized text and hidden-course reads', async ({ browser }) => {
  const studentContext = await browser.newContext(); const teacherContext = await browser.newContext()
  const student = await studentContext.newPage(); const teacher = await teacherContext.newPage()
  try {
    await login(student, 'batch-student-3'); await login(teacher, 'batch-teacher')
    const first = await course(student, 'BATCH-101'); const hidden = await course(teacher, 'BATCH-202')
    const before = await own(student, first.id)
    const missingCsrf = await student.request.put(ownUrl(first.id), { data: { aboutMe: 'must not persist' } })
    expect(missingCsrf.status()).toBe(403)
    const headers = await csrfHeaders(student)
    const tooLong = await student.request.put(ownUrl(first.id), { headers, data: { aboutMe: '🙂'.repeat(501) } })
    expect(tooLong.status()).toBe(400)
    const peer = (await classmates(student, first.id)).find(value => value.studentId === '00007001')!
    const peerWrite = await student.request.put(`/api/courses/${first.id}/classmates/${peer.accountId}`, { headers, data: { aboutMe: 'must not persist' } })
    expect(peerWrite.status()).toBe(403)
    expect((await classmates(student, first.id)).find(value => value.accountId === peer.accountId)).toEqual(peer)
    for (const path of [ownUrl(first.id), `/api/courses/${first.id}/classmates`]) {
      expect((await teacher.request.get(path)).status()).toBe(403)
    }
    expect((await teacher.request.put(ownUrl(first.id), { headers: await csrfHeaders(teacher), data: { aboutMe: 'must not persist' } })).status()).toBe(403)
    const unknown = '00000000-0000-0000-0000-000000000000'
    for (const suffix of ['classmates', 'about-me']) {
      const denied = await student.request.get(`/api/courses/${hidden.id}/${suffix}`)
      const absent = await student.request.get(`/api/courses/${unknown}/${suffix}`)
      expect(denied.status()).toBe(404); expect(absent.status()).toBe(404)
      expect((await denied.json()).code).toBe((await absent.json()).code)
    }
    expect(await own(student, first.id)).toEqual(before)
    await teacher.goto(`/#/courses/${first.id}/classmates`)
    await expect(teacher.getByLabel('About me', { exact: true })).toHaveCount(0)
  } finally { await studentContext.close(); await teacherContext.close() }
})

test('a lost successful PUT response requires reconciliation without automatic replay', async ({ page }) => {
  await login(page, 'batch-student-1')
  const first = await course(page, 'BATCH-101'); const before = (await own(page, first.id)).aboutMe
  const introduction = `Lost-response synthetic introduction ${Date.now()}`
  let puts = 0
  try {
    await visitProfiles(page, first.id)
    await page.route(`**${ownUrl(first.id)}`, async route => {
      if (route.request().method() !== 'PUT') { await route.continue(); return }
      puts++
      const saved = await route.fetch()
      expect(saved.status()).toBe(200)
      await route.abort('failed')
    })
    await page.getByLabel('About me', { exact: true }).fill(introduction)
    await page.getByRole('button', { name: 'Save introduction', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('Save could not be confirmed.')
    await expect(page.getByRole('button', { name: 'Save introduction', exact: true })).toBeDisabled()
    expect(puts).toBe(1)
    expect((await own(page, first.id)).aboutMe).toBe(introduction)
    await page.getByRole('button', { name: 'Check saved introduction', exact: true }).click()
    await expect(page.getByRole('button', { name: 'Save introduction', exact: true })).toBeEnabled()
    await expect(page.getByLabel('About me', { exact: true })).toHaveValue(introduction)
    expect(puts).toBe(1)
  } finally { await page.unroute(`**${ownUrl(first.id)}`); await restore(page, first.id, before) }
})

for (const width of [1280, 390]) test(`profile dialog supports keyboard, exact identities and containment at ${width}px`, async ({ page }, testInfo) => {
  await page.setViewportSize({ width, height: 844 })
  await login(page, 'batch-student-2')
  const first = await course(page, 'BATCH-101')
  await visitProfiles(page, first.id)
  const trigger = page.getByRole('button', { name: 'View profile Alex <Batch> & 二', exact: true })
  await trigger.press('Enter')
  const dialog = page.getByRole('dialog', { name: 'Alex <Batch> & 二', exact: true })
  await expect(dialog).toBeVisible()
  await expect(dialog).toContainText('00007002')
  await expect(dialog.getByRole('button', { name: 'Close profile' })).toBeFocused()
  await page.keyboard.press('Tab')
  const tabFocus = await dialog.evaluate(element => ({
    documentFocused: document.hasFocus(),
    insideDialog: element.contains(document.activeElement),
    backgroundControlFocused: !!document.activeElement?.matches('button, input, textarea, select, a[href], [tabindex]')
      && !element.contains(document.activeElement),
    modal: element.matches(':modal'),
  }))
  expect(tabFocus.modal).toBe(true)
  if (tabFocus.documentFocused) {
    expect(tabFocus.insideDialog).toBe(true)
  } else {
    // Native modal Tab navigation may pass through browser chrome before wrapping.
    expect(tabFocus.backgroundControlFocused).toBe(false)
    await page.keyboard.press('Tab')
    await expect(dialog.getByRole('button', { name: 'Close profile' })).toBeFocused()
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath(`profile-dialog-${width}.png`), fullPage: true })
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  await expect(trigger).toBeFocused()
})
