import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { Invitations } from './Invitations'
import * as api from './api'
vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof api>(), getInvitations: vi.fn(), getApplications: vi.fn(), getGrouping: vi.fn(), listClassmates: vi.fn(), inviteClassmate: vi.fn(), decideInvitation: vi.fn() }))
const course = { id: 'course', code: 'C', name: 'Course' }
const manager = { accountId: 'manager', studentId: '00001', studentName: 'Alex', aboutMe: 'Manager' }
const recipient = { accountId: 'student', studentId: '00002', studentName: '陳 <script>text</script>', aboutMe: 'About me 😀' }
const invitation: api.InvitationAttempt = { id: 'i1', groupId: 'g1', groupName: 'Group 1', sender: manager, recipient, status: 'PENDING', reason: null, createdAt: '2026-10-15T04:00:00Z' }
const page: api.InvitationPage = { activityId: 'activity', status: 'OPEN', items: [invitation], nextCursor: null }
const groups: api.GroupDiscovery = { activityId: 'activity', status: 'OPEN', ownGroupId: null, groups: [{ id: 'g1', name: 'Group 1', capacity: 2, availablePlaces: 1, manager, members: [manager] }] }
beforeEach(() => {
  vi.resetAllMocks(); vi.mocked(api.getInvitations).mockResolvedValue(page); vi.mocked(api.getApplications).mockResolvedValue({ ...page, items: [] }); vi.mocked(api.getGrouping).mockResolvedValue(groups); vi.mocked(api.listClassmates).mockResolvedValue([manager, recipient])
  vi.mocked(api.decideInvitation).mockImplementation(async (_course, _id, command) => { const result: api.InvitationPage = { ...page, items: [{ ...invitation, status: command.decision === 'ACCEPT' ? 'ACCEPTED' : 'REJECTED' }] }; vi.mocked(api.getInvitations).mockResolvedValue(result); return result })
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value() { this.setAttribute('open', ''); this.querySelector('button')?.focus() } })
})
function mount(accountId = 'student', expired = vi.fn()) { return render(<Invitations course={course} accountId={accountId} expired={expired} />) }
async function respond(user: ReturnType<typeof userEvent.setup>, decision = 'acceptance') {
  await user.click(await screen.findByRole('button', { name: decision === 'acceptance' ? 'Accept invitation' : 'Reject invitation' }))
  await user.click(screen.getByRole('button', { name: `Confirm ${decision}` }))
}
test('recipient sees private profiles as plain text, leading zero IDs and Taipei dates', async () => {
  const { container } = mount(); await screen.findByText('Recipient · ' + recipient.studentName)
  expect(screen.getByText('Student ID · 00002')).toBeInTheDocument(); expect(screen.getByText(/Asia\/Taipei/)).toBeInTheDocument(); expect(container.querySelector('script')).toBeNull()
  expect(screen.queryByRole('button', { name: /^Invite / })).not.toBeInTheDocument()
})
test.each(['acceptance', 'rejection'])('%s is explicit and cancel restores focus', async decision => {
  const user = userEvent.setup(); mount(); const button = await screen.findByRole('button', { name: 'Accept invitation' }); await user.click(button)
  expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus(); await user.click(screen.getByRole('button', { name: 'Cancel' })); expect(button).toHaveFocus(); expect(api.decideInvitation).not.toHaveBeenCalled()
  await respond(user, decision); await screen.findByText(decision === 'acceptance' ? 'ACCEPTED' : 'REJECTED')
  expect(api.decideInvitation).toHaveBeenCalledWith('course', 'i1', { commandKey: expect.stringMatching(/^[0-9a-f-]{36}$/), decision: decision === 'acceptance' ? 'ACCEPT' : 'REJECT' })
  expect(api.getApplications).toHaveBeenCalledTimes(2)
})
test('only own manager with space can send; rejection permits a new attempt while an application coexists', async () => {
  vi.mocked(api.getGrouping).mockResolvedValue({ ...groups, ownGroupId: 'g1' }); vi.mocked(api.getInvitations).mockResolvedValue({ ...page, items: [{ ...invitation, status: 'REJECTED' }] })
  vi.mocked(api.getApplications).mockResolvedValue({ ...page, items: [{ ...invitation, applicant: recipient, manager, status: 'PENDING' }] })
  vi.mocked(api.inviteClassmate).mockImplementation(async () => { vi.mocked(api.getInvitations).mockResolvedValue(page); return page })
  const user = userEvent.setup(); mount('manager'); await user.click(await screen.findByRole('button', { name: 'Invite ' + recipient.studentName })); await user.click(screen.getByRole('button', { name: 'Confirm invitation' })); await screen.findByText('Invitation pending')
  expect(api.inviteClassmate).toHaveBeenCalledWith('course', 'g1', { commandKey: expect.any(String), recipientId: 'student' }); expect(api.decideInvitation).not.toHaveBeenCalled()
})
test.each(['member', 'outsider'])('%s cannot send or respond', async actor => {
  mount(actor); await screen.findByText('PENDING'); expect(screen.queryByRole('button', { name: /Accept invitation|Reject invitation|^Invite / })).not.toBeInTheDocument()
})
test.each([0, -1])('full own group has no sending action (%s places)', async availablePlaces => {
  vi.mocked(api.getGrouping).mockResolvedValue({ ...groups, ownGroupId: 'g1', groups: [{ ...groups.groups[0], availablePlaces }] }); mount('manager'); await screen.findByText('PENDING'); expect(screen.queryByRole('button', { name: /^Invite / })).not.toBeInTheDocument()
})
test('older authorized pages append completed attempts, refresh returns first page', async () => {
  vi.mocked(api.getInvitations).mockResolvedValueOnce({ ...page, nextCursor: 'opaque+/=' }).mockResolvedValueOnce({ ...page, items: [{ ...invitation, id: 'old', status: 'CANCELED', reason: 'Group full' }] }).mockResolvedValue(page)
  const user = userEvent.setup(); mount(); await user.click(await screen.findByRole('button', { name: 'Load older' })); await screen.findByText('Reason · Group full'); expect(screen.getAllByRole('article')).toHaveLength(2)
  expect(api.getInvitations).toHaveBeenNthCalledWith(2, 'course', 'opaque+/='); await user.click(screen.getByRole('button', { name: 'Refresh requests' })); await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(1)); expect(api.decideInvitation).not.toHaveBeenCalled()
})
test.each([401, 403, 404])('denied history %s clears identity and actions', async status => {
  const user = userEvent.setup(); const expired = vi.fn(); mount('student', expired); await screen.findByText('PENDING'); vi.mocked(api.getInvitations).mockRejectedValue(new api.ApiError(status, 'private detail'))
  await user.click(screen.getByRole('button', { name: 'Refresh requests' })); await screen.findByRole('alert'); expect(screen.queryByText('Recipient · ' + recipient.studentName)).not.toBeInTheDocument(); expect(expired).toHaveBeenCalledTimes(status === 401 ? 1 : 0)
})
test.each([new TypeError('lost response'), new api.ApiError(403, 'csrf'), new api.ApiError(500, 'failed')])('uncertain response never replays; all histories and discovery must recover', async cause => {
  vi.mocked(api.decideInvitation).mockRejectedValueOnce(cause); const user = userEvent.setup(); mount(); await respond(user); await screen.findByRole('alert'); expect(screen.queryByRole('button', { name: 'Accept invitation' })).not.toBeInTheDocument()
  vi.mocked(api.getApplications).mockRejectedValueOnce(new TypeError('offline')); await user.click(screen.getByRole('button', { name: 'Refresh requests' })); await screen.findByRole('alert'); expect(screen.queryByRole('button', { name: 'Accept invitation' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Refresh requests' })); await screen.findByRole('button', { name: 'Accept invitation' }); expect(api.decideInvitation).toHaveBeenCalledTimes(1); await respond(user); await screen.findByText('ACCEPTED'); expect(vi.mocked(api.decideInvitation).mock.calls[0][2].commandKey).not.toBe(vi.mocked(api.decideInvitation).mock.calls[1][2].commandKey)
})
test('last-place conflict reconciles cancellation without replay or false acceptance', async () => {
  vi.mocked(api.decideInvitation).mockImplementation(async () => { vi.mocked(api.getInvitations).mockResolvedValue({ ...page, items: [{ ...invitation, status: 'CANCELED', reason: 'Group full' }] }); throw new api.ApiError(409, 'full') })
  const user = userEvent.setup(); mount(); await respond(user); await screen.findByText('Reason · Group full'); expect(screen.queryByText('ACCEPTED')).not.toBeInTheDocument(); expect(api.decideInvitation).toHaveBeenCalledTimes(1)
})
test('200 OPEN followed by fresh closed history uses fresh outcomes with no actions or assignments', async () => {
  vi.mocked(api.decideInvitation).mockImplementation(async () => { vi.mocked(api.getInvitations).mockResolvedValue({ ...page, status: 'FINALIZING', items: [{ ...invitation, status: 'EXPIRED' }] }); return page })
  const user = userEvent.setup(); mount(); await respond(user); await screen.findByText('EXPIRED'); expect(screen.getByText('Grouping is finalizing')).toBeInTheDocument(); expect(screen.queryByRole('button', { name: 'Accept invitation' })).not.toBeInTheDocument(); expect(screen.queryByText('ACCEPTED')).not.toBeInTheDocument()
})
test('closed counterpart history suppresses actions despite open invitation and discovery reads', async () => {
  vi.mocked(api.getApplications).mockResolvedValue({ ...page, status: 'FINALIZING', items: [] }); mount(); await screen.findByText('EXPIRED'); expect(screen.queryByRole('button', { name: 'Accept invitation' })).not.toBeInTheDocument()
})
test('double click and late completion cannot affect another course', async () => {
  let finish!: (value: api.InvitationPage) => void; vi.mocked(api.decideInvitation).mockReturnValue(new Promise(resolve => { finish = resolve }))
  const user = userEvent.setup(); const view = mount(); await user.click(await screen.findByRole('button', { name: 'Accept invitation' })); await user.dblClick(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirm acceptance' })); expect(api.decideInvitation).toHaveBeenCalledTimes(1)
  vi.mocked(api.getInvitations).mockResolvedValue({ ...page, status: 'ABSENT', items: [] }); view.rerender(<Invitations course={{ ...course, id: 'next' }} accountId="student" expired={vi.fn()} />); await screen.findByText('No grouping activity yet'); await act(async () => finish(page)); expect(screen.queryByRole('article')).not.toBeInTheDocument()
})

test('finalized discovery keeps completed result navigation despite older finalizing invitation history', async () => {
  vi.mocked(api.getGrouping).mockResolvedValue({ ...groups, status: 'FINALIZED', ownGroupId: null, groups: [] })
  vi.mocked(api.getInvitations).mockResolvedValue({ ...page, status: 'FINALIZING' })
  mount()
  expect(await screen.findByRole('heading', { name: 'Grouping is complete' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Accept invitation' })).not.toBeInTheDocument()
})

test.each(['counterpart history', 'command response'])('post-command finalized %s wins over older own history and discovery', async source => {
  vi.mocked(api.decideInvitation).mockImplementation(async () => {
    const history: api.InvitationPage = { ...page, status: 'FINALIZING', items: [{ ...invitation, status: 'ACCEPTED' }] }
    vi.mocked(api.getInvitations).mockResolvedValue(history)
    vi.mocked(api.getGrouping).mockResolvedValue({ ...groups, status: 'FINALIZING', ownGroupId: null, groups: [] })
    vi.mocked(api.getApplications).mockResolvedValue({ ...page, status: source === 'counterpart history' ? 'FINALIZED' : 'FINALIZING', items: [] })
    return { ...history, status: source === 'command response' ? 'FINALIZED' : 'OPEN' }
  })
  const user = userEvent.setup(); mount(); await respond(user)
  expect(await screen.findByRole('heading', { name: 'Grouping is complete' })).toBeInTheDocument()
  expect(screen.getByText('ACCEPTED')).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Grouping is finalizing' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Accept invitation|Reject invitation|^Invite / })).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'View my group status' })).toBeInTheDocument()
  expect(api.decideInvitation).toHaveBeenCalledTimes(1)
})
