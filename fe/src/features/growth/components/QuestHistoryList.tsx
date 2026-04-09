import type { QuestHistoryItem } from '@/types/api.types'
import { GRADE_COLORS } from '../../../utils/gradeUtils'

interface QuestHistoryListProps {
  history: QuestHistoryItem[]
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
}

export function QuestHistoryList({ history }: QuestHistoryListProps) {
  const recent = history
    .slice()
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 10)

  if (recent.length === 0) {
    return (
      <div
        style={{
          padding: '20px 0',
          textAlign: 'center',
          color: '#475569',
          fontSize: 13,
          fontFamily: "'Courier New', monospace",
        }}
      >
        아직 기록이 없습니다
      </div>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {recent.map((item) => (
        <div
          key={item.id}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            padding: '10px 14px',
            background: '#0F172A',
            border: '1px solid rgba(255,255,255,0.08)',
            borderRadius: 8,
            fontFamily: "'Courier New', monospace",
            fontSize: 12,
          }}
        >
          <span style={{ color: '#F1F5F9', flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {item.questId}
          </span>
          <span
            style={{
              color: GRADE_COLORS[item.grade] ?? '#475569',
              fontWeight: 'bold',
              fontSize: 14,
              minWidth: 16,
              textAlign: 'center',
            }}
          >
            {item.grade}
          </span>
          <span style={{ color: '#F8FAFC', minWidth: 48, textAlign: 'right' }}>{item.score}점</span>
          <span style={{ color: '#F59E0B', minWidth: 56, textAlign: 'right' }}>+{item.earnedXp} XP</span>
          <span style={{ color: '#475569', minWidth: 72, textAlign: 'right' }}>{formatDate(item.createdAt)}</span>
        </div>
      ))}
    </div>
  )
}
