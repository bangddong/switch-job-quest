import type { Act, Quest } from '@/types/quest.types'
import { QUEST_TYPE_CONFIG } from '@/features/quest-map/constants/questData'

interface QuestBriefingViewProps {
  quest: Quest
  act: Act
  onStart: () => void
}

const TIME_ESTIMATE: Record<string, string> = {
  STUDY: '약 30분',
  WRITE: '약 45분',
  DISCOVER: '약 20분',
  BUILD: '약 60분',
  BOSS: '약 30분',
}

export function QuestBriefingView({ quest, act, onStart }: QuestBriefingViewProps) {
  const typeConfig = QUEST_TYPE_CONFIG[quest.type]
  const timeEstimate = TIME_ESTIMATE[quest.type] ?? '약 30분'

  const containerStyle: React.CSSProperties = {
    animation: 'slideIn 0.4s ease',
    paddingTop: 20,
  }

  const actContextStyle: React.CSSProperties = {
    color: '#334155',
    fontSize: 11,
    letterSpacing: 3,
    margin: 0,
    display: 'flex',
    alignItems: 'center',
    gap: 8,
  }

  const badgeRowStyle: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    marginTop: 16,
    flexWrap: 'wrap',
  }

  const tagBadgeStyle: React.CSSProperties = {
    background: typeConfig.bg,
    border: `1px solid ${typeConfig.border}`,
    borderRadius: 6,
    padding: '3px 10px',
    fontSize: 11,
    color: typeConfig.border,
  }

  const xpBadgeStyle: React.CSSProperties = {
    background: 'rgba(245,158,11,0.08)',
    border: '1px solid rgba(245,158,11,0.3)',
    borderRadius: 6,
    padding: '3px 10px',
    fontSize: 11,
    color: '#F59E0B',
  }

  const titleStyle: React.CSSProperties = {
    fontSize: 22,
    fontWeight: 'bold',
    marginTop: 12,
    marginBottom: 8,
    color: '#F8FAFC',
  }

  const descStyle: React.CSSProperties = {
    fontSize: 13,
    color: '#475569',
    lineHeight: 1.6,
    margin: 0,
  }

  const separatorStyle: React.CSSProperties = {
    borderTop: '1px solid rgba(255,255,255,0.06)',
    paddingTop: 16,
    marginTop: 16,
  }

  const sectionLabelStyle: React.CSSProperties = {
    fontSize: 11,
    letterSpacing: 3,
    color: '#334155',
    margin: '0 0 12px',
  }

  const infoChipsStyle: React.CSSProperties = {
    display: 'flex',
    gap: 12,
    flexWrap: 'wrap',
  }

  const infoChipStyle: React.CSSProperties = {
    background: 'rgba(255,255,255,0.04)',
    border: '1px solid rgba(255,255,255,0.08)',
    borderRadius: 8,
    padding: '8px 12px',
    fontSize: 12,
    color: '#94A3B8',
  }

  const taskListStyle: React.CSSProperties = {
    listStyle: 'none',
    padding: 0,
    margin: 0,
    display: 'flex',
    flexDirection: 'column',
    gap: 8,
  }

  const taskItemStyle: React.CSSProperties = {
    fontSize: 13,
    color: '#94A3B8',
    display: 'flex',
    gap: 10,
  }

  const taskNumberStyle: React.CSSProperties = {
    color: act.color,
    fontWeight: 'bold',
    minWidth: 18,
  }

  const startButtonStyle: React.CSSProperties = {
    width: '100%',
    background: `linear-gradient(135deg, ${act.color}, ${act.color}80)`,
    border: 'none',
    borderRadius: 12,
    padding: 14,
    fontSize: 15,
    fontWeight: 'bold',
    color: '#060610',
    cursor: 'pointer',
    marginTop: 20,
    fontFamily: "'Courier New', monospace",
  }

  return (
    <div style={containerStyle}>
      <p style={actContextStyle}>
        <span>{act.icon}</span>
        <span>
          {act.title} · {act.subtitle}
        </span>
      </p>

      <div style={badgeRowStyle}>
        <span style={{ fontSize: 22 }}>{typeConfig.icon}</span>
        <span style={tagBadgeStyle}>{quest.tag}</span>
        <span style={xpBadgeStyle}>+{quest.xp} XP</span>
      </div>

      <h2 style={titleStyle}>{quest.title}</h2>
      <p style={descStyle}>{quest.desc}</p>

      <div style={separatorStyle}>
        <p style={sectionLabelStyle}>이 퀘스트에서</p>
        <div style={infoChipsStyle}>
          <span style={infoChipStyle}>🎁 +{quest.xp} XP 획득</span>
          <span style={infoChipStyle}>⏱ {timeEstimate}</span>
          {quest.aiCheck && <span style={infoChipStyle}>🤖 AI 검사</span>}
        </div>
      </div>

      <div style={separatorStyle}>
        <p style={sectionLabelStyle}>해야 할 일</p>
        <ol style={taskListStyle}>
          {quest.tasks.map((task, i) => (
            <li key={i} style={taskItemStyle}>
              <span style={taskNumberStyle}>{i + 1}.</span>
              <span>{task}</span>
            </li>
          ))}
        </ol>
      </div>

      <button style={startButtonStyle} onClick={onStart}>
        시작하기 →
      </button>
    </div>
  )
}
