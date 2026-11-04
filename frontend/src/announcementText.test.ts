import { expect, test } from 'vitest'
import { announcementTextError, announcementTime } from './announcementText'
test('announcement limits count Unicode code points and preserve valid original text', () => {
  expect(announcementTextError('😀'.repeat(150), 150, 'Title')).toBe('')
  expect(announcementTextError('😀'.repeat(151), 150, 'Title')).toContain('150')
  expect(announcementTextError('😀'.repeat(10000), 10000, 'Body')).toBe('')
  expect(announcementTextError('😀'.repeat(10001), 10000, 'Body')).toContain('10,000')
  expect(announcementTextError(' e\u0301\r\n ', 150, 'Title')).toBe('')
  for (const text of ['\ud800', '\udfff', 'a\0b']) expect(announcementTextError(text, 150, 'Title')).toContain('valid plain text')
  expect(announcementTextError(' \n\t', 150, 'Title')).toContain('required')
})
test('announcement publication and edit timestamps use New York winter and summer offsets', () => {
  expect(announcementTime('2026-01-01T17:00:00Z')).toBe('Jan 1, 2026, 12:00 PM')
  expect(announcementTime('2026-07-01T16:00:00Z')).toBe('Jul 1, 2026, 12:00 PM')
})
