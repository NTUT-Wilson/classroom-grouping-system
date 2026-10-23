import { useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { demoNow, initialCourses, initials, makeCourseState, makeDemoState, people, person, personas, phaseLabel } from './data'
import type { Attempt, Course, DemoState, Group, Phase } from './data'
import { act } from './mockActions'
import { universityDeadline } from '../universityTime'
import type { MockCommand } from './mockActions'
import './prototype.css'

function Icon({ name, size = 20 }: { name: string; size?: number }) {
  const paths: Record<string, ReactNode> = {
    grid: <><rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/></>,
    arrow: <><path d="M5 12h14M13 6l6 6-6 6"/></>,
    book: <><path d="M12 5v16M12 5C9 3 5 3 2 4v15c4-1 7 0 10 2 3-2 6-3 10-2V4c-3-1-7-1-10 1Z"/></>,
    users: <><circle cx="9" cy="7" r="3"/><path d="M2 21v-3a7 7 0 0 1 14 0v3M16 4a3 3 0 0 1 0 6M19 14a5 5 0 0 1 3 5v2"/></>,
    clock: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
    check: <path d="m5 12 4 4L19 6"/>,
    close: <path d="m6 6 12 12M18 6 6 18"/>,
    download: <><path d="M12 3v12m-4-4 4 4 4-4M4 16v5h16v-5"/></>,
    refresh: <><path d="M20 7v5h-5M4 17v-5h5M6 6a8 8 0 0 1 14 6M18 18a8 8 0 0 1-14-6"/></>,
    search: <><circle cx="10" cy="10" r="6"/><path d="m15 15 6 6"/></>,
    plus: <path d="M12 5v14M5 12h14"/>,
    logout: <><path d="M9 4H3v16h6M9 12h12m-4-4 4 4-4 4"/></>,
  }
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name] ?? paths.book}</svg>
}
function Badge({ children, tone = '' }: { children: ReactNode; tone?: string }) { return <span className={`badge ${tone}`}><span className="status-dot"/>{children}</span> }
function Avatar({ name, small = false }: { name: string; small?: boolean }) { return <span className={`avatar ${small ? 'small' : ''}`} aria-hidden="true">{initials(name)}</span> }
function Deadline({ value }: { value: string }) {
  const resolved = universityDeadline(value)
  const date = resolved ? new Intl.DateTimeFormat('en-GB', { timeZone: 'America/New_York', day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(resolved)) : 'Invalid or ambiguous deadline'
  return <span className="deadline"><Icon name="clock" size={16}/><span>{date}<small>America/New_York</small></span></span>
}
function Modal({ title, children, close }: { title: string; children: ReactNode; close: () => void }) {
  const panel = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const previous = document.activeElement as HTMLElement
    const oldOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    panel.current?.querySelector<HTMLElement>('button, input, textarea')?.focus()
    return () => { document.body.style.overflow = oldOverflow; previous?.focus() }
  }, [])
  return <div className="modal-overlay" onClick={close}><div className="modal" role="dialog" aria-modal="true" aria-labelledby="dialog-title" ref={panel} onClick={e => e.stopPropagation()} onKeyDown={e => {
    if (e.key === 'Escape') close()
    if (e.key !== 'Tab') return
    const elements = Array.from(panel.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input, textarea, select, a[href]') ?? [])
    const first = elements[0]; const last = elements.at(-1)
    if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last?.focus() }
    else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first?.focus() }
  }}><div className="modal-title"><h2 id="dialog-title">{title}</h2><button className="icon-button" aria-label="Close dialog" onClick={close}><Icon name="close"/></button></div>{children}</div></div>
}
function Blank({ icon = 'book', title, children }: { icon?: string; title: string; children: ReactNode }) { return <div className="blank-state"><span className="blank-icon"><Icon name={icon} size={30}/></span><h2>{title}</h2><p>{children}</p></div> }
type Dialog = { kind: 'command'; command: MockCommand; title: string; text: string } | { kind: 'profile'; id: string } | { kind: 'instructions' } | { kind: 'deadline' }
const chars = (value: string) => [...value].length
const stateTone = (phase: Phase) => phase === 'finalized' ? 'green' : phase === 'open' ? 'blue' : 'amber'

