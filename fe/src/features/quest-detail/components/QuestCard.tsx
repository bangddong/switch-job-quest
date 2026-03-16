import type { Quest } from '@/types/quest.types'
import { QUEST_TYPE_CONFIG } from '@/features/quest-map'

interface QuestCardProps {
  quest: Quest
  done: boolean
  score?: number
  onClick: () => void
}

export function QuestCard({ quest, done, score, onClick }: QuestCardProps) {
  const qc = QUEST_TYPE_CONFIG[quest.type]

  return (
    <div
      className="hov-q"
      onClick={onClick}
      style={{
        background: quest.type === 'BOSS' ? 'rgba(239,68,68,0.04)' : qc.bg,
        border: `1px solid ${done ? '#10B98140' : qc.border + '25'}`,
        borderRadius: 11,
        padding: '13px 16px',
        animation: quest.type === 'BOSS' ? 'bossP 3s ease-in-out infinite' : 'none',
        display: 'flex',
        alignItems: 'center',
        gap: 12,
      }}
    >
      <div style={{ fontSize: 22, minWidth: 32 }}>{qc.icon}</div>
      <div style={{ flex: 1 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 3 }}>
          <span
            style={{
              fontSize: 9,
              color: qc.border,
              background: `${qc.border}12`,
              padding: '2px 7px',
              borderRadius: 4,
            }}
          >
            {quest.tag}
          </span>
          {quest.aiCheck && <span style={{ fontSize: 10, color: '#A78BFA' }}>🤖 AI</span>}
        </div>
        <div style={{ fontSize: 14, fontWeight: 'bold', color: done ? '#475569' : '#E2E8F0' }}>
          {quest.title}
        </div>
        <div style={{ fontSize: 11, color: '#334155', marginTop: 2 }}>{quest.desc}</div>
      </div>
      <div style={{ textAlign: 'right', minWidth: 52 }}>
        {done ? (
          <div>
            <div style={{ fontSize: 16, color: '#10B981' }}>✓</div>
            {score != null && <div style={{ fontSize: 10, color: '#475569' }}>{score}점</div>}
          </div>
        ) : (
          <div>
            <div style={{ fontSize: 12, color: '#F59E0B', fontWeight: 'bold' }}>+{quest.xp}</div>
            <div style={{ fontSize: 10, color: '#1E293B' }}>XP</div>
          </div>
        )}
      </div>
    </div>
  )
}
