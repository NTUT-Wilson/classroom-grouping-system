import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { execFileSync } from 'node:child_process'

type Notice = { id: string; courseId: string; title: string; body: string; publishedAt: string; updatedAt: string; revision: number }
type Ack = { id: string; revision: number; deleted: boolean }
type Receipt = { committed: boolean; acknowledgement: Ack | null }

async function login(page: Page, username: string) {
  const password = process.env.BATCH_FIXTURE_PASSWORD
  if (!password) throw new Error('Enable batch fixtures and set BATCH_FIXTURE_PASSWORD server-side and in the Node test process')
  await page.goto('/')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByText(username, { exact: true })).toBeVisible()
}
async function read<T>(page: Page, path: string): Promise<T> {
  const response = await page.request.get(path)
  expect(response.status()).toBe(200)
  expect(response.headers()['cache-control']).toContain('no-store')
  return response.json()
}
async function mutate(page: Page, path: string, data: object, method = 'POST') {
  const csrf = await read<{ headerName: string; token: string }>(page, '/api/auth/csrf')
  return page.request.fetch(path, { method, headers: { [csrf.headerName]: csrf.token }, data })
}
async function open(page: Page, course: string, id?: string) {
  await page.goto(`/#/courses/${course}/announcements${id ? `/${id}` : ''}`)
  await expect(page.getByRole('heading', { name: 'Announcements', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Refresh announcements', exact: true })).toBeEnabled()
}
async function edit(page: Page, title: string, body: string) {
  await page.getByRole('button', { name: 'Edit announcement', exact: true }).click()
  await page.getByLabel('Title', { exact: true }).fill(title)
  await page.getByLabel('Body', { exact: true }).fill(body)
}

// Test-only shell access; no enrollment endpoint is added to the product.
function disposableSql(sql: string): string {
  const database = process.env.E2E_ANNOUNCEMENT_DATABASE_URL
  if (!database) throw new Error('Late/revoked acceptance requires explicit E2E_ANNOUNCEMENT_DATABASE_URL for a disposable loopback PostgreSQL target')
  const target = new URL(database)
  if (!['postgresql:', 'postgres:'].includes(target.protocol) || !['127.0.0.1', 'localhost', '[::1]'].includes(target.hostname) || target.password) throw new Error('Acceptance SQL requires a loopback PostgreSQL URL without embedded password')
  if ((!target.pathname.startsWith('/batch3_') || ['5432', '5433', ''].includes(target.port)) && process.env.E2E_ANNOUNCEMENT_DISPOSABLE !== 'true') throw new Error('Non-batch3 or shared-port targets require explicit E2E_ANNOUNCEMENT_DISPOSABLE=true authorization')
  const executable = process.env.E2E_PSQL_PATH ?? (process.platform === 'win32' ? 'C:\\Program Files\\PostgreSQL\\17\\bin\\psql.exe' : 'psql')
  try {
    return execFileSync(executable, ['--no-psqlrc', '--no-password', '--set', 'ON_ERROR_STOP=1', '--tuples-only', '--no-align', '--dbname', database], {
      input: sql, encoding: 'utf8', env: { ...process.env, PGPASSWORD: process.env.E2E_PGPASSWORD ?? process.env.PGPASSWORD }, stdio: ['pipe', 'pipe', 'pipe'],
    }).trim()
  } catch { throw new Error('Disposable acceptance SQL failed; verify the explicit target, PostgreSQL client and Node-only credentials') }
}

test('A1/A2 publish, co-teacher conflict, student reading and confirmed deletion work without any activity', async ({ browser }, testInfo) => {
  test.setTimeout(120_000)
  const contexts = await Promise.all([
    browser.newContext({ viewport: { width: 390, height: 844 }, timezoneId: 'Pacific/Honolulu' }),
    browser.newContext({ viewport: { width: 1440, height: 1000 }, timezoneId: 'Asia/Taipei' }),
    browser.newContext({ viewport: { width: 390, height: 844 }, timezoneId: 'Asia/Taipei' }),
    browser.newContext(), browser.newContext(),
  ])
  try {
    const [teacher, coTeacher, student, outsiderTeacher, outsiderStudent] = await Promise.all(contexts.map(context => context.newPage()))
    for (const [page, username] of [[teacher!, 'v2-teacher'], [coTeacher!, 'v2-co-teacher'], [student!, 'v2-student-1'], [outsiderTeacher!, 'v2-outsider-teacher'], [outsiderStudent!, 'v2-outsider-student']] as const) await login(page, username)
    const course = (await read<{ id: string; code: string }[]>(teacher!, '/api/courses')).find(value => value.code === 'V2-202')!.id
    const base = `/api/courses/${course}`
    expect((await read<{ activity: unknown }>(teacher!, `${base}/activity`)).activity).toBeNull()
    const title = `${crypto.randomUUID()}${'𝒜'.repeat(114)}` // Exactly 150 code points; more than 150 UTF-16 units.
    const body = '<script>window.announcementExecuted=true</script>\n# Literal Markdown\nSecond line <b>is plain text</b> & preserved.'
    await open(teacher!, course)
    await teacher!.getByRole('button', { name: 'New announcement', exact: true }).click()
    await teacher!.getByLabel('Title', { exact: true }).fill(`${title}X`)
    await teacher!.getByLabel('Body', { exact: true }).fill(body)
    let creates = 0
    teacher!.on('request', request => { if (request.method() === 'POST' && new URL(request.url()).pathname === `${base}/announcements`) creates++ })
    await teacher!.getByRole('button', { name: 'Publish announcement', exact: true }).click()
    await expect(teacher!.getByRole('alert')).toContainText('at most 150')
    expect(creates).toBe(0)
    await teacher!.getByLabel('Title', { exact: true }).fill(title)
    await teacher!.getByRole('button', { name: 'Publish announcement', exact: true }).click()
    await expect(teacher!.getByRole('status')).toContainText('Announcement published.')
    expect(creates).toBe(1)
    const initial = (await read<{ items: Notice[] }>(teacher!, `${base}/announcements`)).items.find(value => value.title === title)!
    expect(initial).toMatchObject({ title, body, revision: 1 })
    const path = `${base}/announcements/${initial.id}`
    await expect(teacher!.locator('.announcement-body')).toHaveText(body)
    expect(await teacher!.evaluate(() => 'announcementExecuted' in window)).toBe(false)
    expect(await teacher!.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await teacher!.screenshot({ path: testInfo.outputPath('announcements-max-title-mobile.png'), fullPage: true })
    await open(student!, course, initial.id)
    await expect(student!.getByRole('heading', { name: title, exact: true })).toBeVisible()
    await expect(student!.locator('.announcement-body')).toHaveText(body)
    await expect(student!.getByRole('button', { name: /New announcement|Edit announcement|Delete announcement/ })).toHaveCount(0)
    expect(await student!.evaluate(() => 'announcementExecuted' in window)).toBe(false)
    const newYorkTime = new Intl.DateTimeFormat('en-US', { timeZone: 'America/New_York', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(initial.publishedAt))
    await expect(teacher!.locator('time').first()).toHaveText(newYorkTime)
    await expect(student!.locator('time').first()).toHaveText(newYorkTime)
    await open(coTeacher!, course, initial.id)
    await edit(coTeacher!, 'Preserved losing draft', 'Co-teacher draft remains available after conflict.')
    await open(teacher!, course, initial.id)
    await edit(teacher!, 'Winning revision', body)
    await teacher!.getByRole('button', { name: 'Save announcement', exact: true }).click()
    await expect(teacher!.getByRole('status')).toContainText('Announcement saved.')
    const winning = await read<Notice>(teacher!, path)
    expect(winning).toMatchObject({ title: 'Winning revision', revision: 2, publishedAt: initial.publishedAt })
    await coTeacher!.getByRole('button', { name: 'Save announcement', exact: true }).click()
    await expect(coTeacher!.getByRole('alert')).toContainText('This announcement changed.')
    await expect(coTeacher!.getByLabel('Title', { exact: true })).toHaveValue('Preserved losing draft')
    expect(await read<Notice>(teacher!, path)).toEqual(winning)
    await coTeacher!.getByRole('button', { name: 'Reload current announcement', exact: true }).click()
    await expect(coTeacher!.getByRole('heading', { name: 'Winning revision', exact: true })).toBeVisible()
    await expect(coTeacher!.getByLabel('Title', { exact: true })).toHaveValue('Preserved losing draft')
    await coTeacher!.getByRole('button', { name: 'Review preserved draft', exact: true }).click()
    // Reviewing is not authorization to submit: the winning source must remain untouched.
    expect(await read<Notice>(teacher!, path)).toEqual(winning)
    await expect(coTeacher!.getByRole('button', { name: 'Save announcement', exact: true })).toBeEnabled()
    await coTeacher!.getByRole('button', { name: 'Save announcement', exact: true }).click()
    await expect(coTeacher!.getByRole('status')).toContainText('Announcement saved.')
    const current = await read<Notice>(teacher!, path)
    expect(current).toMatchObject({ title: 'Preserved losing draft', revision: 3, publishedAt: initial.publishedAt })
    await coTeacher!.screenshot({ path: testInfo.outputPath('announcements-co-teacher-desktop.png'), fullPage: true })
    for (const actor of [student!, outsiderStudent!, outsiderTeacher!]) {
      for (const [url, data, method] of [
        [`${base}/announcements`, { commandKey: crypto.randomUUID(), title: 'Denied', body: 'Denied' }, 'POST'],
        [path, { commandKey: crypto.randomUUID(), expectedRevision: current.revision, title: 'Denied', body: 'Denied' }, 'PUT'],
        [`${path}/delete`, { commandKey: crypto.randomUUID(), expectedRevision: current.revision }, 'POST'],
      ] as const) expect([403, 404]).toContain((await mutate(actor, url, data, method)).status())
    }
    for (const actor of [outsiderStudent!, outsiderTeacher!]) {
      expect((await actor.request.get(path)).status()).toBe(404)
      expect((await actor.request.get(`${base}/announcements`)).status()).toBe(404)
    }
    expect(await read<Notice>(teacher!, path)).toEqual(current)
    let deletions = 0
    coTeacher!.on('request', request => { if (request.method() === 'POST' && new URL(request.url()).pathname === `${path}/delete`) deletions++ })
    const remove = coTeacher!.getByRole('button', { name: 'Delete announcement', exact: true })
    await remove.click()
    await expect(coTeacher!.getByRole('dialog', { name: 'Delete announcement?', exact: true })).toContainText(current.title)
    await expect(coTeacher!.getByRole('button', { name: 'Cancel deletion', exact: true })).toBeFocused()
    await coTeacher!.keyboard.press('Escape'); await expect(remove).toBeFocused()
    await remove.click(); await coTeacher!.getByRole('button', { name: 'Cancel deletion', exact: true }).click(); await expect(remove).toBeFocused()
    expect(deletions).toBe(0)
    expect(await read<Notice>(teacher!, path)).toEqual(current)
    await remove.click(); await coTeacher!.getByRole('button', { name: 'Confirm deletion', exact: true }).click()
    await expect(coTeacher!.getByRole('status')).toContainText('Announcement deleted.')
    expect(deletions).toBe(1)
    expect((await student!.request.get(path)).status()).toBe(404)
    // Reopen the direct URL as a document navigation; same-hash goto alone does not remount React.
    await student!.reload()
    await open(student!, course, initial.id)
    await expect(student!.getByRole('heading', { name: 'Announcement unavailable', exact: true })).toBeVisible()
    await expect(student!.getByText(current.body, { exact: true })).toHaveCount(0)
    expect((await read<{ activity: unknown }>(teacher!, `${base}/activity`)).activity).toBeNull()
  } finally { await Promise.all(contexts.map(context => context.close())) }
})

test('successful create/edit/delete responses can be lost and reconciled from actor receipts without replay', async ({ browser }) => {
  test.setTimeout(120_000)
  const context = await browser.newContext()
  try {
    const page = await context.newPage()
    await login(page, 'v2-teacher')
    const course = (await read<{ id: string; code: string }[]>(page, '/api/courses')).find(value => value.code === 'V2-202')!.id
    const base = `/api/courses/${course}`
    const uniqueTitle = `Lost response ${crypto.randomUUID()}`
    let target = ''
    let writes = 0
    for (const command of ['CREATE', 'EDIT', 'DELETE'] as const) {
      await open(page, course, command === 'CREATE' ? undefined : target)
      let commandKey = ''
      let acknowledgement: Ack | undefined
      const url = `${base}/announcements${command === 'CREATE' ? '' : `/${target}`}${command === 'DELETE' ? '/delete' : ''}`
      await page.route(`**${url}`, async route => {
        if (route.request().method() !== (command === 'EDIT' ? 'PUT' : 'POST')) { await route.continue(); return }
        writes++; commandKey = route.request().postDataJSON().commandKey
        // Execute the real command. Drop ONLY its successful response after PostgreSQL commit.
        const response = await route.fetch({ maxRetries: 0 })
        expect(response.status()).toBe(200)
        acknowledgement = await response.json() as Ack
        await route.abort('failed')
      })
      const before = writes
      if (command === 'CREATE') {
        await page.getByRole('button', { name: 'New announcement', exact: true }).click()
        await page.getByLabel('Title', { exact: true }).fill(uniqueTitle)
        await page.getByLabel('Body', { exact: true }).fill('Created despite lost response.')
        await page.getByRole('button', { name: 'Publish announcement', exact: true }).click()
      } else if (command === 'EDIT') {
        await edit(page, `${uniqueTitle} edited`, 'Edited despite lost response.')
        await page.getByRole('button', { name: 'Save announcement', exact: true }).click()
      } else {
        await page.getByRole('button', { name: 'Delete announcement', exact: true }).click()
        await page.getByRole('button', { name: 'Confirm deletion', exact: true }).click()
      }
      await expect(page.getByRole('alert')).toContainText('could not be confirmed')
      expect(writes).toBe(before + 1)
      expect(commandKey).toBeTruthy(); expect(acknowledgement).toBeDefined()
      target = acknowledgement!.id
      const receiptPath = `${base}/announcement-commands/${commandKey}?command=${command}`
      const receipt = await read<Receipt>(page, receiptPath)
      expect(receipt).toEqual({ committed: true, acknowledgement })
      expect(acknowledgement).toMatchObject({ id: target, revision: command === 'CREATE' ? 1 : command === 'EDIT' ? 2 : 3, deleted: command === 'DELETE' })
      if (command === 'DELETE') expect((await page.request.get(`${base}/announcements/${target}`)).status()).toBe(404)
      else expect(await read<Notice>(page, `${base}/announcements/${target}`)).toMatchObject({ title: command === 'CREATE' ? uniqueTitle : `${uniqueTitle} edited`, revision: acknowledgement!.revision })
      await expect(page.getByRole('button', { name: 'Retry original request', exact: true })).toHaveCount(0)
      await page.getByRole('button', { name: 'Check receipt and current announcement', exact: true }).click()
      await expect(page.getByRole('status')).toContainText(command === 'DELETE' ? 'original deletion committed' : 'original request committed')
      await page.getByRole('button', { name: 'Finish review', exact: true }).click()
      expect(writes).toBe(before + 1)
      expect(await read<Receipt>(page, receiptPath)).toEqual(receipt)
      await page.unroute(`**${url}`)
    }
    expect(writes).toBe(3)
    expect((await read<{ items: Notice[] }>(page, `${base}/announcements`)).items.some(value => value.id === target)).toBe(false)
    expect((await read<{ activity: unknown }>(page, `${base}/activity`)).activity).toBeNull()
  } finally { await context.close() }
})

test('late enrollment grants old current announcements and revocation clears protected detail without activity participation', async ({ browser }) => {
  test.setTimeout(60_000)
  // Missing controlled-database setup is a failure, never a silent acceptance skip.
  expect(disposableSql('SELECT current_setting(\'server_version_num\')::integer / 10000;')).toBe('17')
  const teacherContext = await browser.newContext()
  const lateContext = await browser.newContext({ timezoneId: 'Asia/Taipei' })
  let course = ''
  let restore = false
  let enrolled = false
  const enrollment = () => `course_id='${course}'::uuid AND account_id=(SELECT id FROM app_account WHERE username='v2-late-student')`
  function setEnrollment(present: boolean) {
    disposableSql(`BEGIN; SELECT id FROM course WHERE id='${course}'::uuid FOR UPDATE; ${present
      ? `INSERT INTO course_enrollment(course_id,account_id) SELECT '${course}'::uuid,id FROM app_account WHERE username='v2-late-student' ON CONFLICT DO NOTHING;`
      : `DELETE FROM course_enrollment WHERE ${enrollment()};`} COMMIT;`)
  }
  try {
    const teacher = await teacherContext.newPage(); const late = await lateContext.newPage()
    await login(teacher, 'v2-teacher'); await login(late, 'v2-late-student')
    course = (await read<{ id: string; code: string }[]>(teacher, '/api/courses')).find(value => value.code === 'V2-202')!.id
    if (!/^[0-9a-f-]{36}$/i.test(course)) throw new Error('Invalid synthetic course identifier')
    enrolled = disposableSql(`SELECT count(*) FROM course_enrollment WHERE ${enrollment()};`) === '1'
    restore = true
    setEnrollment(false)
    const base = `/api/courses/${course}`
    expect((await read<{ activity: unknown }>(teacher, `${base}/activity`)).activity).toBeNull()
    const title = `Published before enrollment ${crypto.randomUUID()}`
    const create = await mutate(teacher, `${base}/announcements`, { commandKey: crypto.randomUUID(), title, body: 'Current enrollment grants access to older course news.' })
    expect(create.status()).toBe(200)
    const acknowledgement = await create.json() as Ack
    const detail = `${base}/announcements/${acknowledgement.id}`
    expect((await late.request.get(detail)).status()).toBe(404)
    setEnrollment(true)
    await open(late, course, acknowledgement.id)
    await expect(late.getByRole('heading', { name: title, exact: true })).toBeVisible()
    expect((await read<{ items: Notice[] }>(late, `${base}/announcements`)).items.some(value => value.id === acknowledgement.id)).toBe(true)
    expect(disposableSql(`SELECT count(*) FROM activity_participant WHERE account_id=(SELECT id FROM app_account WHERE username='v2-late-student');`)).toBe('0')
    setEnrollment(false)
    await late.getByRole('button', { name: 'Refresh announcements', exact: true }).click()
    await expect(late.getByRole('heading', { name: 'Announcement unavailable', exact: true })).toBeVisible()
    await expect(late.getByText(title, { exact: true })).toHaveCount(0)
    expect((await late.request.get(detail)).status()).toBe(404)
    expect((await late.request.get(`${base}/announcements`)).status()).toBe(404)
    expect((await read<{ activity: unknown }>(teacher, `${base}/activity`)).activity).toBeNull()
  } finally {
    if (restore) setEnrollment(enrolled)
    await teacherContext.close(); await lateContext.close()
  }
})

test('inline publication then confirmed deletion returns to the surviving announcement list', async ({ browser }) => {
  test.setTimeout(60_000)
  const context = await browser.newContext()
  try {
    const page = await context.newPage()
    await login(page, 'v2-teacher')
    const course = (await read<{ id: string; code: string }[]>(page, '/api/courses')).find(value => value.code === 'V2-202')!.id
    const base = `/api/courses/${course}`
    expect((await read<{ activity: unknown }>(page, `${base}/activity`)).activity).toBeNull()
    const survivorTitle = `Surviving announcement ${crypto.randomUUID()}`
    const seed = await mutate(page, `${base}/announcements`, { commandKey: crypto.randomUUID(), title: survivorTitle, body: 'This current announcement remains reachable after another is deleted.' })
    expect(seed.status()).toBe(200)
    const survivor = await seed.json() as Ack
    await open(page, course)
    await expect(page.getByRole('link', { name: survivorTitle, exact: true })).toBeVisible()
    const listHash = `#/courses/${course}/announcements`
    const title = `Inline deletion ${crypto.randomUUID()}`
    let creates = 0
    let deletes = 0
    let createKey = ''
    let deleteKey = ''
    page.on('request', request => {
      if (request.method() !== 'POST') return
      const path = new URL(request.url()).pathname
      if (path === `${base}/announcements`) { creates++; createKey = request.postDataJSON().commandKey }
      if (path.startsWith(`${base}/announcements/`) && path.endsWith('/delete')) { deletes++; deleteKey = request.postDataJSON().commandKey }
    })
    await page.getByRole('button', { name: 'New announcement', exact: true }).click()
    await page.getByLabel('Title', { exact: true }).fill(title)
    await page.getByLabel('Body', { exact: true }).fill('Publish and delete without leaving the list URL.')
    const publishedResponse = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === `${base}/announcements`)
    await page.getByRole('button', { name: 'Publish announcement', exact: true }).click()
    const published = await publishedResponse
    expect(published.status()).toBe(200)
    const target = await published.json() as Ack
    await expect(page.getByRole('status')).toContainText('Announcement published.')
    await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible()
    expect(new URL(page.url()).hash).toBe(listHash)
    expect(await read<Notice>(page, `${base}/announcements/${target.id}`)).toMatchObject({ title, revision: 1 })
    await page.getByRole('button', { name: 'Delete announcement', exact: true }).click()
    const deletedResponse = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === `${base}/announcements/${target.id}/delete`)
    await page.getByRole('button', { name: 'Confirm deletion', exact: true }).click()
    const deleted = await deletedResponse
    expect(deleted.status()).toBe(200)
    expect(await deleted.json()).toEqual({ id: target.id, revision: 2, deleted: true })
    await expect(page.getByRole('status')).toContainText('Announcement deleted.')
    await expect(page.getByRole('link', { name: survivorTitle, exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Announcement unavailable', exact: true })).toHaveCount(0)
    await expect(page.getByRole('heading', { name: title, exact: true })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'New announcement', exact: true })).toBeEnabled()
    expect(new URL(page.url()).hash).toBe(listHash)
    await page.getByRole('button', { name: 'Refresh announcements', exact: true }).click()
    await expect(page.getByRole('link', { name: survivorTitle, exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Announcement unavailable', exact: true })).toHaveCount(0)
    expect((await page.request.get(`${base}/announcements/${target.id}`)).status()).toBe(404)
    expect(await read<Notice>(page, `${base}/announcements/${survivor.id}`)).toMatchObject({ title: survivorTitle, revision: 1 })
    expect(await read<Receipt>(page, `${base}/announcement-commands/${createKey}?command=CREATE`)).toEqual({ committed: true, acknowledgement: target })
    expect(await read<Receipt>(page, `${base}/announcement-commands/${deleteKey}?command=DELETE`)).toEqual({ committed: true, acknowledgement: { id: target.id, revision: 2, deleted: true } })
    await open(page, course, target.id)
    await expect(page.getByRole('heading', { name: 'Announcement unavailable', exact: true })).toBeVisible()
    await page.getByRole('link', { name: 'Back to announcements', exact: true }).click()
    await expect(page.getByRole('link', { name: survivorTitle, exact: true })).toBeVisible()
    expect(creates).toBe(1); expect(deletes).toBe(1)
    expect((await read<{ activity: unknown }>(page, `${base}/activity`)).activity).toBeNull()
  } finally { await context.close() }
})
