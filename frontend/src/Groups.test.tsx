import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { Groups } from './Groups'
import * as api from './api'
vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof api>(), getGrouping: vi.fn(), joinEmptyGroup: vi.fn() }))
const course = { id: 'course-one', code: 'C1', name: 'Computing' }
const own = { accountId: 'own', studentId: '00001', studentName: 'Alex', aboutMe: 'Current own text' }
const peer = { accountId: 'peer', studentId: '00002', studentName: '陳 <Mina>', aboutMe: '<script>literal</script>\nCurrent introduction' }
const empty = { id: 'g1', name: 'Group 1', capacity: 2, availablePlaces: 2, manager: null, members: [] }
const open: api.GroupDiscovery = { activityId: 'activity', status: 'OPEN', ownGroupId: null, groups: [empty, { id: 'g2', name: 'Group 2', capacity: 2, availablePlaces: 1, manager: peer, members: [peer] }] }
const joined: api.GroupDiscovery = { ...open, ownGroupId: 'g1', groups: [{ ...empty, availablePlaces: 1, manager: own, members: [own] }, open.groups[1]] }
beforeEach(() => {
  vi.resetAllMocks(); vi.mocked(api.getGrouping).mockResolvedValue(open); vi.mocked(api.joinEmptyGroup).mockResolvedValue(joined)
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value() { this.setAttribute('open', ''); this.querySelector('button')?.focus() } })
})
function mount() { return render(<Groups course={course} accountId="own" expired={vi.fn()} />) }
async function choose(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: 'Join Group 1' }))
  return screen.getByRole('dialog', { name: 'Join Group 1?' })
}
async function confirm(user: ReturnType<typeof userEvent.setup>) { await choose(user); await user.click(screen.getByRole('button', { name: 'Confirm join' })) }

test('shows permitted current membership/profile fields as text and exposes only empty group joining', async () => {
  const { container } = mount(); await screen.findByText('You are ungrouped')
  const occupied = screen.getByRole('region', { name: 'Group 2' })
  expect(within(occupied).getByText('陳 <Mina>')).toBeInTheDocument()
  expect(within(occupied).getByText('Student ID · 00002')).toBeInTheDocument()
  expect(within(occupied).getByText(/<script>literal/)).toBeInTheDocument()
  expect(container.querySelector('script')).toBeNull()
  expect(within(occupied).getByText('Manager')).toBeInTheDocument()
  expect(within(occupied).queryByRole('button')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /apply|invite/i })).not.toBeInTheDocument()
})

test('joining needs deliberate confirmation; cancel restores focus and successful membership removes join controls', async () => {
  const user = userEvent.setup(); mount()
  const dialog = await choose(user)
  expect(within(dialog).getByText(/initial manager and a member/)).toBeInTheDocument()
  expect(api.joinEmptyGroup).not.toHaveBeenCalled()
  expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus()
  await user.click(screen.getByRole('button', { name: 'Cancel' }))
  expect(screen.getByRole('button', { name: 'Join Group 1' })).toHaveFocus()
  await confirm(user)
  expect(await screen.findByText('You are the group manager and a member of Group 1.')).toBeInTheDocument()
  expect(api.joinEmptyGroup).toHaveBeenCalledWith(course.id, 'g1', { commandKey: expect.stringMatching(/^[0-9a-f-]{36}$/) })
  expect(api.joinEmptyGroup).toHaveBeenCalledTimes(1)
  expect(screen.queryByRole('button', { name: /^Join / })).not.toBeInTheDocument()
})

test('first-join loser shows exact conflict, refreshes permitted status, and never joins occupied group', async () => {
  const user = userEvent.setup(); vi.mocked(api.joinEmptyGroup).mockRejectedValueOnce(new api.ApiError(409, 'conflict', 'GROUP_NOT_EMPTY'))
  vi.mocked(api.getGrouping).mockResolvedValueOnce(open).mockResolvedValue({ ...open, groups: [{ ...empty, availablePlaces: 1, manager: peer, members: [peer] }, open.groups[1]] })
  mount(); await confirm(user)
  expect(await screen.findByRole('alert')).toHaveTextContent(/^This group is no longer empty$/)
  await waitFor(() => expect(api.getGrouping).toHaveBeenCalledTimes(2))
  expect(screen.queryByRole('button', { name: /^Join / })).not.toBeInTheDocument()
  expect(api.joinEmptyGroup).toHaveBeenCalledTimes(1)
})

