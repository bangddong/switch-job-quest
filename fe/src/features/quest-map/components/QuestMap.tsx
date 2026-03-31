import type { Act } from '@/types/quest.types'
import type { Character } from '@/types/character.types'
import { ACTS, ACT_UNLOCK_THRESHOLD } from '../constants/questData'
import { ActCard } from './ActCard'
import { StatsPanel } from './StatsPanel'

interface QuestMapProps {
  onSelectAct: (act: Act) => void
  onOpenCoach: () => void
  completed: Record<string, boolean>
  getActProgress: (act: Act) => number
  character: Character
}

export function QuestMap({ onSelectAct, onOpenCoach, completed, getActProgress, character }: QuestMapProps) {
  const completedCount = Object.keys(completed).length

  return (
    <div style={{ animation: 'slideIn 0.4s ease' }}>
      <div style={{ textAlign: 'center', padding: '40px 0 24px' }}>
        <div style={{ fontSize: 10, letterSpacing: 6, color: '#1E293B', marginBottom: 5 }}>
          {character.years} {character.role.toUpperCase()}
        </div>
        <h1 style={{ fontSize: 30, fontWeight: 'bold', margin: 0, letterSpacing: 2, color: '#F8FAFC' }}>
          {character.nickname}
        </h1>
        <p style={{ color: '#334155', fontSize: 12, marginTop: 8 }}>
          이직 퀘스트 · Spring AI 검사 기반
        </p>
      </div>

      <div
        style={{
          background: 'rgba(78,205,196,0.04)',
          border: '1px solid rgba(78,205,196,0.15)',
          borderRadius: 10,
          padding: '10px 16px',
          marginBottom: 20,
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          fontSize: 12,
        }}
      >
        <span style={{ color: '#4ECDC4' }}>🔌</span>
        <span style={{ color: '#475569' }}>Spring Boot 4.x + Spring AI 연동 필요</span>
        <span style={{ color: '#1E293B', marginLeft: 'auto' }}>localhost:8080</span>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {ACTS.map((act, i) => {
          const progress = getActProgress(act)
          const prevAct = ACTS[i - 1]
          const locked = i > 0 && prevAct != null && getActProgress(prevAct) < ACT_UNLOCK_THRESHOLD

          return (
            <ActCard
              key={act.id}
              act={act}
              progress={progress}
              locked={locked}
              onClick={() => onSelectAct(act)}
            />
          )
        })}
      </div>

      <button
        onClick={onOpenCoach}
        style={{
          width: '100%',
          marginTop: 12,
          background: 'rgba(167,139,250,0.08)',
          border: '1px solid rgba(167,139,250,0.25)',
          borderRadius: 10,
          padding: '14px 20px',
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          cursor: 'pointer',
          fontFamily: "'Courier New', monospace",
        }}
      >
        <span style={{ fontSize: 22 }}>🎯</span>
        <div style={{ textAlign: 'left' }}>
          <p style={{ fontSize: 13, fontWeight: 700, color: '#A78BFA', margin: 0 }}>전담 면접 코치</p>
          <p style={{ fontSize: 11, color: '#475569', margin: '2px 0 0' }}>JD 분석부터 종합 평가까지</p>
        </div>
        <span style={{ marginLeft: 'auto', color: '#475569', fontSize: 16 }}>→</span>
      </button>

      <StatsPanel completedCount={completedCount} />
    </div>
  )
}
