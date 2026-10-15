import { useEffect, useRef, useState } from 'react'
import { ApiError, getAboutMe, listClassmates, saveAboutMe } from './api'
import type { Classmate, Course } from './api'

export function introductionError(text: string): string {
  for (const character of text) {
    const code = character.codePointAt(0)!
    if (code === 0 || (code >= 0xd800 && code <= 0xdfff)) return 'About me must contain valid plain text without NUL characters.'
  }
  return Array.from(text).length > 500 ? 'About me must be 500 characters or fewer.' : ''
}
function Avatar({ name }: { name: string }) { return <span className="profile-avatar" aria-hidden="true">{Array.from(name)[0]}</span> }
function ProfileDialog({ profile, course, close, trigger }: { profile: Classmate; course: Course; close: () => void; trigger: HTMLElement | null }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    dialog.current?.showModal()
    return () => { trigger?.focus() }
  }, [])
  return <dialog ref={dialog} className="profile-dialog" aria-labelledby="profile-title" onCancel={close} onClose={close}>
    <div className="profile-dialog-heading"><h2 id="profile-title">{profile.studentName}</h2><button className="secondary-button" autoFocus onClick={close}>Close profile</button></div>
    <div className="profile-identity"><Avatar name={profile.studentName} /><div><span className="eyebrow">STUDENT ID</span><p>{profile.studentId}</p></div></div>
    <h3>About me</h3><p className="profile-full-text">{profile.aboutMe || 'No introduction yet.'}</p><p className="profile-footnote">Course-specific introduction · {course.name}</p>
  </dialog>
}
export function Classmates({ course, accountId, expired }: { course: Course; accountId: string; expired: () => void }) {
  const [profiles, setProfiles] = useState<Classmate[]>([])
  const [own, setOwn] = useState<Classmate | null>(null)
  const [draft, setDraft] = useState('')
  const [state, setState] = useState<'loading' | 'ready' | 'error' | 'denied'>('loading')
  const [message, setMessage] = useState('')
  const [problem, setProblem] = useState('')
  const [saving, setSaving] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [reconciling, setReconciling] = useState(false)
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<Classmate | null>(null)
  const profileTrigger = useRef<HTMLElement | null>(null)
  const generation = useRef(0)
  function denied(cause: unknown) {
    if (cause instanceof ApiError && cause.status === 401) { expired(); return true }
    if (cause instanceof ApiError && (cause.status === 403 || cause.status === 404)) {
      setProfiles([]); setOwn(null); setSelected(null); setDraft(''); setState('denied'); setProblem('This course is unavailable or you do not have access.'); return true
    }
    return false
  }
  async function load() {
    const request = ++generation.current
    setState('loading'); setProblem(''); setSelected(null)
    try {
      const [peers, profile] = await Promise.all([listClassmates(course.id), getAboutMe(course.id)])
      if (request !== generation.current) return
      setProfiles(peers); setOwn(profile); setDraft(profile.aboutMe); setState('ready')
    } catch (cause) { if (request === generation.current && !denied(cause)) { setState('error'); setProblem('Unable to load classmates. Please retry.') } }
  }
  useEffect(() => { void load(); return () => { generation.current++ } }, [course.id])
  async function save() {
    const error = introductionError(draft)
    if (error) { setProblem(error); return }
    if (saving || uncertain || !own) return
    const request = generation.current
    setSaving(true); setProblem(''); setMessage('')
    try {
      const profile = await saveAboutMe(course.id, draft)
      if (request !== generation.current) return
      setOwn(profile); setDraft(profile.aboutMe); setProfiles(current => current.map(peer => peer.accountId === accountId ? profile : peer))
      setMessage('Your About me has been saved for this course.')
    } catch (cause) {
      if (request !== generation.current || denied(cause)) return
      if (cause instanceof ApiError && cause.status === 400) setProblem(cause.message)
      else { setUncertain(true); setProblem('Save could not be confirmed. Check the saved introduction before saving again.') }
    } finally { if (request === generation.current) setSaving(false) }
  }
  async function reconcile() {
    const request = generation.current
    setReconciling(true)
    try {
      const profile = await getAboutMe(course.id)
      if (request !== generation.current) return
      setOwn(profile); setProfiles(current => current.map(peer => peer.accountId === accountId ? profile : peer)); setUncertain(false)
      setProblem(''); setMessage('Saved introduction checked. Your draft is still in the editor. Review it before saving again.')
    } catch (cause) { if (request === generation.current && !denied(cause)) setProblem('The saved introduction could not be checked. Please retry the read before saving again.') }
    finally { if (request === generation.current) setReconciling(false) }
  }
  if (state === 'loading') return <section className="state-panel" role="status"><h2>Loading classmates…</h2></section>
  if (state !== 'ready') return <section className="state-panel"><h2>Classmates unavailable</h2><p role="alert">{problem}</p><button className="primary-button" onClick={() => void load()}>Retry classmates</button></section>
  const filtered = profiles.filter(profile => `${profile.studentName}\n${profile.studentId}\n${profile.aboutMe}`.toLocaleLowerCase().includes(query.toLocaleLowerCase()))
  const validation = introductionError(draft)
  return <section className="classmates-workspace">
    {problem && <p className="notice" role="alert">{problem}</p>}{message && <p className="success-notice" role="status">{message}</p>}
    <section className="profile-editor"><Avatar name={own!.studentName} /><div><span className="eyebrow">YOUR COURSE INTRODUCTION</span><h2>A few words about you</h2><p>Help classmates get to know you. This introduction belongs to this course.</p>
      <form onSubmit={event => { event.preventDefault(); void save() }}><label htmlFor="about-me">About me</label><textarea id="about-me" value={draft} onChange={event => setDraft(event.target.value)} disabled={saving || reconciling} aria-describedby="about-me-hint about-me-count" aria-invalid={!!validation} rows={4} /><div className="editor-meta"><span id="about-me-hint">Optional · plain text · up to 500 characters</span><span id="about-me-count">{Array.from(draft).length} / 500</span></div>{validation && <p className="field-error" role="alert">{validation}</p>}
      <div className="editor-actions"><button className="primary-button" disabled={saving || uncertain || !!validation}>{saving ? 'Saving…' : 'Save introduction'}</button>{uncertain && <button className="secondary-button" type="button" disabled={reconciling} onClick={() => void reconcile()}>{reconciling ? 'Checking…' : 'Check saved introduction'}</button>}</div></form>
      {message.startsWith('Saved introduction checked') && <div className="saved-introduction"><strong>Currently saved About me</strong><p className="profile-full-text">{own!.aboutMe || 'No introduction yet.'}</p></div>}
    </div></section>
    <div className="classmates-heading"><div><span className="eyebrow">YOUR COURSE COMMUNITY</span><h2>Classmates</h2><p>{profiles.length} {profiles.length === 1 ? 'student' : 'students'} in this course</p></div><label className="classmate-search">Search classmates<input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Name, Student ID, or introduction" /></label></div>
    {filtered.length ? <div className="people-grid">{filtered.map(profile => <article className="person-card" key={profile.accountId}><div className="person-header"><Avatar name={profile.studentName} /><div><h3>{profile.studentName}{profile.accountId === accountId && <span className="you-tag">You</span>}</h3><p>{profile.studentId}</p></div></div><p className="person-about">{profile.aboutMe || 'No introduction yet.'}</p><button className="profile-link" aria-label={`View profile ${profile.studentName}`} onClick={event => { profileTrigger.current = event.currentTarget; setSelected(profile) }}>View profile <span aria-hidden="true">→</span></button></article>)}</div> : <section className="state-panel"><h2>{profiles.length ? 'No classmates match your search' : 'No classmates yet'}</h2><p>{profiles.length ? 'Try another name, Student ID, or introduction.' : 'There are no student profiles available in this course.'}</p></section>}
    {selected && <ProfileDialog profile={selected} course={course} trigger={profileTrigger.current} close={() => setSelected(null)} />}
  </section>
}
