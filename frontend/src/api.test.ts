import { afterEach, expect, test, vi } from 'vitest'

afterEach(() => { vi.unstubAllGlobals(); vi.resetModules() })

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
