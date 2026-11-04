import { useEffect, useRef, useState } from 'react'
import { ApiError, getAnnouncement, getAnnouncementCommand, listAnnouncements, sendAnnouncementCommand } from './api'
import type { Announcement, AnnouncementAcknowledgement, AnnouncementCommand, AnnouncementPage, Course } from './api'
import { announcementTextError, announcementTime } from './announcementText'

function AnnouncementDates({ value }: { value: Announcement }) {
  return <div className="announcement-dates"><span>Published <time dateTime={value.publishedAt}>{announcementTime(value.publishedAt)}</time></span>{value.revision > 1 && <span>Edited <time dateTime={value.updatedAt}>{announcementTime(value.updatedAt)}</time></span>}<span>America/New_York</span></div>
}
function DeleteDialog({ value, cancel, confirm, trigger }: { value: Announcement; cancel: () => void; confirm: () => void; trigger: HTMLElement | null }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { dialog.current?.showModal(); return () => { trigger?.focus() } }, [])
  return <dialog ref={dialog} className="profile-dialog" aria-labelledby="announcement-delete-title" aria-describedby="announcement-delete-description" onCancel={event => { event.preventDefault(); cancel() }} onClose={cancel}>
    <h2 id="announcement-delete-title">Delete announcement?</h2><p className="announcement-dialog-title">{value.title}</p><p id="announcement-delete-description">This removes the announcement from the course. It cannot be restored.</p><div className="editor-actions"><button className="secondary-button" autoFocus onClick={cancel}>Cancel deletion</button><button className="danger-button" onClick={confirm}>Confirm deletion</button></div>
  </dialog>
}
type Editor = { command: 'CREATE' | 'EDIT'; id?: string; revision?: number; title: string; body: string }
type Recovery = 'unknown' | 'retry' | 'committed' | null
export function Announcements({ course, role, announcementId, expired }: { course: Course; role: 'TEACHER' | 'STUDENT'; announcementId?: string; expired: () => void }) {
  const [page, setPage] = useState<AnnouncementPage>({ items: [], nextCursor: null })
  const [detail, setDetail] = useState<Announcement | null>(null)
  const [cursors, setCursors] = useState<(string | undefined)[]>([undefined])
  const [state, setState] = useState<'loading' | 'ready' | 'error' | 'denied'>('loading')
  const [issue, setIssue] = useState('')
  const [message, setMessage] = useState('')
  const [editor, setEditor] = useState<Editor | null>(null)
  const [attempt, setAttempt] = useState<AnnouncementCommand | null>(null)
  const [recovery, setRecovery] = useState<Recovery>(null)
  const [conflict, setConflict] = useState(false)
  const [reviewDraft, setReviewDraft] = useState(false)
  const [missing, setMissing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [deleting, setDeleting] = useState<Announcement | null>(null)
  const deleteTrigger = useRef<HTMLElement | null>(null)
  const titleInput = useRef<HTMLInputElement>(null)
  const generation = useRef(0)
  const pending = useRef(false)
  const cursor = cursors.at(-1)
  function denied(cause: unknown) {
    if (!(cause instanceof ApiError) || ![401, 403, 404].includes(cause.status)) return false
    generation.current++; pending.current = false
    setPage({ items: [], nextCursor: null }); setDetail(null); setEditor(null); setAttempt(null); setRecovery(null); setDeleting(null); setMessage(''); setState('denied'); setBusy(false)
    setIssue('This announcement is unavailable or you do not have access.')
    if (cause.status === 401) expired()
    return true
  }
  async function load(force = false) {
    if (pending.current || (recovery && !force)) return
    const targetId = announcementId ?? (!force ? editor?.id ?? detail?.id : undefined)
    pending.current = true
    const request = ++generation.current
    setState('loading'); setIssue(''); setPage({ items: [], nextCursor: null }); setDetail(null)
    try {
      if (targetId) {
        const current = await getAnnouncement(course.id, targetId)
        if (request !== generation.current) return
        setDetail(current); setMissing(false)
        if (editor?.command === 'EDIT') { setEditor(currentEditor => currentEditor && ({ ...currentEditor, revision: current.revision })); setReviewDraft(true) }
      } else {
        const current = await listAnnouncements(course.id, cursor)
        if (request !== generation.current) return
        setPage(current); setMissing(false)
      }
      setConflict(false); setState('ready')
    } catch (cause) {
      if (request === generation.current && !denied(cause)) { setState('error'); setIssue('Announcements could not be loaded. Please retry the read.') }
    } finally { if (request === generation.current) pending.current = false }
  }
  useEffect(() => {
    setEditor(null); setAttempt(null); setRecovery(null); setConflict(false); setReviewDraft(false); setMissing(false); setMessage(''); setDeleting(null); setBusy(false)
    void load(true)
    return () => { generation.current++; pending.current = false }
  }, [course.id, announcementId, cursor])
  useEffect(() => { if (editor) titleInput.current?.focus() }, [editor?.command, editor?.id])
  // Each reconciliation reads both an actor-scoped receipt and current course/source.
  // A missing resource alone never establishes that this command committed.
  async function readSources(command: AnnouncementCommand, acknowledgement: AnnouncementAcknowledgement | null) {
    let currentPage = await listAnnouncements(course.id, cursor)
    const id = acknowledgement?.id ?? command.id
    let current: Announcement | null = null
    if (id) {
      try { current = await getAnnouncement(course.id, id) }
      catch (cause) {
        if (!(cause instanceof ApiError && cause.status === 404)) throw cause
        currentPage = await listAnnouncements(course.id, cursor)
      }
    }
    return { currentPage, current, id }
  }
  function showSources(source: Awaited<ReturnType<typeof readSources>>) {
    setPage(source.currentPage)
    if (source.id) { setDetail(source.current); setMissing(!!announcementId && !source.current) }
    setState('ready')
  }
  async function execute(command: AnnouncementCommand) {
    if (pending.current || role !== 'TEACHER') return
    pending.current = true; setBusy(true); setAttempt(command); setIssue(''); setMessage(''); setRecovery(null)
    const request = ++generation.current
    try {
      const acknowledgement = await sendAnnouncementCommand(course.id, command)
      if (request !== generation.current) return
      const source = await readSources(command, acknowledgement)
      if (request !== generation.current) return
      showSources(source); setEditor(null); setAttempt(null); setRecovery(null); setConflict(false); setReviewDraft(false)
      setMessage(command.command === 'DELETE' ? 'Announcement deleted.' : command.command === 'CREATE' ? 'Announcement published.' : 'Announcement saved.')
    } catch (cause) {
      if (request !== generation.current || denied(cause)) return
      if (cause instanceof ApiError && cause.status === 400) { setAttempt(null); setIssue(cause.message) }
      else if (cause instanceof ApiError && cause.code === 'ANNOUNCEMENT_REVISION_CONFLICT') {
        setAttempt(null); setConflict(true); setIssue('This announcement changed. Reload the current announcement and review your preserved draft before continuing.')
      } else if (cause instanceof ApiError && cause.code === 'COMMAND_KEY_REUSED') {
        setAttempt(null); setConflict(true); setIssue('This command key was already used for another request. Reload current announcements before making a new request. Your draft is preserved.')
      } else {
        setRecovery('unknown'); setIssue('The request could not be confirmed. Your original request is preserved. Check its receipt and current announcement before continuing.')
      }
    } finally { if (request === generation.current) { pending.current = false; setBusy(false) } }
  }
  async function reconcile() {
    if (!attempt || pending.current) return
    pending.current = true; setBusy(true); setIssue(''); setMessage('')
    const request = ++generation.current
    try {
      const status = await getAnnouncementCommand(course.id, attempt)
      if (request !== generation.current) return
      if (typeof status.committed !== 'boolean' || (status.committed && !status.acknowledgement)) throw new Error('Incomplete receipt')
      const source = await readSources(attempt, status.acknowledgement)
      if (request !== generation.current) return
      showSources(source)
      if (status.committed) {
        const acknowledgement = status.acknowledgement!
        setRecovery('committed')
        setMessage(acknowledgement.deleted ? 'The original deletion committed. The announcement is no longer available.'
          : !source.current ? 'The original request committed, and the announcement was later deleted or is no longer available.'
            : source.current.revision !== acknowledgement.revision ? 'The original request committed, and the announcement has since changed. Current content is shown.'
              : 'The original request committed. Current content is shown.')
      } else {
        setRecovery('retry'); setMessage('No committed receipt was found. Current sources were checked. You may explicitly retry the original request with its original key and inputs.')
      }
    } catch (cause) {
      if (request === generation.current && !denied(cause)) { setRecovery('unknown'); setIssue('The receipt and current announcement could not both be checked. Saving remains paused. Retry the reads.') }
    } finally { if (request === generation.current) { pending.current = false; setBusy(false) } }
  }
  function save() {
    if (!editor || busy || recovery || conflict || reviewDraft) return
    const error = announcementTextError(editor.title, 150, 'Title') || announcementTextError(editor.body, 10000, 'Body')
    if (error) { setIssue(error); titleInput.current?.focus(); return }
    void execute({ command: editor.command, commandKey: crypto.randomUUID(), ...(editor.id ? { id: editor.id, expectedRevision: editor.revision } : {}), title: editor.title, body: editor.body })
  }
  function beginEdit() {
    if (!detail || busy || recovery || conflict) return
    setEditor({ command: 'EDIT', id: detail.id, revision: detail.revision, title: detail.title, body: detail.body }); setIssue(''); setMessage('')
  }
  const locked = busy || !!recovery || conflict || reviewDraft
  return <section className="announcements-workspace">
    <div className="announcements-heading"><div><span className="eyebrow">COURSE NEWS</span><h2>Announcements</h2><p>Published updates for {course.code}.</p></div><div className="editor-actions"><button className="secondary-button" onClick={() => void load()} disabled={busy || state === 'loading' || !!recovery}>{conflict ? 'Reload current announcement' : 'Refresh announcements'}</button>{role === 'TEACHER' && !editor && !announcementId && !detail && <button className="primary-button" disabled={busy || !!recovery || state !== 'ready'} onClick={() => { setEditor({ command: 'CREATE', title: '', body: '' }); setIssue(''); setMessage('') }}>New announcement</button>}</div></div>
    {(announcementId || detail) && <a className="back-link" href={`#/courses/${course.id}/announcements`} onClick={event => { if (!announcementId) { event.preventDefault(); setDetail(null); setMissing(false); setMessage('') } }}>Back to announcements</a>}
    {issue && <p className="notice" role="alert">{issue}</p>}{message && <p className="success-notice" role="status">{message}</p>}
    {recovery && <section className="activity-panel announcement-recovery" aria-label="Request recovery"><h3>Check the original request</h3><p>Other writes are paused. The attempted inputs and command key remain unchanged.</p><div className="editor-actions"><button className="secondary-button" disabled={busy} onClick={() => void reconcile()}>Check receipt and current announcement</button>{recovery === 'retry' && <button className="primary-button" disabled={busy} onClick={() => attempt && void execute(attempt)}>Retry original request</button>}{recovery === 'committed' && <button className="primary-button" disabled={busy} onClick={() => { setAttempt(null); setRecovery(null); setEditor(null); setIssue('') }}>Finish review</button>}</div></section>}
    {state === 'loading' ? <section className="state-panel" role="status"><h3>Loading announcements…</h3></section>
      : state !== 'ready' ? <section className="state-panel"><h3>Announcement unavailable</h3><button className="primary-button" onClick={() => void load()}>Retry announcements</button></section>
        : missing ? <section className="state-panel"><h3>Announcement unavailable</h3><p>This announcement was deleted or is no longer available.</p></section>
          : detail ? <article className="activity-panel announcement-detail"><h3>{detail.title}</h3><AnnouncementDates value={detail} /><p className="announcement-body">{detail.body}</p>{role === 'TEACHER' && !editor && <div className="editor-actions"><button className="secondary-button" disabled={locked} onClick={beginEdit}>Edit announcement</button><button className="danger-button" disabled={locked} onClick={event => { deleteTrigger.current = event.currentTarget; setDeleting(detail) }}>Delete announcement</button></div>}</article>
            : <>{page.items.length ? <div className="announcement-list">{page.items.map(item => <article className="activity-panel announcement-card" key={item.id}><h3><a href={`#/courses/${course.id}/announcements/${item.id}`}>{item.title}</a></h3><AnnouncementDates value={item} /></article>)}</div> : <section className="state-panel"><h3>No announcements yet</h3><p>Published course updates will appear here.</p></section>}<nav className="editor-actions" aria-label="Announcement pages"><button className="secondary-button" disabled={cursors.length === 1 || busy || !!recovery || !!editor} onClick={() => setCursors(current => current.slice(0, -1))}>Previous page</button><span>Page {cursors.length}</span><button className="secondary-button" disabled={!page.nextCursor || busy || !!recovery || !!editor} onClick={() => setCursors(current => [...current, page.nextCursor!])}>Next page</button></nav></>}
    {role === 'TEACHER' && editor && <form className="activity-panel activity-form announcement-editor" noValidate onSubmit={event => { event.preventDefault(); save() }}><span className="eyebrow">{editor.command === 'CREATE' ? 'PUBLISH TO COURSE' : 'UPDATE COURSE NEWS'}</span><h3>{editor.command === 'CREATE' ? 'New announcement' : 'Edit announcement'}</h3><p>{editor.command === 'CREATE' ? 'Publishing makes this announcement immediately visible to currently enrolled students.' : 'Your changes preserve the original publication order.'}</p><label htmlFor="announcement-title">Title</label><input id="announcement-title" ref={titleInput} value={editor.title} disabled={busy || !!recovery} onChange={event => setEditor({ ...editor, title: event.target.value })} aria-describedby="announcement-title-hint" /><p id="announcement-title-hint" className="activity-hint">Required · plain text · {Array.from(editor.title).length} / 150 characters</p><label htmlFor="announcement-body">Body</label><textarea id="announcement-body" rows={10} value={editor.body} disabled={busy || !!recovery} onChange={event => setEditor({ ...editor, body: event.target.value })} aria-describedby="announcement-body-hint" /><p id="announcement-body-hint" className="activity-hint">Required · plain text · {Array.from(editor.body).length} / 10,000 characters</p>{(conflict || reviewDraft) && <p role="status">Your draft is preserved. Compare it with the current announcement above before saving.</p>}<div className="editor-actions">{reviewDraft ? <button type="button" className="primary-button" disabled={busy} onClick={event => { event.preventDefault(); setReviewDraft(false); titleInput.current?.focus() }}>Review preserved draft</button> : <button className="primary-button" disabled={locked || state !== 'ready'}>{busy ? 'Saving…' : editor.command === 'CREATE' ? 'Publish announcement' : 'Save announcement'}</button>}<button type="button" className="secondary-button" disabled={busy || !!recovery} onClick={() => { setEditor(null); setIssue(''); setReviewDraft(false) }}>Cancel editing</button></div></form>}
    {deleting && <DeleteDialog value={deleting} trigger={deleteTrigger.current} cancel={() => setDeleting(null)} confirm={() => { const value = deleting; setDeleting(null); void execute({ command: 'DELETE', commandKey: crypto.randomUUID(), id: value.id, expectedRevision: value.revision }) }} />}
  </section>
}
