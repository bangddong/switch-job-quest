import type { Act, Quest } from '@/types/quest.types'
import { ACTS } from '../constants/questData'
import { QUEST_NEXT } from '@/features/quest-detail/constants/questConnections'

interface ReturnBannerProps {
  lastCompletedAt: string
  completed: Record<string, boolean>
  onResume: (act: Act, quest: Quest) => void
}

function daysSince(dateStr: string): number {
  const last = new Date(dateStr)
  const now = new Date()
  return Math.floor((now.getTime() - last.getTime()) / (1000 * 60 * 60 * 24))
}

function findNextQuest(completed: Record<string, boolean>): { act: Act; quest: Quest } | null {
  // Strategy 1: QUEST_NEXT connection
  for (const [sourceId, { questId: targetId }] of Object.entries(QUEST_NEXT)) {
    if (completed[sourceId] && !completed[targetId]) {
      for (const act of ACTS) {
        const quest = act.quests.find((q) => q.id === targetId)
        if (quest) return { act, quest }
      }
    }
  }
  // Strategy 2: first incomplete
  for (const act of ACTS) {
    for (const quest of act.quests) {
      if (!completed[quest.id]) return { act, quest }
    }
  }
  return null
}

export function ReturnBanner({ lastCompletedAt, completed, onResume }: ReturnBannerProps) {
  const days = daysSince(lastCompletedAt)
  const next = findNextQuest(completed)

  if (!next) return null

  return (
    <div style={{
      background: 'rgba(167,139,250,0.05)',
      border: '1px solid rgba(167,139,250,0.3)',
      borderRadius: 12,
      padding: 18,
      marginBottom: 16,
      animation: 'slideIn 0.4s ease',
    }}>
      <div style={{ fontSize: 11, letterSpacing: 3, color: '#A78BFA', marginBottom: 6 }}>
        👋 다시 돌아오셨네요
      </div>
      <div style={{ fontSize: 13, color: '#94A3B8', marginBottom: 12, lineHeight: 1.6 }}>
        {days}일 만에 돌아오셨어요. 이어서 진행해봐요.
      </div>
      <div style={{ fontSize: 14, fontWeight: 'bold', color: '#F8FAFC', marginBottom: 14 }}>
        {next.quest.title}
      </div>
      <button
        onClick={() => onResume(next.act, next.quest)}
        style={{
          width: '100%',
          padding: '10px 16px',
          background: 'linear-gradient(135deg, #A78BFA, rgba(167,139,250,0.7))',
          border: 'none',
          borderRadius: 8,
          color: '#060610',
          fontSize: 13,
          fontWeight: 'bold',
          cursor: 'pointer',
          fontFamily: "'Courier New', monospace",
        }}
      >
        이어서 하기 →
      </button>
    </div>
  )
}
