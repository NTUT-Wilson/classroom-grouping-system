import { useEffect, useRef, useState } from 'react'
import { ApiError, decideApplication, getApplications, getGrouping } from './api'
import type { ApplicationAttempt, ApplicationPage, Classmate, Course, GroupDiscovery } from './api'

function Party({ title, person }: { title: string; person: Classmate }) {
  return <div className="request-party"><h4>{title} · {person.studentName}</h4><p>Student ID · {person.studentId}</p><p className="profile-full-text">{person.aboutMe || 'No introduction yet.'}</p></div>
}
function DecisionDialog({ selection, close, confirm, trigger }: { selection: { attempt: ApplicationAttempt; decision: 'APPROVE' | 'REJECT' }; close: () => void; confirm: () => void; trigger: HTMLElement | null }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { dialog.current?.showModal(); return () => { trigger?.focus() } }, [])
  const approve = selection.decision === 'APPROVE'
  return <dialog ref={dialog} className="profile-dialog" aria-labelledby="decision-title" onCancel={close} onClose={close}><h2 id="decision-title">{approve ? 'Approve' : 'Reject'} application?</h2><p className="join-explanation">{selection.attempt.applicant.studentName} → {selection.attempt.groupName}</p><p className="join-explanation">{approve ? 'Approval adds this applicant only if current eligibility and capacity still permit joining. Pending applications reserve no places.' : 'Rejection closes this application only. The student may apply again if eligible.'} All responses stop at the grouping cutoff.</p><div className="editor-actions"><button autoFocus className="secondary-button" onClick={close}>Cancel</button><button className="primary-button" onClick={confirm}>Confirm {approve ? 'approval' : 'rejection'}</button></div></dialog>
}
export function Requests({ course, accountId, expired }: { course: Course; accountId: string; expired: () => void }) {
  const [page, setPage] = useState<ApplicationPage | null>(null)
  const [discovery, setDiscovery] = useState<GroupDiscovery | null>(null)
  const [loading, setLoading] = useState(true)
  const [problem, setProblem] = useState('')
  const [message, setMessage] = useState('')
  const [selection, setSelection] = useState<{ attempt: ApplicationAttempt; decision: 'APPROVE' | 'REJECT' } | null>(null)
  const [saving, setSaving] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const generation = useRef(0)
  const operation = useRef(0)
  const busy = useRef(false)
  const trigger = useRef<HTMLElement | null>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  function failure(cause: unknown) {
    setPage(null); setDiscovery(null); setSelection(null)
    setProblem(cause instanceof ApiError && [401, 403, 404].includes(cause.status) ? 'Requests unavailable or you do not have access. Refresh requests to check your access.' : 'Requests and groups could not both be checked. Refresh requests before continuing.')
    if (cause instanceof ApiError && cause.status === 401) expired()
  }
  async function load(cursor?: string, preserveProblem = false) {
    if (busy.current) return
    busy.current = true
    const request = ++generation.current
    setLoading(true); setSelection(null)
    if (!preserveProblem) setProblem('')
    try {
      const [current, groups] = await Promise.all([getApplications(course.id, cursor), getGrouping(course.id)])
      if (request !== generation.current) return
      setPage(previous => cursor && previous?.activityId === current.activityId ? { ...current, items: [...previous.items.filter(item => !current.items.some(next => next.id === item.id)), ...current.items] } : current)
      setDiscovery(groups); setUncertain(false)
    } catch (cause) { if (request === generation.current) failure(cause) }
    finally { if (request === generation.current) { busy.current = false; setLoading(false) } }
  }
  useEffect(() => { busy.current = false; setPage(null); setDiscovery(null); setMessage(''); setProblem(''); setUncertain(false); setSaving(false); void load(); return () => { generation.current++; operation.current++ } }, [course.id])
  async function confirm() {
    if (!selection || busy.current || uncertain || page?.status !== 'OPEN' || discovery?.status !== 'OPEN' || selection.attempt.manager.accountId !== accountId) return
    busy.current = true; setSaving(true); const selected = selection; setSelection(null); setMessage(''); setProblem('')
    const request = ++generation.current
    const attempt = ++operation.current
    try {
      const current = await decideApplication(course.id, selected.attempt.id, { commandKey: crypto.randomUUID(), decision: selected.decision })
      if (request !== generation.current) return
      setPage(current); setDiscovery(null)
      // Refresh eligibility after every response; the mutation may have crossed the cutoff.
      const groups = await getGrouping(course.id)
      if (request !== generation.current) return
      setDiscovery(groups); setMessage(current.status === 'OPEN' && groups.status === 'OPEN' ? 'Application response confirmed. Current outcomes are shown below.' : 'Grouping is closed. Current request history is shown below.'); setUncertain(false)
    } catch (cause) {
      if (request !== generation.current) return
      setUncertain(true); failure(cause)
      setProblem('Application action could not be confirmed. Refresh requests to check current history and groups before choosing another action.')
      if (cause instanceof ApiError && cause.status === 409) { busy.current = false; await load(undefined, true) }
    } finally { if (attempt === operation.current) { busy.current = false; setSaving(false); heading.current?.focus() } }
  }
  const open = page?.status === 'OPEN' && discovery?.status === 'OPEN' && !uncertain
  return <section className="requests-workspace" aria-label="Requests"><div className="activity-toolbar"><div><span className="eyebrow">PRIVATE TO THE TWO PARTIES</span><h2 ref={heading} tabIndex={-1}>Requests</h2></div><button className="secondary-button" disabled={loading || saving} onClick={() => void load()}>Refresh requests</button></div><p className="request-intro">Your applications and applications to your group. Pending applications do not join a group or reserve a place. Earlier attempts are kept.</p>{problem && <p role="alert" className="notice">{problem}</p>}{message && <p role="status" className="success-notice">{message}</p>}{saving ? <p role="status">Confirming application response…</p> : loading ? <p role="status">Loading requests…</p> : page && <>{page.status === 'ABSENT' ? <section className="state-panel"><h3>No grouping activity yet</h3></section> : <>{!open && <section className="group-status"><h3>Grouping is finalizing</h3><p>Grouping is closed. Requests cannot be answered. Final group details are temporarily unavailable. Course profiles remain available in Classmates.</p></section>}<h3>Applications</h3>{page.items.length ? <div className="requests-list">{page.items.map(attempt => <article className="request-card" key={attempt.id} aria-label={`Application to ${attempt.groupName}`}><div className="group-card-heading"><h3>{attempt.groupName}</h3><span className="group-badge">{attempt.status === 'PENDING' && !open ? 'EXPIRED' : attempt.status}</span></div><p className="request-date">Applied <time dateTime={attempt.createdAt}>{new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Taipei' }).format(new Date(attempt.createdAt))}</time> · Asia/Taipei</p><div className="request-parties"><Party title="Applicant" person={attempt.applicant} /><Party title="Manager" person={attempt.manager} /></div>{attempt.reason && <p className="profile-full-text">Reason · {attempt.reason}</p>}{open && attempt.status === 'PENDING' && attempt.manager.accountId === accountId && discovery?.ownGroupId === attempt.groupId && <div className="editor-actions"><button className="primary-button" onClick={event => { trigger.current = event.currentTarget; setSelection({ attempt, decision: 'APPROVE' }) }}>Approve</button><button className="secondary-button" onClick={event => { trigger.current = event.currentTarget; setSelection({ attempt, decision: 'REJECT' }) }}>Reject</button></div>}</article>)}</div> : <section className="state-panel"><h3>No applications yet</h3><p>Your private pending and completed applications will appear here.</p></section>}{page.nextCursor && <button className="secondary-button load-older" onClick={() => void load(page.nextCursor!)}>Load older</button>}</>}</>}{selection && <DecisionDialog selection={selection} trigger={trigger.current} close={() => setSelection(null)} confirm={() => void confirm()} />}</section>
}