export function Prototype() {
  const [personaId, setPersonaId] = useState('alex')
  const [signedIn, setSignedIn] = useState(true)
  const [courses, setCourses] = useState(initialCourses)
  const [states, setStates] = useState<Record<string, DemoState>>(() => Object.fromEntries(initialCourses.map(c => [c.id, makeCourseState(c)])))
  const [route, setRoute] = useState(location.hash.slice(1) || 'courses')
  const [message, setMessage] = useState('')
  const [problem, setProblem] = useState('')
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const [query, setQuery] = useState('')
  const [requestKind, setRequestKind] = useState<'application' | 'invitation'>('application')
  const [historyLimit, setHistoryLimit] = useState(4)
  const [simulation, setSimulation] = useState('normal')
  const persona = personas.find(p => p.id === personaId)!
  const parts = route.split('/')
  const course = courses.find(c => c.id === parts[1])
  const tab = parts[2] || (persona.teacher ? 'activity' : 'groups')
  const state = course ? states[course.id]! : makeDemoState()
  const ownGroup = state.groups.find(g => g.members.includes(personaId))
  const managedGroup = state.groups.find(g => g.manager === personaId)
  const open = course?.phase === 'open'
  useEffect(() => {
    const onHash = () => { setRoute(location.hash.slice(1) || 'courses'); setDialog(null); setMessage(''); setProblem(''); setQuery(''); setHistoryLimit(4) }
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])
  function navigate(path: string) { location.hash = path; setRoute(path); setDialog(null); setQuery(''); setMessage(''); setProblem(''); setHistoryLimit(4) }
  function preview(id: string, phase?: Phase) {
    setPersonaId(id); setSignedIn(true); setDialog(null); setMessage(''); setProblem(''); setSimulation('normal')
    if (phase) {
      const nextCourses = initialCourses.map(c => c.id === 'interaction' ? { ...c, phase } : c)
      setCourses(nextCourses); setStates(Object.fromEntries(nextCourses.map(c => [c.id, makeCourseState(c)])))
    }
    navigate(course ? `course/${course.id}/${id === 'teacher' ? 'activity' : 'groups'}` : 'courses')
  }
  function update(next: DemoState) { if (course) setStates(current => ({ ...current, [course.id]: next })) }
  function perform(command: MockCommand) {
    setDialog(null); setMessage(''); setProblem('')
    if (!course) return
    if (simulation === 'uncertain') { setProblem('The response could not be confirmed. Refresh to check your current status before choosing to try again. No action has been replayed.'); return }
    let current = state
    if (simulation === 'conflict' && command.type === 'join') {
      current = { ...state, groups: state.groups.map(g => g.id === command.group ? { ...g, members: ['sofia'], manager: 'sofia' } : g) }
      update(current)
    }
    try {
      const next = act(current, personaId, course.phase, command)
      update(next)
      setMessage(command.type === 'join' || command.type === 'accept' ? 'Membership confirmed. Related pending requests have been updated.' : command.type === 'reject' ? 'Rejection recorded. Any separate application or invitation remains unchanged.' : command.type === 'apply' ? 'Application sent. A pending request does not reserve a place.' : 'Invitation sent. A pending invitation does not reserve a place.')
    } catch (cause) { setProblem((cause as Error).message) }
  }
  function confirm(command: MockCommand, title: string, text: string) { setDialog({ kind: 'command', command, title, text }) }
  function refresh() { setMessage('Current mock status checked. No action was resubmitted.'); setProblem('') }
  function download() {
    if (!course || !persona.teacher || course.phase !== 'finalized') return
    if (simulation === 'export-error') { setProblem('The roster download is temporarily unavailable. The finalized roster is unchanged. Try the download again later.'); return }
    const rows = state.groups.flatMap(g => [...g.members].sort((a, b) => person(a).studentId.localeCompare(person(b).studentId)).map(id => [`Group ${g.id}`, person(id).studentId, person(id).name]))
    const csv = '\uFEFF' + [['Group name', 'Student ID', 'Student name'], ...rows].map(row => row.map(value => `"${value.replaceAll('"', '""')}"`).join(',')).join('\r\n') + '\r\n'
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
    const link = document.createElement('a'); link.href = url; link.download = 'mock-final-roster.csv'; link.click(); URL.revokeObjectURL(url)
    setMessage('Mock CSV downloaded. When opening in Excel, import Student ID as text to preserve leading zeros.')
  }
  function members(group: Group) { return <div className="member-list">{group.members.map(id => <button className="member-row" key={id} onClick={() => setDialog({ kind: 'profile', id })}><Avatar name={person(id).name}/><span><strong>{person(id).name}</strong><small>{person(id).studentId}</small></span>{id === group.manager && <span className="label-tag">Manager</span>}{id === personaId && <span className="label-tag">You</span>}<Icon name="arrow" size={16}/></button>)}</div> }

  function groupsView() {
    if (!course) return null
    if (course.phase === 'none') return <Blank title="A little patience, a fresh start." icon="users">There is no grouping activity for this course yet. Your teacher will set the instructions and group capacities. You can still browse classmates and edit your About me.</Blank>
    if (!open) return resultsView()
    return <>
      <div className="student-overview"><div><span className="eyebrow">YOUR NEXT STEP</span><h2>{ownGroup ? `You belong to Group ${ownGroup.id}.` : 'Find your people.'}</h2><p>{ownGroup ? managedGroup ? 'Review applications and invite ungrouped classmates while grouping is open.' : 'Get to know your group. Only your manager can review applications and send invitations.' : 'Join an empty group or apply to an occupied group. Your choice starts a conversation.'}</p></div><span className="overview-icon"><Icon name="users" size={40}/></span></div>
      <div className="section-heading"><div><h2>Explore the groups <span className="muted-count">{state.groups.length}</span></h2><p>Places are confirmed only when membership is established.</p></div><button className="button secondary small-button" onClick={refresh}><Icon name="refresh" size={15}/>Refresh</button></div>
      <div className="group-grid">{state.groups.map(g => {
        const pending = state.attempts.some(a => a.student === personaId && a.group === g.id && a.kind === 'application' && a.status === 'Pending')
        return <article className={`group-card ${g.members.includes(personaId) ? 'your-group' : ''}`} key={g.id}>
          <div className="group-top"><span className="group-number">{String(g.id).padStart(2, '0')}</span><Badge tone={g.members.length === g.capacity ? '' : g.members.length === 0 ? 'amber' : 'green'}>{g.members.length === g.capacity ? 'Full' : g.members.length === 0 ? 'Empty group' : `${g.capacity - g.members.length} place${g.capacity - g.members.length === 1 ? '' : 's'} open`}</Badge></div>
          <h3>Group {g.id}{g.members.includes(personaId) && <span className="label-tag">Your group</span>}</h3>
          <p className="group-capacity">{g.members.length} of {g.capacity} members</p><div className="capacity-dots" aria-hidden="true">{Array.from({ length: g.capacity }, (_, i) => <span className={i < g.members.length ? 'filled' : ''} key={i}/>)}</div>
          {g.members.length ? members(g) : <div className="empty-group-copy"><Icon name="plus" size={24}/><p>A new team starts here.</p><small>The first student to join becomes the manager.</small></div>}
          {!ownGroup && <button className={`button ${g.members.length ? 'secondary' : ''}`} disabled={g.members.length === g.capacity || pending} onClick={() => g.members.length ? confirm({ type: 'apply', group: g.id }, `Apply to Group ${g.id}?`, 'The manager will decide your application. This does not reserve a place, and you cannot withdraw a pending application.') : confirm({ type: 'join', group: g.id }, `Join Group ${g.id}?`, 'You will become this group’s first member and manager. Joining cancels your other pending requests. Leaving or switching groups is not available in V1.')}>
            {g.members.length === g.capacity ? 'No places available' : pending ? 'Application pending' : g.members.length ? 'Apply to join' : 'Join this group'}{!pending && g.members.length < g.capacity && <Icon name="arrow" size={16}/>}</button>}
          {g.manager === personaId && <button className="button secondary" onClick={() => navigate(`course/${course.id}/requests`)}>Manage requests<Icon name="arrow" size={16}/></button>}
        </article>
      })}</div>
      <div className="quiet-note"><Icon name="clock"/><p>When the deadline arrives, grouping closes. The system assigns remaining students to available places and makes the complete results available.</p></div>
    </>
  }

  function classmatesView() {
    const filtered = people.filter(p => `${p.name} ${p.studentId}`.toLowerCase().includes(query.toLowerCase()))
    return <>
      <div className="profile-editor panel"><Avatar name={persona.name}/><div><span className="eyebrow">YOUR COURSE INTRODUCTION</span><h2>A few words about you</h2><p>Help classmates get to know you. This introduction belongs to this course.</p><form onSubmit={e => {
        e.preventDefault(); const value = String(new FormData(e.currentTarget).get('about'))
        if (chars(value) > 500) { setProblem('About me must be 500 characters or fewer.'); return }
        update({ ...state, about: { ...state.about, [personaId]: value } }); setMessage('Your About me has been saved for this course.'); setProblem('')
      }}><TextArea key={`${course?.id}-${personaId}`} name="about" label="About me" initial={state.about[personaId] || ''} limit={500}/><button className="button small-button" type="submit">Save introduction</button></form></div></div>
      <div className="section-heading"><div><h2>Your classmates <span className="muted-count">{people.length}</span></h2><p>{open ? 'Discover people, read introductions, and see current grouping status.' : 'Profiles remain available. Other students’ final assignments stay private.'}</p></div><label className="search"><Icon name="search" size={17}/><input aria-label="Search classmates" placeholder="Search name or student ID" value={query} onChange={e => setQuery(e.target.value)}/></label></div>
      <div className="people-grid">{filtered.map(p => {
        const group = state.groups.find(g => g.members.includes(p.id))
        const pending = managedGroup && state.attempts.some(a => a.kind === 'invitation' && a.group === managedGroup.id && a.student === p.id && a.status === 'Pending')
        return <article className="person-card" key={p.id}><div className="person-header"><Avatar name={p.name}/><div><h3>{p.name}{p.id === personaId && <span className="label-tag">You</span>}</h3><small>{p.studentId}</small></div></div><p className="person-about">{state.about[p.id] || 'No introduction yet.'}</p>{open && <Badge tone={group ? '' : 'green'}>{group ? `Group ${group.id}${group.manager === p.id ? ' · Manager' : ''}` : 'Ungrouped'}</Badge>}<div className="person-actions"><button className="text-button" onClick={() => setDialog({ kind: 'profile', id: p.id })}>View profile<Icon name="arrow" size={14}/></button>{open && managedGroup && !group && <button className="button secondary small-button" disabled={pending || managedGroup.members.length >= managedGroup.capacity} onClick={() => confirm({ type: 'invite', group: managedGroup.id, student: p.id }, `Invite ${p.name}?`, `Invite this classmate to Group ${managedGroup.id}. An invitation reserves no place and cannot be manually canceled.`)}>{pending ? 'Invitation pending' : 'Invite'}</button>}</div></article>
      })}</div>{!filtered.length && <Blank icon="search" title="No classmates found">Try another name or student ID.</Blank>}
    </>
  }

  function requestsView() {
    const visible = state.attempts.filter(a => (a.student === personaId || a.manager === personaId) && a.kind === requestKind).map(a => !open && a.status === 'Pending' ? { ...a, status: 'Expired' as const } : a)
    const pending = visible.filter(a => a.status === 'Pending')
    const completed = visible.filter(a => a.status !== 'Pending').slice().reverse()
    const row = (a: Attempt) => {
      const canRespond = open && a.status === 'Pending' && (a.kind === 'application' ? a.manager === personaId : a.student === personaId)
      const studentName = person(a.student).name
      return <div className="request-row" key={a.id}><Avatar name={a.student === personaId ? person(a.manager).name : studentName}/><div className="request-description"><strong>{a.student === personaId ? `Group ${a.group}` : studentName}</strong><span>{a.kind === 'application' ? a.student === personaId ? `Your application · Manager: ${person(a.manager).name}` : `Applied to Group ${a.group}` : a.student === personaId ? `Invitation from ${person(a.manager).name}` : `Your invitation to Group ${a.group}`}</span><small>Attempt #{a.id}{a.reason ? ` · ${a.reason}` : ''}</small></div><Badge tone={a.status === 'Accepted' ? 'green' : a.status === 'Pending' ? 'amber' : ''}>{a.status}</Badge>{canRespond && <div className="request-actions"><button className="button secondary small-button" onClick={() => confirm({ type: 'reject', attempt: a.id }, 'Reject this request?', 'Only this request will be rejected. A separate application or invitation remains unchanged.')}>Reject</button><button className="button small-button" onClick={() => confirm({ type: 'accept', attempt: a.id }, a.kind === 'application' ? `Approve ${studentName}?` : `Accept Group ${a.group} invitation?`, 'This establishes membership if a place is still available. Other pending requests will close. Membership cannot be undone in V1.')}>{a.kind === 'application' ? 'Approve' : 'Accept'}</button></div>}</div>
    }
    return <><div className="section-heading"><div><span className="eyebrow">PRIVATE TO THE TWO PARTIES</span><h2>Your requests</h2><p>Applications and invitations are independent. Neither reserves a place.</p></div><button className="button secondary small-button" onClick={refresh}><Icon name="refresh" size={15}/>Refresh</button></div><div className="segmented" aria-label="Request type"><button className={requestKind === 'application' ? 'active' : ''} onClick={() => { setRequestKind('application'); setHistoryLimit(4) }}>Applications</button><button className={requestKind === 'invitation' ? 'active' : ''} onClick={() => { setRequestKind('invitation'); setHistoryLimit(4) }}>Invitations</button></div><section className="panel requests-panel"><div className="panel-heading"><h3>Pending</h3><span className="muted-count">{pending.length}</span></div>{pending.length ? pending.map(row) : <div className="inline-empty">{open ? 'Nothing waiting for a response.' : 'Grouping is closed. No requests can be answered.'}</div>}</section><section className="panel requests-panel"><div className="panel-heading"><h3>Completed history</h3><span className="caption">Earlier attempts are kept</span></div>{completed.length ? completed.slice(0, historyLimit).map(row) : <div className="inline-empty">Completed attempts will appear here.</div>}{completed.length > historyLimit && <button className="button secondary small-button load-more" onClick={() => setHistoryLimit(n => n + 4)}>Load more history</button>}</section></>
  }

  function resultsView() {
    if (!course) return null
    if (course.phase === 'none') return <Blank title="No activity yet">A final result will be available after an activity has been created, its deadline has passed, and the system has completed grouping.</Blank>
    if (course.phase !== 'finalized') return <section className="panel results-wait"><Blank icon={course.phase === 'open' ? 'clock' : 'refresh'} title={course.phase === 'open' ? 'Results come after the deadline.' : course.phase === 'unavailable' ? 'Results are temporarily unavailable.' : 'Your results are being finalized.'}>{course.phase === 'open' ? 'The system will complete grouping after the cutoff. Return here to view the completed result.' : 'The complete result is not available yet. Refresh to check again; this will not restart grouping or change the allocation.'}</Blank><button className="button secondary" onClick={refresh}><Icon name="refresh" size={16}/>Refresh status</button></section>
    if (!persona.teacher && !ownGroup) return <Blank title="Your result is unavailable">Your membership could not be confirmed. This is not a completed result. Please seek help outside the app.</Blank>
    const groups = persona.teacher ? state.groups : [ownGroup!]
    return <><div className="section-heading"><div><span className="eyebrow">GROUPING COMPLETE</span><h2>{persona.teacher ? 'The final grouping roster' : `You’re in Group ${ownGroup!.id}.`}</h2><p>{persona.teacher ? 'Every participant has one group. The roster is complete and read-only.' : 'Meet your team. Only your own group’s final membership is visible to you.'}</p></div>{persona.teacher && <button className="button" onClick={download}><Icon name="download" size={17}/>Download CSV</button>}</div><div className={persona.teacher ? 'roster-grid' : 'own-result'}>{groups.map(g => <section className="panel final-group" key={g.id}><div className="panel-heading"><div className="final-group-title"><span className="group-number">{String(g.id).padStart(2, '0')}</span><h3>Group {g.id}</h3></div><span className="caption">{g.members.length} members</span></div>{persona.teacher ? <div className="roster-members">{[...g.members].sort((a, b) => person(a).studentId.localeCompare(person(b).studentId)).map(id => <div className="roster-row" key={id}><Avatar small name={person(id).name}/><strong>{person(id).name}</strong><span>{person(id).studentId}</span></div>)}</div> : members(g)}{!g.manager && <p className="footnote">This group was first populated by system assignment and has no manager.</p>}</section>)}</div><div className="quiet-note"><Icon name="check"/><p>{persona.teacher ? 'CSV columns: Group name, Student ID, Student name. Import Student ID as text in Excel to preserve leading zeros.' : 'Group membership is fixed. Your course About me and classmate profiles remain available.'}</p></div></>
  }

  function teacherView() {
    if (!course) return null
    if (course.phase === 'none') return <ActivityForm course={course} initial={state} onCreate={(next) => { update(next); setCourses(cs => cs.map(c => c.id === course.id ? { ...c, phase: 'open' } : c)); setMessage('Activity created. Grouping is now open to all nine course participants.'); setProblem('') }}/>
    return <div className="teacher-columns"><section className="panel instructions-panel"><div className="panel-heading"><h2>Activity instructions</h2><button className="text-button" onClick={() => setDialog({ kind: 'instructions' })}>Edit instructions<Icon name="arrow" size={15}/></button></div><p className="instructions-text">{state.instructions}</p><p className="footnote">Instruction text can be edited after the cutoff without changing grouping or results.</p></section><aside className="panel settings-panel"><span className="eyebrow">ACTIVITY SETTINGS</span><h3>One course. One activity.</h3><div className="setting-block"><span>Grouping deadline</span><Deadline value={state.deadline}/>{open && <button className="text-button" onClick={() => setDialog({ kind: 'deadline' })}>Extend deadline<Icon name="arrow" size={15}/></button>}</div><div className="setting-block"><span>Fixed capacity layout</span><div className="layout-pills">{state.groups.map(g => <span key={g.id}>Group {g.id}<strong>{g.capacity}</strong></span>)}</div><small>Capacities and participants are fixed after creation.</small></div><div className="setting-block"><span>Results</span><p>{course.phase === 'finalized' ? 'The completed roster is ready.' : 'Available only after the deadline and complete finalization.'}</p><button className="button secondary" onClick={() => navigate(`course/${course.id}/results`)}>View result status<Icon name="arrow" size={15}/></button></div></aside></div>
  }

  const tabs = persona.teacher ? [['activity', 'Activity'], ['results', 'Final roster']] : [['groups', open || course?.phase === 'none' ? 'Groups' : 'My result'], ['classmates', 'Classmates'], ['requests', 'Requests'], ['instructions', 'Instructions']]
  const allowed = tabs.some(([key]) => key === tab)
  return <div className="prototype">
    <div className="preview-bar"><span><span className="preview-dot"/>UI PROTOTYPE <span className="preview-description">Mock data only · changes stay in memory</span></span><div className="preview-controls"><label>Preview as<select aria-label="Preview as" value={personaId} onChange={e => preview(e.target.value)}>{personas.map(p => <option key={p.id} value={p.id}>{p.name} — {p.role}</option>)}</select></label><label>Activity state<select aria-label="Activity state" value={courses[0].phase} onChange={e => preview(personaId, e.target.value as Phase)}>{(['open', 'finalizing', 'finalized', 'unavailable'] as Phase[]).map(p => <option value={p} key={p}>{phaseLabel(p)}</option>)}</select></label><details className="preview-options"><summary>Scenarios</summary><div><label>Interaction simulation<select aria-label="Interaction simulation" value={simulation} onChange={e => { setSimulation(e.target.value); setMessage(''); setProblem('') }}><option value="normal">Normal</option><option value="conflict">Empty-group join conflict</option><option value="uncertain">Uncertain action response</option><option value="export-error">CSV download unavailable</option></select></label><p>Preview controls are not product features. Changing activity state resets mock data; changing persona preserves it. Demo clock: 20 Oct 2026, 10:00 America/New_York.</p><a href="/">Real sign-in shell ↗</a></div></details></div></div>
    {!signedIn ? <main className="demo-signin"><div className="brand"><span className="brand-symbol"><Icon name="users"/></span>Classroom Grouping</div><span className="eyebrow">WELCOME BACK</span><h1>A better way<br/>to come together.</h1><p>This preview uses a synthetic demo account. No credentials or backend connection are required.</p><form onSubmit={e => { e.preventDefault(); setSignedIn(true); navigate('courses') }}><label>Demo username<input value={personaId === 'teacher' ? 'evelyn.demo' : `${personaId}.demo`} readOnly/></label><label>Demo password<input type="password" value="prototype-only" readOnly/></label><button className="button">Enter demo workspace<Icon name="arrow" size={17}/></button></form></main> : <div className="app-layout">
      <aside className="sidebar"><a href="#courses" className="brand" onClick={e => { e.preventDefault(); navigate('courses') }}><span className="brand-symbol"><Icon name="users"/></span><span>Classroom<span className="brand-sub">Grouping</span></span></a><span className="sidebar-label">YOUR WORKSPACE</span><a href="#courses" className={`nav-item ${!course ? 'active' : ''}`}><Icon name="grid"/>My courses</a>{course && <><span className="sidebar-label">CURRENT COURSE</span><div className="sidebar-course"><span>{course.code}</span><strong>{course.name}</strong></div>{tabs.map(([key, label]) => <a className={`nav-item ${tab === key ? 'active' : ''}`} href={`#course/${course.id}/${key}`} key={key}><Icon name={key === 'classmates' ? 'users' : key === 'requests' ? 'grid' : key === 'results' ? 'check' : 'book'} size={18}/>{label}</a>)}</>}<div className="sidebar-bottom"><p>Good groups start<br/>with good connections.</p><div className="account"><Avatar name={persona.name}/><div><strong>{persona.name}</strong><span>{persona.teacher ? 'Teacher' : managedGroup && open ? 'Student · group manager' : 'Student'}</span></div></div><button className="text-button" onClick={() => { setSignedIn(false); setDialog(null) }}><Icon name="logout" size={16}/>Sign out of demo</button></div></aside>
      <main className="workspace"><header className="workspace-top"><div className="breadcrumb"><a href="#courses">My courses</a>{course && <><span>/</span><span>{course.name}</span></>}</div><span className="semester">FALL 2026 <span>·</span> COURSE WORKSPACE</span><button className="mobile-signout text-button" onClick={() => { setSignedIn(false); setDialog(null) }}>Sign out</button></header>
        {!course && route !== 'courses' ? <Blank title="This page is unavailable">Return to My courses to choose an accessible course.</Blank> : !course ? <><div className="page-heading home-heading"><div><span className="eyebrow">A LITTLE STRUCTURE. A LOT OF POSSIBILITY.</span><h1>{persona.teacher ? 'Bring your class together.' : `Make room for collaboration.`}</h1><p>{persona.teacher ? 'Set up grouping for your assigned courses, then return for the complete results.' : `Welcome, ${persona.name.split(' ')[0]}. Find classmates, form a group, and build something together.`}</p></div><div className="semester-card"><Icon name="book" size={24}/><span>YOUR SEMESTER<strong>Fall 2026</strong></span></div></div><div className="section-heading"><h2>My courses <span className="muted-count">{courses.length}</span></h2><span className="caption">{persona.teacher ? 'Assigned to you' : 'Enrolled courses'}</span></div><div className="course-grid">{courses.map(c => <a className="course-card" href={`#course/${c.id}/${persona.teacher ? 'activity' : 'groups'}`} key={c.id}><div className={`course-art ${c.tone}`}><span className="art-code">{c.code}</span><div className="art-shapes" aria-hidden="true"><i/><i/><i/><i/></div><span className="art-term">FALL / 2026</span></div><div className="course-card-body"><Badge tone={stateTone(c.phase)}>{phaseLabel(c.phase)}</Badge><h2>{c.name}</h2><p>{c.subtitle}</p><div className="course-card-footer"><span>{persona.teacher && c.phase === 'none' ? 'Set up an activity' : c.phase === 'finalized' ? (persona.teacher ? 'View final roster' : 'View your results') : 'Open course'}</span><span className="round-arrow"><Icon name="arrow" size={18}/></span></div></div></a>)}</div><section className="how-it-works panel"><div><span className="eyebrow">THE IDEA IS SIMPLE</span><h2>{persona.teacher ? 'You set the structure.<br/>Students make the connections.' : 'A place for everyone.'}</h2></div><div className="journey-step"><span>01</span><h3>{persona.teacher ? 'Set up' : 'Explore'}</h3><p>{persona.teacher ? 'Enter instructions, capacities, and a deadline.' : 'Read introductions and get to know the groups.'}</p></div><div className="journey-step"><span>02</span><h3>{persona.teacher ? 'Let students connect' : 'Connect'}</h3><p>{persona.teacher ? 'Students manage their own team formation.' : 'Join an empty group or apply to an occupied one.'}</p></div><div className="journey-step"><span>03</span><h3>{persona.teacher ? 'Get the complete roster' : 'Come together'}</h3><p>The system fills remaining places after the cutoff.</p></div></section></> : <>
          <div className="page-heading"><div><span className="eyebrow">{course.code} <span> / </span> FALL 2026</span><h1>{course.name}</h1><p>{course.subtitle}</p></div><div className="course-heading-state"><Badge tone={stateTone(course.phase)}>{phaseLabel(course.phase)}</Badge>{course.phase !== 'none' && <Deadline value={state.deadline}/>}</div></div>
          <nav className="course-tabs" aria-label="Course navigation">{tabs.map(([key, label]) => <a className={tab === key ? 'active' : ''} aria-current={tab === key ? 'page' : undefined} href={`#course/${course.id}/${key}`} key={key}>{label}</a>)}</nav>
          {message && <div className="feedback success" role="status"><Icon name="check" size={18}/><span>{message}</span></div>}{problem && <div className="feedback error" role="alert"><Icon name="clock" size={18}/><span>{problem}</span><button className="text-button" onClick={refresh}>Refresh status</button></div>}
          {!allowed ? <Blank title="This view is not available to your role">Use the course navigation to open a permitted view.</Blank> : persona.teacher ? tab === 'results' ? resultsView() : teacherView() : tab === 'groups' ? groupsView() : tab === 'classmates' ? classmatesView() : tab === 'requests' ? requestsView() : course.phase === 'none' ? <Blank title="No instructions yet">Your teacher has not created a grouping activity.</Blank> : <section className="panel instructions-panel"><span className="eyebrow">ACTIVITY INSTRUCTIONS</span><h2>A shared starting point</h2><p className="instructions-text">{state.instructions}</p><div className="quiet-note"><Icon name="clock"/><p>The latest saved instructions are shown here. Your teacher can update the text without changing your group.</p></div></section>}
        </>}
        <footer className="workspace-footer"><span>Classroom Grouping</span><span>Thoughtful teams. Shared possibilities.</span></footer>
      </main>
    </div>}
    {dialog && course && <Modal title={dialog.kind === 'profile' ? person(dialog.id).name : dialog.kind === 'instructions' ? 'Edit activity instructions' : dialog.kind === 'deadline' ? 'Extend grouping deadline' : dialog.title} close={() => setDialog(null)}>
      {dialog.kind === 'profile' ? <><div className="profile-detail"><Avatar name={person(dialog.id).name}/><div><span className="eyebrow">STUDENT ID</span><p>{person(dialog.id).studentId}</p></div></div><h3>About me</h3><p className="profile-about">{state.about[dialog.id] || 'No introduction yet.'}</p><p className="footnote">Course-specific introduction · {course.name}</p></> : dialog.kind === 'command' ? <><p className="modal-copy">{dialog.text}</p><div className="modal-actions"><button className="button secondary" onClick={() => setDialog(null)}>Go back</button><button className="button" onClick={() => perform(dialog.command)}>{dialog.command.type === 'join' ? 'Confirm joining' : dialog.command.type === 'apply' ? 'Send application' : dialog.command.type === 'invite' ? 'Send invitation' : dialog.command.type === 'reject' ? 'Confirm rejection' : 'Confirm membership'}</button></div></> : <EditSettings kind={dialog.kind} state={state} phase={course.phase} onSave={next => { update(next); setDialog(null); setMessage('Your changes have been saved.'); setProblem('') }}/>}</Modal>}
  </div>
}

