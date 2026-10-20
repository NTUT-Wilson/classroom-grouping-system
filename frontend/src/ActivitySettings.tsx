import { useEffect, useRef, useState } from 'react'
import { ApiError, extendDeadline, getActivity, saveInstructions } from './api'
import type { Activity } from './api'
import { ActivityDetails, Deadline, instructionsError, universityDeadline } from './Activity'
import { universityWallTime } from './universityTime'

type Editor = 'instructions' | 'deadline' | null
export function ActivitySettings({ initial, courseId, role, expired }: { initial: Activity; courseId: string; role: 'TEACHER' | 'STUDENT'; expired: () => void }) {
  const [activity, setActivity] = useState<Activity | null>(initial)
  const [editor, setEditor] = useState<Editor>(null)
  const [draft, setDraft] = useState('')
  const [issue, setIssue] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [blocked, setBlocked] = useState(false)
  const [reviewDraft, setReviewDraft] = useState(false)
  const generation = useRef(0)
  const pending = useRef(false)
  const field = useRef<HTMLTextAreaElement & HTMLInputElement>(null)
  const editorHeading = useRef<HTMLHeadingElement>(null)
  useEffect(() => () => { generation.current++ }, [])
  useEffect(() => { if (editor) field.current?.focus() }, [editor])
  function clearSession() {
    generation.current++; setActivity(null); setDraft(''); setEditor(null); setBlocked(true); expired()
  }
  async function refresh() {
    if (pending.current) return
    pending.current = true; setBusy(true); setIssue(''); setMessage('')
    const request = ++generation.current
    try {
      const current = await getActivity(courseId)
      if (request !== generation.current) return
      if (!current.activity) throw new Error('Activity unavailable')
      setActivity(current.activity)
      if (blocked) { setBlocked(false); setReviewDraft(!!editor) }
      setMessage('Current activity checked.')
    } catch (cause) {
      if (request !== generation.current) return
      if (cause instanceof ApiError && cause.status === 401) { clearSession(); return }
      setActivity(null); setBlocked(true)
      setIssue(cause instanceof ApiError && (cause.status === 403 || cause.status === 404) ? 'This course is unavailable or you do not have access.' : 'Unable to check activity status. Please retry the read.')
    } finally { pending.current = false; if (request === generation.current) setBusy(false) }
  }
  function begin(kind: Exclude<Editor, null>) {
    if (!activity || busy || blocked) return
    setEditor(kind); setDraft(kind === 'instructions' ? activity.instructions : universityWallTime(activity.deadline))
    setIssue(''); setMessage(''); setReviewDraft(false)
  }
  async function save() {
    if (pending.current || blocked || reviewDraft || !activity || !editor) return
    const value = universityDeadline(draft)
    const error = editor === 'instructions' ? instructionsError(draft) : !value ? 'Enter a valid, unambiguous deadline in America/New_York time. Times skipped or repeated by daylight saving are not accepted.'
      : activity.status !== 'OPEN' ? 'Grouping is closed. The deadline cannot be extended or reopened.'
        : Date.parse(value) <= Date.parse(activity.deadline) ? 'Choose a deadline later than the current deadline.' : ''
    if (error) { setIssue(error); field.current?.focus(); return }
    pending.current = true; setBusy(true); setIssue(''); setMessage('')
    const request = ++generation.current
    try {
      const commandKey = crypto.randomUUID()
      const current = await (editor === 'instructions' ? saveInstructions(courseId, { commandKey, instructions: draft }) : extendDeadline(courseId, { commandKey, deadline: value! }))
      if (request !== generation.current) return
      if (!current.activity) throw new Error('Missing confirmed activity')
      setActivity(current.activity); setEditor(null); setDraft(''); setMessage(editor === 'instructions' ? 'Instructions saved.' : 'Deadline extended.')
    } catch (cause) {
      if (request !== generation.current) return
      if (cause instanceof ApiError && cause.status === 401) { clearSession(); return }
      if (cause instanceof ApiError && cause.status === 400) setIssue(cause.message)
      else {
        setBlocked(true)
        if (cause instanceof ApiError && (cause.status === 403 || cause.status === 404)) setActivity(null)
        setIssue('Save could not be confirmed. Check current activity before trying again. Your request may already have succeeded.')
      }
    } finally { pending.current = false; if (request === generation.current) setBusy(false) }
  }
  return <>
    <div className="activity-toolbar"><span className="eyebrow">COURSE GROUPING</span><button className="secondary-button" disabled={busy} onClick={() => void refresh()}>{blocked ? 'Check activity' : 'Refresh activity'}</button></div>
    {issue && <p role="alert" className="notice">{issue}</p>}{message && <p role="status" className="success-notice">{message}</p>}
    {!activity ? <section className="state-panel"><h2>Activity unavailable</h2><p>Check current activity before continuing.</p></section> : <>
      <ActivityDetails activity={activity} />
      <p className="results-footnote"><a href={`#/courses/${courseId}/${role === 'TEACHER' ? 'results' : 'groups'}`}>{role === 'TEACHER' ? 'View final roster status' : 'View my group'}</a></p>
      {role === 'TEACHER' && !editor && <div className="editor-actions"><button className="secondary-button" disabled={busy || blocked} onClick={() => begin('instructions')}>Edit instructions</button>{activity.status === 'OPEN' && <button className="secondary-button" disabled={busy || blocked} onClick={() => begin('deadline')}>Extend deadline</button>}</div>}
    </>}
    {role === 'TEACHER' && editor && <form className="activity-panel activity-form settings-editor" noValidate onSubmit={event => { event.preventDefault(); void save() }}>
      <span className="eyebrow">ACTIVITY SETTINGS</span><h2 ref={editorHeading} tabIndex={-1}>{editor === 'instructions' ? 'Edit activity instructions' : 'Extend grouping deadline'}</h2>
      {editor === 'instructions' ? <><p>Update the task description and guidance. Instructions remain editable after grouping closes.</p><label htmlFor="settings-instructions">Instructions</label><textarea ref={field} id="settings-instructions" rows={8} value={draft} disabled={busy} onChange={event => setDraft(event.target.value)} aria-describedby="settings-text-hint" /><p id="settings-text-hint" className="activity-hint">Required · plain text · {Array.from(draft).length} / 5,000 characters</p></> : <><p>The deadline can only move later while grouping is open. Eligibility is checked again when saved.</p><label htmlFor="settings-deadline">New deadline (America/New_York)</label><input ref={field} id="settings-deadline" type="datetime-local" value={draft} disabled={busy} onChange={event => setDraft(event.target.value)} aria-describedby="settings-deadline-hint" /><p id="settings-deadline-hint" className="activity-hint">America/New_York, regardless of your device timezone.</p></>}
      {activity && <div className="settings-current"><strong>{editor === 'instructions' ? 'Current saved instructions' : 'Current grouping deadline'}</strong>{editor === 'instructions' ? <p className="activity-instructions">{activity.instructions}</p> : <Deadline value={activity.deadline} />}</div>}
      {blocked && <p role="status">Your draft is preserved. Saving is paused until a successful activity check.</p>}
      {reviewDraft && <p role="status">Compare your preserved draft with the current saved value before making a new explicit save.</p>}
      <div className="editor-actions">{reviewDraft ? <button type="button" className="primary-button" disabled={busy} onClick={event => { event.preventDefault(); setReviewDraft(false); field.current?.focus() }}>Review preserved draft</button> : <button className="primary-button" disabled={busy || blocked || !activity || (editor === 'deadline' && activity.status !== 'OPEN')}>{busy ? 'Saving…' : editor === 'instructions' ? 'Save instructions' : 'Save deadline'}</button>}<button type="button" className="secondary-button" disabled={busy || blocked} onClick={() => { setEditor(null); setDraft(''); setIssue('') }}>{editor === 'instructions' ? 'Cancel editing' : 'Cancel extension'}</button></div>
    </form>}
  </>
}

