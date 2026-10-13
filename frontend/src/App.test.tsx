import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { App } from './App'
import * as api from './api'

vi.mock('./api', () => ({ currentSession: vi.fn(), signIn: vi.fn(), signOut: vi.fn() }))

beforeEach(() => { vi.resetAllMocks(); vi.mocked(api.currentSession).mockResolvedValue(null) })

test('signs in and out without adding grouping workflows', async () => {
  const user = userEvent.setup()
  render(<App />)
  await user.type(screen.getByLabelText('Username'), 'demo')
  await user.type(screen.getByLabelText('Password'), 'test-password-123')
  vi.mocked(api.currentSession).mockResolvedValue('demo')
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
  expect(await screen.findByText('Signed in as demo.')).toBeInTheDocument()
  expect(api.signIn).toHaveBeenCalledWith('demo', 'test-password-123')
  await user.click(screen.getByRole('button', { name: 'Sign out' }))
  expect(await screen.findByLabelText('Password')).toBeInTheDocument()
})

test('shows an authentication failure without retrying automatically', async () => {
  vi.mocked(api.signIn).mockRejectedValue(new Error('Invalid username or password.'))
  const user = userEvent.setup()
  render(<App />)
  await user.type(screen.getByLabelText('Username'), 'demo')
  await user.type(screen.getByLabelText('Password'), 'wrong')
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password.')
  expect(api.signIn).toHaveBeenCalledTimes(1)
})
