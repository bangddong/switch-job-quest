import type { QuestHistoryItem } from '@/types/api.types'

interface StreakBadgeProps {
  history: QuestHistoryItem[]
}

function toDateStr(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function calcStreak(history: QuestHistoryItem[]): number {
  const activeDates = new Set(history.map((h) => toDateStr(new Date(h.createdAt))))
  let streak = 0
  const cursor = new Date()
  while (activeDates.has(toDateStr(cursor))) {
    streak++
    cursor.setDate(cursor.getDate() - 1)
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
