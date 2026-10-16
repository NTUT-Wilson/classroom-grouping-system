import { useEffect, useRef, useState } from 'react'
import { ApiError, getResults } from './api'
import type { Course, FinalResults } from './api'

export function Results({ course, role, expired }: { course: Course; role: 'TEACHER' | 'STUDENT'; expired: () => void }) {
  const [data, setData] = useState<FinalResults | null>(null)
  const [state, setState] = useState<'loading' | 'ready' | 'error' | 'denied'>('loading')
  const generation = useRef(0)
  async function load() {
    const request = ++generation.current
    setData(null); setState('loading')
    try {
      const result = await getResults(course.id, role)
      if (request !== generation.current) return
      if (result.status === 'FINALIZED' && (!result.groups.length || (role === 'STUDENT' && result.groups.length !== 1))) throw new Error('Incomplete result')
      setData(result); setState('ready')
    } catch (cause) {
      if (request !== generation.current) return
      setData(null)
      setState(cause instanceof ApiError && [401, 403, 404].includes(cause.status) ? 'denied' : 'error')
      if (cause instanceof ApiError && cause.status === 401) expired()
    }
  }
  useEffect(() => { void load(); return () => { generation.current++ } }, [course.id, role])
  const finalized = data?.status === 'FINALIZED'
  return <section className="results-workspace" aria-label={role === 'TEACHER' ? 'Final roster' : 'My final group'}>
    <div className="activity-toolbar"><div><span className="eyebrow">{finalized ? 'GROUPING COMPLETE' : 'COURSE RESULTS'}</span><h2>{role === 'TEACHER' ? 'Final roster' : 'My final group'}</h2></div><button className="secondary-button" disabled={state === 'loading'} onClick={() => void load()}>Refresh results</button></div>
    {state === 'loading' ? <section className="state-panel" role="status"><h3>Loading results…</h3><p>Checking the complete result and your access.</p></section>
      : state === 'denied' ? <section className="state-panel"><h3>Results unavailable</h3><p role="alert">This course is unavailable or you do not have access.</p></section>
        : state === 'error' ? <section className="state-panel"><h3>Results are temporarily unavailable</h3><p role="alert">The complete result could not be loaded. Refresh to check again.</p><p>Refreshing only checks the result. It does not restart grouping or change assignments.</p></section>
          : data?.status === 'ABSENT' ? <section className="state-panel"><h3>No grouping activity yet</h3><p>Results will be available after an activity is created and grouping completes.</p></section>
            : data?.status === 'OPEN' ? <section className="state-panel"><h3>Results come after the deadline</h3><p>The system will complete grouping after the cutoff. Return here to view the completed result.</p></section>
              : !finalized ? <section className="state-panel"><h3>Grouping is finalizing</h3><p>The complete result is not available yet. Refresh to check again; this will not restart grouping or change assignments.</p></section>
                : <><p className="results-intro">{role === 'TEACHER' ? 'Every participant has one group. The complete roster is read-only.' : 'Only your own group’s final membership is visible to you. Group membership is fixed.'}</p><div className={role === 'TEACHER' ? 'results-grid' : 'own-result'}>{data!.groups.map(group => <section className="group-card final-group" key={group.id} aria-label={group.name}><div className="group-card-heading"><div className="final-group-title"><span className="group-number" aria-hidden="true">{String(group.ordinal).padStart(2, '0')}</span><h3>{group.name}</h3></div><span className="group-badge">{group.capacity} places</span></div><ul className="group-members">{group.members.map(member => <li className="group-member" key={member.accountId}><div><strong>{member.studentName}</strong>{group.managerAccountId === member.accountId && <span className="you-tag">Manager</span>}<p>Student ID · {member.studentId}</p>{role === 'STUDENT' && <p className="profile-full-text">{member.aboutMe || 'No introduction yet.'}</p>}</div></li>)}</ul>{group.managerAccountId === null && <p className="activity-hint">This group was first populated by system assignment and has no manager.</p>}</section>)}</div>{role === 'STUDENT' && <p className="results-footnote">Your course About me and classmate profiles remain available in <a href={`#/courses/${course.id}/classmates`}>Classmates</a>.</p>}</>}
  </section>
}
