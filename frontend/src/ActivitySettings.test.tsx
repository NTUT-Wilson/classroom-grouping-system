import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { ActivitySettings } from './ActivitySettings'
import * as api from './api'
vi.mock('./api', async original => ({ ...await original<typeof api>(), getActivity: vi.fn(), saveInstructions: vi.fn(), extendDeadline: vi.fn() }))
const activity: api.Activity = { id: 'a', courseId: 'c', instructions: 'Saved <b>plain</b> text  ', deadline: '2099-10-20T02:00:00Z', status: 'OPEN', groups: [{ id: 'g', ordinal: 1, name: 'Group 1', capacity: 2 }] }
beforeEach(() => { vi.resetAllMocks(); vi.mocked(api.getActivity).mockResolvedValue({ activity, enrolledCount: null }); vi.mocked(api.saveInstructions).mockResolvedValue({ activity, enrolledCount: null }); vi.mocked(api.extendDeadline).mockResolvedValue({ activity, enrolledCount: null }) })
function view(initial = activity, role: 'TEACHER' | 'STUDENT' = 'TEACHER', expired = vi.fn()) { return render(<ActivitySettings initial={initial} courseId="c" role={role} expired={expired} />) }
test('instructions save exact escaped text after cutoff without other fields', async () => {
  const user = userEvent.setup(); const { container } = view({ ...activity, status: 'FINALIZING' })
  expect(screen.queryByRole('button', { name: 'Extend deadline' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Edit instructions' }))
  expect(screen.getByLabelText('Instructions')).toHaveFocus()
  fireEvent.change(screen.getByLabelText('Instructions'), { target: { value: ' 😀\n<script>literal</script>  ' } })
  await user.click(screen.getByRole('button', { name: 'Save instructions' })); await screen.findByText('Instructions saved.')
  expect(api.saveInstructions).toHaveBeenCalledExactlyOnceWith('c', { commandKey: expect.stringMatching(/^[0-9a-f-]{36}$/), instructions: ' 😀\n<script>literal</script>  ' })
  expect(container.querySelector('script')).toBeNull(); expect(api.extendDeadline).not.toHaveBeenCalled()
})
test('deadline requires later fixed Taipei wall time and sends only deadline command', async () => {
  const user = userEvent.setup(); view(); await user.click(screen.getByRole('button', { name: 'Extend deadline' })); await user.click(screen.getByRole('button', { name: 'Save deadline' }))
  await screen.findByText('Choose a deadline later than the current deadline.'); expect(api.extendDeadline).not.toHaveBeenCalled()
  fireEvent.change(screen.getByLabelText('New deadline (Asia/Taipei)'), { target: { value: '2099-10-21T10:00' } })
  await user.click(screen.getByRole('button', { name: 'Save deadline' })); await screen.findByText('Deadline extended.')
  expect(api.extendDeadline).toHaveBeenCalledExactlyOnceWith('c', { commandKey: expect.any(String), deadline: '2099-10-21T10:00:00+08:00' })
})
test.each([new TypeError('lost'), new api.ApiError(403, 'csrf'), new api.ApiError(404, 'denied')])('uncertain/denied save preserves draft and requires read plus explicit review and new key', async cause => {
  vi.mocked(api.saveInstructions).mockRejectedValueOnce(cause)
  const user = userEvent.setup(); view(); await user.click(screen.getByRole('button', { name: 'Edit instructions' }))
  fireEvent.change(screen.getByLabelText('Instructions'), { target: { value: 'Preserved draft' } })
  await user.click(screen.getByRole('button', { name: 'Save instructions' })); await screen.findByText(/Save could not be confirmed/)
  expect(screen.getByRole('button', { name: 'Save instructions' })).toBeDisabled()
  vi.mocked(api.getActivity).mockRejectedValueOnce(new TypeError('read failed'))
  await user.click(screen.getByRole('button', { name: 'Check activity' })); await screen.findByText('Unable to check activity status. Please retry the read.')
  expect(screen.getByLabelText('Instructions')).toHaveValue('Preserved draft')
  await user.click(screen.getByRole('button', { name: 'Check activity' })); await screen.findByText('Current activity checked.')
  expect(api.saveInstructions).toHaveBeenCalledTimes(1); expect(screen.queryByRole('button', { name: 'Save instructions' })).not.toBeInTheDocument()
  expect(screen.getByText('Current saved instructions')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Review preserved draft' })); expect(api.saveInstructions).toHaveBeenCalledTimes(1); await user.click(screen.getByRole('button', { name: 'Save instructions' })); await screen.findByText('Instructions saved.')
  expect(vi.mocked(api.saveInstructions).mock.calls[0][1].commandKey).not.toBe(vi.mocked(api.saveInstructions).mock.calls[1][1].commandKey)
})
test('cutoff reconciliation disables deadline save while instructions remain editable', async () => {
  const user = userEvent.setup(); view(); await user.click(screen.getByRole('button', { name: 'Extend deadline' }))
  vi.mocked(api.getActivity).mockResolvedValue({ activity: { ...activity, status: 'FINALIZING' }, enrolledCount: null })
  await user.click(screen.getByRole('button', { name: 'Refresh activity' })); await screen.findByText('Current activity checked.')
  expect(screen.getByRole('button', { name: 'Save deadline' })).toBeDisabled()
  await user.click(screen.getByRole('button', { name: 'Cancel extension' })); expect(screen.getByRole('button', { name: 'Edit instructions' })).toBeEnabled()
})
test('401 clears activity and draft and requests authentication without replay', async () => {
  vi.mocked(api.saveInstructions).mockRejectedValue(new api.ApiError(401, 'expired'))
  const expired = vi.fn(); const user = userEvent.setup(); view(activity, 'TEACHER', expired)
  await user.click(screen.getByRole('button', { name: 'Edit instructions' })); await user.click(screen.getByRole('button', { name: 'Save instructions' }))
  await waitFor(() => expect(expired).toHaveBeenCalledOnce()); expect(screen.queryByLabelText('Instructions')).not.toBeInTheDocument(); expect(screen.queryByText(activity.instructions)).not.toBeInTheDocument()
})
test('student refresh reads latest instructions without edit or result controls', async () => {
  const user = userEvent.setup(); view(activity, 'STUDENT'); vi.mocked(api.getActivity).mockResolvedValue({ activity: { ...activity, instructions: 'Latest text', status: 'FINALIZING' }, enrolledCount: null })
  await user.click(screen.getByRole('button', { name: 'Refresh activity' })); await screen.findByText('Latest text')
  expect(screen.getByText(/Grouping is closed/)).toBeInTheDocument(); expect(screen.queryByRole('button', { name: /Edit|Extend|Allocate|Results/i })).not.toBeInTheDocument()
})


test('invalid instructions and backend validation preserve draft with correction feedback', async () => {
  const user = userEvent.setup(); view(); await user.click(screen.getByRole('button', { name: 'Edit instructions' }))
  fireEvent.change(screen.getByLabelText('Instructions'), { target: { value: '😀'.repeat(5001) } })
  await user.click(screen.getByRole('button', { name: 'Save instructions' })); await screen.findByText('Instructions must be 5,000 characters or fewer.'); expect(api.saveInstructions).not.toHaveBeenCalled()
  fireEvent.change(screen.getByLabelText('Instructions'), { target: { value: 'Valid draft' } })
  vi.mocked(api.saveInstructions).mockRejectedValueOnce(new api.ApiError(400, 'Correct the text.'))
  await user.click(screen.getByRole('button', { name: 'Save instructions' })); await screen.findByText('Correct the text.'); expect(screen.getByLabelText('Instructions')).toHaveValue('Valid draft')
})
test('unmounted editor ignores a stale write result', async () => {
  let finish!: (result: api.ActivityWorkspace) => void
  vi.mocked(api.saveInstructions).mockReturnValue(new Promise(resolve => { finish = resolve }))
  const user = userEvent.setup(); const { unmount } = view(); await user.click(screen.getByRole('button', { name: 'Edit instructions' })); await user.click(screen.getByRole('button', { name: 'Save instructions' })); unmount(); finish({ activity, enrolledCount: null })
  expect(screen.queryByText('Instructions saved.')).not.toBeInTheDocument()
})
