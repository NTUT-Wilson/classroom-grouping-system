/** Synthetic UI fixtures only. Nothing here imports the API client or persists data. */
export type Phase = 'none' | 'open' | 'finalizing' | 'finalized' | 'unavailable'
export type Person = { id: string; name: string; studentId: string; about: string }
export type Group = { id: number; capacity: number; members: string[]; manager?: string }
export type Attempt = { id: number; kind: 'application' | 'invitation'; student: string; group: number; manager: string; status: 'Pending' | 'Accepted' | 'Rejected' | 'System-canceled' | 'Expired'; reason?: string }
export type Course = { id: string; code: string; name: string; subtitle: string; phase: Phase; tone: string }
export type DemoState = { groups: Group[]; attempts: Attempt[]; instructions: string; deadline: string; about: Record<string, string> }
export const people: Person[] = [
  { id: 'alex', name: 'Alex Chen', studentId: '01120401', about: 'I enjoy turning messy ideas into clear interfaces. Happy to help with research and prototyping.' },
  { id: 'maya', name: 'Maya Lin', studentId: '01120402', about: 'Interested in accessible design and thoughtful typography.' },
  { id: 'leo', name: 'Leo Wang', studentId: '01120403', about: 'I like building interactive prototypes and testing new ideas.' },
  { id: 'noah', name: 'Noah Wu', studentId: '01120404', about: 'A detail-oriented collaborator. I can help with interviews and visual design.' },
  { id: 'grace', name: 'Grace Liu', studentId: '01120405', about: 'Curious about how people use everyday products.' },
  { id: 'iris', name: 'Iris Huang', studentId: '01120406', about: 'I enjoy user research and making sense of what we learn.' },
  { id: 'ethan', name: 'Ethan Tsai', studentId: '01120407', about: 'Comfortable with frontend development and presenting ideas.' },
  { id: 'owen', name: 'Owen Chang', studentId: '01120408', about: '' },
  { id: 'sofia', name: 'Sofia Lee', studentId: '01120409', about: 'Looking forward to working on something useful together.' },
]
export const personas = [
  { id: 'alex', name: 'Alex Chen', role: 'Student · ungrouped', teacher: false },
  { id: 'grace', name: 'Grace Liu', role: 'Student · member', teacher: false },
  { id: 'noah', name: 'Noah Wu', role: 'Student · manager', teacher: false },
  { id: 'teacher', name: 'Dr. Evelyn Lee', role: 'Teacher', teacher: true },
]
export const initialCourses: Course[] = [
  { id: 'interaction', code: 'DES 201', name: 'Interaction Design', subtitle: 'Designing for everyday experiences', phase: 'open', tone: 'blue' },
  { id: 'database', code: 'CS 230', name: 'Database Systems', subtitle: 'From information to understanding', phase: 'finalized', tone: 'mint' },
  { id: 'visual', code: 'DES 104', name: 'Visual Communication', subtitle: 'Ideas, expressed with clarity', phase: 'none', tone: 'peach' },
]
export const initialInstructions = 'Work together to investigate an everyday campus experience. Interview users, identify a meaningful problem, and develop an interactive prototype. Bring different perspectives, share the work, and make room for everyone’s ideas.'
export const demoNow = '2026-10-20T10:00'
export const initialDeadline = '2026-10-23T17:00'
export const person = (id: string) => people.find(p => p.id === id)!
export const initials = (name: string) => name.split(' ').map(n => n[0]).slice(0, 2).join('')
export const phaseLabel = (phase: Phase) => ({ none: 'No activity yet', open: 'Grouping open', finalizing: 'Finalizing', finalized: 'Results ready', unavailable: 'Temporarily unavailable' })[phase]
export function makeDemoState(finalized = false): DemoState {
  return {
    groups: finalized ? [
      { id: 1, capacity: 3, members: ['maya', 'leo', 'alex'], manager: 'maya' },
      { id: 2, capacity: 3, members: ['noah', 'grace', 'iris'], manager: 'noah' },
      { id: 3, capacity: 3, members: ['ethan', 'owen', 'sofia'] },
    ] : [
      { id: 1, capacity: 3, members: ['maya', 'leo'], manager: 'maya' },
      { id: 2, capacity: 3, members: ['noah', 'grace'], manager: 'noah' },
      { id: 3, capacity: 3, members: [] },
    ],
    attempts: [
      { id: 1, kind: 'application', student: 'alex', group: 2, manager: 'noah', status: 'Pending' },
      { id: 2, kind: 'application', student: 'ethan', group: 2, manager: 'noah', status: 'Pending' },
      { id: 3, kind: 'invitation', student: 'alex', group: 2, manager: 'noah', status: 'Pending' },
      { id: 4, kind: 'application', student: 'iris', group: 2, manager: 'noah', status: 'Rejected' },
      { id: 5, kind: 'invitation', student: 'alex', group: 1, manager: 'maya', status: 'Rejected' },
      { id: 6, kind: 'invitation', student: 'iris', group: 2, manager: 'noah', status: 'Rejected' },
    ],
    instructions: initialInstructions, deadline: initialDeadline,
    about: Object.fromEntries(people.map(p => [p.id, p.about])),
  }
}

export function makeCourseState(course: Course): DemoState {
  const state = makeDemoState(course.phase === 'finalized')
  if (course.phase === 'none') return { ...state, groups: [], attempts: [] }
  // All scenarios share the explicit 20 October clock; closed fixtures have a past cutoff.
  return { ...state, deadline: ['finalizing', 'finalized', 'unavailable'].includes(course.phase) ? '2026-10-19T17:00' : initialDeadline }
}
