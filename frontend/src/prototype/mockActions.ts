import type { Attempt, DemoState, Phase } from './data'

export type MockCommand = { type: 'join' | 'apply' | 'invite'; group: number; student?: string }
  | { type: 'accept' | 'reject'; attempt: number }

/** In-memory interaction simulation. These checks are not production authorization. */
export function act(state: DemoState, actor: string, phase: Phase, command: MockCommand): DemoState {
  if (phase !== 'open' || actor === 'teacher') throw new Error('Grouping actions are closed or not permitted.')
  const next = structuredClone(state)
  let target: number
  let student = actor
  let accepted: Attempt | undefined
  if ('attempt' in command) {
    accepted = next.attempts.find(a => a.id === command.attempt)
    if (!accepted || accepted.status !== 'Pending') throw new Error('This request is no longer pending. Refresh to check its status.')
    const authorized = accepted.kind === 'application' ? accepted.manager === actor : accepted.student === actor
    if (!authorized) throw new Error('You cannot respond to this request.')
    if (accepted.kind === 'application' && !next.groups.some(g => g.id === accepted!.group && g.manager === actor)) throw new Error('Manager access is required.')
    if (command.type === 'reject') { accepted.status = 'Rejected'; return next }
    target = accepted.group; student = accepted.student
  } else { target = command.group; student = command.student ?? actor }
  const group = next.groups.find(g => g.id === target)
  if (!group) throw new Error('This group is unavailable.')
  if (next.groups.some(g => g.members.includes(student))) throw new Error('This student already belongs to a group.')
  if (group.members.length >= group.capacity) throw new Error('This group is full. Choose another available group.')
  if (command.type === 'apply' || command.type === 'invite') {
    const kind = command.type === 'apply' ? 'application' : 'invitation'
    if (!group.members.length || !group.manager) throw new Error('An empty group uses deliberate joining, not applications.')
    if (command.type === 'invite' && group.manager !== actor) throw new Error('Only this group’s manager can send invitations.')
    if (command.type === 'apply' && student !== actor) throw new Error('You may apply only for yourself.')
    if (next.attempts.some(a => a.kind === kind && a.group === target && a.student === student && a.status === 'Pending')) throw new Error('A request of this type is already pending.')
    next.attempts.push({ id: Math.max(...next.attempts.map(a => a.id), 0) + 1, kind, student, group: target, manager: group.manager, status: 'Pending' })
    return next
  }
  if (command.type === 'join') {
    if (group.members.length) throw new Error('This group is no longer empty. Review its current members before choosing to apply.')
    if (student !== actor) throw new Error('You may join only for yourself.')
    group.manager = actor
  }
  group.members.push(student)
  if (accepted) accepted.status = 'Accepted'
  for (const a of next.attempts) {
    if (a.status !== 'Pending') continue
    if (a.student === student) { a.status = 'System-canceled'; a.reason = 'Student joined a group' }
    else if (a.group === group.id && group.members.length === group.capacity) { a.status = 'System-canceled'; a.reason = 'Group full' }
  }
  return next
}
