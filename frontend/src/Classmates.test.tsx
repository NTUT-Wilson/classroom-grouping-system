import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { Classmates, introductionError } from './Classmates'
import * as api from './api'
vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof api>(), listClassmates: vi.fn(), getAboutMe: vi.fn(), saveAboutMe: vi.fn() }))
const course = { id: 'course-one', code: 'C1', name: 'Computing' }
const own = { accountId: 'own', studentId: '00001', studentName: 'Alex', aboutMe: 'Original text' }
const peer = { accountId: 'peer', studentId: '00002', studentName: '陳 <Mina>', aboutMe: '<script>plain</script>\nDesign' }
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.listClassmates).mockResolvedValue([own, peer])
  vi.mocked(api.getAboutMe).mockResolvedValue(own)
  vi.mocked(api.saveAboutMe).mockImplementation(async (_, aboutMe) => ({ ...own, aboutMe }))
})
test('validates Unicode code points without normalizing text', () => {
  expect(introductionError('😀'.repeat(500))).toBe('')
  expect(introductionError('😀'.repeat(501))).toContain('500')
  expect(introductionError('')).toBe('')
  expect(introductionError('a\0b')).toContain('NUL')
  expect(introductionError('\ud800')).toContain('valid plain text')
  expect(introductionError('\udfff')).toContain('valid plain text')
  expect(introductionError('e\u0301\r\n ')).toBe('')
})
test('preserves IDs and escaped text; searches profiles and handles no matches', async () => {
  const user = userEvent.setup(); const { container } = render(<Classmates course={course} accountId="own" expired={vi.fn()} />)
  await screen.findByDisplayValue('Original text')
  expect(screen.getByText('00002')).toBeInTheDocument()
  expect(container.querySelector('script')).toBeNull()
  await user.type(screen.getByRole('searchbox'), '00002')
  expect(screen.queryByRole('heading', { name: /Alex/ })).not.toBeInTheDocument()
  expect(screen.getByRole('heading', { name: '陳 <Mina>' })).toBeInTheDocument()
  await user.clear(screen.getByRole('searchbox')); await user.type(screen.getByRole('searchbox'), 'missing')
  expect(screen.getByText('No classmates match your search')).toBeInTheDocument()
})
test('empty introduction is saved only on explicit submission', async () => {
  const user = userEvent.setup(); render(<Classmates course={course} accountId="own" expired={vi.fn()} />)
  await user.clear(await screen.findByLabelText('About me'))
  expect(api.saveAboutMe).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Save introduction' }))
  expect(api.saveAboutMe).toHaveBeenCalledWith(course.id, '')
  expect(await screen.findByRole('status')).toHaveTextContent('has been saved')
})
test('lost PUT blocks replay until a successful read; preserves draft for explicit save', async () => {
  const user = userEvent.setup(); vi.mocked(api.saveAboutMe).mockRejectedValueOnce(new TypeError('lost'))
  render(<Classmates course={course} accountId="own" expired={vi.fn()} />)
  const editor = await screen.findByLabelText('About me'); await user.clear(editor); await user.type(editor, 'New draft')
  await user.click(screen.getByRole('button', { name: 'Save introduction' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Save could not be confirmed')
  expect(screen.getByRole('button', { name: 'Save introduction' })).toBeDisabled()
  vi.mocked(api.getAboutMe).mockRejectedValueOnce(new Error('offline'))
  await user.click(screen.getByRole('button', { name: 'Check saved introduction' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('could not be checked')
  expect(screen.getByRole('button', { name: 'Save introduction' })).toBeDisabled()
  await user.click(screen.getByRole('button', { name: 'Check saved introduction' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Your draft is still in the editor')
  expect(editor).toHaveValue('New draft')
  expect(screen.getByRole('button', { name: 'Save introduction' })).toBeEnabled()
  expect(api.saveAboutMe).toHaveBeenCalledTimes(1)
  await user.click(screen.getByRole('button', { name: 'Save introduction' }))
  expect(await screen.findByRole('status')).toHaveTextContent('has been saved')
  expect(api.saveAboutMe).toHaveBeenCalledTimes(2)
})
test('denied save clears protected profiles and draft', async () => {
  vi.mocked(api.saveAboutMe).mockRejectedValue(new api.ApiError(403, 'denied'))
  const user = userEvent.setup(); render(<Classmates course={course} accountId="own" expired={vi.fn()} />)
  await screen.findByLabelText('About me'); await user.click(screen.getByRole('button', { name: 'Save introduction' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('unavailable or you do not have access')
  expect(screen.queryByText('00002')).not.toBeInTheDocument()
  expect(screen.queryByLabelText('About me')).not.toBeInTheDocument()
})
test('session expiry delegates reconciliation without a mutation replay', async () => {
  const expired = vi.fn(); vi.mocked(api.getAboutMe).mockRejectedValue(new api.ApiError(401, 'expired'))
  render(<Classmates course={course} accountId="own" expired={expired} />)
  await waitFor(() => expect(expired).toHaveBeenCalledTimes(1))
  expect(screen.queryByText('00002')).not.toBeInTheDocument()
  expect(api.saveAboutMe).not.toHaveBeenCalled()
})
test('late reads from another course cannot populate the current course', async () => {
  let finish!: (value: api.Classmate[]) => void
  vi.mocked(api.listClassmates).mockReturnValueOnce(new Promise(resolve => { finish = resolve })).mockResolvedValueOnce([])
  const view = render(<Classmates course={course} accountId="own" expired={vi.fn()} />)
  await waitFor(() => expect(api.listClassmates).toHaveBeenCalledWith(course.id))
  view.rerender(<Classmates course={{ ...course, id: 'course-two' }} accountId="own" expired={vi.fn()} />)
  await screen.findByText('No classmates yet'); finish([peer])
  await waitFor(() => expect(screen.queryByText('00002')).not.toBeInTheDocument())
})
