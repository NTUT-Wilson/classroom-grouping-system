import { afterEach, expect, test, vi } from 'vitest'

afterEach(() => { vi.unstubAllGlobals(); vi.resetModules() })

test('bootstraps CSRF before checking the session, including anonymous startup', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'startup' })))
    .mockResolvedValueOnce(new Response(null, { status: 401 }))
  vi.stubGlobal('fetch', fetchMock)
  const { initializeSession } = await import('./api')
  expect(await initializeSession()).toBeNull()
  expect(fetchMock.mock.calls.map(call => call[0])).toEqual(['/api/auth/csrf', '/api/auth/session'])
})

test('a lost response never replays login and the next explicit attempt fetches a new token', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'old' })))
    .mockRejectedValueOnce(new TypeError('Network unavailable'))
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'new' })))
    .mockResolvedValueOnce(new Response(null, { status: 204 }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'after' })))
  vi.stubGlobal('fetch', fetchMock)
  const { signIn } = await import('./api')
  await expect(signIn('demo', 'test-password')).rejects.toThrow('Network unavailable')
  expect(fetchMock).toHaveBeenCalledTimes(2)
  await signIn('demo', 'test-password')
  expect(fetchMock.mock.calls[3][1].headers).toEqual({ 'X-CSRF-TOKEN': 'new' })
})

test('sends CSRF headers and obtains a fresh token after login', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'before' })))
    .mockResolvedValueOnce(new Response(null, { status: 204 }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'after' })))
  vi.stubGlobal('fetch', fetchMock)
  const { signIn } = await import('./api')
  await signIn('demo', 'test-password')
  expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: 'POST', headers: { 'X-CSRF-TOKEN': 'before' } })
  expect(fetchMock.mock.calls[2][0]).toBe('/api/auth/csrf')
})

test('does not replay a request rejected for CSRF', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'expired' })))
    .mockResolvedValueOnce(new Response(null, { status: 403 }))
  vi.stubGlobal('fetch', fetchMock)
  const { signIn } = await import('./api')
  await expect(signIn('demo', 'test-password')).rejects.toThrow('could not be confirmed')
  expect(fetchMock).toHaveBeenCalledTimes(2)
})

test('session returns identity and role metadata', async () => {
  const session = { accountId: 'actor', username: 'demo', role: 'STUDENT' }
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(session))))
  const { currentSession } = await import('./api')
  expect(await currentSession()).toEqual(session)
})
test('course reads preserve denial status and use uncached credentialed GET', async () => {
  const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify([]))).mockResolvedValueOnce(new Response(null, { status: 404 }))
  vi.stubGlobal('fetch', fetchMock)
  const { listCourses, getCourse } = await import('./api')
  expect(await listCourses()).toEqual([])
  await expect(getCourse('course')).rejects.toMatchObject({ status: 404, message: 'This course is unavailable or you do not have access.' })
  expect(fetchMock.mock.calls[0]).toEqual(['/api/courses', { credentials: 'same-origin', cache: 'no-store' }])
})
test('expired course session is reported as 401 for reconciliation', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })))
  const { listCourses } = await import('./api')
  await expect(listCourses()).rejects.toMatchObject({ status: 401 })
})

test('profile PUT uses JSON and CSRF once; a lost response never replays', async () => {
  const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'profile-token' })))
    .mockRejectedValueOnce(new TypeError('lost response'))
    .mockResolvedValueOnce(new Response(JSON.stringify({ accountId: 'own', studentId: '00001', studentName: 'Alex', aboutMe: 'exact\ntext' })))
  vi.stubGlobal('fetch', fetchMock)
  const { saveAboutMe, getAboutMe } = await import('./api')
  await expect(saveAboutMe('course', 'exact\ntext')).rejects.toThrow('lost response')
  expect(fetchMock).toHaveBeenCalledTimes(2)
  expect(fetchMock.mock.calls[1]).toEqual(['/api/courses/course/about-me', { method: 'PUT', credentials: 'same-origin', headers: { 'X-CSRF-TOKEN': 'profile-token', 'Content-Type': 'application/json' }, body: JSON.stringify({ aboutMe: 'exact\ntext' }) }])
  expect(await getAboutMe('course')).toMatchObject({ studentId: '00001', aboutMe: 'exact\ntext' })
  expect(fetchMock.mock.calls.filter(call => call[1]?.method === 'PUT')).toHaveLength(1)
})

