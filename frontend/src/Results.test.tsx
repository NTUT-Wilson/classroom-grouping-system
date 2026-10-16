import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { Results } from './Results'
import { Groups } from './Groups'
import * as api from './api'
vi.mock('./api', async original => ({ ...await original<typeof api>(), getResults: vi.fn(), getGrouping: vi.fn(), getApplications: vi.fn(), getInvitations: vi.fn() }))
const course = { id: 'course-one', code: 'R1', name: 'Results classroom' }
const result: api.FinalResults = { activityId: 'activity', status: 'FINALIZED', groups: [{ id: 'g1', ordinal: 1, name: 'Group 1', capacity: 2, managerAccountId: 'a', members: [{ accountId: 'a', studentId: '00001', studentName: '陳 <Name>', aboutMe: '<script>literal</script>\nAbout me' }, { accountId: 'b', studentId: '00002', studentName: 'Alex', aboutMe: '' }] }, { id: 'g2', ordinal: 2, name: 'Group 2', capacity: 2, managerAccountId: null, members: [{ accountId: 'c', studentId: '00003', studentName: 'Sam' }] }] }
beforeEach(() => { vi.resetAllMocks(); vi.mocked(api.getResults).mockResolvedValue(result) })
test('teacher roster preserves text IDs, managerless groups and excludes About me with no assignment or export controls', async () => {
  const { container } = render(<Results course={course} role="TEACHER" expired={vi.fn()} />)
  expect(await screen.findByText('陳 <Name>')).toBeInTheDocument()
  expect(screen.getByText('Student ID · 00001')).toBeInTheDocument()
  expect(screen.getByText('Manager')).toBeInTheDocument()
  expect(screen.getByText(/first populated by system assignment/)).toBeInTheDocument()
  expect(screen.queryByText(/literal/)).not.toBeInTheDocument()
  expect(container.querySelector('script')).toBeNull()
  expect(screen.getAllByRole('button').map(button => button.textContent)).toEqual(['Refresh results'])
  expect(api.getResults).toHaveBeenCalledWith(course.id, 'TEACHER')
})
test('student result shows only own members and introductions as plain text', async () => {
  vi.mocked(api.getResults).mockResolvedValue({ ...result, groups: [result.groups[0]] })
  render(<Results course={course} role="STUDENT" expired={vi.fn()} />)
  expect(await screen.findByText(/<script>literal/)).toBeInTheDocument()
  expect(screen.queryByText('Sam')).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Classmates' })).toHaveAttribute('href', '#/courses/course-one/classmates')
  expect(api.getResults).toHaveBeenCalledWith(course.id, 'STUDENT')
})
test.each(['ABSENT', 'OPEN', 'FINALIZING'] as const)('%s hides all result data and refresh only repeats a read', async status => {
  const user = userEvent.setup(); vi.mocked(api.getResults).mockResolvedValue({ ...result, status })
  render(<Results course={course} role="TEACHER" expired={vi.fn()} />)
  await waitFor(() => expect(screen.getByRole('button', { name: 'Refresh results' })).toBeEnabled())
  expect(screen.queryByText('陳 <Name>')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Refresh results' }))
  await waitFor(() => expect(api.getResults).toHaveBeenCalledTimes(2))
})
test.each([401, 403, 404, 503, 500])('refresh failure %s removes stale roster and expires session only on 401', async status => {
  const user = userEvent.setup(); const expired = vi.fn()
  render(<Results course={course} role="TEACHER" expired={expired} />)
  await screen.findByText('陳 <Name>')
  vi.mocked(api.getResults).mockRejectedValue(new api.ApiError(status, 'safe failure'))
  await user.click(screen.getByRole('button', { name: 'Refresh results' }))
  await screen.findByRole('alert')
  expect(screen.queryByText('陳 <Name>')).not.toBeInTheDocument()
  expect(expired).toHaveBeenCalledTimes(status === 401 ? 1 : 0)
  expect(api.getResults).toHaveBeenCalledTimes(2)
})
test('ignores late result from previous course and clears protected roster while loading', async () => {
  let resolve!: (value: api.FinalResults) => void
  vi.mocked(api.getResults).mockReturnValueOnce(new Promise(done => { resolve = done })).mockResolvedValueOnce({ activityId: null, status: 'ABSENT', groups: [] })
  const view = render(<Results course={course} role="TEACHER" expired={vi.fn()} />)
  view.rerender(<Results course={{ ...course, id: 'second' }} role="TEACHER" expired={vi.fn()} />)
  await screen.findByText('No grouping activity yet')
  await act(async () => resolve(result))
  expect(screen.queryByText('陳 <Name>')).not.toBeInTheDocument()
})
test.each([{ groups: [] }, { groups: result.groups }])('a finalized student response with invalid group coverage remains unavailable (%j)', async ({ groups }) => {
  vi.mocked(api.getResults).mockResolvedValue({ ...result, groups })
  render(<Results course={course} role="STUDENT" expired={vi.fn()} />)
  expect(await screen.findByRole('alert')).toHaveTextContent('complete result could not be loaded')
  expect(screen.queryByText('陳 <Name>')).not.toBeInTheDocument()
})
test('finalized Groups never depends on request histories and presents own result', async () => {
  vi.mocked(api.getGrouping).mockResolvedValue({ activityId: 'activity', status: 'FINALIZED', ownGroupId: null, groups: [] })
  vi.mocked(api.getResults).mockResolvedValue({ ...result, groups: [result.groups[0]] })
  vi.mocked(api.getApplications).mockRejectedValue(new Error('history unavailable'))
  render(<Groups course={course} accountId="a" expired={vi.fn()} />)
  expect(await screen.findByText('陳 <Name>')).toBeInTheDocument()
  expect(api.getApplications).not.toHaveBeenCalled()
  expect(api.getInvitations).not.toHaveBeenCalled()
})

test('loading removes the previous roster and disables duplicate refresh until its read settles', async () => {
  const user = userEvent.setup()
  render(<Results course={course} role="TEACHER" expired={vi.fn()} />)
  await screen.findByText('陳 <Name>')
  let resolve!: (value: api.FinalResults) => void
  vi.mocked(api.getResults).mockReturnValueOnce(new Promise(done => { resolve = done }))
  await user.click(screen.getByRole('button', { name: 'Refresh results' }))
  expect(screen.getByRole('status')).toHaveTextContent('Loading results')
  expect(screen.getByRole('button', { name: 'Refresh results' })).toBeDisabled()
  expect(screen.queryByText('陳 <Name>')).not.toBeInTheDocument()
  await act(async () => resolve(result))
  expect(await screen.findByText('陳 <Name>')).toBeInTheDocument()
  expect(api.getResults).toHaveBeenCalledTimes(2)
})
