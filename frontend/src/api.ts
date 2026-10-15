export type Session = { accountId: string; username: string; role: 'TEACHER' | 'STUDENT' }
export type Course = { id: string; code: string; name: string }
export type Classmate = { accountId: string; studentId: string; studentName: string; aboutMe: string }
export type Activity = { id: string; courseId: string; instructions: string; deadline: string; status: 'OPEN' | 'FINALIZING'; groups: { id: string; ordinal: number; name: string; capacity: number }[] }
export type ActivityWorkspace = { activity: Activity | null; enrolledCount: number | null }
export type CreateActivity = { commandKey: string; instructions: string; deadline: string; capacities: number[] }
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
export function getActivity(id: string): Promise<ActivityWorkspace> { return readCourse(`/api/courses/${encodeURIComponent(id)}/activity`) }
async function saveActivitySetting(id: string, setting: 'instructions' | 'deadline', command: { commandKey: string; instructions?: string; deadline?: string }): Promise<ActivityWorkspace> {
  if (!token) await refreshCsrf()
  const csrf = token!
  token = undefined
  const response = await fetch(`/api/courses/${encodeURIComponent(id)}/activity/${setting}`, {
    method: setting === 'instructions' ? 'PUT' : 'POST', credentials: 'same-origin',
    headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'application/json' }, body: JSON.stringify(command),
  })
  if (!response.ok) throw new ApiError(response.status, response.status === 400
    ? setting === 'instructions' ? 'Enter nonblank plain-text instructions of at most 5,000 characters.' : 'Choose a later deadline while grouping is still open. Refresh to check the current deadline.'
    : response.status === 401 ? 'Your session expired. Please sign in again.'
      : response.status === 403 || response.status === 404 ? 'This request is unavailable or you do not have access. Check current activity before continuing.'
        : 'Save could not be confirmed. Check current activity before trying again.')
  return response.json() as Promise<ActivityWorkspace>
}
export function saveInstructions(id: string, command: { commandKey: string; instructions: string }): Promise<ActivityWorkspace> { return saveActivitySetting(id, 'instructions', command) }
export function extendDeadline(id: string, command: { commandKey: string; deadline: string }): Promise<ActivityWorkspace> { return saveActivitySetting(id, 'deadline', command) }
export async function createActivity(id: string, command: CreateActivity): Promise<ActivityWorkspace> {
  if (!token) await refreshCsrf()
  const csrf = token!
  token = undefined
  const response = await fetch(`/api/courses/${encodeURIComponent(id)}/activity`, {
    method: 'POST', credentials: 'same-origin', headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'application/json' }, body: JSON.stringify(command),
  })
  if (!response.ok) throw new ApiError(response.status, response.status === 400
    ? 'Check the instructions, future Asia/Taipei deadline, and whole-number capacities. Their total must match all currently enrolled students.'
    : response.status === 401 ? 'Your session expired. Please sign in again.'
      : response.status === 403 ? 'The request was denied. Refresh your session and check activity status before continuing.'
        : response.status === 404 ? 'This course is unavailable or you do not have access.' : 'Creation could not be confirmed. Check activity status before trying again.')
  return response.json() as Promise<ActivityWorkspace>
}
export function listClassmates(id: string): Promise<Classmate[]> { return readCourse(`/api/courses/${encodeURIComponent(id)}/classmates`) }
export function getAboutMe(id: string): Promise<Classmate> { return readCourse(`/api/courses/${encodeURIComponent(id)}/about-me`) }
export async function saveAboutMe(id: string, aboutMe: string): Promise<Classmate> {
  if (!token) await refreshCsrf()
  const csrf = token!
  token = undefined
  const response = await fetch(`/api/courses/${encodeURIComponent(id)}/about-me`, {
    method: 'PUT', credentials: 'same-origin', headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'application/json' },
    body: JSON.stringify({ aboutMe }),
  })
  if (!response.ok) throw new ApiError(response.status, response.status === 400 ? 'About me must contain valid plain text of 500 characters or fewer.'
    : response.status === 404 || response.status === 403 ? 'This course is unavailable or you do not have access.' : 'Save could not be confirmed.')
  return response.json() as Promise<Classmate>
}
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
