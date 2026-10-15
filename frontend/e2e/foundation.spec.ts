import { expect, test } from '@playwright/test'

test('real same-origin login, browser refresh, and logout', async ({ page }) => {
  const username = process.env.DEMO_USERNAME
  const password = process.env.DEMO_PASSWORD
  if (!username || !password) throw new Error('Set DEMO_USERNAME and DEMO_PASSWORD for the running opt-in demo backend')
  const startupCsrf = page.waitForResponse(response => response.url().endsWith('/api/auth/csrf'))
  await page.goto('/')
  expect((await startupCsrf).status()).toBe(200)
  await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible()
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByText(username, { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'No courses yet' })).toBeVisible()
  const reloadCsrf = page.waitForResponse(response => response.url().endsWith('/api/auth/csrf'))
  await page.reload()
  expect((await reloadCsrf).status()).toBe(200)
  await expect(page.getByText(username, { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByLabel('Password')).toBeVisible()
  const session = await page.request.get('/api/auth/session')
  expect(session.status()).toBe(401)
})
