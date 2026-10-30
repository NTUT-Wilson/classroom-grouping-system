import { useEffect, useRef, useState } from 'react'
import { ApiError, decideInvitation, getApplications, getGrouping, getInvitations, inviteClassmate, listClassmates } from './api'
import type { Classmate, Course, GroupDiscovery, InvitationAttempt, InvitationPage } from './api'

type Selection = { kind: 'send'; recipient: Classmate; groupId: string; groupName: string } | { kind: 'ACCEPT' | 'REJECT'; attempt: InvitationAttempt }
function Confirmation({ selection, close, confirm, trigger }: { selection: Selection; close: () => void; confirm: () => void; trigger: HTMLElement | null }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { dialog.current?.showModal(); return () => { trigger?.focus() } }, [])
  const send = selection.kind === 'send'
  return <dialog ref={dialog} className="profile-dialog" aria-labelledby="invitation-title" onCancel={close} onClose={close}><h2 id="invitation-title">{send ? `Invite ${selection.recipient.studentName}?` : selection.kind === 'ACCEPT' ? 'Accept invitation?' : 'Reject invitation?'}</h2><p className="join-explanation">{send ? selection.groupName : selection.attempt.groupName}</p><p className="join-explanation">{send ? 'Sending records an invitation without joining or reserving a place. You cannot manually cancel it. The recipient decides whether to accept.' : selection.kind === 'ACCEPT' ? 'Acceptance joins this group only if current eligibility and capacity permit it. Your other pending applications and received invitations will be canceled. You cannot leave or switch groups.' : 'Rejection closes this invitation only. Any pending application remains unchanged. The manager may invite you again.'} All grouping actions stop at the cutoff.</p><div className="editor-actions"><button autoFocus className="secondary-button" onClick={close}>Cancel</button><button className="primary-button" onClick={confirm}>Confirm {send ? 'invitation' : selection.kind === 'ACCEPT' ? 'acceptance' : 'rejection'}</button></div></dialog>
}
function Person({ title, person }: { title: string; person: Classmate }) {
  return <div className="request-party"><h4>{title} · {person.studentName}</h4><p>Student ID · {person.studentId}</p><p className="profile-full-text">{person.aboutMe || 'No introduction yet.'}</p></div>
}
export function Invitations({ course, accountId, expired }: { course: Course; accountId: string; expired: () => void }) {
  const [page, setPage] = useState<InvitationPage | null>(null)
  const [groups, setGroups] = useState<GroupDiscovery | null>(null)
  const [peers, setPeers] = useState<Classmate[]>([])
  const [loading, setLoading] = useState(true)
  const [problem, setProblem] = useState('')
  const [message, setMessage] = useState('')
  const [selection, setSelection] = useState<Selection | null>(null)
  const [saving, setSaving] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const generation = useRef(0)
  const operation = useRef(0)
  const busy = useRef(false)
  const trigger = useRef<HTMLElement | null>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  function failure(cause: unknown) {
    setPage(null); setGroups(null); setPeers([]); setSelection(null)
    setProblem(cause instanceof ApiError && [401, 403, 404].includes(cause.status) ? 'Requests unavailable or you do not have access. Refresh requests to check your access.' : 'Invitations, applications and groups could not all be checked. Refresh requests before continuing.')
    if (cause instanceof ApiError && cause.status === 401) expired()
  }
  async function load(cursor?: string, preserveProblem = false) {
    if (busy.current) return
    busy.current = true; const request = ++generation.current
    setLoading(true); setSelection(null)
    if (!preserveProblem) setProblem('')
    try {
      const [history, discovery, applications, classmates] = await Promise.all([getInvitations(course.id, cursor), getGrouping(course.id), getApplications(course.id), listClassmates(course.id)])
      if (request !== generation.current) return
      const visible = { ...history, status: discovery.status === 'FINALIZED' || applications.status === 'FINALIZED' ? 'FINALIZED' as const : history.status }
      // Replace private pages so a refresh cannot retain former-manager history.
      setPage(visible)
      setGroups({ ...discovery, status: discovery.status === 'FINALIZED' || history.status === 'FINALIZED' || applications.status === 'FINALIZED' ? 'FINALIZED' : history.status === 'FINALIZING' || applications.status === 'FINALIZING' ? 'FINALIZING' : discovery.status }); setPeers(classmates); setUncertain(false)
    } catch (cause) { if (request === generation.current) failure(cause) }
    finally { if (request === generation.current) { busy.current = false; setLoading(false) } }
  }
  useEffect(() => { busy.current = false; setPage(null); setGroups(null); setPeers([]); setSaving(false); setUncertain(false); setMessage(''); setProblem(''); void load(); return () => { generation.current++; operation.current++ } }, [course.id])
  const own = groups?.status === 'OPEN' ? groups.groups.find(group => group.id === groups.ownGroupId && group.manager?.accountId === accountId) : undefined
  const open = page?.status === 'OPEN' && groups?.status === 'OPEN' && !uncertain
  async function confirm() {
    if (!selection || busy.current || !open) return
    if (selection.kind === 'send' ? !own || own.id !== selection.groupId || own.availablePlaces <= 0 : selection.attempt.recipient.accountId !== accountId || selection.attempt.status !== 'PENDING' || !!groups?.ownGroupId) return
    busy.current = true; setSaving(true); const selected = selection; setSelection(null); setProblem(''); setMessage('')
    const request = ++generation.current
    const attempt = ++operation.current
    try {
      const current = selected.kind === 'send'
        ? await inviteClassmate(course.id, selected.groupId, { commandKey: crypto.randomUUID(), recipientId: selected.recipient.accountId })
        : await decideInvitation(course.id, selected.attempt.id, { commandKey: crypto.randomUUID(), decision: selected.kind })
      if (request !== generation.current) return
      // Read both private histories: accepting can cancel applications, but rejecting must not change them.
      const [discovery, applications, history] = await Promise.all([getGrouping(course.id), getApplications(course.id), getInvitations(course.id)])
      if (request !== generation.current) return
      const statuses = [current.status, discovery.status, applications.status, history.status]
      const closed = statuses.some(status => status !== 'OPEN')
      const finalized = statuses.includes('FINALIZED')
      setPage({ ...history, status: finalized ? 'FINALIZED' : closed ? 'FINALIZING' : history.status }); setGroups({ ...discovery, status: finalized ? 'FINALIZED' : closed ? 'FINALIZING' : discovery.status }); setUncertain(false)
      setMessage(!closed ? selected.kind === 'send' ? 'Invitation recorded. It reserves no place. Current outcomes are shown below.' : 'Invitation response confirmed. Current outcomes are shown below.' : 'Grouping is closed. Current request history is shown below.')
    } catch (cause) {
      if (request !== generation.current) return
      setUncertain(true); failure(cause)
      setProblem('Invitation action could not be confirmed. Refresh requests to check both private histories and groups before choosing another action.')
      if (cause instanceof ApiError && cause.status === 409) { busy.current = false; await load(undefined, true) }
    } finally { if (attempt === operation.current) { busy.current = false; setSaving(false); heading.current?.focus() } }
  }
  const groupedIds = new Set(groups?.groups.flatMap(group => group.members.map(member => member.accountId)) ?? [])
  function choose(value: Selection, element: HTMLElement) { trigger.current = element; setSelection(value) }
  return <section aria-label="Invitations"><div className="activity-toolbar"><div><span className="eyebrow">PRIVATE REQUEST HISTORY</span><h2 tabIndex={-1} ref={heading}>Requests</h2></div><button className="secondary-button" disabled={loading || saving} onClick={() => void load()}>Refresh requests</button></div><p className="request-intro">Private to the current group manager and recipient. Original senders are preserved. Pending invitations reserve no places. Applications and invitations may coexist; rejection changes only the answered request.</p>{problem && <p role="alert" className="notice">{problem}</p>}{message && <p role="status" className="success-notice">{message}</p>}{saving ? <p role="status">Confirming invitation action…</p> : loading ? <p role="status">Loading invitations…</p> : page && <>{page.status === 'ABSENT' ? <section className="state-panel"><h3>No grouping activity yet</h3></section> : <>{!open && <section className="group-status"><h3>{page.status === 'FINALIZED' ? 'Grouping is complete' : 'Grouping is finalizing'}</h3><p>Grouping is closed. Requests cannot be answered. <a href={`#/courses/${course.id}/groups`}>View my group status</a>. Course profiles remain available in Classmates.</p></section>}{open && own && own.availablePlaces > 0 && <section className="invitation-candidates"><h3>Invite classmates to {own.name}</h3><p>Choose an ungrouped classmate. Current eligibility is checked when sending and accepting.</p><div className="people-grid">{peers.filter(peer => !groupedIds.has(peer.accountId)).map(peer => {
    const pending = page.items.some(item => item.groupId === own.id && item.recipient.accountId === peer.accountId && item.status === 'PENDING')
    return <article className="person-card" key={peer.accountId}><h4>{peer.studentName}</h4><p>Student ID · {peer.studentId}</p><p className="person-about">{peer.aboutMe || 'No introduction yet.'}</p>{pending ? <p>Invitation pending</p> : <button className="secondary-button" onClick={event => choose({ kind: 'send', recipient: peer, groupId: own.id, groupName: own.name }, event.currentTarget)}>Invite {peer.studentName}</button>}</article>
  })}</div></section>}<h3>Invitations</h3><div className="requests-list">{page.items.map(attempt => <article className="request-card" key={attempt.id} aria-label={`Invitation to ${attempt.recipient.studentName} for ${attempt.groupName}`}><div className="group-card-heading"><h3>{attempt.groupName}</h3><span className="group-badge">{attempt.status === 'PENDING' && !open ? 'EXPIRED' : attempt.status}</span></div><p className="request-date">Invited <time dateTime={attempt.createdAt}>{new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/New_York' }).format(new Date(attempt.createdAt))}</time> · America/New_York</p><div className="request-parties"><Person title="Original sender" person={attempt.sender} /><Person title="Recipient" person={attempt.recipient} /></div>{attempt.reason && <p className="profile-full-text">Reason · {attempt.reason}</p>}{open && !groups?.ownGroupId && attempt.status === 'PENDING' && attempt.recipient.accountId === accountId && <div className="editor-actions"><button className="primary-button" onClick={event => choose({ kind: 'ACCEPT', attempt }, event.currentTarget)}>Accept invitation</button><button className="secondary-button" onClick={event => choose({ kind: 'REJECT', attempt }, event.currentTarget)}>Reject invitation</button></div>}</article>)}</div>{!page.items.length && <section className="state-panel"><h3>No invitations yet</h3><p>Your private pending and completed invitations will appear here.</p></section>}{page.nextCursor && <button className="secondary-button load-older" onClick={() => void load(page.nextCursor!)}>Load older</button>}</>}</>}{selection && <Confirmation selection={selection} trigger={trigger.current} close={() => setSelection(null)} confirm={() => void confirm()} />}</section>
}
