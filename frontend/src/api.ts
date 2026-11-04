export type Session = { accountId: string; username: string; role: 'TEACHER' | 'STUDENT' }
export type Course = { id: string; code: string; name: string }
export type Classmate = { accountId: string; studentId: string; studentName: string; aboutMe: string }
export type Activity = { id: string; courseId: string; instructions: string; deadline: string; status: 'OPEN' | 'FINALIZING' | 'FINALIZED'; groups: { id: string; ordinal: number; name: string; capacity: number }[] }
export type ActivityWorkspace = { activity: Activity | null; enrolledCount: number | null }
export type CreateActivity = { commandKey: string; instructions: string; deadline: string; capacities: number[] }
export type DiscoveryGroup = { id: string; name: string; capacity: number; availablePlaces: number; manager: Classmate | null; members: Classmate[] }
export type GroupDiscovery = { activityId: string | null; status: 'ABSENT' | 'OPEN' | 'FINALIZING' | 'FINALIZED'; ownGroupId: string | null; groups: DiscoveryGroup[] }
export type ApplicationAttempt = { id: string; groupId: string; groupName: string; applicant: Classmate; manager: Classmate; status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELED' | 'EXPIRED'; reason: string | null; createdAt: string }
export type ApplicationPage = { activityId: string | null; status: 'ABSENT' | 'OPEN' | 'FINALIZING' | 'FINALIZED'; items: ApplicationAttempt[]; nextCursor: string | null }
export type InvitationAttempt = Omit<ApplicationAttempt, 'applicant' | 'manager'> & { sender: Classmate; recipient: Classmate }
export type InvitationPage = Omit<ApplicationPage, 'items'> & { items: InvitationAttempt[] }
export function getInvitations(id: string, cursor?: string): Promise<InvitationPage> {
  return readCourse(`/api/courses/${encodeURIComponent(id)}/invitations?limit=20${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`)
}
async function invitationCommand(url: string, command: { commandKey: string; recipientId?: string; decision?: 'ACCEPT' | 'REJECT' }): Promise<InvitationPage> {
  if (!token) await refreshCsrf()
  const csrf = token!
  token = undefined
  const response = await fetch(url, { method: 'POST', credentials: 'same-origin', headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'application/json' }, body: JSON.stringify(command) })
  if (!response.ok) throw new ApiError(response.status, 'Invitation action could not be confirmed. Refresh requests before choosing another action.')
  return response.json() as Promise<InvitationPage>
}
export function inviteClassmate(id: string, groupId: string, command: { commandKey: string; recipientId: string }): Promise<InvitationPage> {
  return invitationCommand(`/api/courses/${encodeURIComponent(id)}/groups/${encodeURIComponent(groupId)}/invitations`, command)
}
export function decideInvitation(id: string, invitationId: string, command: { commandKey: string; decision: 'ACCEPT' | 'REJECT' }): Promise<InvitationPage> {
  return invitationCommand(`/api/courses/${encodeURIComponent(id)}/invitations/${encodeURIComponent(invitationId)}/decision`, command)
}
export function getApplications(id: string, cursor?: string): Promise<ApplicationPage> {
  return readCourse(`/api/courses/${encodeURIComponent(id)}/applications?limit=20${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`)
}
async function applicationCommand(url: string, command: { commandKey: string; decision?: 'APPROVE' | 'REJECT' }): Promise<ApplicationPage> {
  if (!token) await refreshCsrf()
  const csrf = token!
  token = undefined
  const response = await fetch(url, { method: 'POST', credentials: 'same-origin', headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'application/json' }, body: JSON.stringify(command) })
  if (!response.ok) throw new ApiError(response.status, 'Application action could not be confirmed. Refresh requests and groups before choosing another action.')
  return response.json() as Promise<ApplicationPage>
}
export function applyToGroup(id: string, groupId: string, command: { commandKey: string }): Promise<ApplicationPage> {
  return applicationCommand(`/api/courses/${encodeURIComponent(id)}/groups/${encodeURIComponent(groupId)}/applications`, command)
}
export function decideApplication(id: string, applicationId: string, command: { commandKey: string; decision: 'APPROVE' | 'REJECT' }): Promise<ApplicationPage> {
  return applicationCommand(`/api/courses/${encodeURIComponent(id)}/applications/${encodeURIComponent(applicationId)}/decision`, command)
}
type CsrfToken = { headerName: string; token: string }
let token: CsrfToken | undefined

export class ApiError extends Error {
  constructor(public readonly status: number, message: string, public readonly code?: string) { super(message) }
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
export function getGrouping(id: string): Promise<GroupDiscovery> { return readCourse(`/api/courses/${encodeURIComponent(id)}/grouping`) }
async function lifecycleCommand(id: string, groupId: string, action: 'leave' | 'manager-transfer', command: { commandKey: string; targetMemberId?: string }): Promise<GroupDiscovery> {
  if (!token) await refreshCsrf()
  const csrf = token!
  token = undefined
  const response = await fetch(`/api/courses/${encodeURIComponent(id)}/groups/${encodeURIComponent(groupId)}/${action}`, {
    method: 'POST', credentials: 'same-origin', headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'application/json' }, body: JSON.stringify(command),
  })
  if (!response.ok) throw new ApiError(response.status, 'Group action could not be confirmed. Check current groups before continuing.')
  return response.json() as Promise<GroupDiscovery>
}
export function leaveGroup(id: string, groupId: string, command: { commandKey: string }): Promise<GroupDiscovery> {
  return lifecycleCommand(id, groupId, 'leave', command)
}
export function transferManager(id: string, groupId: string, command: { commandKey: string; targetMemberId: string }): Promise<GroupDiscovery> {
  return lifecycleCommand(id, groupId, 'manager-transfer', command)
}
export async function joinEmptyGroup(id: string, groupId: string, command: { commandKey: string }): Promise<GroupDiscovery> {
  if (!token) await refreshCsrf()
  const csrf = token!
  token = undefined
  const response = await fetch(`/api/courses/${encodeURIComponent(id)}/groups/${encodeURIComponent(groupId)}/join`, {
    method: 'POST', credentials: 'same-origin', headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'application/json' }, body: JSON.stringify(command),
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { code?: unknown } | null
    const code = typeof problem?.code === 'string' ? problem.code : undefined
    throw new ApiError(response.status, 'Joining could not be confirmed. Check current groups before continuing.', code)
  }
  return response.json() as Promise<GroupDiscovery>
}
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
    ? 'Check the instructions, future America/New_York deadline, and whole-number capacities. Their total must match all currently enrolled students.'
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

