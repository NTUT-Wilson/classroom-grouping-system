type CsrfToken = { headerName: string; token: string }
let token: CsrfToken | undefined

async function refreshCsrf(): Promise<void> {
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) throw new Error('Unable to establish a secure session. Please retry.')
  token = await response.json() as CsrfToken
}

export async function currentSession(): Promise<string | null> {
  const response = await fetch('/api/auth/session', { credentials: 'same-origin', cache: 'no-store' })
  if (response.status === 401) return null
  if (!response.ok) throw new Error('Unable to check your session. Please retry.')
  const session = await response.json() as { username: string }
  return session.username
}

async function mutate(url: string, body?: URLSearchParams): Promise<void> {
  if (!token) await refreshCsrf()
  const csrf = token!
  const response = await fetch(url, {
    method: 'POST', credentials: 'same-origin', headers: { [csrf.headerName]: csrf.token }, body,
  })
  // Never replay a mutation automatically, including after expiry or an uncertain network outcome.
  token = undefined
  if (!response.ok) throw new Error(response.status === 401 ? 'Invalid username or password.'
    : 'Request could not be confirmed. Check your session before trying again.')
  await refreshCsrf()
}

export function signIn(username: string, password: string): Promise<void> {
  return mutate('/api/auth/login', new URLSearchParams({ username, password }))
}

export function signOut(): Promise<void> { return mutate('/api/auth/logout') }
