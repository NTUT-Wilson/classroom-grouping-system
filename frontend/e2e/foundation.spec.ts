import { expect, test } from '@playwright/test'

test('real same-origin login, browser refresh, and logout', async ({ page }) => {
  const username = process.env.DEMO_USERNAME
  const password = process.env.DEMO_PASSWORD
  if (!username || !password) throw new Error('Set DEMO_USERNAME and DEMO_PASSWORD for the running opt-in demo backend')
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Classroom Grouping' })).toBeVisible()
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByText(`Signed in as ${username}.`)).toBeVisible()
  await page.reload()
  await expect(page.getByText(`Signed in as ${username}.`)).toBeVisible()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByLabel('Password')).toBeVisible()
  const session = await page.request.get('/api/auth/session')
  expect(session.status()).toBe(401)
})
