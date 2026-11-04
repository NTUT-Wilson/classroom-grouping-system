export function announcementTextError(value: string, maximum: number, label: string): string {
  if (/^[\s\u0085\u001c-\u001f]*$/u.test(value)) return `${label} is required.`
  if (value.includes('\0') || Array.from(value).some(character => { const point = character.codePointAt(0)!; return point >= 0xd800 && point <= 0xdfff })) return `${label} must contain valid plain text without NUL.`
  return Array.from(value).length > maximum ? `${label} must be at most ${maximum.toLocaleString('en-US')} characters.` : ''
}
export function announcementTime(value: string): string {
  return new Intl.DateTimeFormat('en-US', { timeZone: 'America/New_York', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}
