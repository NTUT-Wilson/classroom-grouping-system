export type Session = { accountId: string; username: string; role: 'TEACHER' | 'STUDENT' }
export type Course = { id: string; code: string; name: string }
type CsrfToken = { headerName: string; token: string }
let token: CsrfToken | undefined

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) { super(message) }
}
async function refreshCsrf(): Promise<void> {
  token = undefined
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) throw new Error('Unable to establish a secure session. Please retry.')
  token = await response.json() as CsrfToken
}
export async function currentSession(): Promise<Session | null> {
  const response = await fetch('/api/auth/session', { credentials: 'same-origin', cache: 'no-store' })
  if (response.status === 401) return null
  if (!response.ok) throw new Error('Unable to check your session. Please retry.')
  return response.json() as Promise<Session>
}
export async function initializeSession(): Promise<Session | null> {
  await refreshCsrf()
  return currentSession()
}
async function readCourse<T>(url: string): Promise<T> {
  const response = await fetch(url, { credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) throw new ApiError(response.status, response.status === 404
    ? 'This course is unavailable or you do not have access.' : 'Unable to load courses. Please retry.')
  return response.json() as Promise<T>
}
export function listCourses(): Promise<Course[]> { return readCourse('/api/courses') }
export function getCourse(id: string): Promise<Course> { return readCourse(`/api/courses/${encodeURIComponent(id)}`) }
async function mutate(url: string, body?: URLSearchParams): Promise<void> {
  if (!token) await refreshCsrf()
  const csrf = token!
  // Discard before sending: an uncertain response must never reuse the token or replay the mutation.
  token = undefined
  const response = await fetch(url, {
    method: 'POST', credentials: 'same-origin', headers: { [csrf.headerName]: csrf.token }, body,
  })
  if (!response.ok) throw new Error(response.status === 401 ? 'Invalid username or password.'
    : 'Request could not be confirmed. Check your session before trying again.')
  await refreshCsrf()
}
export function signIn(username: string, password: string): Promise<void> {
  return mutate('/api/auth/login', new URLSearchParams({ username, password }))
}
export function signOut(): Promise<void> { return mutate('/api/auth/logout') }
