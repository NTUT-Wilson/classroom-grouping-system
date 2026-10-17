import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Results } from './Results'
import { Groups } from './Groups'
import { Requests } from './Requests'
import { Classmates } from './Classmates'
import { CourseActivity } from './Activity'
import { ApiError, getCourse, initializeSession, listCourses, signIn, signOut } from './api'
import type { Course, Session } from './api'

type Authentication = { state: 'checking' | 'unavailable' | 'anonymous' } | { state: 'authenticated'; session: Session }
type Workspace = { state: 'loading' } | { state: 'error'; message: string } | { state: 'list'; courses: Course[] } | { state: 'detail'; course: Course }
function readRoute() { return window.location.hash.slice(1) || '/courses' }
function Brand() { return <a className="brand" href="#/courses"><span className="brand-symbol" aria-hidden="true">▦</span><span>Classroom<span className="brand-sub">Grouping</span></span></a> }

export function App() {
  const [auth, setAuth] = useState<Authentication>({ state: 'checking' })
  const [notice, setNotice] = useState('')
  const [route, setRoute] = useState(readRoute)
  const routeRef = useRef(route)
  const [workspace, setWorkspace] = useState<Workspace>({ state: 'loading' })
  const generation = useRef(0)

  async function checkSession(message = '') {
    const request = ++generation.current
    setAuth({ state: 'checking' }); setWorkspace({ state: 'loading' })
    try {
      const session = await initializeSession()
      if (request !== generation.current) return
      setAuth(session ? { state: 'authenticated', session } : { state: 'anonymous' })
      setNotice(message)
    } catch {
      if (request !== generation.current) return
      setAuth({ state: 'unavailable' }); setNotice('Your session could not be checked. Please retry.')
    }
  }
  useEffect(() => {
    void checkSession()
    const navigate = () => {
      const next = readRoute()
      if (next === routeRef.current) return
      routeRef.current = next
      setWorkspace({ state: 'loading' }); setRoute(next)
    }
    window.addEventListener('hashchange', navigate)
    return () => { generation.current++; window.removeEventListener('hashchange', navigate) }
  }, [])
  useEffect(() => {
    if (auth.state !== 'authenticated') return
    let active = true
    setWorkspace({ state: 'loading' })
    const match = /^\/courses\/([0-9a-f-]{36})(?:\/(?:classmates|groups|requests|results))?$/i.exec(route)
    const request = route === '/courses' ? listCourses().then(courses => ({ state: 'list', courses }) as Workspace)
      : match ? getCourse(match[1]).then(course => ({ state: 'detail', course }) as Workspace)
      : Promise.resolve({ state: 'error', message: 'This course is unavailable or you do not have access.' } as Workspace)
    request.then(result => { if (active) setWorkspace(result) }).catch(cause => {
      if (!active) return
      if (cause instanceof ApiError && cause.status === 401) void checkSession('Your session expired. Please sign in again.')
      else setWorkspace({ state: 'error', message: cause instanceof Error ? cause.message : 'Unable to load courses. Please retry.' })
    })
    return () => { active = false }
  }, [auth, route])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    setNotice(''); setAuth({ state: 'checking' })
    try {
      await signIn(String(data.get('username')), String(data.get('password')))
      form.reset()
      await checkSession()
    } catch (cause) {
      await checkSession(cause instanceof Error ? cause.message : 'Sign in could not be confirmed.')
    }
  }
  async function logout() {
    setNotice(''); setAuth({ state: 'checking' }); setWorkspace({ state: 'loading' })
    try { await signOut(); await checkSession() }
    catch { await checkSession('Sign out could not be confirmed. Your current session is shown below.') }
  }
  if (auth.state !== 'authenticated') return <main className="sign-in-page">
    <section className="sign-in-intro"><Brand /><div><span className="eyebrow">YOUR COURSE WORKSPACE</span><h1>A place for your classroom.</h1><p>Sign in to access your courses.</p></div><p className="intro-footer">Classroom Grouping</p></section>
    <section className="sign-in-panel"><div className="sign-in-card">
      {auth.state === 'anonymous' ? <><span className="eyebrow">WELCOME BACK</span><h2>Sign in</h2><p>Use your provisioned account to continue.</p><form onSubmit={submit}><label>Username<input name="username" autoComplete="username" required /></label><label>Password<input name="password" type="password" autoComplete="current-password" required /></label><button className="primary-button">Sign in <span aria-hidden="true">→</span></button></form><p className="sign-in-help">Need help with your credentials? Contact your course administrator.</p></>
        : <><h2>{auth.state === 'checking' ? 'Checking your session…' : 'Session unavailable'}</h2>{auth.state === 'checking' ? <p role="status">Please wait while we check your access.</p> : <button className="primary-button" onClick={() => void checkSession()}>Retry session check</button>}</>}
      {notice && <p role="alert" className="notice">{notice}</p>}
    </div></section>
  </main>
  const session = auth.session
  return <div className="course-app"><a className="skip-link" href="#workspace" onClick={event => { event.preventDefault(); document.getElementById('workspace')?.focus() }}>Skip to content</a><aside className="workspace-sidebar"><Brand /><nav aria-label="Main navigation"><span className="eyebrow">WORKSPACE</span><a className="nav-item" aria-current={route === '/courses' ? 'page' : undefined} href="#/courses"><span aria-hidden="true">▦</span> My courses</a></nav><div className="sidebar-account"><span className="account-avatar" aria-hidden="true">{session.username.slice(0, 1).toUpperCase()}</span><div><strong>{session.username}</strong><span>{session.role === 'TEACHER' ? 'Teacher' : 'Student'}</span></div><button className="text-button" onClick={() => void logout()}>Sign out</button></div></aside>
    <main id="workspace" className="workspace" tabIndex={-1}><header className="workspace-header"><nav aria-label="Breadcrumb"><a href="#/courses">My courses</a>{route !== '/courses' && <><span aria-hidden="true"> / </span><span>Course</span></>}</nav><button className="secondary-button" onClick={() => void checkSession()}>Refresh</button></header>
      {notice && <p role="alert" className="notice">{notice}</p>}
      {workspace.state === 'loading' ? <section className="state-panel" role="status"><h1>Loading courses…</h1><p>Retrieving your course workspace.</p></section>
        : workspace.state === 'error' ? <section className="state-panel"><h1>Course unavailable</h1><p role="alert">{workspace.message}</p><button className="primary-button" onClick={() => void checkSession()}>Retry</button><a className="back-link" href="#/courses">Back to my courses</a></section>
        : workspace.state === 'list' ? <><section className="page-heading"><span className="eyebrow">YOUR WORKSPACE</span><h1>My courses</h1><p>{session.role === 'TEACHER' ? 'Your assigned courses, in one place.' : 'Your enrolled courses, in one place.'}</p></section>{workspace.courses.length === 0 ? <section className="state-panel"><h2>No courses yet</h2><p>{session.role === 'TEACHER' ? 'You do not have any assigned courses.' : 'You do not have any enrolled courses.'}</p></section> : <div className="course-grid">{workspace.courses.map((course, index) => <a className="course-card" key={course.id} href={`#/courses/${course.id}`}><div className={`course-art tone-${index % 3}`} aria-hidden="true"><span className="art-shapes"><i /><i /><i /><i /></span></div><div className="course-card-content"><span className="eyebrow">{course.code}</span><h2>{course.name}</h2><span className="open-course">Open course <span aria-hidden="true">→</span></span></div></a>)}</div>}</>
        : <><section className="page-heading"><span className="eyebrow">{workspace.course.code}</span><h1>{workspace.course.name}</h1><p>Course workspace</p></section>{session.role === 'STUDENT' && <nav className="course-tabs" aria-label="Course navigation"><a href={`#/courses/${workspace.course.id}`} aria-current={!route.endsWith('/classmates') && !route.endsWith('/groups') && !route.endsWith('/requests') ? 'page' : undefined}>Overview</a><a href={`#/courses/${workspace.course.id}/classmates`} aria-current={route.endsWith('/classmates') ? 'page' : undefined}>Classmates</a><a href={`#/courses/${workspace.course.id}/groups`} aria-current={route.endsWith('/groups') ? 'page' : undefined}>Groups</a><a href={`#/courses/${workspace.course.id}/requests`} aria-current={route.endsWith('/requests') ? 'page' : undefined}>Requests</a></nav>}{session.role === 'TEACHER' && <nav className="course-tabs" aria-label="Course navigation"><a href={`#/courses/${workspace.course.id}`} aria-current={!route.endsWith('/results') ? 'page' : undefined}>Activity</a><a href={`#/courses/${workspace.course.id}/results`} aria-current={route.endsWith('/results') ? 'page' : undefined}>Final roster</a></nav>}{route.endsWith('/results') ? session.role === 'TEACHER' ? <Results key={workspace.course.id} course={workspace.course} role={session.role} expired={() => void checkSession('Your session expired. Please sign in again.')} /> : <section className="state-panel"><h2>Results unavailable</h2><p role="alert">This course is unavailable or you do not have access.</p></section> : route.endsWith('/requests') ? session.role === 'STUDENT' ? <Requests key={workspace.course.id} course={workspace.course} accountId={session.accountId} expired={() => void checkSession('Your session expired. Please sign in again.')} /> : <section className="state-panel"><h2>Requests unavailable</h2><p role="alert">This course is unavailable or you do not have access.</p></section> : route.endsWith('/groups') ? session.role === 'STUDENT' ? <Groups key={workspace.course.id} course={workspace.course} accountId={session.accountId} expired={() => void checkSession('Your session expired. Please sign in again.')} /> : <section className="state-panel"><h2>Groups unavailable</h2><p role="alert">This course is unavailable or you do not have access.</p></section> : route.endsWith('/classmates') ? session.role === 'STUDENT' ? <Classmates key={workspace.course.id} course={workspace.course} accountId={session.accountId} expired={() => void checkSession('Your session expired. Please sign in again.')} /> : <section className="state-panel"><h2>Classmates unavailable</h2><p role="alert">This course is unavailable or you do not have access.</p></section> : <><section className="course-overview"><h2>Course details</h2><dl><div><dt>Course code</dt><dd>{workspace.course.code}</dd></div><div><dt>Course name</dt><dd>{workspace.course.name}</dd></div></dl></section><CourseActivity key={workspace.course.id} course={workspace.course} role={session.role} expired={() => void checkSession('Your session expired. Please sign in again.')} /></>}</>}
    </main></div>
}
