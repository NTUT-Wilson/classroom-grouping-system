import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { currentSession, signIn, signOut } from './api'

export function App() {
  const [session, setSession] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => { currentSession().then(setSession).catch(() => setError('Service unavailable. Please retry.')) }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    setError(''); setBusy(true)
    try {
      await signIn(String(data.get('username')), String(data.get('password')))
      form.reset()
      setSession(await currentSession())
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Sign in could not be confirmed.') }
    finally { setBusy(false) }
  }

  async function logout() {
    setError(''); setBusy(true)
    try { await signOut(); setSession(null) }
    catch { setError('Sign out could not be confirmed. Refresh to check your session.') }
    finally { setBusy(false) }
  }

  return <main>
    <h1>Classroom Grouping</h1>
    {session ? <><p>Signed in as {session}.</p><button disabled={busy} onClick={logout}>Sign out</button>
      <p>Course and grouping workflows are not available yet.</p></>
      : <form onSubmit={submit}>
        <h2>Sign in</h2>
        <label>Username<input name="username" autoComplete="username" required /></label>
        <label>Password<input name="password" type="password" autoComplete="current-password" required /></label>
        <button disabled={busy}>Sign in</button>
      </form>}
    {error && <p role="alert">{error}</p>}
  </main>
}
