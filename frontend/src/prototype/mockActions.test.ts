import { expect, test } from 'vitest'
import { initialCourses, makeCourseState, makeDemoState } from './data'
import { act } from './mockActions'

test('a course without an activity has no group or request fixtures', () => {
  const state = makeCourseState(initialCourses.find(c => c.phase === 'none')!)
  expect(state.groups).toEqual([])
  expect(state.attempts).toEqual([])
})

test('empty-group joining establishes manager and cancels only the joining student’s pending requests', () => {
  const before = makeDemoState()
  const after = act(before, 'alex', 'open', { type: 'join', group: 3 })
  expect(after.groups[2]).toMatchObject({ members: ['alex'], manager: 'alex' })
  expect(after.attempts.filter(a => a.student === 'alex' && a.status === 'System-canceled')).toHaveLength(2)
  expect(after.attempts.find(a => a.id === 5)?.status).toBe('Rejected')
  expect(after.attempts.find(a => a.id === 2)?.status).toBe('Pending')
  expect(before.groups[2].members).toHaveLength(0)
})

test('rejecting an application leaves the pending invitation unchanged', () => {
  const after = act(makeDemoState(), 'noah', 'open', { type: 'reject', attempt: 1 })
  expect(after.attempts.find(a => a.id === 1)?.status).toBe('Rejected')
  expect(after.attempts.find(a => a.id === 3)?.status).toBe('Pending')
  const reapplied = act(after, 'alex', 'open', { type: 'apply', group: 2 })
  expect(reapplied.attempts.filter(a => a.kind === 'application' && a.student === 'alex' && a.group === 2)).toHaveLength(2)
})

test('last-place approval preserves acceptance and closes other requests with the group-full reason', () => {
  const after = act(makeDemoState(), 'noah', 'open', { type: 'accept', attempt: 2 })
  expect(after.groups[1].members).toEqual(['noah', 'grace', 'ethan'])
  expect(after.attempts.find(a => a.id === 2)?.status).toBe('Accepted')
  expect(after.attempts.find(a => a.id === 1)).toMatchObject({ status: 'System-canceled', reason: 'Group full' })
  expect(after.attempts.find(a => a.id === 3)).toMatchObject({ status: 'System-canceled', reason: 'Group full' })
  expect(() => act(after, 'alex', 'open', { type: 'apply', group: 2 })).toThrow('full')
})

test('members and teachers cannot exercise manager authority, and cutoff blocks rejection too', () => {
  const state = makeDemoState()
  expect(() => act(state, 'grace', 'open', { type: 'accept', attempt: 1 })).toThrow('cannot respond')
  expect(() => act(state, 'grace', 'open', { type: 'invite', group: 2, student: 'iris' })).toThrow('manager')
  expect(() => act(state, 'teacher', 'open', { type: 'invite', group: 2, student: 'iris' })).toThrow('not permitted')
  expect(() => act(state, 'noah', 'finalizing', { type: 'reject', attempt: 1 })).toThrow('closed')
})

test('pending applications do not reserve places and an empty-group conflict does not auto-apply', () => {
  const state = makeDemoState()
  const applied = act(state, 'alex', 'open', { type: 'apply', group: 1 })
  expect(applied.groups).toEqual(state.groups)
  expect(() => act(applied, 'alex', 'open', { type: 'apply', group: 1 })).toThrow('already pending')
  expect(() => act(state, 'alex', 'open', { type: 'apply', group: 3 })).toThrow('empty group')
  state.groups[2] = { id: 3, capacity: 3, members: ['sofia'], manager: 'sofia' }
  expect(() => act(state, 'alex', 'open', { type: 'join', group: 3 })).toThrow('no longer empty')
  expect(state.attempts).toHaveLength(6)
})
