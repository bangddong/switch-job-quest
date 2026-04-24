import type { Act, Quest } from '@/types/quest.types'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { PixelIcon } from '@/components/ui/PixelIcon'
import { QuestCard } from './QuestCard'

interface ActViewProps {
  act: Act
  completed: Record<string, boolean>
  aiScores: Record<string, number>
  progress: number
  onSelectQuest: (quest: Quest) => void
}

export function ActView({ act, completed, aiScores, progress, onSelectQuest }: ActViewProps) {
  return (
    <div style={{ animation: 'slideIn 0.4s ease' }}>
      <div style={{ padding: '24px 0 18px', display: 'flex', alignItems: 'center', gap: 14 }}>
        <div
          style={{
            animation: 'float 3s ease-in-out infinite',
            color: act.color,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <PixelIcon name={act.icon} size={40} />
        </div>
        <div>
          <div style={{ fontSize: 9, letterSpacing: 4, color: act.color, marginBottom: 3 }}>
            {act.title}
          </div>
          <h2 style={{ margin: 0, fontSize: 22, fontWeight: 'bold' }}>{act.subtitle}</h2>
        </div>
      </div>

      <div
        style={{
          background: 'rgba(10,14,26,0.6)',
          border: `1px solid ${act.color}20`,
          borderRadius: 10,
          padding: '10px 14px',
          marginBottom: 18,
          display: 'flex',
          alignItems: 'center',
          gap: 10,
        }}
      >
        <div style={{ flex: 1 }}>
          <ProgressBar value={progress} color={act.color} height={5} />
        </div>
        <span style={{ fontSize: 12, color: act.color, fontWeight: 'bold' }}>{progress}%</span>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
        {act.quests.map((quest) => (
          <QuestCard
            key={quest.id}
            quest={quest}
            done={!!completed[quest.id]}
            score={aiScores[quest.id]}
            onClick={() => onSelectQuest(quest)}
          />
        ))}
      </div>
    </div>
  )
}
