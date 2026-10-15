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
