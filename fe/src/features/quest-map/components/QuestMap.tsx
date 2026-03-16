import type { Act } from '@/types/quest.types'
import { ACTS, ACT_UNLOCK_THRESHOLD } from '../constants/questData'
import { ActCard } from './ActCard'
import { StatsPanel } from './StatsPanel'

interface QuestMapProps {
  onSelectAct: (act: Act) => void
  completed: Record<string, boolean>
  getActProgress: (act: Act) => number
}

export function QuestMap({ onSelectAct, completed, getActProgress }: QuestMapProps) {
  const completedCount = Object.keys(completed).length

  return (
    <div style={{ animation: 'slideIn 0.4s ease' }}>
      <div style={{ textAlign: 'center', padding: '40px 0 24px' }}>
        <div style={{ fontSize: 10, letterSpacing: 6, color: '#1E293B', marginBottom: 5 }}>
          5YR BACKEND DEVELOPER
        </div>
        <h1 style={{ fontSize: 30, fontWeight: 'bold', margin: 0, letterSpacing: 2, color: '#F8FAFC' }}>
          이직 퀘스트
        </h1>
        <p style={{ color: '#334155', fontSize: 12, marginTop: 8 }}>
          Spring AI 기반 퀘스트 검사 · 퀘스트 완료로 이직 성공
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

      <StatsPanel completedCount={completedCount} />
    </div>
  )
}
