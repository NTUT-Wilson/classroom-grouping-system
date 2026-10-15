import { useEffect, useRef, useState } from 'react'
import { ApiError, getGrouping, joinEmptyGroup } from './api'
import type { Classmate, Course, DiscoveryGroup, GroupDiscovery } from './api'

function JoinDialog({ group, close, confirm, trigger }: { group: DiscoveryGroup; close: () => void; confirm: () => void; trigger: HTMLElement | null }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { dialog.current?.showModal(); return () => { trigger?.focus() } }, [])
  return <dialog ref={dialog} className="profile-dialog" aria-labelledby="join-title" onCancel={close} onClose={close}>
    <h2 id="join-title">Join {group.name}?</h2><p className="join-explanation">You will become this group's initial manager and a member. You cannot leave or switch groups in V1. Confirm only if you want to join this empty group.</p>
    <div className="editor-actions"><button className="secondary-button" autoFocus onClick={close}>Cancel</button><button className="primary-button" onClick={confirm}>Confirm join</button></div>
  </dialog>
}
function Member({ profile, manager }: { profile: Classmate; manager: boolean }) {
  return <li className="group-member"><div><strong>{profile.studentName}</strong>{manager && <span className="you-tag">Manager</span>}<p>Student ID · {profile.studentId}</p><p className="profile-full-text">{profile.aboutMe || 'No introduction yet.'}</p></div></li>
}
export function Groups({ course, accountId, expired }: { course: Course; accountId: string; expired: () => void }) {
  const [data, setData] = useState<GroupDiscovery | null>(null)
  const [state, setState] = useState<'loading' | 'ready' | 'error' | 'denied'>('loading')
  const [problem, setProblem] = useState('')
  const [message, setMessage] = useState('')
  const [saving, setSaving] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [selected, setSelected] = useState<DiscoveryGroup | null>(null)
  const generation = useRef(0)
  const operation = useRef(0)
  const busy = useRef(false)
  const trigger = useRef<HTMLElement | null>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  function deny(cause: unknown) {
    if (cause instanceof ApiError && [401, 403, 404].includes(cause.status)) {
      setData(null); setSelected(null); setState('denied'); setUncertain(false)
      setProblem('This course is unavailable or you do not have access.')
      if (cause.status === 401) expired()
      return true
    }
    return false
  }
  async function load(preserveProblem = false) {
    const request = ++generation.current
    setState('loading'); setData(null); setSelected(null)
    if (!preserveProblem) setProblem('')
    try {
      const current = await getGrouping(course.id)
      if (request !== generation.current) return
      setData(current); setState('ready'); setUncertain(false)
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
      const current = await joinEmptyGroup(course.id, group.id, { commandKey: crypto.randomUUID() })
      if (request !== generation.current) return
      setData(current); setState('ready'); setMessage('Your join was confirmed.'); setUncertain(false)
    } catch (cause) {
      if (request !== generation.current || deny(cause)) return
      setData(null); setUncertain(true)
      if (cause instanceof ApiError && cause.status === 409) {
        setProblem(cause.code === 'GROUP_NOT_EMPTY' ? 'This group is no longer empty' : 'Joining conditions have changed. Check current groups before choosing another action.')
        await load(true)
      } else { setProblem('Joining could not be confirmed. Check current groups before continuing.'); setState('error') }
    } finally {
      if (attempt === operation.current) { busy.current = false; setSaving(false); heading.current?.focus() }
    }
  }
  const own = data?.status === 'OPEN' ? data.groups.find(group => group.id === data.ownGroupId) : undefined
  return <section className="groups-workspace" aria-label="Groups"><div className="activity-toolbar"><div><span className="eyebrow">FIND YOUR GROUP</span><h2 tabIndex={-1} ref={heading}>Groups</h2></div><button className="secondary-button" disabled={saving || state === 'loading'} onClick={() => void load()}>Refresh groups</button></div>
    {problem && <p role="alert" className="notice">{problem}</p>}{message && <p role="status" className="success-notice">{message}</p>}
    {saving ? <section className="state-panel" role="status"><h3>Confirming your join…</h3><p>Please wait while your current eligibility is checked.</p></section>
      : uncertain ? <section className="state-panel"><h3>Check groups before continuing</h3><p>Your join may already be recorded. Check current groups before choosing another action.</p><button className="primary-button" disabled={state === 'loading'} onClick={() => void load()}>Check current groups</button></section>
        : state === 'loading' ? <p role="status">Loading groups…</p>
          : state === 'denied' ? <section className="state-panel"><h3>Groups unavailable</h3></section>
            : state === 'error' ? <button className="primary-button" onClick={() => void load()}>Retry groups</button>
              : data?.status === 'ABSENT' ? <section className="state-panel"><h3>No grouping activity yet</h3><p>Your teacher has not created a grouping activity for this course. Refresh to check again.</p></section>
                : data?.status !== 'OPEN' ? <section className="state-panel"><h3>Grouping is finalizing</h3><p>Grouping is closed. Your final group is temporarily unavailable. Refresh to check its status.</p></section>
                  : <><section className="group-status"><h3>{own ? 'Your membership' : 'You are ungrouped'}</h3><p>{own ? own.manager?.accountId === accountId ? `You are the group manager and a member of ${own.name}.` : `You are a member of ${own.name}.` : 'Choose an empty group to become its initial manager and member.'}</p></section><div className="groups-grid">{data.groups.map(group => <section className={`group-card ${own?.id === group.id ? 'own-group' : ''}`} key={group.id} aria-label={group.name}><div className="group-card-heading"><h3>{group.name}</h3><span className="group-badge">{group.members.length === 0 ? 'Empty group' : 'Occupied group'}</span></div><p className="group-capacity">{group.availablePlaces} available / {group.capacity} places</p>{group.members.length ? <><h4>Current members</h4><ul className="group-members">{group.members.map(profile => <Member key={profile.accountId} profile={profile} manager={group.manager?.accountId === profile.accountId} />)}</ul>{!group.manager && <p>No group manager.</p>}</> : <p className="empty-group-copy">Be the first to join and help this group get started.</p>}{!data.ownGroupId && group.members.length === 0 && group.manager === null && group.availablePlaces > 0 && <button className="primary-button" onClick={event => { trigger.current = event.currentTarget; setSelected(group) }}>Join {group.name}</button>}</section>)}</div></>}
    {selected && <JoinDialog group={selected} trigger={trigger.current} close={() => setSelected(null)} confirm={() => void confirm()} />}
  </section>
}