test.each([403, 500])('creation POST status %s is never replayed and carries explicit UUID and exact JSON', async status => {
  const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'creation-token' })))
    .mockResolvedValueOnce(new Response(null, { status }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ activity: null, enrolledCount: 6 })))
  vi.stubGlobal('fetch', fetchMock)
  const { createActivity, getActivity } = await import('./api')
  const command = { commandKey: '11111111-1111-4111-8111-111111111111', instructions: 'Exact\ntext  ', deadline: '2099-10-20T10:00:00+08:00', capacities: [2, 4] }
  await expect(createActivity('course', command)).rejects.toMatchObject({ status })
  expect(fetchMock.mock.calls[1]).toEqual(['/api/courses/course/activity', { method: 'POST', credentials: 'same-origin', headers: { 'X-CSRF-TOKEN': 'creation-token', 'Content-Type': 'application/json' }, body: JSON.stringify(command) }])
  expect(await getActivity('course')).toEqual({ activity: null, enrolledCount: 6 })
  expect(fetchMock.mock.calls.filter(call => call[1]?.method === 'POST')).toHaveLength(1)
})
test('lost creation response is not replayed and an explicit later attempt obtains fresh CSRF', async () => {
  const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'old' })))
    .mockRejectedValueOnce(new TypeError('lost response'))
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'new' })))
    .mockResolvedValueOnce(new Response(JSON.stringify({ activity: { id: 'a' }, enrolledCount: null })))
  vi.stubGlobal('fetch', fetchMock)
  const { createActivity } = await import('./api')
  const command = { commandKey: 'first', instructions: 'Task', deadline: '2099-10-20T10:00:00+08:00', capacities: [2] }
  await expect(createActivity('course', command)).rejects.toThrow('lost response')
  expect(fetchMock).toHaveBeenCalledTimes(2)
  await expect(createActivity('course', { ...command, commandKey: 'second' })).resolves.toMatchObject({ activity: { id: 'a' } })
  expect(fetchMock.mock.calls[3][1].headers).toMatchObject({ 'X-CSRF-TOKEN': 'new' })
})

test.each(['instructions', 'deadline'] as const)('settings %s sends narrow JSON once and never replays CSRF rejection', async setting => {
  const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'settings-token' })))
    .mockResolvedValueOnce(new Response(null, { status: 403 }))
  vi.stubGlobal('fetch', fetchMock)
  const api = await import('./api')
  const command = setting === 'instructions' ? { commandKey: 'new-key', instructions: 'Exact\n<script>text</script>  😀' } : { commandKey: 'new-key', deadline: '2099-10-21T10:00:00+08:00' }
  await expect(setting === 'instructions' ? api.saveInstructions('course', command as { commandKey: string; instructions: string }) : api.extendDeadline('course', command as { commandKey: string; deadline: string })).rejects.toMatchObject({ status: 403 })
  expect(fetchMock).toHaveBeenCalledTimes(2)
  expect(fetchMock.mock.calls[1]).toEqual([`/api/courses/course/activity/${setting}`, { method: setting === 'instructions' ? 'PUT' : 'POST', credentials: 'same-origin', headers: { 'X-CSRF-TOKEN': 'settings-token', 'Content-Type': 'application/json' }, body: JSON.stringify(command) }])
})

test('settings lost response never replays and explicit later command obtains fresh CSRF', async () => {
  const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'old' })))
    .mockRejectedValueOnce(new TypeError('lost response'))
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'new' })))
    .mockResolvedValueOnce(new Response(JSON.stringify({ activity: { id: 'a' }, enrolledCount: null })))
  vi.stubGlobal('fetch', fetchMock)
  const { saveInstructions } = await import('./api')
  await expect(saveInstructions('course', { commandKey: 'first', instructions: 'Text' })).rejects.toThrow('lost response')
  expect(fetchMock).toHaveBeenCalledTimes(2)
  await saveInstructions('course', { commandKey: 'second', instructions: 'Text' })
  expect(fetchMock.mock.calls[3][1].headers).toMatchObject({ 'X-CSRF-TOKEN': 'new' })
})
