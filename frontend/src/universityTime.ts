export const UNIVERSITY_TIME_ZONE = 'America/New_York'

const wallFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: UNIVERSITY_TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
})

export function universityWallTime(value: string): string {
  const parts = Object.fromEntries(wallFormatter.formatToParts(new Date(value)).map(part => [part.type, part.value]))
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`
}

// Resolve the two modern New York offsets against the browser's IANA timezone
// data. Zero matches is a gap/invalid date; two matches is a repeated wall time.
export function universityDeadline(value: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) return null
  const matches = ['-04:00', '-05:00'].filter(offset => {
    const instant = new Date(`${value}:00${offset}`)
    if (!Number.isFinite(instant.getTime())) return false
    return universityWallTime(instant.toISOString()) === value
  })
  return matches.length === 1 ? `${value}:00${matches[0]}` : null
}
