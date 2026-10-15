import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { App } from './App'
import * as api from './api'
vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof api>(), initializeSession: vi.fn(), signIn: vi.fn(), signOut: vi.fn(), listCourses: vi.fn(), getCourse: vi.fn() }))
const teacher: api.Session = { accountId: 'teacher', username: 'demo', role: 'TEACHER' }
const course: api.Course = { id: '11111111-1111-1111-1111-111111111111', code: 'CS101', name: 'Computing' }
beforeEach(() => {
  vi.resetAllMocks(); window.location.hash = '#/courses'
  vi.mocked(api.initializeSession).mockResolvedValue(null)
  vi.mocked(api.listCourses).mockResolvedValue([course])
  vi.mocked(api.getCourse).mockResolvedValue(course)
})
async function login(user: ReturnType<typeof userEvent.setup>) {
  await user.type(await screen.findByLabelText('Username'), 'demo')
  await user.type(screen.getByLabelText('Password'), 'test-password-123')
  await user.click(screen.getByRole('button', { name: /Sign in/ }))
}
test('signs in to assigned courses and signs out', async () => {
  const user = userEvent.setup(); render(<App />)
  await screen.findByLabelText('Username')
  vi.mocked(api.initializeSession).mockResolvedValue(teacher)
  await login(user)
  expect(await screen.findByRole('link', { name: /Computing/ })).toBeInTheDocument()
  expect(api.signIn).toHaveBeenCalledWith('demo', 'test-password-123')
  vi.mocked(api.initializeSession).mockResolvedValue(null)
  await user.click(screen.getByRole('button', { name: 'Sign out' }))
  expect(await screen.findByLabelText('Password')).toBeInTheDocument()
})
test('authentication failure reconciles reads without replaying login', async () => {
  vi.mocked(api.signIn).mockRejectedValue(new Error('Invalid username or password.'))
  const user = userEvent.setup(); render(<App />); await login(user)
  expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password.')
  expect(api.signIn).toHaveBeenCalledTimes(1)
  expect(api.initializeSession).toHaveBeenCalledTimes(2)
})
test('unavailable bootstrap hides the form and supports read retry', async () => {
  vi.mocked(api.initializeSession).mockRejectedValueOnce(new Error('Offline'))
  const user = userEvent.setup(); render(<App />)
  await screen.findByText('Session unavailable')
  expect(screen.queryByLabelText('Password')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Retry session check' }))
  expect(await screen.findByLabelText('Password')).toBeInTheDocument()
})
test('deep-linked student course loads actual metadata without later modules', async () => {
  window.location.hash = `#/courses/${course.id}`
  vi.mocked(api.initializeSession).mockResolvedValue({ ...teacher, role: 'STUDENT' })
  render(<App />)
  expect(await screen.findByRole('heading', { name: 'Computing', level: 1 })).toBeInTheDocument()
  expect(api.getCourse).toHaveBeenCalledWith(course.id)
  expect(screen.getByRole('link', { name: 'Classmates' })).toHaveAttribute('href', `#/courses/${course.id}/classmates`)
  expect(screen.queryByText('About me')).not.toBeInTheDocument()
})
test('empty course list differs from a failed read and can be refreshed', async () => {
  vi.mocked(api.initializeSession).mockResolvedValue(teacher)
  vi.mocked(api.listCourses).mockRejectedValueOnce(new Error('Unable to load courses. Please retry.')).mockResolvedValue([])
  const user = userEvent.setup(); render(<App />)
  expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load courses')
  expect(screen.queryByText('No courses yet')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Retry' }))
  expect(await screen.findByText('No courses yet')).toBeInTheDocument()
})
test('inaccessible course shows neutral denied feedback', async () => {
  window.location.hash = `#/courses/${course.id}`
  vi.mocked(api.initializeSession).mockResolvedValue(teacher)
  vi.mocked(api.getCourse).mockRejectedValue(new api.ApiError(404, 'This course is unavailable or you do not have access.'))
  render(<App />)
  expect(await screen.findByRole('alert')).toHaveTextContent('unavailable or you do not have access')
  expect(screen.queryByText('Computing')).not.toBeInTheDocument()
})
test('course expiry clears protected content and reconciles session', async () => {
  vi.mocked(api.initializeSession).mockResolvedValueOnce(teacher).mockResolvedValue(null)
  vi.mocked(api.listCourses).mockRejectedValue(new api.ApiError(401, 'expired'))
  render(<App />)
  expect(await screen.findByLabelText('Password')).toBeInTheDocument()
  expect(screen.queryByText('Computing')).not.toBeInTheDocument()
  expect(await screen.findByRole('alert')).toHaveTextContent('session expired')
})
test('lost logout with failed reconciliation hides data until retry', async () => {
  vi.mocked(api.initializeSession).mockResolvedValueOnce(teacher).mockRejectedValueOnce(new Error('Offline')).mockResolvedValue(null)
  vi.mocked(api.signOut).mockRejectedValue(new Error('Lost response'))
  const user = userEvent.setup(); render(<App />)
  await screen.findByText('Computing')
  await user.click(screen.getByRole('button', { name: 'Sign out' }))
  await screen.findByText('Session unavailable')
  expect(screen.queryByText('Computing')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Retry session check' }))
  await screen.findByLabelText('Password')
  expect(api.signOut).toHaveBeenCalledTimes(1)
})
test('late response cannot replace newly selected course', async () => {
  let finish!: (courses: api.Course[]) => void
  vi.mocked(api.initializeSession).mockResolvedValue(teacher)
  vi.mocked(api.listCourses).mockReturnValue(new Promise(resolve => { finish = resolve }))
  render(<App />)
  await waitFor(() => expect(api.listCourses).toHaveBeenCalled())
  window.location.hash = `#/courses/${course.id}`
  await screen.findByRole('heading', { name: 'Computing', level: 1 })
  finish([{ ...course, name: 'Stale course' }])
  await waitFor(() => expect(screen.queryByText('Stale course')).not.toBeInTheDocument())
})
test('keyboard skip link focuses content without changing course route', async () => {
  vi.mocked(api.initializeSession).mockResolvedValue(teacher)
  const user = userEvent.setup(); render(<App />)
  await screen.findByText('Computing')
  await user.tab(); expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveFocus()
  await user.keyboard('{Enter}')
  expect(screen.getByRole('main')).toHaveFocus()
  expect(window.location.hash).toBe('#/courses')
})

test('lost login response may reconcile to authenticated without replay', async () => {
  vi.mocked(api.signIn).mockRejectedValue(new Error('Sign in could not be confirmed.'))
  const user = userEvent.setup(); render(<App />)
  await screen.findByLabelText('Password')
  vi.mocked(api.initializeSession).mockResolvedValue(teacher)
  await login(user)
  expect(await screen.findByText('Computing')).toBeInTheDocument()
  expect(api.signIn).toHaveBeenCalledTimes(1)
})
test('failed refresh removes previous course data until session can be read', async () => {
  vi.mocked(api.initializeSession).mockResolvedValueOnce(teacher).mockRejectedValueOnce(new Error('Offline'))
  const user = userEvent.setup(); render(<App />)
  await screen.findByText('Computing')
  await user.click(screen.getByRole('button', { name: 'Refresh' }))
  await screen.findByText('Session unavailable')
  expect(screen.queryByText('Computing')).not.toBeInTheDocument()
})

test('teacher direct Groups route is denied without grouping data or student navigation', async () => {
  window.location.hash = `#/courses/${course.id}/groups`
  vi.mocked(api.initializeSession).mockResolvedValue(teacher)
  render(<App />)
  expect(await screen.findByRole('heading', { name: 'Groups unavailable' })).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Groups' })).not.toBeInTheDocument()
  expect(screen.queryByRole('navigation', { name: 'Course navigation' })).not.toBeInTheDocument()
})