export type FinalResults = { activityId: string | null; status: 'ABSENT' | 'OPEN' | 'FINALIZING' | 'FINALIZED'; groups: { id: string; ordinal: number; name: string; capacity: number; managerAccountId: string | null; members: { accountId: string; studentId: string; studentName: string; aboutMe?: string }[] }[] }
export function getResults(id: string, role: 'TEACHER' | 'STUDENT'): Promise<FinalResults> {
  return readCourse(`/api/courses/${encodeURIComponent(id)}/results/${role === 'TEACHER' ? 'roster' : 'my-group'}`)
}
export async function getRosterCsv(id: string): Promise<{ blob: Blob; filename: string }> {
  const response = await fetch(`/api/courses/${encodeURIComponent(id)}/results/roster.csv`, { credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) throw new ApiError(response.status, 'CSV download is unavailable. Please retry.')
  const filename = response.headers.get('Content-Disposition')?.match(/filename="?(classroom-groups-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.csv)"?(?:;|$)/i)?.[1]
  if (!filename || !/^text\/csv(?:;|$)/i.test(response.headers.get('Content-Type') ?? '')) throw new Error('Invalid CSV response')
  // Fully buffer first: a broken response body must never create a partial download.
  const blob = await response.blob()
  if (!blob.size) throw new Error('Empty CSV response')
  return { blob, filename }
}

export type Announcement = { id: string; courseId: string; title: string; body: string; publishedAt: string; updatedAt: string; revision: number }
export type AnnouncementPage = { items: Announcement[]; nextCursor: string | null }
export type AnnouncementAcknowledgement = { id: string; revision: number; deleted: boolean }
export type AnnouncementCommand = { command: 'CREATE' | 'EDIT' | 'DELETE'; commandKey: string; id?: string; expectedRevision?: number; title?: string; body?: string }
export function listAnnouncements(courseId: string, cursor?: string): Promise<AnnouncementPage> {
  return readCourse(`/api/courses/${encodeURIComponent(courseId)}/announcements?limit=20${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`)
}
export function getAnnouncement(courseId: string, id: string): Promise<Announcement> {
  return readCourse(`/api/courses/${encodeURIComponent(courseId)}/announcements/${encodeURIComponent(id)}`)
}
function validAnnouncementAcknowledgement(value: unknown): value is AnnouncementAcknowledgement {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<AnnouncementAcknowledgement>
  return typeof candidate.id === 'string' && candidate.id.length > 0 && Number.isSafeInteger(candidate.revision) && candidate.revision! > 0 && typeof candidate.deleted === 'boolean'
}
export async function getAnnouncementCommand(courseId: string, attempt: AnnouncementCommand): Promise<{ committed: boolean; acknowledgement: AnnouncementAcknowledgement | null }> {
  const status = await readCourse<{ committed?: unknown; acknowledgement?: unknown }>(`/api/courses/${encodeURIComponent(courseId)}/announcement-commands/${encodeURIComponent(attempt.commandKey)}?command=${attempt.command}`)
  if (!status || typeof status.committed !== 'boolean' || (status.committed ? !validAnnouncementAcknowledgement(status.acknowledgement) : status.acknowledgement !== null)) throw new Error('The command receipt could not be confirmed.')
  return status as { committed: boolean; acknowledgement: AnnouncementAcknowledgement | null }
}
export async function sendAnnouncementCommand(courseId: string, attempt: AnnouncementCommand): Promise<AnnouncementAcknowledgement> {
  if (!token) await refreshCsrf()
  const csrf = token!
  token = undefined
  const { command, id, ...body } = attempt
  const response = await fetch(`/api/courses/${encodeURIComponent(courseId)}/announcements${id ? `/${encodeURIComponent(id)}` : ''}${command === 'DELETE' ? '/delete' : ''}`, {
    method: command === 'EDIT' ? 'PUT' : 'POST', credentials: 'same-origin', headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { code?: unknown } | null
    const code = typeof problem?.code === 'string' ? problem.code : undefined
    throw new ApiError(response.status, code === 'INVALID_ANNOUNCEMENT' ? 'Enter a nonblank title of at most 150 characters and body of at most 10,000 characters, using valid plain text.'
      : code === 'ANNOUNCEMENT_REVISION_CONFLICT' ? 'This announcement changed. Reload the current announcement and review your preserved draft.'
        : code === 'COMMAND_KEY_REUSED' ? 'This command key was already used for another request. Check its receipt before continuing.'
          : 'The announcement request could not be confirmed.', code)
  }
  const acknowledgement: unknown = await response.json()
  if (!validAnnouncementAcknowledgement(acknowledgement) || (id && acknowledgement.id !== id) || acknowledgement.deleted !== (command === 'DELETE')) throw new Error('The announcement acknowledgement could not be confirmed.')
  return acknowledgement as AnnouncementAcknowledgement
}
