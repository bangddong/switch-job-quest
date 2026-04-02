import type { Act, Quest } from '@/types/quest.types'
import { ACTS } from '../constants/questData'
import { QUEST_NEXT } from '@/features/quest-detail/constants/questConnections'

interface TodayMissionBannerProps {
  completed: Record<string, boolean>
  onStart: (act: Act, quest: Quest) => void
}

function findActForQuest(questId: string): { act: Act; quest: Quest } | null {
  for (const act of ACTS) {
    const quest = act.quests.find((q) => q.id === questId)
    if (quest) return { act, quest }
  }
  return null
}

function findTodayMission(completed: Record<string, boolean>): { act: Act; quest: Quest; message: string } | null {
  // Strategy 1: QUEST_NEXT connections — source completed, target not completed
  for (const [sourceId, { questId: targetId, message }] of Object.entries(QUEST_NEXT)) {
    if (completed[sourceId] && !completed[targetId]) {
      const found = findActForQuest(targetId)
      if (found) return { ...found, message }
    }
  }

  // Strategy 2: Fallback — first incomplete quest in order
  for (const act of ACTS) {
    for (const quest of act.quests) {
      if (!completed[quest.id]) {
        return { act, quest, message: '다음 퀘스트를 시작해봐요' }
      }
    }
  }

  return null
}

export function TodayMissionBanner({ completed, onStart }: TodayMissionBannerProps) {
  const mission = findTodayMission(completed)

  if (!mission) return null

  const { act, quest, message } = mission

  const containerStyle: React.CSSProperties = {
    border: '1px solid rgba(245,158,11,0.35)',
    background: 'rgba(245,158,11,0.04)',
    borderRadius: 12,
    padding: 18,
    marginBottom: 16,
  }

  const headerStyle: React.CSSProperties = {
    fontSize: 11,
    letterSpacing: 3,
    color: '#F59E0B',
    margin: 0,
    marginBottom: 4,
  }

  const messageStyle: React.CSSProperties = {
    fontSize: 12,
    color: '#94A3B8',
    margin: '4px 0 6px',
  }

  const titleStyle: React.CSSProperties = {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#F8FAFC',
    margin: '0 0 12px',
  }

  const buttonStyle: React.CSSProperties = {
    width: '100%',
    background: 'linear-gradient(135deg, #F59E0B, rgba(245,158,11,0.7))',
    border: 'none',
    borderRadius: 8,
    padding: '10px 16px',
    fontSize: 13,
    fontWeight: 'bold',
    color: '#060610',
    cursor: 'pointer',
    fontFamily: "'Courier New', monospace",
  }

  return (
    <div style={containerStyle}>
      <p style={headerStyle}>⚡ 오늘의 미션</p>
      <p style={messageStyle}>{message}</p>
      <p style={titleStyle}>{quest.title}</p>
      <button style={buttonStyle} onClick={() => onStart(act, quest)}>
        바로 시작하기 →
      </button>
    </div>
  )
}
