import { useEffect, useRef, useState } from 'react'
import { ApiError, createActivity, getActivity } from './api'
import type { Activity, ActivityWorkspace, Course } from './api'

export function instructionsError(text: string): string {
  if (!/[^\u0009-\u000d\u001c-\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]/u.test(text)) return 'Enter activity instructions.'
  for (const character of text) {
    const code = character.codePointAt(0)!
    if (code === 0 || (code >= 0xd800 && code <= 0xdfff)) return 'Instructions must contain valid plain text without NUL characters.'
  }
  return Array.from(text).length > 5000 ? 'Instructions must be 5,000 characters or fewer.' : ''
}
// datetime-local is a wall time. Never interpret it in the device timezone.
export function taipeiDeadline(value: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) return null
  const instant = new Date(`${value}:00+08:00`)
  if (!Number.isFinite(instant.getTime())) return null
  const wall = new Date(instant.getTime() + 8 * 3600000).toISOString().slice(0, 16)
  return wall === value ? `${value}:00+08:00` : null
}
export function capacityError(entries: string[], count: number): string {
  if (count < 2) return 'At least two enrolled students are required to create an activity.'
  if (!entries.length || entries.some(value => !/^\d+$/.test(value) || !Number.isSafeInteger(Number(value)) || Number(value) < 2)) return 'Enter a whole-number capacity of at least 2 for every group.'
  return entries.reduce((sum, value) => sum + Number(value), 0) !== count ? `Capacities must total exactly ${count}, including every enrolled student.` : ''
}
export function Deadline({ value }: { value: string }) {
  return <time dateTime={value}>{new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Taipei', year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(value))} · Asia/Taipei (UTC+8)</time>
}
function ActivityDetails({ activity }: { activity: Activity }) {
  return <div className="activity-columns"><section className="activity-panel"><span className="eyebrow">GROUPING ACTIVITY</span><h2>Activity instructions</h2><p className="activity-instructions">{activity.instructions}</p></section><aside className="activity-panel"><span className="eyebrow">ACTIVITY DETAILS</span><h2>One course. One activity.</h2><div className="activity-setting"><strong>Grouping deadline</strong><Deadline value={activity.deadline} /></div><div className="activity-setting"><strong>Fixed capacity layout</strong><ol className="capacity-list">{activity.groups.map(group => <li key={group.id}><span>{group.name}</span><strong>{group.capacity} places</strong></li>)}</ol><p className="activity-hint">Capacities and participants are fixed after creation.</p></div>{activity.status === 'FINALIZING' && <p role="status">Grouping is closed. Final results are not available. Refresh to check current activity status.</p>}</aside></div>
}
export function CourseActivity({ course, role, expired }: { course: Course; role: 'TEACHER' | 'STUDENT'; expired: () => void }) {
  const [workspace, setWorkspace] = useState<ActivityWorkspace | null>(null)
  const [loading, setLoading] = useState(true)
  const [problem, setProblem] = useState('')
  const [message, setMessage] = useState('')
  const [stage, setStage] = useState<'empty' | 'setup' | 'review'>('empty')
  const [instructions, setInstructions] = useState('')
  const [deadline, setDeadline] = useState('')
  const [capacities, setCapacities] = useState([''])
  const [attempted, setAttempted] = useState(false)
  const [saving, setSaving] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [denied, setDenied] = useState(false)
  const generation = useRef(0)
  const submitting = useRef(false)
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => { if (stage !== 'empty') heading.current?.focus() }, [stage])
  async function load(reconcile = false) {
    const request = ++generation.current
    setLoading(true); setProblem('')
    try {
      const current = await getActivity(course.id)
      if (request !== generation.current) return
      setWorkspace(current); setUncertain(false); setDenied(false)
      if (reconcile) { setStage(current.activity ? 'empty' : 'setup'); setMessage(current.activity ? 'Current activity checked.' : 'No activity is saved. Review your setup before a new explicit creation attempt.') }
    } catch (cause) {
      if (request !== generation.current) return
      if (cause instanceof ApiError && cause.status === 401) { expired(); return }
      setWorkspace(null)
      setProblem(cause instanceof ApiError && (cause.status === 403 || cause.status === 404) ? 'This course is unavailable or you do not have access.' : 'Unable to check activity status. Please retry the read.')
    } finally { if (request === generation.current) setLoading(false) }
  }
  useEffect(() => { void load(); return () => { generation.current++ } }, [course.id])
  const instructionIssue = instructionsError(instructions)
  const parsedDeadline = taipeiDeadline(deadline)
  const deadlineIssue = !parsedDeadline ? 'Enter a valid deadline in Asia/Taipei time.' : Date.parse(parsedDeadline) <= Date.now() ? 'Choose a future deadline. The deadline is checked again when saved.' : ''
  const capacityIssue = capacityError(capacities, workspace?.enrolledCount ?? 0)
  function review() {
    setAttempted(true); setProblem(''); setMessage('')
    if (instructionIssue || deadlineIssue || capacityIssue) { document.getElementById(instructionIssue ? 'activity-instructions' : capacityIssue ? 'activity-capacities' : 'activity-deadline')?.focus(); return }
    setStage('review')
  }
  async function confirm() {
    if (submitting.current || uncertain || denied) return
    if (instructionIssue || deadlineIssue || capacityIssue) { setAttempted(true); setStage('setup'); return }
    submitting.current = true; setSaving(true); setProblem(''); setMessage('')
    const request = generation.current
    try {
      const current = await createActivity(course.id, { commandKey: crypto.randomUUID(), instructions, deadline: parsedDeadline!, capacities: capacities.map(Number) })
      if (request !== generation.current) return
      if (!current.activity) throw new Error('Missing confirmed activity')
      setWorkspace(current); setStage('empty'); setMessage('Activity created. It is now visible to course participants.')
    } catch (cause) {
      if (request !== generation.current) return
      if (cause instanceof ApiError && cause.status === 401) { expired(); return }
      if (cause instanceof ApiError && cause.status === 400) { setStage('setup'); setProblem(cause.message) }
      else if (cause instanceof ApiError && (cause.status === 403 || cause.status === 404)) { setDenied(true); setWorkspace(null); setProblem(cause.message) }
      else { setUncertain(true); setProblem('Creation could not be confirmed. Check activity status before trying again. Your request may already have succeeded.') }
    } finally { submitting.current = false; if (request === generation.current) setSaving(false) }
  }
  if (loading) return <section className="state-panel" role="status"><h2>Checking activity…</h2></section>
  if (!workspace) return <section className="state-panel"><h2>Activity unavailable</h2><p role="alert">{problem}</p><button className="secondary-button" onClick={() => void load(true)}>Check activity</button></section>
  return <section className="activity-workspace">
    <div className="activity-toolbar"><span className="eyebrow">COURSE GROUPING</span><button className="secondary-button" disabled={saving} onClick={() => void load(uncertain)}>Refresh activity</button></div>
    {problem && <p role="alert" className="notice">{problem}</p>}{message && <p role="status" className="success-notice">{message}</p>}
    {uncertain ? <section className="state-panel"><h2>Check before continuing</h2><p>No further creation request will be sent until current activity status is checked.</p><button className="primary-button" onClick={() => void load(true)}>Check activity</button></section>
      : workspace.activity ? <ActivityDetails activity={workspace.activity} />
        : role === 'STUDENT' ? <section className="state-panel"><h2>No grouping activity yet</h2><p>Your teacher has not created a grouping activity for this course. Refresh to check again.</p></section>
          : stage === 'empty' ? <section className="state-panel"><span className="eyebrow">A NEW GROUPING ACTIVITY</span><h2>Give collaboration a starting point.</h2><p>Create an activity for all {workspace.enrolledCount} enrolled students.</p>{workspace.enrolledCount! < 2 && <p role="alert">At least two enrolled students are required to create an activity.</p>}<button className="primary-button" disabled={workspace.enrolledCount! < 2} onClick={() => setStage('setup')}>Create activity</button></section>
            : stage === 'review' ? <section className="activity-panel activity-review"><span className="eyebrow">REVIEW BEFORE CREATING</span><h2 ref={heading} tabIndex={-1}>Ready to open grouping?</h2><p>Creating this activity makes it immediately visible to all {workspace.enrolledCount} course participants. The capacity layout cannot be changed, and the activity cannot be deleted, replaced, or reset.</p><h3>Activity instructions</h3><p className="activity-instructions">{instructions}</p><h3>Grouping deadline</h3><Deadline value={parsedDeadline!} /><h3>Fixed capacity layout</h3><ol className="capacity-list">{capacities.map((value, index) => <li key={index}><span>Group {index + 1}</span><strong>{value} places</strong></li>)}</ol><div className="editor-actions"><button className="secondary-button" disabled={saving} onClick={() => setStage('setup')}>Back to setup</button><button className="primary-button" disabled={saving} onClick={() => void confirm()}>{saving ? 'Creating…' : 'Confirm create activity'}</button></div></section>
              : <div className="activity-columns"><form noValidate className="activity-panel activity-form" onSubmit={event => { event.preventDefault(); review() }}><span className="eyebrow">A NEW GROUPING ACTIVITY</span><h2 ref={heading} tabIndex={-1}>Set up your activity</h2><p>Include every enrolled student. Review all details before creating.</p><label htmlFor="activity-instructions">Instructions</label><textarea id="activity-instructions" rows={6} value={instructions} onChange={event => setInstructions(event.target.value)} aria-describedby="instructions-hint instructions-count" aria-invalid={attempted && !!instructionIssue} /><div className="editor-meta"><span id="instructions-hint">Required · plain text</span><span id="instructions-count">{Array.from(instructions).length} / 5,000</span></div>{attempted && instructionIssue && <p className="field-error" role="alert">{instructionIssue}</p>}<fieldset id="activity-capacities" tabIndex={-1}><legend>Manual group capacities</legend><p className="activity-hint">One group per entry. Whole numbers, minimum 2. No automatic adjustment.</p>{capacities.map((value, index) => <div className="capacity-entry" key={index}><label>Group {index + 1} capacity<input type="text" inputMode="numeric" value={value} onChange={event => setCapacities(current => current.map((entry, position) => position === index ? event.target.value : entry))} aria-invalid={attempted && !!capacityIssue} /></label><button type="button" className="secondary-button" aria-label={`Remove Group ${index + 1}`} onClick={() => setCapacities(current => current.filter((_, position) => position !== index))}>Remove</button></div>)}<button className="secondary-button" type="button" onClick={() => setCapacities(current => [...current, ''])}>Add group</button><p className="capacity-total">{capacities.reduce((sum, value) => /^\d+$/.test(value) ? sum + Number(value) : sum, 0)} places / {workspace.enrolledCount} enrolled students</p>{attempted && capacityIssue && <p className="field-error" role="alert">{capacityIssue}</p>}</fieldset><label htmlFor="activity-deadline">Deadline (Asia/Taipei)</label><input id="activity-deadline" type="datetime-local" value={deadline} onChange={event => setDeadline(event.target.value)} aria-describedby="deadline-hint" aria-invalid={attempted && !!deadlineIssue} /><p id="deadline-hint" className="activity-hint">Asia/Taipei (UTC+8), regardless of your device timezone. The deadline must still be in the future when saved.</p>{attempted && deadlineIssue && <p className="field-error" role="alert">{deadlineIssue}</p>}<div className="editor-actions"><button className="primary-button">Review activity</button><button className="secondary-button" type="button" onClick={() => setStage('empty')}>Cancel setup</button></div></form><aside className="activity-guide"><h2>Set the structure.<br />Leave room for connection.</h2><p>Each enrolled student is included. You choose the group capacities.</p><h3>01 · Check the layout</h3><p>Capacities and participants are fixed after creation.</p><h3>02 · Choose a clear cutoff</h3><p>Grouping opens immediately after creation and closes at the deadline.</p><p className="activity-hint">Review is a confirmation step. It does not save a draft.</p></aside></div>}
  </section>
}