function TextArea({ label, name, initial, limit }: { label: string; name: string; initial: string; limit: number }) {
  const [value, setValue] = useState(initial)
  return <label className="field">{label}<textarea aria-label={label} name={name} value={value} rows={name === 'about' ? 3 : 6} onChange={e => setValue(e.target.value)} aria-describedby={`${name}-counter`} aria-invalid={chars(value) > limit}/><span className={`field-hint ${chars(value) > limit ? 'invalid' : ''}`} id={`${name}-counter`}>Plain text <span>{chars(value).toLocaleString()} / {limit.toLocaleString()} characters</span></span></label>
}
function EditSettings({ kind, state, phase, onSave }: { kind: 'instructions' | 'deadline'; state: DemoState; phase: Phase; onSave: (next: DemoState) => void }) {
  const [error, setError] = useState('')
  return <form onSubmit={e => { e.preventDefault(); const data = new FormData(e.currentTarget); const value = String(data.get(kind)); if (kind === 'instructions') { if (!value.trim() || chars(value) > 5000) { setError('Enter nonblank instructions of at most 5,000 characters.'); return }; onSave({ ...state, instructions: value }) } else { if (phase !== 'open' || !universityDeadline(value) || value <= state.deadline) { setError('While grouping is open, the new deadline must be later than the current deadline.'); return }; onSave({ ...state, deadline: value }) } }}>
    {kind === 'instructions' ? <><p className="modal-copy">Only the instruction text will change. The capacity layout, deadline, and grouping state stay as they are.</p><TextArea label="Instructions" name="instructions" initial={state.instructions} limit={5000}/></> : <><p className="modal-copy">The deadline can only move later while grouping is open. It cannot reopen a closed activity.</p><label className="field">New deadline · America/New_York<input name="deadline" type="datetime-local" defaultValue={state.deadline} required/></label></>}{error && <p className="form-error" role="alert">{error}</p>}<div className="modal-actions"><button className="button">Save changes</button></div>
  </form>
}
function ActivityForm({ course, initial, onCreate }: { course: Course; initial: DemoState; onCreate: (state: DemoState) => void }) {
  const [capacities, setCapacities] = useState('3, 3, 3')
  const [error, setError] = useState('')
  const [review, setReview] = useState<DemoState | null>(null)
  const entries = capacities.split(',').map(s => s.trim()).filter(Boolean)
  const total = entries.reduce((sum, value) => sum + (Number.isFinite(Number(value)) ? Number(value) : 0), 0)
  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const data = new FormData(e.currentTarget); const instructions = String(data.get('instructions')); const deadline = String(data.get('deadline'))
    if (!instructions.trim() || chars(instructions) > 5000) { setError('Instructions must be nonblank plain text, up to 5,000 characters.'); return }
    if (!entries.length || capacities.split(',').some(s => !s.trim()) || entries.some(s => !/^\d+$/.test(s) || Number(s) < 2) || total !== people.length) { setError('Enter whole-number capacities of at least 2, totaling exactly 9 participants.'); return }
    if (!universityDeadline(deadline) || deadline <= demoNow) { setError('Choose a deadline later than 20 Oct 2026, 10:00 America/New_York, the prototype clock.'); return }
    setError(''); setReview({ ...initial, instructions, deadline, groups: entries.map((s, i) => ({ id: i + 1, capacity: Number(s), members: [] })), attempts: [] })
  }
  return <div className="teacher-columns"><form className="panel creation-form" onSubmit={submit}><span className="eyebrow">A NEW GROUPING ACTIVITY</span><h2>Give collaboration a starting point.</h2><p>Create the activity for all nine students enrolled in {course.name}.</p><TextArea name="instructions" label="Activity instructions" initial={initial.instructions} limit={5000}/><label className="field">Group capacities<input aria-label="Group capacities" name="capacities" value={capacities} onChange={e => setCapacities(e.target.value)} placeholder="e.g. 2, 2, 2, 3"/><span className="field-hint">One group per entry. Whole numbers, minimum 2.</span></label><div className={`capacity-validation ${total === people.length ? 'valid' : ''}`}><Icon name={total === people.length ? 'check' : 'users'} size={18}/><strong>{total} places / {people.length} participants</strong><span>{total === people.length ? 'The total matches' : 'Totals must match exactly'}</span></div><label className="field">Grouping deadline · America/New_York<input type="datetime-local" name="deadline" defaultValue={initial.deadline} required/></label>{error && <p className="form-error" role="alert">{error}</p>}<button className="button" type="submit">Review activity<Icon name="arrow" size={17}/></button><small className="footnote">Review is a confirmation step, not a saved draft.</small></form><aside className="setup-guide"><span className="guide-symbol"><Icon name="users" size={36}/></span><h2>Set the structure.<br/>Leave room for connection.</h2><p>Each enrolled student is included. You choose the group capacities; students choose how to connect.</p><div className="guide-rule"><strong>01 · Check the layout</strong><p>Capacities are fixed after creation. The system will not automatically adjust your list.</p></div><div className="guide-rule"><strong>02 · Choose a clear cutoff</strong><p>Grouping opens immediately after creation and closes at the deadline.</p></div><div className="guide-rule"><strong>03 · Return for the result</strong><p>The system completes the roster. There is no teacher assignment or finalization step.</p></div></aside>{review && <Modal title="Ready to open grouping?" close={() => setReview(null)}><p className="modal-copy">Creating this activity makes it immediately visible to all nine course participants. The capacity layout cannot be changed, and this activity cannot be deleted, replaced, or reset.</p><div className="review-summary"><strong>{course.name}</strong><span>{review.groups.length} groups · {people.length} participants</span><span>Capacities: {review.groups.map(g => g.capacity).join(', ')}</span><Deadline value={review.deadline}/></div><div className="modal-actions"><button className="button secondary" onClick={() => setReview(null)}>Back to form</button><button className="button" onClick={() => { onCreate(review); setReview(null) }}>Create activity</button></div></Modal>}</div>
}
