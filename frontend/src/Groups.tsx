import { Results } from './Results'
import { useEffect, useRef, useState } from 'react'
import { ApiError, applyToGroup, getApplications, getGrouping, getInvitations, joinEmptyGroup, leaveGroup, transferManager } from './api'
import type { Classmate, Course, DiscoveryGroup, GroupDiscovery } from './api'

function JoinDialog({ group, close, confirm, trigger, application }: { group: DiscoveryGroup; close: () => void; confirm: () => void; trigger: HTMLElement | null; application: boolean }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { dialog.current?.showModal(); return () => { trigger?.focus() } }, [])
  return <dialog ref={dialog} className="profile-dialog" aria-labelledby="join-title" onCancel={close} onClose={close}>
    <h2 id="join-title">{application ? 'Apply to' : 'Join'} {group.name}?</h2><p className="join-explanation">{application ? 'Your application requires the group manager’s approval. Applying does not join the group or reserve a place. Current eligibility and capacity will be checked again at approval. You cannot withdraw a pending application.' : "You will become this group's initial manager and a member. You may leave before cutoff; returning uses normal joining rules. Confirm only if you want to join this empty group."}</p>
    <div className="editor-actions"><button className="secondary-button" autoFocus onClick={close}>Cancel</button><button className="primary-button" onClick={confirm}>{application ? 'Confirm application' : 'Confirm join'}</button></div>
  </dialog>
}
function Member({ profile, manager }: { profile: Classmate; manager: boolean }) {
  return <li className="group-member"><div><strong>{profile.studentName}</strong>{manager && <span className="you-tag">Manager</span>}<p>Student ID · {profile.studentId}</p><p className="profile-full-text">{profile.aboutMe || 'No introduction yet.'}</p></div></li>
}
type LifecycleSelection = { group: DiscoveryGroup; kind: 'leave' | 'transfer'; target?: Classmate }
function LifecycleDialog({ selection, close, confirm, trigger }: { selection: LifecycleSelection; close: () => void; confirm: () => void; trigger: HTMLElement | null }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { dialog.current?.showModal(); return () => { trigger?.focus() } }, [])
  const leaving = selection.kind === 'leave'
  return <dialog ref={dialog} className="profile-dialog" aria-labelledby="lifecycle-title" onCancel={close} onClose={close}>
    <h2 id="lifecycle-title">{leaving ? `Leave ${selection.group.name}?` : 'Transfer manager role?'}</h2>
    <p className="join-explanation">{leaving ? 'You will become ungrouped. Returning uses normal joining rules, with no reserved place or guaranteed destination. If you are the manager, one remaining member becomes manager at random. If nobody remains, pending group requests are canceled with reason Group empty.' : `${selection.target?.studentName} (Student ID ${selection.target?.studentId}) will become the manager immediately, without acceptance. You remain a member and lose management access to group request history. Pending requests and original actors are preserved.`}</p>
    <p>Current membership, authority and cutoff are checked when saving.</p>
    <div className="editor-actions"><button autoFocus className="secondary-button" onClick={close}>Cancel</button><button className="primary-button" onClick={confirm}>{leaving ? 'Confirm departure' : 'Confirm transfer'}</button></div>
  </dialog>
}
export function Groups({ course, accountId, expired }: { course: Course; accountId: string; expired: () => void }) {
  const [data, setData] = useState<GroupDiscovery | null>(null)
  const [state, setState] = useState<'loading' | 'ready' | 'error' | 'denied'>('loading')
  const [problem, setProblem] = useState('')
  const [message, setMessage] = useState('')
  const [saving, setSaving] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [selected, setSelected] = useState<DiscoveryGroup | null>(null)
  const [application, setApplication] = useState(false)
  const [lifecycle, setLifecycle] = useState<LifecycleSelection | null>(null)
  const [pendingGroups, setPendingGroups] = useState<string[]>([])
  const generation = useRef(0)
  const operation = useRef(0)
  const busy = useRef(false)
  const trigger = useRef<HTMLElement | null>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  function deny(cause: unknown) {
    if (cause instanceof ApiError && [401, 403, 404].includes(cause.status)) {
      setData(null); setSelected(null); setLifecycle(null); setState('denied'); setUncertain(false)
      setProblem('This course is unavailable or you do not have access.')
      if (cause.status === 401) expired()
      return true
    }
    return false
  }
  async function load(preserveProblem = false) {
    const request = ++generation.current
    setState('loading'); setData(null); setSelected(null); setLifecycle(null)
    if (!preserveProblem) setProblem('')
    try {
      const current = await getGrouping(course.id)
      if (request !== generation.current) return
      if (current.status !== 'OPEN') { setPendingGroups([]); setData(current); setState('ready'); setUncertain(false); return }
      const [history, invitations] = await Promise.all([getApplications(course.id), getInvitations(course.id)])
      if (request !== generation.current) return
      setPendingGroups(history.items.filter(item => item.status === 'PENDING' && item.applicant.accountId === accountId).map(item => item.groupId))
      setData({ ...current, status: history.status === 'FINALIZED' || invitations.status === 'FINALIZED' ? 'FINALIZED' : history.status === 'FINALIZING' || invitations.status === 'FINALIZING' ? 'FINALIZING' : current.status }); setState('ready'); setUncertain(false)
    } catch (cause) {
      if (request !== generation.current || deny(cause)) return
      setState('error'); setProblem(previous => `${preserveProblem && previous ? previous + '. ' : ''}Current groups could not be checked. Please retry.`)
    }
  }
  useEffect(() => { setMessage(''); setProblem(''); setUncertain(false); busy.current = false; setSaving(false); void load(); return () => { generation.current++; operation.current++ } }, [course.id])
  async function confirm() {
    if (!selected || busy.current || uncertain || data?.status !== 'OPEN' || data.ownGroupId) return
    busy.current = true; setSaving(true); const group = selected; setSelected(null); setMessage(''); setProblem('')
    const request = ++generation.current
    const attempt = ++operation.current
    try {
      if (application) {
        const history = await applyToGroup(course.id, group.id, { commandKey: crypto.randomUUID() })
        if (request !== generation.current) return
        setPendingGroups(history.items.filter(item => item.status === 'PENDING' && item.applicant.accountId === accountId).map(item => item.groupId))
        const current = await getGrouping(course.id)
        if (request !== generation.current) return
        setData(current); setState('ready'); setMessage(history.status === 'OPEN' && current.status === 'OPEN' ? 'Application recorded. It reserves no place. Check Requests for its current outcome.' : 'Grouping is closed. Check Requests for current history. Final group details are temporarily unavailable.'); setUncertain(false)
        return
      }
      const current = await joinEmptyGroup(course.id, group.id, { commandKey: crypto.randomUUID() })
      if (request !== generation.current) return
      setData(current); setState('ready'); setMessage('Your join was confirmed.'); setUncertain(false)
    } catch (cause) {
      if (request !== generation.current) return
      if (deny(cause)) return
      setData(null); setUncertain(true)
      if (cause instanceof ApiError && cause.status === 409) {
        setProblem(application ? 'Application conditions have changed. Current requests and groups must be checked before another choice.' : cause.code === 'GROUP_NOT_EMPTY' ? 'This group is no longer empty' : 'Joining conditions have changed. Check current groups before choosing another action.')
        await load(true)
      } else { setProblem(application ? 'Application could not be confirmed. Check current requests and groups before continuing.' : 'Joining could not be confirmed. Check current groups before continuing.'); setState('error') }
    } finally {
      if (attempt === operation.current) { busy.current = false; setSaving(false); heading.current?.focus() }
    }
  }
  async function confirmLifecycle() {
    if (!lifecycle || busy.current || uncertain || data?.status !== 'OPEN' || data.ownGroupId !== lifecycle.group.id) return
    const selectedAction = lifecycle
    if (selectedAction.kind === 'transfer' && (!selectedAction.target || selectedAction.group.manager?.accountId !== accountId)) return
    busy.current = true; setSaving(true); setLifecycle(null); setMessage(''); setProblem('')
    const request = ++generation.current
    const attempt = ++operation.current
    try {
      const current = selectedAction.kind === 'leave'
        ? await leaveGroup(course.id, selectedAction.group.id, { commandKey: crypto.randomUUID() })
        : await transferManager(course.id, selectedAction.group.id, { commandKey: crypto.randomUUID(), targetMemberId: selectedAction.target!.accountId })
      if (request !== generation.current) return
      setData(null)
      await load()
      if (attempt !== operation.current) return
      setMessage(current.status !== 'OPEN' ? 'Group action confirmed. Grouping is now closed.' : selectedAction.kind === 'leave' ? 'Departure confirmed. You may use normal joining rules to choose a group.' : 'Manager transfer confirmed. Check Requests for your currently permitted history.')
    } catch (cause) {
      if (request !== generation.current || deny(cause)) return
      setData(null); setUncertain(true); setState('error')
      setProblem('Group action could not be confirmed. Check current groups and requests before another explicit action.')
      if (cause instanceof ApiError && cause.status === 409) await load(true)
    } finally { if (attempt === operation.current) { busy.current = false; setSaving(false); heading.current?.focus() } }
  }
  if (data?.status === 'FINALIZED' && state === 'ready') return <Results course={course} role="STUDENT" expired={expired} />
  const own = data?.status === 'OPEN' ? data.groups.find(group => group.id === data.ownGroupId) : undefined
  return <section className="groups-workspace" aria-label="Groups"><div className="activity-toolbar"><div><span className="eyebrow">FIND YOUR GROUP</span><h2 tabIndex={-1} ref={heading}>Groups</h2></div><button className="secondary-button" disabled={saving || state === 'loading'} onClick={() => void load()}>Refresh groups</button></div>
    {problem && <p role="alert" className="notice">{problem}</p>}{message && <p role="status" className="success-notice">{message} {application && <a href={`#/courses/${course.id}/requests`}>View requests</a>}</p>}
    {saving ? <section className="state-panel" role="status"><h3>Confirming group action…</h3><p>Please wait while your current eligibility is checked.</p></section>
      : uncertain ? <section className="state-panel"><h3>Check groups before continuing</h3><p>Your group action may already be recorded. Check current groups and requests before choosing another action.</p><button className="primary-button" disabled={state === 'loading'} onClick={() => void load()}>Check current groups</button></section>
        : state === 'loading' ? <p role="status">Loading groups…</p>
          : state === 'denied' ? <section className="state-panel"><h3>Groups unavailable</h3></section>
            : state === 'error' ? <button className="primary-button" onClick={() => void load()}>Retry groups</button>
              : data?.status === 'ABSENT' ? <section className="state-panel"><h3>No grouping activity yet</h3><p>Your teacher has not created a grouping activity for this course. Refresh to check again.</p></section>
                : data?.status !== 'OPEN' ? <section className="state-panel"><h3>Grouping is finalizing</h3><p>Grouping is closed. Your final group is temporarily unavailable. Refresh to check its status.</p></section>
                  : <><section className="group-status"><h3>{own ? 'Your membership' : 'You are ungrouped'}</h3><p>{own ? own.manager?.accountId === accountId ? `You are the group manager and a member of ${own.name}.` : `You are a member of ${own.name}.` : 'Choose an empty group to become its initial manager and member, or apply to an occupied group with a manager and available places.'}</p>{own && <div className="editor-actions"><button className="secondary-button" onClick={event => { trigger.current = event.currentTarget; setLifecycle({ group: own, kind: 'leave' }) }}>Leave group</button>{own.manager?.accountId === accountId && own.members.filter(member => member.accountId !== accountId).map(member => <button className="secondary-button" key={member.accountId} onClick={event => { trigger.current = event.currentTarget; setLifecycle({ group: own, kind: 'transfer', target: member }) }}>Transfer manager role to {member.studentName}</button>)}</div>}</section><div className="groups-grid">{data.groups.map(group => <section className={`group-card ${own?.id === group.id ? 'own-group' : ''}`} key={group.id} aria-label={group.name}><div className="group-card-heading"><h3>{group.name}</h3><span className="group-badge">{group.members.length === 0 ? 'Empty group' : 'Occupied group'}</span></div><p className="group-capacity">{group.availablePlaces} available / {group.capacity} places</p>{group.members.length ? <><h4>Current members</h4><ul className="group-members">{group.members.map(profile => <Member key={profile.accountId} profile={profile} manager={group.manager?.accountId === profile.accountId} />)}</ul>{!group.manager && <p>No group manager.</p>}</> : <p className="empty-group-copy">Be the first to join and help this group get started.</p>}{!data.ownGroupId && group.members.length === 0 && group.manager === null && group.availablePlaces > 0 && <button className="primary-button" onClick={event => { trigger.current = event.currentTarget; setApplication(false); setSelected(group) }}>Join {group.name}</button>}{!data.ownGroupId && group.members.length > 0 && group.manager !== null && !pendingGroups.includes(group.id) && group.availablePlaces > 0 && <button className="primary-button" onClick={event => { trigger.current = event.currentTarget; setApplication(true); setSelected(group) }}>Apply to {group.name}</button>}{!data.ownGroupId && pendingGroups.includes(group.id) && <p className="application-pending">Application pending. <a href={`#/courses/${course.id}/requests`}>View requests</a></p>}</section>)}</div></>}
    {lifecycle && <LifecycleDialog selection={lifecycle} trigger={trigger.current} close={() => setLifecycle(null)} confirm={() => void confirmLifecycle()} />}{selected && <JoinDialog group={selected} application={application} trigger={trigger.current} close={() => setSelected(null)} confirm={() => void confirm()} />}
  </section>
}
