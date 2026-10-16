import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { Requests } from './Requests'
import * as api from './api'
vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof api>(), getInvitations: vi.fn(), getGrouping: vi.fn(), getApplications: vi.fn(), decideApplication: vi.fn() }))
const course = { id: 'course', code: 'C', name: 'Course' }
const applicant = { accountId: 'student', studentId: '00002', studentName: '陳 <script>text</script>', aboutMe: 'Exact\nAbout me 😀' }
const manager = { accountId: 'manager', studentId: '00001', studentName: 'Alex', aboutMe: 'Manager introduction' }
const attempt: api.ApplicationAttempt = { id: 'a1', groupId: 'g1', groupName: 'Group 1', applicant, manager, status: 'PENDING', reason: null, createdAt: '2026-10-15T04:00:00Z' }
const page: api.ApplicationPage = { activityId: 'activity', status: 'OPEN', items: [attempt], nextCursor: null }
const discovery: api.GroupDiscovery = { activityId: 'activity', status: 'OPEN', ownGroupId: 'g1', groups: [] }
beforeEach(() => {
  vi.resetAllMocks(); vi.mocked(api.getInvitations).mockResolvedValue({ ...page, items: [] }); vi.mocked(api.getApplications).mockResolvedValue(page); vi.mocked(api.getGrouping).mockResolvedValue(discovery)
  vi.mocked(api.decideApplication).mockImplementation(async () => { const result: api.ApplicationPage = { ...page, items: [{ ...attempt, status: 'ACCEPTED' }] }; vi.mocked(api.getApplications).mockResolvedValue(result); return result })
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value() { this.setAttribute('open', ''); this.querySelector('button')?.focus() } })
})
function mount(accountId = 'manager', expired = vi.fn()) { return render(<Requests course={course} accountId={accountId} expired={expired} />) }
async function respond(user: ReturnType<typeof userEvent.setup>, action: 'Approve' | 'Reject' = 'Approve') {
  await user.click(await screen.findByRole('button', { name: action }))
  await user.click(screen.getByRole('button', { name: action === 'Approve' ? 'Confirm approval' : 'Confirm rejection' }))
}
test('private history preserves Unicode, leading zeros and plain text with no roster or withdrawal controls', async () => {
  const { container } = mount('student')
  expect(await screen.findByText(`Applicant · ${applicant.studentName}`)).toBeInTheDocument()
  expect(screen.getByText('Student ID · 00002')).toBeInTheDocument()
  expect(screen.getByText('Exact About me 😀')).toBeInTheDocument()
  expect(container.querySelector('script')).toBeNull()
  expect(screen.queryByRole('button', { name: /Approve|Reject|Withdraw|Leave|Remove|Invite/i })).not.toBeInTheDocument()
  expect(screen.getByText(/Asia\/Taipei/)).toBeInTheDocument()
})
test.each(['Approve', 'Reject'] as const)('%s requires explicit confirmation, supports cancel focus, and records a new command', async action => {
  const user = userEvent.setup(); mount()
  await user.click(await screen.findByRole('button', { name: action }))
  expect(api.decideApplication).not.toHaveBeenCalled()
  expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus()
  await user.click(screen.getByRole('button', { name: 'Cancel' }))
  expect(screen.getByRole('button', { name: action })).toHaveFocus()
  await respond(user, action)
  await screen.findByText('ACCEPTED')
  expect(api.decideApplication).toHaveBeenCalledWith('course', 'a1', { commandKey: expect.stringMatching(/^[0-9a-f-]{36}$/), decision: action === 'Approve' ? 'APPROVE' : 'REJECT' })
  expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
})
test('load older follows authorized cursor, retains earlier attempts, and refresh resets to first page', async () => {
  vi.mocked(api.getApplications).mockResolvedValueOnce({ ...page, nextCursor: 'opaque+/=' }).mockResolvedValueOnce({ ...page, items: [{ ...attempt, id: 'old', status: 'REJECTED' }] }).mockResolvedValue(page)
  const user = userEvent.setup(); mount('student')
  await user.click(await screen.findByRole('button', { name: 'Load older' }))
  expect(await screen.findByText('REJECTED')).toBeInTheDocument()
  expect(screen.getAllByRole('article')).toHaveLength(2)
  expect(api.getApplications).toHaveBeenNthCalledWith(2, 'course', 'opaque+/=')
  await user.click(screen.getByRole('button', { name: 'Refresh requests' }))
  await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(1))
  expect(api.decideApplication).not.toHaveBeenCalled()
})
test.each([401, 403, 404])('history denial %s clears previously visible identity and all actions', async status => {
  const user = userEvent.setup(); const expired = vi.fn(); mount('manager', expired)
  await screen.findByText(`Applicant · ${applicant.studentName}`)
  vi.mocked(api.getApplications).mockRejectedValue(new api.ApiError(status, 'unsafe details'))
  await user.click(screen.getByRole('button', { name: 'Refresh requests' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Requests unavailable')
  expect(screen.queryByText(`Applicant · ${applicant.studentName}`)).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
  expect(expired).toHaveBeenCalledTimes(status === 401 ? 1 : 0)
})
test.each([new TypeError('lost response'), new api.ApiError(403, 'CSRF'), new api.ApiError(500, 'failure')])('failed decision stops until history AND discovery succeed and never replays', async cause => {
  vi.mocked(api.decideApplication).mockRejectedValueOnce(cause)
  const user = userEvent.setup(); mount(); await respond(user)
  await screen.findByRole('alert')
  expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
  vi.mocked(api.getGrouping).mockRejectedValueOnce(new TypeError('offline'))
  await user.click(screen.getByRole('button', { name: 'Refresh requests' }))
  await waitFor(() => expect(api.getGrouping).toHaveBeenCalledTimes(2))
  expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Refresh requests' }))
  await screen.findByRole('button', { name: 'Approve' })
  expect(api.decideApplication).toHaveBeenCalledTimes(1)
  await respond(user)
  await screen.findByText('ACCEPTED')
  expect(vi.mocked(api.decideApplication).mock.calls[0][2].commandKey).not.toBe(vi.mocked(api.decideApplication).mock.calls[1][2].commandKey)
})
test('conflict reads current closed history without replay or response actions', async () => {
  const user = userEvent.setup(); mount()
  await screen.findByRole('button', { name: 'Approve' })
  vi.mocked(api.decideApplication).mockRejectedValue(new api.ApiError(409, 'cutoff'))
  vi.mocked(api.getApplications).mockResolvedValue({ ...page, status: 'FINALIZING', items: [{ ...attempt, status: 'EXPIRED' }] })
  vi.mocked(api.getGrouping).mockResolvedValue({ ...discovery, status: 'FINALIZING', ownGroupId: null })
  await respond(user, 'Reject')
  expect(await screen.findByText('EXPIRED')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Grouping is finalizing' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
  expect(api.decideApplication).toHaveBeenCalledTimes(1)
})
test('successful decision returned after cutoff retains history without exposing assignments', async () => {
  vi.mocked(api.decideApplication).mockImplementation(async () => { const result: api.ApplicationPage = { ...page, status: 'FINALIZING', items: [{ ...attempt, status: 'ACCEPTED' }] }; vi.mocked(api.getApplications).mockResolvedValue(result); return result })
  vi.mocked(api.getGrouping).mockResolvedValueOnce(discovery).mockResolvedValue({ ...discovery, status: 'FINALIZING', ownGroupId: null })
  const user = userEvent.setup(); mount(); await respond(user)
  await screen.findByText('ACCEPTED')
  expect(screen.getByRole('heading', { name: 'Grouping is finalizing' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
  expect(screen.queryByText(/You are a member/)).not.toBeInTheDocument()
})
test('late command cannot alter another course or release its active read', async () => {
  let finish!: (value: api.ApplicationPage) => void
  vi.mocked(api.decideApplication).mockReturnValue(new Promise(resolve => { finish = resolve }))
  const user = userEvent.setup(); const view = mount(); await respond(user)
  vi.mocked(api.getApplications).mockResolvedValue({ activityId: null, status: 'ABSENT', items: [], nextCursor: null })
  view.rerender(<Requests course={{ ...course, id: 'next' }} accountId="manager" expired={vi.fn()} />)
  await screen.findByText('No grouping activity yet')
  await act(async () => { finish(page) })
  expect(screen.queryByRole('article')).not.toBeInTheDocument()
  expect(screen.queryByText(/response confirmed/)).not.toBeInTheDocument()
})
test('double confirmation creates one command and pending history is not itself approval', async () => {
  let finish!: (value: api.ApplicationPage) => void
  vi.mocked(api.decideApplication).mockReturnValue(new Promise(resolve => { finish = resolve }))
  const user = userEvent.setup(); mount(); await user.click(await screen.findByRole('button', { name: 'Approve' }))
  await user.dblClick(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirm approval' }))
  expect(api.decideApplication).toHaveBeenCalledTimes(1)
  vi.mocked(api.getApplications).mockResolvedValue({ ...page, items: [{ ...attempt, status: 'REJECTED' }] }); await act(async () => { finish({ ...page, items: [{ ...attempt, status: 'REJECTED' }] }) })
  await screen.findByText('REJECTED')
})
