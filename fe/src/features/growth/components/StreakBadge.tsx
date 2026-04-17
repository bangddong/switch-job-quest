import type { QuestHistoryItem } from '@/types/api.types'

interface StreakBadgeProps {
  history: QuestHistoryItem[]
}

function toLocalDateStr(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function toUtcDateStr(date: Date): string {
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}-${String(date.getUTCDate()).padStart(2, '0')}`
}

function toHistoryDateStr(createdAt: string): string {
  const datePart = createdAt.match(/^\d{4}-\d{2}-\d{2}/)?.[0]
  if (datePart) {
    return datePart
  }
  return toLocalDateStr(new Date(createdAt))
}

function subtractDays(dateStr: string, days: number): string {
  const parts = dateStr.split('-').map(Number)
  const [year, month, day] = parts as [number, number, number]
  const date = new Date(Date.UTC(year, month - 1, day))
  date.setUTCDate(date.getUTCDate() - days)
  return toUtcDateStr(date)
}

function calcStreak(history: QuestHistoryItem[]): number {
  const activeDates = new Set(history.map((h) => toHistoryDateStr(h.createdAt)))
  let streak = 0
  let cursor = toLocalDateStr(new Date())

  while (activeDates.has(cursor)) {
    streak++
    cursor = subtractDays(cursor, 1)
  }
  return streak
}

export function StreakBadge({ history }: StreakBadgeProps) {
  const streak = calcStreak(history)

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 16,
        padding: '24px 0',
        fontFamily: "'Courier New', monospace",
      }}
    >
      <div style={{ textAlign: 'center' }}>
        <div style={{ fontSize: 48, fontWeight: 'bold', color: streak > 0 ? '#F59E0B' : '#334155', lineHeight: 1 }}>
          {streak}
        </div>
        <div style={{ fontSize: 12, color: '#475569', marginTop: 6 }}>연속 학습일</div>
      </div>
      <div style={{ fontSize: 36 }}>{streak >= 7 ? '🔥' : streak >= 3 ? '⚡' : streak > 0 ? '✨' : '💤'}</div>
    </div>
  )
}