test('uncertain response prevents mutations until successful read and later deliberate attempt uses fresh UUID', async () => {
  const user = userEvent.setup(); vi.mocked(api.joinEmptyGroup).mockRejectedValueOnce(new TypeError('lost')).mockResolvedValueOnce(joined)
  mount(); await confirm(user)
  expect(await screen.findByRole('heading', { name: 'Check groups before continuing' })).toBeInTheDocument()
  expect(screen.queryByRole('region', { name: 'Group 1' })).not.toBeInTheDocument()
  vi.mocked(api.getGrouping).mockRejectedValueOnce(new Error('offline'))
  await user.click(screen.getByRole('button', { name: 'Check current groups' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('could not be checked')
  expect(screen.queryByRole('button', { name: /^Join / })).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Check current groups' }))
  await screen.findByRole('button', { name: 'Join Group 1' })
  expect(api.joinEmptyGroup).toHaveBeenCalledTimes(1)
  await confirm(user)
  await screen.findByText('Your join was confirmed.')
  const keys = vi.mocked(api.joinEmptyGroup).mock.calls.map(call => call[2].commandKey)
  expect(keys[0]).not.toBe(keys[1])
})

test.each([403, 404, 401])('read denial %s clears protected data and expires sessions only on 401', async status => {
  const expired = vi.fn(); vi.mocked(api.getGrouping).mockRejectedValue(new api.ApiError(status, 'denied'))
  render(<Groups course={course} accountId="own" expired={expired} />)
  expect(await screen.findByRole('alert')).toHaveTextContent('unavailable or you do not have access')
  expect(screen.queryByText(peer.studentName)).not.toBeInTheDocument()
  expect(expired).toHaveBeenCalledTimes(status === 401 ? 1 : 0)
})

test.each([403, 404, 401])('join denial %s removes roster with no automatic mutation replay', async status => {
  const user = userEvent.setup(); vi.mocked(api.joinEmptyGroup).mockRejectedValue(new api.ApiError(status, 'denied')); mount(); await confirm(user)
  expect(await screen.findByRole('heading', { name: 'Groups unavailable' })).toBeInTheDocument()
  expect(screen.queryByText(peer.studentName)).not.toBeInTheDocument()
  expect(api.joinEmptyGroup).toHaveBeenCalledTimes(1)
})

test('closed read hides even malformed partial roster and refresh remains read-only', async () => {
  const user = userEvent.setup(); vi.mocked(api.getGrouping).mockResolvedValue({ ...joined, status: 'FINALIZING' }); mount()
  await screen.findByRole('heading', { name: 'Grouping is finalizing' })
  expect(screen.queryByText(own.studentName)).not.toBeInTheDocument()
  expect(screen.queryByRole('region', { name: 'Group 1' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Refresh groups' }))
  await waitFor(() => expect(api.getGrouping).toHaveBeenCalledTimes(2))
  expect(api.joinEmptyGroup).not.toHaveBeenCalled()
})

test('successful join that crosses cutoff stays confirmed and hides partial membership', async () => {
  const user = userEvent.setup(); vi.mocked(api.joinEmptyGroup).mockResolvedValue({ activityId: 'activity', status: 'FINALIZING', ownGroupId: null, groups: [] }); mount(); await confirm(user)
  expect(await screen.findByText('Your join was confirmed.')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Grouping is finalizing' })).toBeInTheDocument()
  expect(screen.queryByText(own.studentName)).not.toBeInTheDocument()
})

test('late discovery cannot replace the next course', async () => {
  let finish!: (data: api.GroupDiscovery) => void
  vi.mocked(api.getGrouping).mockReturnValueOnce(new Promise(resolve => { finish = resolve })).mockResolvedValueOnce({ activityId: null, status: 'ABSENT', ownGroupId: null, groups: [] })
  const view = mount(); await waitFor(() => expect(api.getGrouping).toHaveBeenCalledWith(course.id))
  view.rerender(<Groups course={{ ...course, id: 'next' }} accountId="own" expired={vi.fn()} />)
  await screen.findByText('No grouping activity yet')
  await act(async () => { finish(open) })
  expect(screen.queryByText(peer.studentName)).not.toBeInTheDocument()
})


test('other conflict reconciles current closed state without another command', async () => {
  const user = userEvent.setup(); vi.mocked(api.joinEmptyGroup).mockRejectedValue(new api.ApiError(409, 'conflict', 'ACTIVITY_CLOSED'))
  vi.mocked(api.getGrouping).mockResolvedValueOnce(open).mockResolvedValue({ activityId: 'activity', status: 'FINALIZING', ownGroupId: null, groups: [] })
  mount(); await confirm(user)
  expect(await screen.findByRole('heading', { name: 'Grouping is finalizing' })).toBeInTheDocument()
  expect(screen.getByRole('alert')).toHaveTextContent('Joining conditions have changed')
  expect(api.joinEmptyGroup).toHaveBeenCalledTimes(1)
})

test('late successful join cannot display old-course membership after navigation', async () => {
  const user = userEvent.setup(); let finish!: (data: api.GroupDiscovery) => void
  vi.mocked(api.joinEmptyGroup).mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
  vi.mocked(api.getGrouping).mockResolvedValueOnce(open).mockResolvedValueOnce({ activityId: null, status: 'ABSENT', ownGroupId: null, groups: [] })
  const view = mount(); await confirm(user)
  await screen.findByText('Confirming your join…')
  view.rerender(<Groups course={{ ...course, id: 'next' }} accountId="own" expired={vi.fn()} />)
  await screen.findByText('No grouping activity yet')
  await act(async () => { finish(joined) })
  expect(screen.queryByText('Your join was confirmed.')).not.toBeInTheDocument()
  expect(screen.queryByText(own.studentName)).not.toBeInTheDocument()
})
